import { z } from 'zod';

// Centralizamos y validamos las variables de entorno acá para que un typo
// en el .env explote al arrancar el server, no en medio de un request.
const envSchema = z.object({
  DATABASE_URL: z.string().min(1, 'DATABASE_URL es obligatorio'),
  SESSION_SECRET: z.string().min(10, 'SESSION_SECRET debe tener al menos 10 caracteres'),
  PORT: z.coerce.number().int().positive().default(3000),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
});

function loadEnv() {
  const parsed = envSchema.safeParse(process.env);
  if (!parsed.success) {
    console.error('❌ Variables de entorno inválidas:');
    console.error(parsed.error.flatten().fieldErrors);
    throw new Error('Configuración de entorno inválida');
  }
  return parsed.data;
}

export const env = loadEnv();
