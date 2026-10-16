# Modelo de campeonato

Decisiones acordadas (2026-10-16). Solo fútbol.

## Estructura

```
Campeonato (Tournament)
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
- La tabla de posiciones es por zona.
- **Frontend:** si una categoría tiene una sola zona, no mostrar "Zona 1" (la API devuelve `zoneCount` en cada categoría).

## Tabla de posiciones (`GET /api/public/zones/{id}/standings`)

- Victoria 3, empate 1, derrota 0.
- **Punto de presencia:** +1 al equipo que se presenta a un partido (el admin lo marca al cargar el resultado).
- **Walkover:** si un equipo no se presenta, el que se presentó gana 3-0 y suma presencia; el ausente no suma nada.
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

## Promociones y finales (pendiente: `feat/playoffs`)

- Partidos especiales `type = PROMOTION | FINAL`, fuera de la tabla.
- Cada lado del partido se define por una **regla** configurable: "posición N de la zona X" (zona de este campeonato o de uno anterior), o un equipo elegido a mano.
- La regla se resuelve contra la tabla del momento; mientras el partido no se jugó, se puede recalcular.
- Ejemplos: 7º de la A vs 3º de la B (promoción); 1º del Apertura vs 1º del Clausura (final).
