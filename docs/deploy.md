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

## Preparar el servidor

Hoy corre en Oracle Cloud Always Free, en una `VM.Standard.E2.1.Micro` (Ubuntu 24.04, x86_64, 1 GB de RAM). La
instancia y su red se pueden crear con [`infra/oci_launch.py`](../infra/oci_launch.py), que reintenta hasta que haya
capacidad. Una vez creada, por SSH:

```bash
# 2 GB de swap: con 1 GB de RAM, sin swap Linux mata procesos (OOM killer) cuando se llena la memoria
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swap.conf && sudo sysctl -p /etc/sysctl.d/99-swap.conf

sudo apt update && sudo apt upgrade -y        # y sudo reboot si existe /var/run/reboot-required

# Firewall interno: las imágenes de Oracle solo dejan entrar el 22. Antes de instalar Docker, para no guardar sus reglas
sudo iptables -I INPUT 5 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# Docker desde su repositorio oficial (el paquete docker.io de Ubuntu viene atrasado)
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu                # docker sin sudo (vale desde la próxima sesión)

# SSH: sin login directo de root, menos intentos por conexión; fail2ban bloquea 1 h a quien falla 5 veces en 10 min
printf "PermitRootLogin no\nMaxAuthTries 3\nLoginGraceTime 30\n" | sudo tee /etc/ssh/sshd_config.d/99-hardening.conf
sudo sshd -t && sudo systemctl reload ssh
sudo apt install -y fail2ban
printf "[sshd]\nenabled = true\nbackend = systemd\njournalmatch = _SYSTEMD_UNIT=ssh.service\nmaxretry = 5\nfindtime = 10m\nbantime = 1h\n" | sudo tee /etc/fail2ban/jail.d/sshd.local
sudo systemctl restart fail2ban
```

Las contraseñas de SSH ya vienen desactivadas y las actualizaciones automáticas de seguridad (`unattended-upgrades`)
ya vienen activas. Además del firewall interno, en la *Security List* de la VCN tienen que estar abiertos el 80 y el 443.

Con 1 GB hay que limitar la memoria de Java: descomentar `JAVA_OPTS` en `.env` (ver `.env.example`).

## Primer deploy

Requisitos: un servidor Linux con Docker, los puertos 80 y 443 abiertos, y un dominio apuntando a su IP (sirve un
subdominio gratis de [DuckDNS](https://www.duckdns.org)).

```bash
# en el servidor: la carpeta de la app (/opt es de root)
sudo mkdir -p /opt/torneo && sudo chown ubuntu:ubuntu /opt/torneo

# desde tu máquina, dentro de deploy/: los archivos (no el .env local ni los backups)
scp docker-compose.yml Caddyfile .env.example *.sh ubuntu@IP:/opt/torneo/

# en el servidor
cd /opt/torneo
cp .env.example .env && nano .env    # DOMAIN, contraseñas, secretos (y JAVA_OPTS si el server tiene 1 GB)
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

## Deploy automático

El job `deploy` del CI (ver [ci.md](ci.md)) hace lo mismo después de cada push a `main` que pasa los tests: entra por SSH
y pide desplegar `sha-<commit>`. Usa una clave propia, distinta de la personal, atada a un comando forzado en
`~/.ssh/authorized_keys` del servidor:

```
restrict,command="/opt/torneo/deploy-ci.sh" ssh-ed25519 AAAA... github-actions-deploy
```

- `command="…"`: se ejecuta [`deploy-ci.sh`](../deploy/deploy-ci.sh) sin importar lo que pida el cliente. Lo pedido le
  llega en `SSH_ORIGINAL_COMMAND` y solo se acepta si es un tag (`sha-` + hash). Con la clave robada lo único posible es
  desplegar una versión que construyó el CI.
- `restrict`: sin terminal, sin túneles, sin reenvíos.
- Para revocarla, borrar esa línea. El acceso personal sigue funcionando.

Probarla desde una máquina con la clave: `ssh -i torneo_ci ubuntu@IP whoami` tiene que contestar `refused`, y
`ssh -i torneo_ci ubuntu@IP sha-abc1234` despliega.

## Backups

```bash
./backup.sh                                       # backups/torneo-AAAAMMDD-HHMMSS.sql.gz
./restore.sh backups/torneo-20261026-040000.sql.gz
```

Para que corra todas las noches, con `crontab -e` en el servidor (el servidor está en UTC: 07:00 UTC son las 04:00 en Argentina):

```
0 7 * * * /opt/torneo/backup.sh >> /opt/torneo/backups/backup.log 2>&1
```

Un backup que vive en el mismo servidor no sirve si se pierde el servidor: hay que copiarlo afuera también (por
ejemplo bajarlo con `scp`, o subirlo a un bucket).

## Probarlo local

El mismo stack se puede levantar en la propia máquina con `DOMAIN=localhost`. Caddy usa un certificado propio (el
navegador avisa que no es de confianza) en vez de Let's Encrypt:

```bash
cd deploy
cp .env.example .env    # DOMAIN=localhost y contraseñas
./deploy.sh             # https://localhost
docker compose down     # (-v borra también la base y los certificados)
```
