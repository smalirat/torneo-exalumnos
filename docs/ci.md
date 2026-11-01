# CI/CD (GitHub Actions)

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). Corre en cada push y pull request a `main`
(y a mano desde la pestaña Actions).

```
push / PR ──► backend (tests) ─┐
          └─► frontend (build) ─┴─► images (api, web) ──► ghcr.io ──► deploy (demo)   ← estos dos, solo desde main
```

| Job | Qué hace | Si falla… |
|---|---|---|
| **backend** | `./mvnw -B verify`: compila y corre los tests de integración (H2 en memoria, sin base ni secretos) | El commit rompió algo del backend; los reportes de tests quedan como *artifact* del run |
| **frontend** | `npm ci` + `npm run build` (chequeo de tipos con vue-tsc + build de Vite) | Error de tipos o de build |
| **images** | Construye las imágenes `api` y `web` para `amd64` y `arm64`; desde `main` las publica en GitHub Container Registry | Problema en un Dockerfile |
| **deploy** | Entra por SSH al servidor y corre `deploy.sh sha-<commit>`, que baja esas imágenes y espera los healthchecks | No se pudo conectar, o la versión nueva no arrancó sana (la que estaba sigue andando) |

- `images` corre solo si `backend` y `frontend` pasaron (`needs`): no se publica una imagen de código roto. `deploy`
  corre solo si `images` publicó.
- En un pull request las imágenes se construyen (para probar los Dockerfiles) pero no se publican ni se despliegan.
- Tags: `sha-<commit>` siempre y `latest` desde `main`. El servidor despliega por `sha-…`, así cada deploy es un commit
  identificable y volver atrás es desplegar el tag anterior.
- Imágenes públicas: `ghcr.io/smalirat/torneo-api` y `ghcr.io/smalirat/torneo-web`.
- Las etapas de compilación de los Dockerfiles corren en la plataforma de la máquina que construye
  (`--platform=$BUILDPLATFORM`): el jar y los archivos del frontend son iguales para cualquier CPU, así que la imagen ARM
  no compila bajo emulación; solo la etapa final es por plataforma.
- Caches: `~/.m2` (setup-java), `~/.npm` (setup-node) y las capas de Docker (`type=gha`).
- Concurrencia: en un pull request, un push nuevo cancela el run en curso; en `main` los runs hacen fila, para no
  cortar un deploy a la mitad.
- Dos tests se saltean en CI: leen la planilla real de la liga, que no está en el repo (`design/`).

## Permisos y configuración

- El workflow solo lee el código; `images` además puede escribir paquetes, con el `GITHUB_TOKEN` que GitHub da en cada run.
- El deploy usa el *environment* `demo` (historial de deploys y link en la página del repo) y esta configuración, en
  *Settings → Secrets and variables → Actions*:

| Nombre | Tipo | Valor |
|---|---|---|
| `DEPLOY_SSH_KEY` | Secret | Clave privada SSH exclusiva del CI. En el servidor solo puede desplegar un tag (comando forzado, ver [deploy.md](deploy.md#deploy-automático)) |
| `DEPLOY_SSH_HOST` | Variable | Dirección a la que el CI se conecta por SSH |
| `DEPLOY_KNOWN_HOSTS` | Variable | Huella del servidor (`ssh-keyscan -t ed25519 <host>`): el job se niega a hablar con otra máquina |

Un *secret* queda cifrado y GitHub lo tapa en los logs; una *variable* es configuración visible (la IP y la huella del
servidor son públicas). Si falta alguno, el job lo dice y no intenta conectarse.

## Uptime (monitor externo)

[`.github/workflows/uptime.yml`](../.github/workflows/uptime.yml) corre cada 10 minutos desde los servidores de
GitHub, fuera de Oracle: si se cae el servidor entero, este chequeo sigue funcionando (Prometheus y Grafana no).

- Pide la página principal **y** `/api/public/league`. La home sola no alcanza: nginx la sirve aunque la API esté
  caída. Reintenta durante unos 2 minutos para que un deploy (la API reiniciándose) no cuente como caída.
- Revisa que al certificado HTTPS le queden 14 días o más. Caddy lo renueva 30 días antes, así que menos de 14
  significa que la renovación está fallando.
- Si falla, GitHub manda un mail a quien cambió por última vez la línea `cron` (*Settings → Notifications →
  Actions*: "Notify on failed workflows").
- Límites: GitHub puede arrancar los runs programados unos minutos tarde, y los desactiva después de 60 días sin
  commits en el repo. No avisa cuando el sitio vuelve: hay que mirar el próximo run verde.

## Seguridad

[`.github/workflows/security.yml`](../.github/workflows/security.yml) corre en cada push, en cada pull request y
todos los lunes (los CVE nuevos salen todos los días: una imagen limpia la semana pasada puede no estarlo hoy):

| Job | Qué hace |
|---|---|
| **Image vulnerabilities** | Arma las imágenes `api` y `web` y las escanea con Trivy: paquetes de Alpine, la JRE y cada librería del jar. Falla si hay una vulnerabilidad alta o crítica que ya tenga arreglo |
| **Container configuration** | Trivy revisa Dockerfiles y compose (contenedores como root, etc.) |
| **Code analysis** | CodeQL lee el código Java y TypeScript buscando patrones vulnerables; los hallazgos quedan en *Security › Code scanning* |

- No frena el deploy (es otro workflow), pero un rojo acá es para arreglar enseguida. Las excepciones justificadas van
  en [`.trivyignore`](../.trivyignore).
- Trivy corre desde su imagen oficial fijada por *digest* (el hash del contenido): aunque alguien publique otra
  imagen con el mismo tag, el CI sigue usando exactamente la misma.
- **Dependabot** ([`dependabot.yml`](../.github/dependabot.yml)) abre pull requests semanales con versiones nuevas
  (Maven, npm, imágenes base y del compose, actions), y el CI las prueba antes de mergear.

Detalle de todas las capas de seguridad: [security.md](security.md).
