import { PrismaClient } from '@prisma/client';

// Singleton para no abrir una connection pool nueva en cada hot-reload
// de ts-node-dev (problema clásico de Prisma + dev server).
declare global {
  // eslint-disable-next-line no-var
  var __prisma: PrismaClient | undefined;
}

export const prisma =
  global.__prisma ??
  new PrismaClient({
    log: process.env.NODE_ENV === 'development' ? ['warn', 'error'] : ['error'],
  });

if (process.env.NODE_ENV !== 'production') {
  global.__prisma = prisma;
}
