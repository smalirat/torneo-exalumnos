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
- **Sorteo de zonas** (`POST /api/admin/categories/{id}/zone-draw` con `registrationIds`): el admin elige qué equipos sin zona van a una categoría y el sistema los reparte al azar entre sus zonas, siempre a la que tenga menos equipos (desempata el orden de las zonas). No mueve a los que ya tienen zona. Con una sola categoría la UI preselecciona a todos los sin asignar.
- La tabla de posiciones es por zona. Incluye los **partidos interzonales**: partidos regulares sin zona entre equipos de distintas zonas del mismo campeonato (`POST /api/admin/fixtures/manual` con `tournamentId`), que suman en la tabla de la zona de cada equipo.
- Cada partido puede tener número de fecha (`round`), etiqueta de fecha especial (`roundLabel`: "Interzonal", "Repetición en zonas"), campo (`venue`), cancha (`pitch`) y una nota libre (`notes`). Se editan con `PATCH /api/admin/matches/{id}`. El todos contra todos numera las fechas solo.
- **Frontend:** si una categoría tiene una sola zona, no mostrar "Zona 1" (la API devuelve `zoneCount` en cada categoría).

## Tabla de posiciones (`GET /api/public/zones/{id}/standings`)

- Victoria 3, empate 1, derrota 0.
- **Punto de presencia:** +1 al equipo que se presenta a un partido (el admin lo marca al cargar el resultado).
- **Walkover:** si un equipo no se presenta, el que se presentó gana 3-0 y suma presencia; el ausente no suma nada.
- **Walkover anticipado:** si un walkover ya está cargado pero su fecha todavía no se jugó (p. ej. un equipo que se retiró y el Excel trae los 3-0 de las próximas fechas), se guarda pero **no cuenta en la tabla hasta que se juegue algún otro partido de esa fecha** (misma zona y número de fecha). El día del calendario no alcanza porque las fechas se postergan. Solo si la fecha no tiene otros partidos se usa el día (hora de Argentina). La UI lo marca con "Cuenta en la tabla cuando se juegue la fecha" y no lo toma como fecha jugada.
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

## Seguir campeonatos y notificaciones

- Se sacaron del producto (migración `V6` borra `follows`, `notifications` y `users.notifications_seen_at`). Para enterarse alcanza con la página pública y el link compartido.

## Vista pública (inicio del sitio)

- Se organiza **un solo torneo** en ediciones (cada `Tournament` es una edición: Apertura/Clausura de una temporada). Por eso `/` es la página pública de la **edición actual** (la más nueva "En juego"; si no hay, la más nueva) y `/ver/:id` muestra una edición puntual. La gestión de ediciones quedó en `/ediciones`.
- Inicio: **Última fecha** (carrusel), **Tabla de posiciones** y **Próxima fecha**, más las pestañas Fixture, Goleadores y Definiciones. Sin login y pensada para celular. Categoría, zona y pestaña quedan en la URL: el link compartido abre exactamente lo mismo.
- El nombre del torneo de la barra superior sale de `app.league.title` (`GET /api/public/league`).
- Goleadores y figuras: si el campeonato pertenece a una temporada se muestran los totales anuales (los del Excel); si no, los goles cargados por partido.
- Diseño inspirado en la referencia de rugby (títulos condensados, carrusel y tabla con encabezado de color) pero con la paleta azul y blanca de la app; sin marcas ni lógica de ese deporte.

## Collage de campeones (pestaña Historia)

- Fotos sueltas, **no asociadas a un campeón** (`collage_photos`, imagen en `bytea`). Layout libre: `x`, `y` y `w` son fracciones del ancho del tablero, así el collage conserva su forma en cualquier pantalla; `z` es la capa (más alto = encima). Una sola foto puede ser la **destacada** (banner arriba del collage).
- Público: `GET /api/public/collage` (layout) y `/collage/{id}/image` (cacheada un año: una foto nunca cambia). Admin: `POST /api/admin/collage` (JPG/PNG hasta 5 MB; el navegador la achica a 1600 px antes), `PUT /layout`, `PUT /{id}/featured`, `DELETE /{id}`.
- En la web, el admin ve un botón flotante "+" (elige varias fotos a la vez), arrastra para mover, usa la esquina para agrandar y toca una foto para traerla al frente / atrás, destacarla o borrarla. Las nuevas entran en una grilla suelta de 4 columnas.

## Capitanes

- **Todo equipo tiene exactamente un capitán, y un capitán un solo equipo** (`teams.captain_id` obligatorio y único, migración `V8`).
- Un equipo creado por la **importación del Excel** o por un **admin** nace con un capitán **pendiente**: usuario `pendiente-…`, sin contraseña válida, no puede iniciar sesión. No se pide ningún dato extra para importar.
- En Registro de equipos el admin ve "Capitán pendiente" y el botón **Invitar capitán**: genera un link `/invitacion/{token}` (el mismo hasta que se use) para copiar o mandar por WhatsApp (`POST /api/admin/teams/{id}/invite`).
- Con el link, el capitán elige usuario y contraseña (`POST /api/auth/claim`) y queda logueado. Si ya tenía cuenta y todavía no tiene equipo, lo acepta con esa cuenta (`POST /api/me/invites/{token}/accept`) y la cuenta pendiente se borra. El link sirve una sola vez.
- Un capitán que se registra solo (`/registro`) puede crear **un** equipo. Nadie puede elegir un usuario que empiece con `pendiente-`.
- Un equipo que se da de baja conserva su capitán (por si vuelve). Al **borrar** un equipo se borra su capitán solo si seguía pendiente.

