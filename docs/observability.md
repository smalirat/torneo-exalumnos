# Observabilidad

Poder contestar *"¿anda bien? y si no, ¿por qué?"* sin entrar al servidor a revisar a mano. Todo corre en el mismo
servidor, con la memoria acotada para no quitarle a la app:

```
api (Spring Boot) :8082/actuator/prometheus ──┐
node-exporter     :9100 (CPU, RAM, disco…) ───┼──► prometheus (guarda 30 días) ──► grafana (dashboard)
prometheus        :9090 (a sí mismo) ─────────┘          127.0.0.1:9090              127.0.0.1:3000
```

| Pieza | Qué hace | Memoria |
|---|---|---|
| **Actuator + Micrometer** (en la API) | Expone métricas en formato Prometheus: memoria de Java y GC, cada request HTTP (endpoint, código, tiempo), pool de conexiones a la base | dentro de la API |
| **node-exporter** | Lee `/proc` y `/sys` del servidor: CPU, RAM, swap, disco, red | ~10 MB (tope 48) |
| **Prometheus** | Cada 30 s le pide las métricas a cada uno (*scrape*) y las guarda como series de tiempo: 30 días, máximo 1 GB | ~30–60 MB (tope 192) |
| **Grafana** | Dashboards sobre Prometheus. El origen de datos y el dashboard vienen del repo (*provisioning*), no se configuran a mano | ~90 MB (tope 256) |

Archivos: [`deploy/monitoring/`](../deploy/monitoring), servicios en [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

## Qué no queda expuesto

- Las métricas de la API van en **otro puerto (8082)**. nginx solo reenvía `/api`, así que desde internet
  `/actuator/prometheus` devuelve la página de la app y no las métricas. Además solo están habilitados `health` y
  `prometheus`: `env`, `heapdump` y el resto no existen.
- Grafana y Prometheus publican sus puertos solo en `127.0.0.1` del servidor: no se pueden ver desde afuera, ni
  siquiera si se abre el puerto en Oracle. Se entra por un **túnel SSH**.

## Entrar a Grafana

Desde tu máquina (tiene que estar apagado lo que use el 3000 y el 9090 en tu máquina, por ejemplo el stack local):

```bash
ssh -i ~/.ssh/oracle_torneo -N -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 ubuntu@144.22.190.249
```

`-L 3000:127.0.0.1:3000` significa: lo que llegue al puerto 3000 de mi máquina, llevalo por la conexión SSH (cifrada)
al 3000 del servidor. `-N`: no abrir una terminal, solo el túnel. Mientras quede corriendo:

- http://localhost:3000: Grafana, usuario `admin`, contraseña `GRAFANA_ADMIN_PASSWORD` del `.env` del servidor
  (`grep GRAFANA /opt/torneo/.env`). Dashboard **Torneo › Torneo · app and server**.
- http://localhost:9090: Prometheus. En *Status › Targets* se ve si cada fuente responde; en *Graph* se puede
  probar cualquier consulta.

## El dashboard

| Fila | Paneles | Qué mirar |
|---|---|---|
| Arriba | API arriba/caída, tiempo desde el último arranque, errores 5xx de la última hora, RAM, swap, disco | Todo en verde. Un uptime corto sin deploy = la API se reinició sola |
| Traffic | Requests por segundo según código, tiempo de respuesta (p50 y p95), % de errores, endpoints más lentos | p95: el 95% de las requests tardó menos que eso. Un 4xx alto suele ser normal (sesiones vencidas); un 5xx no |
| Java and database | Heap usado contra el máximo, % de tiempo en GC, conexiones del pool | Heap en serrucho es sano. GC alto o `pending` > 0 = falta memoria o conexiones |
| Server | CPU (incluido *steal*), memoria, red | *steal*: tiempo que Oracle le da a otras VMs del mismo host; en la micro la CPU es compartida |

El dashboard es [`torneo.json`](../deploy/monitoring/grafana/dashboards/torneo.json), en git. Los cambios hechos en
la interfaz no se guardan: para cambiarlo, editar el JSON (o exportarlo desde Grafana) y redesplegar.

## Consultas útiles (PromQL)

```promql
rate(http_server_requests_seconds_count[5m])                      # requests por segundo
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))   # p95
jvm_memory_used_bytes{area="heap"}                                # heap de Java
node_memory_MemAvailable_bytes                                    # RAM disponible del servidor
up                                                                # 1 = Prometheus llega a esa fuente
```

`rate(x[5m])`: cuánto creció por segundo un contador en los últimos 5 minutos. Los contadores solo suben (requests
totales), así que lo útil es su velocidad.

## Lo que falta

- **Alertas**: hoy hay que mirar el dashboard. Grafana puede mandar mails, pero necesita un servidor SMTP.
- **Monitoreo desde afuera**: si se cae el servidor entero, se caen también Prometheus y Grafana. Para eso hace falta
  un chequeo externo (UptimeRobot, Healthchecks.io).
- **Logs centralizados**: hoy `docker compose logs`. Loki los haría buscables desde Grafana (más memoria).
