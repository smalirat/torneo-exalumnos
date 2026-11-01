#!/bin/sh
# Database users with the least privilege each one needs (runs inside the db container, as the superuser):
#   torneo_owner  owns the tables: Flyway uses it to run the migrations when the API starts
#   torneo_app    the API's everyday user: reads and writes rows, cannot drop or change tables
#   postgres      the superuser: only creates the database and takes backups; the API never gets its password
# If a bug ever let someone run their own SQL through the API, they would get torneo_app: no dropping tables, no
# reading the server's files or running programs (COPY ... PROGRAM needs a superuser).
#
# A new database volume runs it by itself (mounted in /docker-entrypoint-initdb.d). On an existing database, run it
# once:  docker compose exec db sh /docker-entrypoint-initdb.d/10-roles.sh
# It can be run again: it creates what is missing and resets the passwords from the environment.
# No "set -e" here: the postgres image sources (not runs) init scripts, and options would leak into its entrypoint
: "${DB_OWNER_PASSWORD:?Set DB_OWNER_PASSWORD}"
: "${DB_APP_PASSWORD:?Set DB_APP_PASSWORD}"

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" -v owner_pw="$DB_OWNER_PASSWORD" -v app_pw="$DB_APP_PASSWORD" <<'SQL' || exit 1
SELECT NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'torneo_owner') AS missing_owner,
       NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'torneo_app') AS missing_app \gset
\if :missing_owner
  CREATE ROLE torneo_owner LOGIN;
\endif
\if :missing_app
  CREATE ROLE torneo_app LOGIN;
\endif
-- No superuser, no creating databases or roles, for either of them
ALTER ROLE torneo_owner NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'owner_pw';
ALTER ROLE torneo_app NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_pw';

-- The owner owns the database, its schema and everything already in it (an existing database was created by postgres)
ALTER DATABASE :"db" OWNER TO torneo_owner;
ALTER SCHEMA public OWNER TO torneo_owner;
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT c.relname, c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
             AND pg_get_userbyid(c.relowner) <> 'torneo_owner'
             -- sequences that belong to a column move with their table
             AND NOT (c.relkind = 'S' AND EXISTS (SELECT FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i')))
  LOOP
    EXECUTE format('ALTER %s public.%I OWNER TO torneo_owner',
                   CASE r.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
                   r.relname);
  END LOOP;
END $$;

-- Nobody else may connect or create anything; the app may connect and use the schema
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO torneo_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO torneo_app;

-- Rows only: the tables that exist now, and the ones future migrations create
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO torneo_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO torneo_app;
ALTER DEFAULT PRIVILEGES FOR ROLE torneo_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO torneo_app;
ALTER DEFAULT PRIVILEGES FOR ROLE torneo_owner IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO torneo_app;
SQL
echo "database roles ready: torneo_owner (migrations), torneo_app (the API)"
