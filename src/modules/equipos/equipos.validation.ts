import { z } from 'zod';

export const crearEquipoSchema = z.object({
  nombre: z.string().trim().min(1).max(100),
});
export type CrearEquipoInput = z.infer<typeof crearEquipoSchema>;

export const actualizarEquipoSchema = crearEquipoSchema.partial();
export type ActualizarEquipoInput = z.infer<typeof actualizarEquipoSchema>;
