import { z } from 'zod';

// POST /amonestaciones — entrada manual de amarillas. `cantidad` es opcional:
// el caso típico es +1 por tarjeta, pero el admin puede cargar varias de una.
export const registrarAmonestacionSchema = z.object({
  jugadorId: z.coerce.number().int().positive(),
  equipoId: z.coerce.number().int().positive(),
  torneoId: z.coerce.number().int().positive(),
  cantidad: z.coerce.number().int().min(1).optional(),
});
export type RegistrarAmonestacionInput = z.infer<typeof registrarAmonestacionSchema>;