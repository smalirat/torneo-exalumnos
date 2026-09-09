import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarEstadisticasInput } from './estadisticas.validation';

export async function actualizarEstadisticas(input: ActualizarEstadisticasInput) {
  const [jugador, torneo] = await Promise.all([
    prisma.jugador.findUnique({ where: { id: input.jugadorId } }),
    prisma.torneo.findUnique({ where: { id: input.torneoId } }),
  ]);
  if (!jugador) throw new NotFoundError('Jugador', input.jugadorId);
  if (!torneo) throw new NotFoundError('Torneo', input.torneoId);

  // Cada modelo tiene @@unique([jugadorId, torneoId]) del schema: upsert vuelve
  // el endpoint idempotente para el jugador/torneo, y `increment` acumula el
  // delta (o suma el inicial si es la primera vez del jugador en el torneo).
  const resultados: {
    goleador?: { goles: number };
    figura?: { veces: number };
    imbatible?: { golesRecibidos: number };
  } = {};

  if (input.goles !== undefined) {
    resultados.goleador = await prisma.goleador.upsert({
      where: {
        jugadorId_torneoId: { jugadorId: input.jugadorId, torneoId: input.torneoId },
      },
      create: { jugadorId: input.jugadorId, torneoId: input.torneoId, goles: input.goles },
      update: { goles: { increment: input.goles } },
    });
  }

  if (input.mvp !== undefined) {
    resultados.figura = await prisma.figura.upsert({
      where: {
        jugadorId_torneoId: { jugadorId: input.jugadorId, torneoId: input.torneoId },
      },
      create: { jugadorId: input.jugadorId, torneoId: input.torneoId, veces: input.mvp },
      update: { veces: { increment: input.mvp } },
    });
  }

  if (input.golesRecibidos !== undefined) {
    resultados.imbatible = await prisma.imbatible.upsert({
      where: {
        jugadorId_torneoId: { jugadorId: input.jugadorId, torneoId: input.torneoId },
      },
      create: { jugadorId: input.jugadorId, torneoId: input.torneoId, golesRecibidos: input.golesRecibidos },
      update: { golesRecibidos: { increment: input.golesRecibidos } },
    });
  }

  return resultados;
}