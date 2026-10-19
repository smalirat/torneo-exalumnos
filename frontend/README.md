# Frontend

Vue 3 + Vite + TypeScript. UI en español (textos en `src/i18n/es-AR.json`).

## Desarrollo

```bash
# 1) Backend sin PostgreSQL (H2 en memoria, admin/admin123), desde la raíz del repo:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # requiere APP_JWT_SECRET

# 2) Frontend:
cd frontend
npm install
npm run dev        # http://localhost:5173, /api se proxyea a http://localhost:8081
npm run build      # chequeo de tipos (vue-tsc) + build
```

## Estructura

- `src/api/` cliente `fetch` con JWT (`client.ts`), endpoints y tipos que espejan los DTOs del backend.
- `src/auth.ts` sesión (token en `localStorage`, usuario actual, `isAdmin`).
- `src/i18n/` textos y `t('clave', { params })`. No hardcodear strings en los componentes.
- `src/layouts/` `GlobalLayout` (fuera de un campeonato) y `TournamentLayout` (sidebar del campeonato + breadcrumb).
- `src/views/` una vista por ruta (ver `src/router.ts`).
- `src/components/` reutilizables: `BaseModal` (modal con título/cerrar/footer), `ConfirmButton` (borrar con confirmación en un diálogo propio), `InlineEdit` (renombrar en el lugar), `TournamentForm` (alta/edición de campeonato), `MatchResultEditor` (resultado por eventos, walkover, penales, borrar partido), `SlotPicker` (lado de una promoción/final), `ZonePicker` (categoría/zona, recordada en la URL), `CollapsibleList`, `TabsNav`, `PlayerRankingTable`, `MatchCard` (línea de partido), `StandingsTable` (tabla con fila resaltable), `TeamBadge`, `AppIcon`.
- Diseño: `design/NOTES.md` en la raíz del repo (local, no versionado).

## Pantallas

Todas las URLs se arman en `src/paths.ts` (slugs en `src/slugs.ts`). Criterio: palabras en español, minúsculas y con guiones, de lo general a lo particular, slugs legibles en lugar de ids (`2026-clausura`, `41-gulp`, `categoria-a/zona-2`, o solo `zona-1` si la edición tiene una sola categoría) y `?` solo para filtros. Sitio público en la raíz, gestión bajo `/panel`. Sin edición en la URL = la edición actual. Las URLs viejas (`/ver/:id?tab=…`, `/campeonatos/:id/…`, `/temporadas/:id`, `/ediciones`, `/login`, …) redirigen a las nuevas; una ruta inexistente muestra la página 404.

| Ruta | Quién | Qué |
| ---- | ----- | --- |
| `/` (`?grupo=categoria-a`) | todos | Inicio del torneo (edición actual): Última fecha, Tabla de posiciones, Próxima fecha |
| `/fixture/:edicion?/:grupo*` | todos | Resultados por fecha, con la lista de categorías/zonas y selector de año |
| `/posiciones/:edicion?/:grupo*` | todos | Tabla de posiciones del mismo grupo |
| `/goleadores/:edicion?` | todos | Goleadores y figuras |
| `/sancionados/:edicion?` | todos | Sancionados (pendientes y cumplidas) + amonestados, con buscador |
| `/definiciones/:edicion?` | todos | Promociones y finales |
| `/historia` | todos | Collage de fotos de campeones + campeones de todos los años (solo lectura) |
| `/equipos/:equipo` | todos | Página del equipo (`/equipos/41` se canoniza a `/equipos/41-gulp`) |
| `/ingresar`, `/registrarse` | todos | Login y alta de capitán con su equipo (`?redirect=` solo acepta rutas de la app) |
| `/invitacion/:token` | todos | El capitán de un equipo creado por el Excel/admin activa su usuario (o lo acepta con su cuenta) |
| `/panel/ediciones` | logueado | Ediciones del torneo; el admin crea ediciones (FAB +) |
| `/panel/ediciones/:edicion` | logueado | Inicio de la edición: estado, inscripción, categorías; admin edita/elimina edición y categorías |
| `/panel/ediciones/:edicion/clasificacion` | logueado | Tabla por zona + Juegos; admin genera fixture, agrega partidos y carga resultados |
| `/panel/ediciones/:edicion/fixture` | logueado | Fixture por fecha (‹ Fecha N ›); admin carga resultados |
| `/panel/ediciones/:edicion/rankings` | logueado | Goleadores y amonestados |
| `/panel/ediciones/:edicion/definiciones` | logueado | Promociones y finales; admin las crea, recalcula y carga resultados |
| `/panel/ediciones/:edicion/equipos` | admin | Asignar equipos a zonas (a mano o por sorteo), crear zonas |
| `/panel/temporadas/:anio` | logueado | Temporada: goleadores, figuras, imbatibles y sanciones del año |
| `/panel/equipos` | logueado | Registro de equipos: escudo, plantel, invitar capitán, renombrar/eliminar |
| `/panel/planilla` | admin | Planilla Excel: importar con vista previa y conflictos, historial, exportar temporada |
| `/panel/historia` | admin | Editar el collage de Historia (borrador con Guardar cambios / Descartar / Restablecer) |
| `/panel/solicitudes` | admin | Aprobar o rechazar los cambios de los capitanes |

El build de producción agrega una Content-Security-Policy (`vite.config.ts`): scripts y API solo del mismo origen, fuentes de Google Fonts, imágenes también `blob:`/`data:`.
