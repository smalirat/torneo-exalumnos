import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { RegistrarAmonestacionInput } from './amonestaciones.validation';

async function validarReferencias(jugadorId: number, equipoId: number, torneoId: number): Promise<void> {
  const [jugador, equipo, torneo] = await Promise.all([
    prisma.jugador.findUnique({ where: { id: jugadorId } }),
    prisma.equipo.findUnique({ where: { id: equipoId } }),
    prisma.torneo.findUnique({ where: { id: torneoId } }),
  ]);

  if (!jugador) throw new NotFoundError('Jugador', jugadorId);
  if (!equipo) throw new NotFoundError('Equipo', equipoId);
  if (!torneo) throw new NotFoundError('Torneo', torneoId);
}

/**
 * Las amonestaciones se acumulan por jugador dentro del torneo
 * (@@unique([jugadorId, torneoId]) + campo `cantidad`), así que una amarilla
 * "se carga" incrementando el contador (upsert si es la primera del jugador).
 */
export async function registrarAmonestacion(input: RegistrarAmonestacionInput) {
  await validarReferencias(input.jugadorId, input.equipoId, input.torneoId);

  const cantidad = input.cantidad ?? 1;

  return prisma.amonestacion.upsert({
    where: {
      jugadorId_torneoId: {
        jugadorId: input.jugadorId,
        torneoId: input.torneoId,
      },
    },
    create: {
      jugadorId: input.jugadorId,
      equipoId: input.equipoId,
      torneoId: input.torneoId,
      cantidad,
    },
    update: {
      cantidad: { increment: cantidad },
    },
  });
}

/**
 * Invariante de la entrada manual: el DELETE es la operación inversa del
 * POST (deshacer una amarilla cargada por error). Como cada jugador tiene UN
 * solo registro por torneo, "borrar una amarilla" = decrementar `cantidad`;
 * si el contador llega a 0, recién ahí se elimina el registro.
 */
export async function eliminarAmonestacion(id: number): Promise<void> {
  const amonestacion = await prisma.amonestacion.findUnique({ where: { id } });
  if (!amonestacion) {
    throw new NotFoundError('Amonestacion', id);
  }

  const cantidad = amonestacion.cantidad - 1;
  if (cantidad <= 0) {
    await prisma.amonestacion.delete({ where: { id } });
  } else {
    await prisma.amonestacion.update({ where: { id }, data: { cantidad } });
  }
}