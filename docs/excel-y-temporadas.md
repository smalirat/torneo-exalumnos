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

## Importación (fase 5)

- `POST /api/admin/imports` (multipart `file`, .xlsx) → **vista previa**: corre la importación completa en una transacción que se descarta, y devuelve contadores por entidad, cambios, conflictos, advertencias y avisos. Guarda el archivo.
- `POST /api/admin/imports/{id}/apply` con `{"overwrite": ["match:12", ...]}` → aplica (misma lógica, se confirma). Las claves son las de los conflictos que el admin decide pisar con el valor del Excel.
- `GET /api/admin/imports` → historial (últimas 30).
- Código: `excel/WorkbookParser` (lee el libro, tolerante a variaciones), `excel/ExcelImporter` (escribe en la base), `service/ExcelImportService` (preview/apply).

Reglas:
- La **tabla de posiciones** del Excel decide en qué categoría/zona juega cada equipo. Etiqueta de partido distinta → advertencia, se carga donde juegan. Equipos de grupos distintos → interzonal.
- Un año = una temporada; "TABLA APERTURA/CLAUSURA" → torneo con esa etapa (se crea si falta). Apertura con zonas → categoría "Única".
- Equipos y jugadores se buscan por nombre normalizado (mayúsculas, sin tildes ni espacios extra) y se crean si no existen.
- Partidos: misma combinación torneo + local + visitante + fecha N → mismo partido. Walkover: la nota "X no se presentó" / "X se dio de baja" marca ausente a X.
- Promociones/finales con posiciones ("7° CLAUSURA CAT B") se crean como definiciones con regla por posición. Partidos regulares con posiciones sin definir se omiten.
- **Conflictos**: un registro editado en la web (`source = WEB`, o partido con goles por jugador) cuyo valor difiere del Excel no se toca, salvo que su clave venga en `overwrite`. Al pisar un resultado se borran sus goles por jugador.
- Nunca se borra nada. Reimportar el mismo archivo no cambia nada.
- Chequeos que se informan como advertencias: PR del Excel distinto de "1 por partido presentado"; puntos por resultados de la tabla distintos de los que dan sus partidos; filas repetidas (se toma el mayor / el último).

## Fases
1. ✅ **Modelo base** — `Season` (año) agrupa torneos con etapa `APERTURA`/`CLAUSURA`. Partido: `round` (Fecha N), `roundLabel` ("Interzonal", "Repetición en zonas"), `venue` (campo), `pitch` (cancha), `notes`. Partidos regulares sin zona (interzonales) cuentan en la tabla de la zona de cada equipo.
2. ✅ **Estadísticas anuales de jugador** — goles, veces figura, goles recibidos por temporada.
3. ✅ **Sanciones** — expulsiones (fechas de sanción / cumplidas / pendiente, observaciones) y amarillas acumuladas por torneo (4 = 1 fecha, 8 = 2).
4. ✅ **Historia de campeones** — campeón por año y categoría + máximos campeones.
5. ✅ **Importación** (Apache POI) con vista previa, conflictos y origen de cada dato (`EXCEL` / `WEB`).
6. ✅ **Exportación** al mismo formato: `GET /api/admin/imports/export/{seasonId}` devuelve el `.xlsx` de la temporada (tablas, jugados, próximos, goleadores, figuras, imbatibles, sancionados, historia). **Ida y vuelta garantizada**: importar el archivo exportado no cambia nada (tests sobre datos sintéticos, datos cargados en la web y el Excel real). Los walkovers cargados en la web se exportan con la nota "el equipo X no se presento"; las sanciones sin observaciones, con el texto del tipo y el código TA/TC.
7. **Pantallas** del frontend para todo lo anterior.
