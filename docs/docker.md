# Docker

La app completa corre con Docker Compose: un comando levanta la base, el backend y el frontend.

```
navegador ──► web (nginx :80, publicado en :8080)
                ├── /            archivos estáticos de Vue (y fallback a index.html para las rutas)
                └── /api/…  ──►  api (Spring Boot :8081) ──► db (PostgreSQL :5432)
```

- **db** — `postgres:16-alpine`, datos en el volumen `db-data`. Sin puerto publicado: solo el backend llega a la base.
- **api** — imagen propia (`Dockerfile` en la raíz): Maven compila el jar y una imagen chica con el JRE 21 lo corre,
  con un usuario sin privilegios. Flyway crea/actualiza el esquema al arrancar. Sin puerto publicado.
- **web** — imagen propia (`frontend/Dockerfile`): Node compila Vite y nginx sirve `dist/`, reenvía `/api` al backend
  (así no hay CORS entre frontend y API), cachea los assets con hash y agrega headers de seguridad.
- Cada servicio tiene *healthcheck*; `api` espera a que la base esté sana y `web` a que la API responda.
- Todas las imágenes base existen para `amd64` y `arm64` (la instancia gratis de Oracle Cloud es ARM).

## Configuración

Todo sale de `.env` (copiar `.env.example`; `.env` está en `.gitignore`):

| Variable | Para qué |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales con las que se crea la base la primera vez (cambiarlas después no cambia la base ya creada) |
| `APP_JWT_SECRET` | Firma de los tokens (32+ caracteres; `openssl rand -base64 48`) |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | Admin que se crea al arrancar si no existe |
| `PUBLIC_URL` | URL desde la que se abre la app (CORS) |
| `WEB_PORT` | Puerto de la máquina donde se publica (por defecto 8080) |
| `APP_SWAGGER_ENABLED` | Swagger en `/swagger-ui.html` (dejar en `false` en producción) |

Compose corta con un error claro si falta `DB_PASSWORD` o `APP_JWT_SECRET`.

## Comandos

```bash
docker compose up -d --build       # construir y levantar (o actualizar tras un cambio)
docker compose ps                  # estado y healthchecks
docker compose logs -f api         # logs del backend
docker compose down                # parar (los datos quedan en el volumen)
docker compose down -v             # parar y BORRAR la base
```

Backup y restore de la base:

```bash
docker compose exec -T db pg_dump -U postgres football_tournament > backup.sql
docker compose exec -T db psql -U postgres football_tournament < backup.sql
```

## Desarrollo

Para programar sigue siendo más cómodo sin Docker para la app (recarga en caliente): `./mvnw spring-boot:run` +
`npm run dev` en `frontend/`, con la base que quieras (ver README). El stack de Compose es independiente: tiene su
propio volumen, así que no toca la base local de desarrollo.
