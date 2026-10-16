# Modelo de campeonato

Decisiones acordadas (2026-10-16). Solo fútbol.

## Estructura

```
Temporada (Season)        2026  (opcional; agrupa Apertura y Clausura)
Campeonato (Tournament)   etapa APERTURA / CLAUSURA
└── Categoría (Category)   A, B, C…
    └── Zona (Zone)        Zona 1, Zona 2…
        ├── Inscripciones (Registration): equipo ↔ zona
        └── Partidos regulares (MatchGame type=REGULAR)
```

- Al crear una categoría se crea automáticamente una zona por defecto.
- Un equipo juega en **una sola zona por campeonato** (unique `tournament_id, team_id`).
- El capitán inscribe su equipo en el **campeonato**; queda sin asignar (`zoneId = null`).
- El admin le asigna categoría y zona (`PATCH /api/admin/registrations/{id}/zone`). Puede reasignarlo a cualquier zona del mismo campeonato mientras no tenga partidos.
- Futuro: asignación automática para equipos que ya jugaron (p. ej. según categoría del torneo anterior).
- La tabla de posiciones es por zona. Incluye los **partidos interzonales**: partidos regulares sin zona entre equipos de distintas zonas del mismo campeonato (`POST /api/admin/fixtures/manual` con `tournamentId`), que suman en la tabla de la zona de cada equipo.
- Cada partido puede tener número de fecha (`round`), etiqueta de fecha especial (`roundLabel`: "Interzonal", "Repetición en zonas"), campo (`venue`), cancha (`pitch`) y una nota libre (`notes`). Se editan con `PATCH /api/admin/matches/{id}`. El todos contra todos numera las fechas solo.
- **Frontend:** si una categoría tiene una sola zona, no mostrar "Zona 1" (la API devuelve `zoneCount` en cada categoría).

## Tabla de posiciones (`GET /api/public/zones/{id}/standings`)

- Victoria 3, empate 1, derrota 0.
- **Punto de presencia:** +1 al equipo que se presenta a un partido (el admin lo marca al cargar el resultado).
- **Walkover:** si un equipo no se presenta, el que se presentó gana 3-0 y suma presencia; el ausente no suma nada.
- **Walkover anticipado:** si un walkover ya está cargado pero su fecha todavía no llegó (p. ej. un equipo que se retiró y el Excel trae los 3-0 de las próximas fechas), se guarda pero **no cuenta en la tabla hasta el día del partido** (hora de Argentina). La UI lo marca con "Cuenta en la tabla desde su fecha".
- **Desempate:** PTS → diferencia de gol → goles a favor → resultado entre ellos → fair play → puntos de presencia → orden alfabético.
- **Fair play:** penalización de 1 por amarilla y 3 por roja (menos es mejor), a partir de las tarjetas cargadas por jugador.
- "Resultado entre ellos" = puntos (3/1/0, sin presencia) de los partidos entre los equipos empatados.
- Las reglas de puntaje viven en `MatchGame` (las usan la tabla y el dashboard del equipo).

## Goles y tarjetas (`MatchEvent`)

- El resultado se carga **siempre por jugador** (`PATCH /api/admin/matches/{id}/score` con `events`): `GOAL`, `OWN_GOAL`, `YELLOW_CARD`, `RED_CARD`. El marcador se calcula a partir de los eventos.
- Gol en contra: suma al rival y no cuenta para el goleador.
- Walkover: no se cargan eventos; los 3 goles van solo al equipo (ningún goleador).
- Volver a cargar un resultado reemplaza los eventos anteriores.
- Rankings por campeonato: `GET /api/public/tournaments/{id}/scorers` y `/cards` (rojas primero, después amarillas).

## Promociones y finales

- Partidos especiales `type = PROMOTION | FINAL`, sin zona: no suman a ninguna tabla (sí a goleadores/amonestados del campeonato donde se crean).
- Crear: `POST /api/admin/tournaments/{id}/special-matches` con `type`, `scheduledAt`, `home` y `away`. Cada lado es `{teamId}` o `{zoneId, position}` (zona de cualquier campeonato).
- La regla se resuelve contra la tabla al crear. `POST /api/admin/matches/{id}/resolve` la recalcula mientras el partido no se jugó (409 si ya se jugó).
- Se listan en `GET /api/public/tournaments/{id}/special-matches`; cada partido trae `homeSource`/`awaySource` con la regla.
- **Penales:** si una promoción/final termina empatada (y se presentaron los dos), hay que cargar `homePenalties`/`awayPenalties` junto con el resultado; no pueden empatar. En cualquier otro caso los penales se rechazan (400).
- Cada partido devuelve `winner` (goles, y si hubo empate, penales); en un partido regular empatado es `null`.
- Ejemplos: 7º de la A vs 3º de la B (promoción); 1º del Apertura vs 1º del Clausura (final).
