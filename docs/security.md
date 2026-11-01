# Seguridad

La app guarda datos personales (DNI de jugadores, mail y teléfono de capitanes), así que la protección va en capas:
si una falla, la siguiente sigue en pie. Este documento dice qué hay en cada capa, cómo comprobarlo y qué falta.

```
internet ─► Caddy ─────────► nginx ─────────────► API (Spring Boot) ───────► PostgreSQL
            HTTPS, HSTS      límites por IP        validación, JWT,          red interna,
                             cabeceras, CSP        permisos por equipo       sin internet
            └──────────── contenedores sin root, sin capabilities, solo lectura ──────────┘
```

## 1. Aplicación (backend)

| Ataque | Defensa | Dónde |
|---|---|---|
| **Inyección SQL** | Todas las consultas son de Spring Data o JPQL con parámetros (`:id`); no hay SQL armado concatenando texto. El valor viaja separado de la consulta y la base nunca lo interpreta como código | `repo/` |
| **Datos inválidos o gigantes** | Cada campo que llega tiene límite de largo y formato (`@Size`, `@Pattern`, `@Email`), igual o menor que la columna de la base. Las listas también (un plantel, 60 jugadores como máximo) | `dto/` |
| **Caracteres invisibles** | Todo texto JSON pierde los caracteres de control (PostgreSQL rechaza `\u0000` con un error 500) y los de dirección bidireccional (U+202E hace que un nombre se lea al revés) | [`TextSanitizing`](../src/main/java/com/mjt/Football_Tournament_Management_System/config/TextSanitizing.java) |
| **XSS** (meter un `<script>`) | Vue siempre muestra los datos como texto (no hay `v-html`), y la CSP de nginx no deja correr scripts que no vengan del propio sitio. El HTML no se borra en el backend a propósito: se mostraría tal cual y borrarlo solo corrompería datos | frontend + nginx |
| **Imágenes maliciosas** | El formato se detecta por el contenido, no por lo que dice el navegador. Se lee el tamaño antes de decodificar (un PNG de 1 KB que dice medir 50000×50000 ocuparía 10 GB de memoria). Lo que se guarda es la imagen **redibujada**: sin metadatos (la ubicación GPS de una foto de celular) ni nada escondido después de la imagen | [`SafeImages`](../src/main/java/com/mjt/Football_Tournament_Management_System/service/SafeImages.java) |
| **Adivinar contraseñas** | BCrypt para guardarlas; bloqueo de 5 minutos tras 5 intentos fallidos por usuario (con tope de memoria), y nginx corta por IP | `security/` |
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
- **Dependabot** ([`dependabot.yml`](../.github/dependabot.yml)) abre un pull request por semana con las dependencias
  nuevas: Maven, npm, imágenes base, imágenes del compose y actions.

El primer escaneo encontró **60 vulnerabilidades altas o críticas** en la imagen web (la base `nginx:1.27-alpine` era
vieja) y varias en Jackson, Tomcat y el driver de PostgreSQL. Spring Boot 3.5 ya no recibe parches gratuitos, así que
esas tres versiones se fuerzan en el `pom.xml`. Hoy las dos imágenes dan 0.

## Lo que falta

- **Migrar a Spring Boot 4.** La rama 3.5 ya no tiene soporte gratuito. Dos CVE críticos de Spring Framework 6.2 están
  arreglados solo en la 7: hoy no aplican (requieren vistas XSLT o Server-Sent Events, que la app no usa), pero el
  próximo puede aplicar.
- **Al pasar a producción**, en el servidor:
  - copiar la clave pública (`backup-key.asc`) y el `backup.sh` nuevo;
  - `X11Forwarding no` en SSH;
  - apagar `rpcbind`, que no se usa, aunque el firewall ya bloquea su puerto.
- **Cifrar columnas** (DNI) dentro de la base: protegería contra un volcado robado de la base, pero la clave tendría
  que estar en el mismo servidor, y buscar por DNI (transferencias, sancionados) exigiría un índice aparte. Con los
  backups cifrados y la base sin acceso desde afuera, el beneficio extra es chico frente a la complejidad.
- **Base con usuario de mínimos privilegios:** hoy la API usa el usuario dueño de la base; uno que no pueda borrar
  tablas limitaría el daño de un bug.
- **Revocar sesiones:** un JWT vale hasta que vence (3 horas); no hay forma de cerrarlo antes.
