# Seguridad

La app guarda datos personales (DNI de jugadores, mail y teléfono de capitanes), así que la protección va en capas:
si una falla, la siguiente sigue en pie. Este documento dice qué hay en cada capa, cómo comprobarlo y qué falta.

```
internet ─► Caddy ─────────► nginx ─────────────► API (Spring Boot) ───────► PostgreSQL
            HTTPS, HSTS      límites por IP        validación, JWT,          red interna,
                             cabeceras, CSP        permisos por equipo       sin internet
            └──────────── contenedores sin root, sin capabilities, solo lectura ──────────┘
```

## Modelo de amenazas

Antes de defender hay que saber de quién y qué. Lo que vale en esta app son los **datos personales** (DNI,
teléfonos, mails) y la **cuenta del admin**, que puede cambiar resultados y planteles.

| Quién | Qué querría | Qué lo frena |
|---|---|---|
| Bot de internet (escanea todo, sin objetivo) | Paneles abiertos, versiones con fallas conocidas, contraseñas por defecto | Solo 22/80/443 abiertos; nada interno expuesto; imágenes escaneadas cada semana; la API no arranca con claves de ejemplo |
| Alguien que quiere entrar como admin | Adivinar la contraseña, falsificar un token | Límite por IP y bloqueo por usuario; contraseña de 12+; JWT firmado y verificado |
| Un capitán curioso o con mala intención | Ver o tocar otros equipos, ver DNI ajenos | Cada operación verifica que el equipo sea suyo; tests que lo intentan |
| Alguien que sube archivos o textos armados | XSS, ejecutar código, tirar el servidor | Imágenes redibujadas, límites de tamaño, CSP, texto siempre mostrado como texto |
| Alguien que llega a ejecutar código en un contenedor (un bug futuro) | Pasar a otros servicios, sacar los datos | Sin root, sin capabilities, solo lectura, redes separadas y sin internet, usuario de base sin poder |
| Quien consiga el bucket de backups | Leer los DNI | Backups cifrados; la clave privada no está en el servidor |
| Una dependencia o action comprometida | Meter código en la imagen o robar el token de publicación | Versiones fijas, actions de terceros por hash, escaneo de imágenes y de secretos |

Fuera de alcance (lo cubre el proveedor): acceso físico al servidor u Oracle mismo. Alguien con la cuenta de GitHub o
de Oracle del admin podría todo: por eso el 2FA en esas cuentas es lo primero de *Lo que falta*.

## 1. Aplicación (backend)

