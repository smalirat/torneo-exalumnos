# Torneo de Exalumnos - Colegio Marianista

Backend del sistema de gestión de la liga (Node.js + TypeScript + Express + Postgres + Prisma).

## Setup

```bash
npm install
cp .env.example .env        # completar DATABASE_URL y SESSION_SECRET
npx prisma migrate dev --name init
npx prisma db seed          # crea el usuario admin inicial (ver abajo)
npm run dev                 # http://localhost:3000/health
```

## Usuario admin inicial

El seed crea un usuario admin con `username=admin` y `password=changeme123`
(o los valores de `SEED_ADMIN_USERNAME`/`SEED_ADMIN_PASSWORD` si los seteás
en el `.env`). **Cambiá esa contraseña** creando un nuevo admin con
`POST /usuarios` y borrando el original, antes de ir a producción.

```bash
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"changeme123"}' \
  -c cookies.txt   # guarda la cookie de sesión

curl http://localhost:3000/auth/me -b cookies.txt
```

## Tests

```bash
cp .env.example .env.test   # DATABASE_URL_TEST apuntando a una DB de test aparte
npm test
```

## Panel web (frontend mínimo)

Server-rendered con EJS, sin lógica de negocio del lado del cliente — todo el
cálculo (standings, validaciones) vive en los `*.service.ts`, las vistas solo
muestran lo que el service ya calculó.

- `/panel` — inicio, elegís torneo/categoría/zona
- `/panel/standings?categoriaId=&zonaId=` — tabla de posiciones
- `/panel/partidos` — historial de partidos
- `/panel/goleadores?torneoId=`, `/panel/figuras?torneoId=`, `/panel/imbatibles?torneoId=`, `/panel/sancionados?torneoId=`
- `/panel/campeones` — historial + máximos campeones
- `/panel/login` — login (mismo `auth.service.ts` que usa la API)
- `/panel/admin` — panel admin (requiere sesión ADMIN): cargar resultado de partido, importar Excel, cargar sanción

La API JSON (`/standings`, `/partidos`, etc., sin el prefijo `/panel`) sigue
funcionando igual que antes — el panel HTML es una capa aparte que llama a
los mismos services, no reemplaza la API.

## Estructura (se va a ir completando parte por parte)


```
src/
  app.ts              # arma el express app (middleware, sesión, rutas, error handler)
  server.ts           # entrypoint: levanta el server
  config/env.ts        # validación de variables de entorno
  lib/prisma.ts         # singleton de PrismaClient
  middleware/
    asyncHandler.ts    # wrapper para handlers async
    errorHandler.ts    # traduce AppError / errores de Prisma / Zod a HTTP
    auth.ts             # requireAuth, requireRole
  modules/
    router.ts           # agrega los routers de cada módulo
    auth/                # POST /auth/login, /logout, GET /auth/me
    usuarios/            # admin-only: crear/listar/borrar cuentas (delegados, otros admins)
    vistas/               # panel HTML server-rendered (EJS) — ver sección arriba
    <modulo>/            # un folder por dominio (temporadas, partidos, standings, etc.)
      <modulo>.routes.ts
      <modulo>.service.ts
      <modulo>.validation.ts
  views/                 # templates EJS (layout.ejs + una carpeta por sección)
  types/session.d.ts    # tipado de express-session
  utils/AppError.ts      # jerarquía de errores de negocio
prisma/schema.prisma
tests/
```
