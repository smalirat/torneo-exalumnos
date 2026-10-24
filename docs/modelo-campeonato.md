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
- **Abandono** (en el código, *walkover*): si un equipo no se presenta, el que se presentó gana 3-0 y suma presencia; el ausente no suma nada. Si no se presenta ninguno: 0-0, sin ganador y nadie suma (ni presencia). En la UI no se usa "walkover" ni "W.O.".
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
- Se edita **solo desde Historia del panel de admin** (`/historia`); la pestaña pública es de solo lectura para todos.
- El admin trabaja sobre un **borrador**: botón flotante "+" (varias fotos a la vez, quedan en memoria), arrastrar, esquina para agrandar, al frente / atrás, destacar y borrar. **Nada se publica hasta "Guardar cambios"** (borra, sube, guarda posiciones y destacada, en ese orden); "Descartar" vuelve a lo guardado y salir con cambios pendientes pide confirmación.
- **"Restablecer"** acomoda todo en la grilla por defecto (4 columnas sueltas, en orden de subida): la misma regla que usa el backend para ubicar una foto nueva.

## Capitanes

- **Todo equipo tiene exactamente un capitán, y un capitán un solo equipo** (`teams.captain_id` obligatorio y único, migración `V8`).
- Un equipo creado por la **importación del Excel** o por un **admin** nace con un capitán **pendiente**: usuario `pendiente-…`, sin contraseña válida, no puede iniciar sesión. No se pide ningún dato extra para importar.
- En Registro de equipos el admin ve "Capitán pendiente" y el botón **Invitar capitán**: genera un link `/invitacion/{token}` (el mismo hasta que se use) para copiar o mandar por WhatsApp (`POST /api/admin/teams/{id}/invite`).
- Con el link, el capitán elige usuario y contraseña (`POST /api/auth/claim`) y queda logueado. Si ya tenía cuenta y todavía no tiene equipo, lo acepta con esa cuenta (`POST /api/me/invites/{token}/accept`) y la cuenta pendiente se borra. El link sirve una sola vez.
- Un capitán que se registra solo (`/registro`) puede crear **un** equipo. Nadie puede elegir un usuario que empiece con `pendiente-`.
- Un equipo que se da de baja conserva su capitán (por si vuelve). Al **borrar** un equipo se borra su capitán solo si seguía pendiente.

## Contacto del capitán

- `users.email` y `users.phone` (`V16`). **Obligatorios al registrarse** (`POST /api/auth/register`) y al reclamar un equipo por link de invitación (`POST /api/auth/claim`); mail válido y teléfono con números, espacios, guiones, paréntesis y `+`.
- Los usuarios de antes quedan sin datos: en Registro de equipos ven un aviso "Te falta cargar tu mail y teléfono" y los completan en **Mis datos** (tocando su usuario en la barra lateral; `PUT /api/auth/me`). `GET /api/auth/me` los devuelve.
- Privados: solo el propio usuario y el admin, que los ve junto al capitán de cada equipo en Registro de equipos (`GET /api/teams`: `captainEmail`, `captainPhone`, con links mailto/tel). Nunca en endpoints públicos.

## Escudos

- Cada equipo puede tener un escudo (PNG/JPG hasta 2 MB; el navegador lo achica a 256 px PNG antes de subirlo). La imagen va en `team_crests` y `teams.crest_version` cambia en cada subida.
- Toda referencia a un equipo en la API (`TeamRef`, `TeamRes`, `TeamSummaryRes`) trae `crest`: la URL `/api/public/teams/{id}/crest?v=…` (cacheada un año, la versión la invalida) o `null`.
- **Sin escudo no se muestra nada** (no hay más círculos de colores). En Registro de equipos y en la página del equipo, el admin o el capitán dueño ven un círculo punteado "+" para subirlo, y una × para quitarlo (`PUT|DELETE /api/teams/{id}/crest`).

## Estado de las ediciones (manual, una sola activa)

- El admin mueve cada edición a mano desde su Inicio (`PATCH /api/admin/tournaments/{id}/status`):
  **En preparación** (`DRAFT`) → **Inscripción abierta** (`OPEN`) → **En juego** (`LIVE`) → **Finalizado** (`COMPLETE`), y también hacia atrás (un torneo finalizado se puede reabrir).