| Ataque | Defensa | Dónde |
|---|---|---|
| **Inyección SQL** | Todas las consultas son de Spring Data o JPQL con parámetros (`:id`); no hay SQL armado concatenando texto. El valor viaja separado de la consulta y la base nunca lo interpreta como código | `repo/` |
| **Datos inválidos o gigantes** | Cada campo que llega tiene límite de largo y formato (`@Size`, `@Pattern`, `@Email`), igual o menor que la columna de la base. Las listas también (un plantel, 60 jugadores como máximo) | `dto/` |
| **Caracteres invisibles** | Todo texto JSON pierde los caracteres de control (PostgreSQL rechaza `\u0000` con un error 500) y los de dirección bidireccional (U+202E hace que un nombre se lea al revés) | [`TextSanitizing`](../src/main/java/com/mjt/Football_Tournament_Management_System/config/TextSanitizing.java) |
| **XSS** (meter un `<script>`) | Vue siempre muestra los datos como texto (no hay `v-html`), y la CSP de nginx no deja correr scripts que no vengan del propio sitio. El HTML no se borra en el backend a propósito: se mostraría tal cual y borrarlo solo corrompería datos | frontend + nginx |
| **Imágenes maliciosas** | El formato se detecta por el contenido, no por lo que dice el navegador. Se lee el tamaño antes de decodificar (un PNG de 1 KB que dice medir 50000×50000 ocuparía 10 GB de memoria). Lo que se guarda es la imagen **redibujada**: sin metadatos (la ubicación GPS de una foto de celular) ni nada escondido después de la imagen | [`SafeImages`](../src/main/java/com/mjt/Football_Tournament_Management_System/service/SafeImages.java) |
| **Contraseñas larguísimas** | bcrypt solo usa los primeros 72 **bytes** (una "ñ" ocupa 2). Una más larga se rechaza con un 400 claro al registrarse y cuenta como contraseña incorrecta al entrar; antes daba un error 500 | [`PasswordBytes`](../src/main/java/com/mjt/Football_Tournament_Management_System/dto/PasswordBytes.java) |
| **Adivinar contraseñas** | BCrypt para guardarlas (con *salt* aleatorio por contraseña: dos iguales quedan distintas y no sirven tablas precalculadas); bloqueo de 5 minutos tras 5 intentos fallidos por usuario (con tope de memoria), y nginx corta por IP | `security/` |
| **Tokens falsos** | JWT firmado con HMAC-SHA256 y una clave de 32+ caracteres del entorno; se rechaza un token sin firma (`alg: none`), firmado con otra clave, de otro emisor o vencido | [`JwtService`](../src/main/java/com/mjt/Football_Tournament_Management_System/security/JwtService.java) |
| **Clave o contraseña de ejemplo** | La API no arranca si la clave de los JWT falta, es el valor de `.env.example` (público en GitHub: con él cualquiera firmaría tokens de admin) o es trivial (`aaaa…`), ni si la contraseña del admin inicial tiene menos de 12 caracteres | `JwtService`, `AdminBootstrap` |
| **Averiguar qué usuarios existen** | El login responde lo mismo para un usuario que no existe que para una contraseña incorrecta | `GlobalExceptionHandler` |
| **Mails inventados** | Además del formato, el dominio tiene que tener punto (`ana@localhost` no pasa). Verificar que el mail sea de verdad requiere mandar un correo (no hay servidor de correo); los capitanes igual pasan por la aprobación del admin | `dto/` |
| **Ver o tocar datos ajenos** | Cada operación de un capitán verifica que el equipo sea suyo (`ownedTeam`): plantel, jugadores, escudo, inscripción, resumen. `/api/admin/**` exige rol de admin, y no hay forma de crear otro admin desde la API. Los DNI, mails y teléfonos nunca salen en las respuestas públicas | `service/`, `SecurityConfig` |
| **Otros sitios usando tu sesión** | CORS solo permite el dominio de la app. Además la sesión va en un token que el frontend manda a mano (no en una cookie), así que otro sitio no puede hacer pedidos "en nombre" de un usuario logueado | `SecurityConfig` |
| **Mensajes de error que filtran** | Los errores devuelven un mensaje genérico en castellano, nunca el stack trace ni el SQL | `GlobalExceptionHandler` |

Los tests de [`SecurityHardeningApiTests`](../src/test/java/com/mjt/Football_Tournament_Management_System/SecurityHardeningApiTests.java)
prueban estos ataques en cada push:
- recorren todos los endpoints públicos buscando DNI, mail o teléfono;
- intentan cada operación sobre el equipo de otro capitán;
- piden permiso de CORS desde otro dominio;
- mandan tokens falsificados, claves de ejemplo, mails sin dominio y textos gigantes o con caracteres invisibles;
- suben SVG con scripts, imágenes con HTML escondido y "bombas" de descompresión.

## 2. Borde (nginx y Caddy)

- **Tamaño de los pedidos:** 1 MB como máximo (cualquier JSON de la app pesa pocos KB); solo las cuatro rutas de
  subida de archivos aceptan hasta 12 MB. Lo más grande se corta en nginx (413) sin llegar al backend.
- **Límites de pedidos por IP:** login, registro e invitaciones, 10 por minuto; el resto de la API, 20 por segundo
  con margen para ráfagas. Arriba de eso, 429. La IP real viene del `X-Forwarded-For` que agrega Caddy, y nginx solo
  le cree a redes privadas: alguien de afuera no puede inventarse una IP distinta en cada intento.
