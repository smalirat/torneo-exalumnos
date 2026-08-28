import { z } from 'zod';
import { RolUsuario } from '@prisma/client';

export const crearUsuarioSchema = z
  .object({
    username: z.string().trim().min(3).max(50),
    password: z.string().min(8, 'La contraseña tiene que tener al menos 8 caracteres'),
    rol: z.nativeEnum(RolUsuario),
    equipoId: z.coerce.number().int().positive().optional(),
  })
  .refine((data) => data.rol !== RolUsuario.DELEGADO || data.equipoId !== undefined, {
    message: 'Un usuario DELEGADO necesita un equipoId',
    path: ['equipoId'],
  });
export type CrearUsuarioInput = z.infer<typeof crearUsuarioSchema>;
