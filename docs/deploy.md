# Deploy

Producción corre en un servidor Linux con Docker. El servidor **no compila nada**: baja las imágenes que el CI ya
publicó en `ghcr.io` (ver [ci.md](ci.md)) y las levanta con la carpeta [`deploy/`](../deploy), que es lo único que se
copia al servidor.

```
internet ──► caddy (:80 y :443, HTTPS) ──► web (nginx) ──► api (Spring Boot) ──► db (PostgreSQL)
```

| Archivo | Para qué |
|---|---|
| `docker-compose.yml` | Los mismos servicios que el compose de la raíz, con imágenes de `ghcr.io` en vez de `build`, Caddy adelante y logs con tope de tamaño |
| `Caddyfile` | Caddy consigue y renueva solo el certificado HTTPS de Let's Encrypt para `DOMAIN` |
| `.env.example` | Variables (copiar a `.env` en el servidor) |
| `deploy.sh` | Pone online una versión: `./deploy.sh sha-14f7f04` |
| `backup.sh` / `restore.sh` | Dump comprimido de la base / volver a un dump |

Diferencias con el compose local de la raíz:

- **Imágenes en vez de build.** Lo que corre en producción es exactamente lo que pasó los tests del CI, identificado
  por el commit (`TAG=sha-…`). Volver atrás es desplegar el tag anterior.
- **Solo Caddy publica puertos** (80 y 443). `web`, `api` y `db` solo se ven entre ellos.
- **Swagger siempre apagado** y CORS fijo a `https://DOMAIN`.
- **Logs rotados** (3 × 10 MB por contenedor): por defecto Docker los guarda para siempre y llenan el disco.

## Primer deploy

Requisitos: un servidor Linux con Docker, los puertos 80 y 443 abiertos, y un dominio apuntando a su IP (sirve un
subdominio gratis de [DuckDNS](https://www.duckdns.org)).

```bash
# desde tu máquina: copiar la carpeta al servidor
scp -r deploy/ usuario@IP:/opt/torneo

# en el servidor
cd /opt/torneo
cp .env.example .env && nano .env    # DOMAIN, ACME_EMAIL, contraseñas y secretos
chmod +x *.sh
./deploy.sh                          # baja las imágenes, levanta todo y espera los healthchecks
```

La primera vez Caddy pide el certificado (unos segundos) y la app queda en `https://DOMAIN`.

## Actualizar y volver atrás

Cada push a `main` que pasa el CI publica imágenes con el tag `sha-<commit>`.

```bash
./deploy.sh sha-abc1234     # actualizar a ese commit (queda guardado en .env)
./deploy.sh sha-14f7f04     # rollback: el mismo comando con el tag anterior
```

`deploy.sh` usa `docker compose up --wait`: si algún healthcheck no pasa, termina con error en vez de dejarlo a medias
sin avisar. Flyway aplica las migraciones nuevas al arrancar la API. Ojo: un rollback **no deshace migraciones**.

## Backups

```bash
./backup.sh                                       # backups/torneo-AAAAMMDD-HHMMSS.sql.gz
./restore.sh backups/torneo-20261026-040000.sql.gz
```

Para que corra todas las noches, con `crontab -e` en el servidor:

```
0 4 * * * /opt/torneo/backup.sh >> /opt/torneo/backups/backup.log 2>&1
```

Un backup que vive en el mismo servidor no sirve si se pierde el servidor: hay que copiarlo afuera también (por
ejemplo bajarlo con `scp`, o subirlo a un bucket).

## Probarlo local

El mismo stack se puede levantar en la propia máquina con `DOMAIN=localhost`. Caddy usa un certificado propio (el
navegador avisa que no es de confianza) en vez de Let's Encrypt:

```bash
cd deploy
cp .env.example .env    # DOMAIN=localhost, ACME_EMAIL cualquiera, contraseñas
./deploy.sh             # https://localhost
docker compose down     # (-v borra también la base y los certificados)
```