- **Cabeceras de seguridad** en todas las respuestas ([`nginx.conf`](../frontend/nginx.conf)):
  - **CSP:** scripts, estilos, fuentes y llamadas solo del propio sitio. Las fuentes se sirven desde el sitio
    y no desde Google, así ningún tercero ve las visitas.
  - `X-Frame-Options` / `frame-ancestors`: ningún sitio puede meter la app en un frame.
  - `nosniff`, `Referrer-Policy`, `Permissions-Policy`: sin cámara, micrófono ni ubicación.
  - `Cross-Origin-Opener/Resource-Policy`.
- **Caché de 30 segundos para los datos públicos** (tablas, fixture, goleadores): la mayoría de las visitas no llega a
  Java. Si llegan muchas juntas, una sola va al backend (*thundering herd*); si el backend se cae, se sigue mostrando
  lo último (*graceful degradation*). Un pedido con token (alguien logueado) nunca se guarda ni se sirve de la caché,
  así no se mezclan datos de usuarios.
- **HSTS** en Caddy: después de la primera visita, el navegador usa solo HTTPS durante un año. Caddy además oculta
  qué servidor es.
- **Nada interno expuesto:** Swagger apagado y sin ruta en nginx; métricas en otro puerto; Grafana y Prometheus
  solo en `127.0.0.1` del servidor.

## 3. Contenedores

En [`deploy/docker-compose.yml`](../deploy/docker-compose.yml) (y en el compose local, para que dev se parezca a prod):

| Medida | Qué evita |
|---|---|
| **Sin root**: la API corre como `app`, nginx entero como `nginx` (por eso escucha en 8080) | Un atacante que entre por un bug no es root dentro del contenedor |
| `cap_drop: ALL` | Los poderes sueltos de root (cambiar dueños de archivos, abrir puertos bajos…). Solo Postgres y Caddy recuperan los que necesitan, por nombre |
| `no-new-privileges` | Ganar privilegios después de arrancar (binarios setuid, sudo) |
| `read_only: true` + `tmpfs` | Modificar la imagen o dejar herramientas instaladas: solo se escribe en el volumen de datos y en carpetas temporales en memoria |
| `pids_limit` | Que un proceso desbocado (o una *fork bomb*) congele el servidor |
| **Redes separadas** | Caddy no ve la base; la base y la API no tienen salida a internet (redes `internal`), así que una API comprometida no puede bajar herramientas ni sacar datos |

Comprobado con el stack de producción levantado localmente: Caddy no resuelve `db`, la API no llega a internet, el
filesystem de la API es de solo lectura y sus capabilities efectivas son `0`.

## 4. Datos

