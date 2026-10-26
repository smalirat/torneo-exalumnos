# CI (GitHub Actions)

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). Corre en cada push y pull request a `main`
(y a mano desde la pestaña Actions).

```
push / PR ──► backend (tests) ─┐
          └─► frontend (build) ─┴─► images (api, web) ──► ghcr.io  (solo desde main)
```

| Job | Qué hace | Si falla… |
|---|---|---|
| **backend** | `./mvnw -B verify`: compila y corre los tests de integración (H2 en memoria, sin base ni secretos) | El commit rompió algo del backend; los reportes de tests quedan como *artifact* del run |
| **frontend** | `npm ci` + `npm run build` (chequeo de tipos con vue-tsc + build de Vite) | Error de tipos o de build |
| **images** | Construye las imágenes `api` y `web` para `amd64` y `arm64`; desde `main` las publica en GitHub Container Registry | Problema en un Dockerfile |

- `images` corre solo si `backend` y `frontend` pasaron (`needs`): no se publica una imagen de código roto.
- En un pull request las imágenes se construyen (para probar los Dockerfiles) pero no se publican.
- Tags: `sha-<commit>` siempre y `latest` desde `main`. El deploy va a usar estas imágenes en vez de compilar en el servidor.
- Imágenes: `ghcr.io/smalirat/torneo-api` y `ghcr.io/smalirat/torneo-web` (privadas por defecto; se ven en la pestaña
  Packages del perfil/repositorio).
- Las etapas de compilación de los Dockerfiles corren en la plataforma de la máquina que construye
  (`--platform=$BUILDPLATFORM`): el jar y los archivos del frontend son iguales para cualquier CPU, así que la imagen ARM
  (Oracle Cloud) no compila bajo emulación; solo la etapa final es por plataforma.
- Caches: `~/.m2` (setup-java), `~/.npm` (setup-node) y las capas de Docker (`type=gha`).
- Permisos mínimos: el workflow solo lee el código; `images` además puede escribir paquetes. El token
  (`GITHUB_TOKEN`) lo da GitHub en cada run: no hay secretos que crear.
- Dos tests se saltean en CI: leen la planilla real de la liga, que no está en el repo (`design/`).