- **Solo una edición activa** (`OPEN` o `LIVE`). Activar otra devuelve 409 con el nombre de la activa; con `finishActive: true` la activa pasa a Finalizado en el mismo paso (la web lo pregunta con "Finalizarla y continuar").
- Los **capitanes** solo inscriben su equipo con la edición en `OPEN`; empezar el torneo cierra la inscripción. El admin inscribe y corrige en cualquier estado.
- Las **fechas de inscripción son opcionales e informativas**: se muestran ("Inscripción hasta el …") pero no abren ni cierran nada.
- Una edición nueva nace En preparación. La **importación del Excel** deja sus ediciones nuevas Finalizadas y, si no hay ninguna activa, pone En juego la más nueva (Clausura antes que Apertura).
- El inicio público muestra la edición activa; si no hay, la más nueva.

## Baja de un equipo (abandono)

- Un equipo puede **darse de baja** de una edición (`registrations.withdrawn` + `withdrawn_at`, informativa). No se borra: sigue en la tabla y en el historial, y puede volver.
- **Dar de baja** (pantalla Equipos de la edición, `POST /api/admin/registrations/{id}/withdraw`): todos los partidos que le quedan pasan a 3-0 para el rival, marcados `matches.withdrawal = true`. Los ya jugados no cambian.
- **Revertir baja** (`DELETE …/withdraw`): los W.O. por baja de fechas que todavía no se jugaron vuelven a quedar pendientes; los de fechas jugadas quedan.
- **Excel**: un W.O. con la nota "X se dio de baja" se marca como baja y el equipo queda dado de baja; "no se presentó" sigue siendo un W.O. común.
- Un W.O. por baja **cuenta en la tabla** como cualquier W.O. y se ve en el Fixture, pero **no aparece en "Última fecha" ni "Próxima fecha"** del inicio ni cuenta para elegir cuál es la última fecha. El W.O. común (faltó a un partido) sí se muestra.
- Por ahora sin etiquetas ni distintivos para el equipo que abandonó (pendiente de definir).

## Aprobación de cambios de los capitanes

- Todo cambio de un capitán sobre su equipo espera la aprobación del admin: **crear el equipo, cambiarle el nombre, dar de alta o de baja jugadores e inscribirse en una edición**. **Borrar un equipo es solo del admin** (el capitán recibe 403; `teams.deletion_requested_at` y la solicitud `DELETE_TEAM` quedan solo para pedidos viejos). Los cambios estéticos (**escudo y números de camiseta**) se aplican directo. Lo que hace el admin (y la importación del Excel) se aplica directo.
- Lo pendiente vive en las filas (`V12`): `teams.approved` (equipo en revisión), `teams.pending_name`, `teams.deletion_requested_at`, `players.approved`, `players.removal_requested_at`, `registrations.approved`.
- **Equipo en revisión**: no es público ni puede inscribirse; el capitán ya arma el plantel y el escudo. Al aprobarlo se aprueban también sus jugadores. Un pedido nuevo de nombre reemplaza al anterior; retirar un jugador todavía pendiente es inmediato.
- Lo público solo muestra lo aprobado (equipos, jugadores, inscripciones); un jugador con baja pedida sigue hasta que se apruebe.
- Admin: **Solicitudes** (`/panel/solicitudes`, contador en la barra lateral; `GET /api/admin/requests`), agrupadas por equipo: Aprobar / Rechazar (motivo opcional) y **Aprobar todo** por equipo (nunca incluye borrar el equipo). Cada respuesta queda en `request_decisions` (`GET /api/me/requests`); el capitán no las ve en Registro de equipos.
- **Registrarse va con un equipo** (`POST /api/auth/register` con `teamName` o `claimTeamId`, uno de los dos): un equipo **nuevo** (queda en revisión) o **pedir ser el capitán** de un equipo que ya está en el torneo y no tiene capitán (aprobado, capitán pendiente, sin otro pedido: `GET /api/public/teams/unclaimed`). El pedido (`teams.claim_user_id`, `V13`) es la solicitud `CLAIM`: al aprobarla el usuario pasa a ser el capitán y se borra la cuenta pendiente; al rechazarla queda sin equipo y puede volver a pedir (`POST /api/me/claims`) o crear uno. Un pedido por equipo a la vez. El link de invitación del admin sigue sin pasar por aprobación.

## Armado de una edición (panel del admin)