- **Usuarios de base con lo justo** ([`db-roles.sh`](../deploy/db-roles.sh)):

  | Usuario | Para qué | Puede |
  |---|---|---|
  | `postgres` | Crear la base y los backups; la API nunca recibe su contraseña | Todo |
  | `torneo_owner` | Flyway, al arrancar la API | Crear y cambiar tablas de esta base |
  | `torneo_app` | Cada pedido de la app | Leer y escribir filas. **No** borra ni cambia tablas, no lee archivos del servidor, no ejecuta programas |

  Así, una inyección SQL que apareciera en el futuro quedaría limitada a filas, sin `DROP TABLE` ni
  `COPY ... TO PROGRAM` (ejecutar comandos en el servidor). Comprobado conectándose como `torneo_app`. Una base
  nueva crea los usuarios sola; en una existente se corre una vez (ver [deploy.md](deploy.md#usuarios-de-la-base)).

- **Backups cifrados fuera del servidor:** [`backup.sh`](../deploy/backup.sh) cifra con una **clave pública** la copia
  que sube al bucket. El servidor puede cerrar el archivo pero no abrirlo; solo la clave privada (que no está en el
  servidor) lo descifra. Sin `backup-key.asc` no sube nada: un backup con DNI nunca sale del servidor sin cifrar.
- La copia local queda sin cifrar, con permisos solo para el usuario (`umask 077`, carpeta `700`): quien tome el
  servidor ya tiene la base viva, y así se puede restaurar rápido después de un deploy fallido.
- El disco de la VM está cifrado por Oracle, y todo el tráfico va por HTTPS.

### La clave de los backups

Se creó con GnuPG en la PC del administrador (`Torneo backups`, ed25519/cv25519). **Si se pierde la clave privada,
los backups del bucket no se pueden abrir nunca más.** Por eso:

```bash
gpg --passwd "Torneo backups"                      # ponerle una frase de contraseña (se crea sin ella)
gpg --armor --export-secret-keys "Torneo backups" > torneo-backups-PRIVADA.asc
#   guardar ese archivo fuera de la PC (gestor de contraseñas, pendrive) y borrarlo del disco
gpg --armor --export "Torneo backups" > backup-key.asc   # la pública: esta va al servidor
scp backup-key.asc ubuntu@<servidor>:/opt/torneo/
```

Restaurar una copia del bucket: bajarla desde la consola de Oracle (la URL del servidor solo sube), descifrarla en la
PC y seguir como siempre:

```bash
gpg --decrypt torneo-AAAAMMDD-HHMMSS.sql.gz.gpg > torneo-AAAAMMDD-HHMMSS.sql.gz
scp torneo-AAAAMMDD-HHMMSS.sql.gz ubuntu@<servidor>:/opt/torneo/backups/
ssh ubuntu@<servidor> /opt/torneo/restore.sh backups/torneo-AAAAMMDD-HHMMSS.sql.gz
```

## 5. Cadena de suministro (CI)

[`security.yml`](../.github/workflows/security.yml) corre en cada push y todos los lunes, porque cada día se publican
CVE nuevos:

- **Trivy** arma las imágenes y busca vulnerabilidades conocidas en todo lo que llevan adentro: paquetes de Alpine, la
  JRE y cada librería del jar. Falla si hay alguna alta o crítica que ya tenga arreglo. También revisa los Dockerfiles
  y los compose. Las excepciones van en [`.trivyignore`](../.trivyignore), cada una con su motivo.
- **CodeQL** (análisis estático de GitHub) lee el código Java y TypeScript buscando patrones vulnerables. Los
  hallazgos aparecen en la pestaña *Security* del repo.
- **gitleaks** revisa **todo el historial** de git buscando contraseñas, claves o tokens commiteados por error (un
  secreto borrado en un commit posterior sigue en el viejo). Hoy: 0 en 119 commits.
- Las actions de terceros (las de Docker, que reciben el permiso de publicar imágenes) están fijadas por **hash de
  commit**, no por tag: un tag se puede mover a otro código, un hash no.
- **Dependabot** ([`dependabot.yml`](../.github/dependabot.yml)) abre un pull request por semana con las dependencias
  nuevas: Maven, npm, imágenes base, imágenes del compose y actions.

El primer escaneo encontró **60 vulnerabilidades altas o críticas** en la imagen web (la base `nginx:1.27-alpine` era
vieja) y varias en Jackson, Tomcat y el driver de PostgreSQL. Spring Boot 3.5 ya no recibe parches gratuitos, así que
esas tres versiones se fuerzan en el `pom.xml`. Hoy las dos imágenes dan 0.

## Conceptos de seguridad, uno por uno

| Concepto | Estado |
|---|---|
| SQL Injection | ✅ consultas parametrizadas; además el usuario de la app no puede borrar tablas |
| XSS | ✅ Vue escapa todo, sin `v-html`; CSP estricta |
| CSRF | ✅ no aplica: la sesión va en un token que se manda a mano, no en una cookie |
| CORS | ✅ solo el dominio de la app (test) |
| SSRF | ✅ la API no hace pedidos salientes, y su red no tiene internet |
| IDOR / autorización del lado del servidor | ✅ `ownedTeam` en cada operación de capitán (test con 10 intentos) |
| Path Traversal | ✅ no se usan archivos del disco; las imágenes viven en la base |
| Mass Assignment | ✅ los pedidos se leen en DTOs, nunca directo en entidades |
| Clickjacking, CSP, cabeceras | ✅ nginx (sección 2) |
| Validación de entrada | ✅ largo y formato en cada campo; caracteres invisibles fuera |
| RBAC / mínimo privilegio | ✅ roles ADMIN y CAPTAIN; usuarios de base separados; contenedores sin root |
| Row-Level Security | ➖ no hace falta: el control por equipo está en el código, con tests |
| Secretos | ✅ `.env` con permisos 600 e ignorado por git; GitHub Secrets en el CI; gitleaks en el historial |
| Rotación de claves | 🟡 a mano: cambiar `APP_JWT_SECRET` y reiniciar cierra todas las sesiones; la URL del bucket vence al año |
| Hashing vs cifrado, salt, bcrypt | ✅ contraseñas con bcrypt (hash con salt: no se pueden descifrar); backups cifrados (sí se descifran, con la clave) |
| Vencimiento del JWT | ✅ 3 horas; sin refresh tokens (se vuelve a entrar) |
| OAuth / OIDC | ➖ no hace falta: usuarios propios, sin "entrar con Google" |
| Session fixation | ➖ no aplica: no hay sesión en el servidor |
| MFA | 🟡 no en la app (ver abajo); sí conviene en GitHub y Oracle |
| Fuerza bruta, enumeración, timing | ✅ límites por IP y por usuario; mismo mensaje y mismo tiempo para usuario inexistente o contraseña mal (Spring compara igual contra un hash de mentira) |
| Subida de archivos | ✅ sección 1 |
| Webhooks firmados | ➖ no hay webhooks |
| Vencimiento del certificado, HSTS | ✅ Caddy lo renueva; el monitor de uptime avisa si quedan menos de 14 días |
| Cifrado en reposo | ✅ disco de Oracle y backups cifrados |
| Dependencias y cadena de suministro | ✅ Trivy, Dependabot, lockfile de npm, versiones fijas, actions por hash |
| Límite de tamaño de pedidos | ✅ 1 MB; archivos, 12 MB |
| Rotación de logs | ✅ 3 × 10 MB por contenedor (también limita cuánto tiempo se guardan las IPs) |

## Lo que falta

- **Migrar a Spring Boot 4.** La rama 3.5 ya no tiene soporte gratuito. Dos CVE críticos de Spring Framework 6.2 están
  arreglados solo en la 7: hoy no aplican (requieren vistas XSLT o Server-Sent Events, que la app no usa), pero el
  próximo puede aplicar.
- **Al pasar a producción**, en el servidor:
  - copiar la clave pública (`backup-key.asc`) y el `backup.sh` nuevo;
  - crear los usuarios de la base (`db-roles.sh`, una vez) y agregar sus contraseñas al `.env`;
  - `X11Forwarding no` en SSH;
  - apagar `rpcbind`, que no se usa, aunque el firewall ya bloquea su puerto.
- **Cifrar columnas** (DNI) dentro de la base: protegería contra un volcado robado de la base, pero la clave tendría
  que estar en el mismo servidor, y buscar por DNI (transferencias, sancionados) exigiría un índice aparte. Con los
  backups cifrados y la base sin acceso desde afuera, el beneficio extra es chico frente a la complejidad.
- **2FA en las cuentas que mandan:** GitHub (puede cambiar el código y desplegar) y Oracle (puede borrar el servidor).
  Es lo más importante de esta lista y no requiere código.
- **Proteger la rama `main`** en GitHub: que no se pueda pushear sin que pase el CI.
- **MFA para el admin de la app** (un código de una app tipo Google Authenticator): es la cuenta de más poder. Se puede
  agregar; con contraseña de 12+ y límites de intentos, el riesgo hoy es bajo.
- **Vencimiento de las invitaciones** de capitán: hoy valen hasta que se usan.
- **Revocar sesiones:** un JWT vale hasta que vence (3 horas); no hay forma de cerrarlo antes.
