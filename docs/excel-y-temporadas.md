# Excel y temporadas

Decisiones (2026-10-16). El formato real del Excel está descripto en `design/EXCEL_NOTES.md` (local).

## Decisiones
- **Ida y vuelta:** el admin sigue usando el Excel y la web a la vez. Se importa con **vista previa** (qué cambia + conflictos con lo editado en la web) y se puede **exportar** un Excel con el mismo formato.
- **Goleadores, figuras e imbatibles:** se toman los **totales anuales del Excel**. Cargar goles por jugador en la web queda como detalle opcional.
- **Presencia:** un equipo que no se presentó **no suma PR** (regla actual). Si el Excel dice otra cosa, el importador lo advierte.
- **Alcance:** temporada + datos del partido, figuras e imbatibles, sanciones, historia de campeones.

## API de las fases 2–4

| Qué | Público | Admin |
| --- | ------- | ----- |
| Goleadores / figuras / imbatibles anuales | `GET /api/public/seasons/{id}/scorers\|figures\|goalkeepers` | `PUT /api/admin/seasons/{id}/player-stats` (`playerId`, `goals`, `figureCount`, `goalsConceded`), `DELETE .../player-stats/{playerId}` |
| Sanciones | `GET /api/public/seasons/{id}/sanctions` (pendientes primero) | `POST /api/admin/seasons/{id}/sanctions`, `PUT/DELETE /api/admin/sanctions/{id}` |
| Amarillas acumuladas (4 = 1 fecha) | `GET /api/public/tournaments/{id}/yellow-cards` | `PUT /api/admin/tournaments/{id}/yellow-cards`, `DELETE .../yellow-cards/{playerId}` |
| Historia de campeones | `GET /api/public/champions` (por categoría + máximos campeones) | `PUT /api/admin/champions` (año + categoría + equipo; reemplaza), `DELETE /api/admin/champions/{id}` |

Todo registro importable guarda `source` (`EXCEL` / `WEB`): las ediciones en la web lo marcan `WEB`, la importación `EXCEL`. Sanción sin estado explícito: `SERVED` si cumplió todas las fechas, si no `PENDING`; `TRIBUNAL` se indica a mano.

## Fases
1. ✅ **Modelo base** — `Season` (año) agrupa torneos con etapa `APERTURA`/`CLAUSURA`. Partido: `round` (Fecha N), `roundLabel` ("Interzonal", "Repetición en zonas"), `venue` (campo), `pitch` (cancha), `notes`. Partidos regulares sin zona (interzonales) cuentan en la tabla de la zona de cada equipo.
2. ✅ **Estadísticas anuales de jugador** — goles, veces figura, goles recibidos por temporada.
3. ✅ **Sanciones** — expulsiones (fechas de sanción / cumplidas / pendiente, observaciones) y amarillas acumuladas por torneo (4 = 1 fecha, 8 = 2).
4. ✅ **Historia de campeones** — campeón por año y categoría + máximos campeones.
5. **Importación** (Apache POI) con vista previa, conflictos y origen de cada dato (`EXCEL` / `WEB`).
6. **Exportación** al mismo formato.
7. **Pantallas** del frontend para todo lo anterior.
