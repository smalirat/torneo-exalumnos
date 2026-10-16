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
- El capitán inscribe su equipo en una **categoría**; queda en la primera zona. El admin puede moverlo de zona.
- La tabla de posiciones es por zona.
- **Frontend:** si una categoría tiene una sola zona, no mostrar "Zona 1" (la API devuelve `zoneCount` en cada categoría).

## Tabla de posiciones (pendiente: `feat/standings`)

- Victoria 3, empate 1, derrota 0.
- **Punto de presencia:** +1 al equipo que se presenta a un partido (el admin lo marca al cargar el resultado).
- Desempate: a definir (propuesta: PTS, DIF, GF).

## Promociones y finales (pendiente: `feat/playoffs`)

- Partidos especiales `type = PROMOTION | FINAL`, fuera de la tabla.
- Cada lado del partido se define por una **regla** configurable: "posición N de la zona X" (zona de este campeonato o de uno anterior), o un equipo elegido a mano.
- La regla se resuelve contra la tabla del momento; mientras el partido no se jugó, se puede recalcular.
- Ejemplos: 7º de la A vs 3º de la B (promoción); 1º del Apertura vs 1º del Clausura (final).
