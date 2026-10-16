# Base de datos

PostgreSQL. En producción: **Supabase** (plan gratuito); la app se despliega en **Render**.

## Esquema y migraciones (Flyway)

- El esquema vive en `src/main/resources/db/migration/` y lo aplica **Flyway** al arrancar la app.
- Hibernate **no** modifica tablas: `ddl-auto=validate` solo verifica que las entidades coincidan con el esquema. Si no coinciden, la app no arranca (mejor que descubrirlo con datos rotos).
- Para cambiar el modelo: modificar la entidad **y** agregar una migración nueva `V2__descripcion.sql`, `V3__...`. **Nunca editar una migración ya aplicada** (Flyway lo detecta por checksum y falla).
- Las migraciones tienen que correr en PostgreSQL y en H2 modo PostgreSQL (dev y tests usan H2 con las mismas migraciones). SQL estándar alcanza.
- No hay datos precargados. El único dato inicial es el admin, que crea `AdminBootstrap` si está `APP_ADMIN_PASSWORD`.

## Entornos

| Entorno | Base | Cómo |
| ------- | ---- | ---- |
| Tests (`./mvnw test`) | H2 en memoria, modo PostgreSQL | perfil `test` |
| Desarrollo rápido | H2 en memoria, modo PostgreSQL | perfil `dev` (admin/admin123) |
| Desarrollo con Postgres | Postgres local o Docker | sin perfil + `DB_URL` |
| Producción | Supabase | sin perfil + variables de entorno |

Postgres local con Docker:

```bash
docker run -d --name ftms-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=football_tournament -p 55432:5432 postgres:16-alpine
DB_URL=jdbc:postgresql://localhost:55432/football_tournament ./mvnw spring-boot:run
```

## Conectar Supabase

1. Crear el proyecto en Supabase y guardar la contraseña de la base.
2. En el dashboard: **Connect** → **Session pooler** (no "Direct connection": la conexión directa es solo IPv6 y Render no sale por IPv6).
3. Pasar los datos a las variables de entorno de la app (en Render: *Environment*):

| Variable | Valor |
| -------- | ----- |
| `DB_URL` | `jdbc:postgresql://aws-0-<región>.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.<project-ref>` |
| `DB_PASSWORD` | la contraseña de la base |
| `DB_POOL_SIZE` | opcional, default `5` (el plan gratis limita conexiones) |

El host exacto, la región y el usuario los muestra Supabase en esa pantalla. Usar el puerto **5432** (modo sesión); el modo transacción (6543) no se lleva bien con los prepared statements de JDBC.

La primera vez que la app arranca contra Supabase, Flyway crea todas las tablas.
