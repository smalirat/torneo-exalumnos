import { z } from 'zod';
import { NombreCategoria } from '@prisma/client';

export const crearCategoriaSchema = z.object({
  torneoId: z.coerce.number().int().positive(),
  nombre: z.nativeEnum(NombreCategoria),
});
export type CrearCategoriaInput = z.infer<typeof crearCategoriaSchema>;

export const actualizarCategoriaSchema = z.object({
  nombre: z.nativeEnum(NombreCategoria).optional(),
});
export type ActualizarCategoriaInput = z.infer<typeof actualizarCategoriaSchema>;

export const listarCategoriasQuerySchema = z.object({
  torneoId: z.coerce.number().int().positive().optional(),
});
