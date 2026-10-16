# Frontend

Vue 3 + Vite + TypeScript. UI en español (textos en `src/i18n/es-AR.json`).

## Desarrollo

```bash
# 1) Backend sin MySQL (H2 en memoria, admin/admin123), desde la raíz del repo:
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
- `src/components/` `MatchResultEditor` (carga de resultado por eventos, walkover y penales), `SlotPicker` (lado de una promoción/final), `TeamBadge`, `AppIcon`.
- Diseño: `design/NOTES.md` en la raíz del repo (local, no versionado).

## Pantallas

| Ruta | Quién | Qué |
| ---- | ----- | --- |
| `/` | todos | Mis campeonatos; el admin crea campeonatos (FAB +) |
| `/login`, `/registro` | todos | Login y alta de capitán |
| `/equipos` | logueado | Registro de equipos: plantel e inscripción a campeonatos |
| `/campeonatos/:id` | todos | Inicio: estado, inscripción, categorías (admin agrega) |
| `/campeonatos/:id/clasificacion` | todos | Tabla por zona + Juegos; admin genera fixture, agrega partidos y carga resultados |
| `/campeonatos/:id/rankings` | todos | Goleadores y amonestados |
| `/campeonatos/:id/definiciones` | todos | Promociones y finales; admin las crea, recalcula y carga resultados |
| `/campeonatos/:id/equipos` | admin | Asignar equipos a zonas, crear zonas |