- **Inicio**: portada (banner) y logo de la edición (`PUT/DELETE /api/admin/tournaments/{id}/images/{banner|logo}`, PNG/JPG hasta 5 MB, se achican en el navegador; `GET /api/public/tournaments/{id}/images/{kind}`, `V14`). El logo también va en la barra lateral. Categorías y zonas se arman en la misma vista (al crear una categoría se elige cuántas zonas tiene).
- **Clasificación → Equipos de la zona**: el admin pone un equipo directo en una zona (`POST /api/admin/zones/{id}/teams` con `teamId`, o `teamName` para uno nuevo con capitán pendiente) y lo quita mientras no tenga partidos (`DELETE /api/admin/registrations/{id}`; con partidos, es una baja).
- **Fixture → Nueva fecha**: todos los cruces de una fecha de la zona de una vez (número de fecha, primer horario + minutos entre partidos, campo, cancha). "Armar cruces" empareja a los libres de esa fecha prefiriendo rivales que todavía no enfrentaron. También "Generar fixture" (todos contra todos).
- **Fixture → Interzonales** (`POST /api/admin/tournaments/{id}/interzonal`, `?preview=true` no guarda; `InterzonalService`): el admin elige qué zonas juegan entre sí (de la misma categoría = "Interzonal", de distintas = "Intercategoría"), cuántas fechas, desde qué número de fecha (por defecto, después de la última) y el primer horario (una fecha por semana). Los equipos se cruzan **por la posición de la tabla en ese momento**: *misma posición* (1º vs 1º, 2º vs 2º; en cada fecha siguiente rota uno) o *cruzado* (1º vs último…). Nadie repite rival: alcanzan tantas fechas como equipos tiene la zona más grande; los que sobran de la zona más grande quedan libres. Local y visitante se alternan por fecha. Cada zona va en un solo cruce por generación. Son partidos regulares sin zona: suman en la tabla de la zona de cada equipo.
- **Cargar resultados** (`/panel/ediciones/:edicion/resultados`): una fecha a la vez para toda la edición (todas las categorías y zonas, interzonales y definiciones), cada partido abre el editor (resultado, goles, tarjetas, W.O.). Abre en la primera fecha ya vencida con partidos sin cargar; al guardar puede abrir el siguiente.
- **Armado automático** (`/panel/ediciones/:edicion/armado`; `GET /api/admin/tournaments/{id}/build/proposal?from={otra}` no guarda nada, `POST …/build` aplica). Lógica en `EditionBuilderService`:
  - Cada categoría de la edición nueva es un nivel (A = 0, B = 1…). Una categoría de la edición base conserva su nivel si hay una con el mismo nombre; si no, el de su lugar en la lista.
  - Promociones jugadas: el ganador juega en la categoría de arriba y el perdedor en la de abajo. Entre zonas de una misma categoría (5º Zona 1 vs 6º Zona 2), arriba es esa categoría y abajo la siguiente.
  - Los puestos de promoción parten cada tabla: en el grupo que defiende la categoría de arriba, los de abajo de esos puestos bajan; en el que la desafía, los de arriba suben. El resto se mantiene.
  - Son **preguntas** para el admin: promoción sin jugar, equipo nuevo (inscripto en la edición nueva y no en la base), equipo que se dio de baja, y grupos sin ninguna regla cuando cambió la estructura. Hay que responderlas antes de aplicar.
  - Al aplicar: inscribe (aprobados) a todos en su categoría, los reparte en las zonas — balanceado en serpentina por posición (1º→Z1, 2º→Z2, 3º→Z2, 4º→Z1…) o por sorteo — y opcionalmente genera todos contra todos en cada zona (una fecha por semana). Solo para una edición **sin partidos** (409).
  - **Interzonales / intercategorías** (opcional): las mismas preguntas y el mismo cruce que el botón Interzonales del fixture (`InterzonalService.cross`): qué zonas juegan entre sí, cuántas fechas, misma posición o cruzado. Como las tablas todavía están vacías, la posición es el **puesto de siembra** (el orden en que vienen de la edición base). Van después de la última fecha de la edición, una por semana.
  - **Definiciones** (opcional): promociones y finales por posición de las tablas nuevas. La propuesta copia las de la edición base cuando las zonas se corresponden (misma categoría por nombre y mismo orden de zona; si un lado era un equipo fijo, se usa la posición en la que terminó). El admin las cambia o agrega.
  - Las definiciones por posición **siguen a la tabla**: cada vez que se carga un resultado regular, las promociones/finales sin jugar que dependen de una zona de esa edición se recalculan solas (`PlayoffService.refreshRules`; antes había que tocar "Recalcular").
  - Reproduce el pase Apertura 2026 → Clausura 2026: 1º–4º de cada zona a la A, 5º vs 6º cruzados en promoción, del 7º para abajo a la B.

