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
