import { prisma } from '../../lib/prisma';
import { NotFoundError, ValidationError } from '../../utils/AppError';
import { ActualizarSancionInput, CrearSancionInput } from './sanciones.validation';

async function validarReferencias(jugadorId: number, equipoId: number, torneoId: number) {
  const [jugador, equipo, torneo] = await Promise.all([
    prisma.jugador.findUnique({ where: { id: jugadorId } }),
    prisma.equipo.findUnique({ where: { id: equipoId } }),
    prisma.torneo.findUnique({ where: { id: torneoId } }),
  ]);
  if (!jugador) throw new NotFoundError('Jugador', jugadorId);
  if (!equipo) throw new NotFoundError('Equipo', equipoId);
  if (!torneo) throw new NotFoundError('Torneo', torneoId);
}

export async function crearSancion(input: CrearSancionInput) {
  await validarReferencias(input.jugadorId, input.equipoId, input.torneoId);

  return prisma.sancion.create({
    data: {
      jugadorId: input.jugadorId,
      equipoId: input.equipoId,
      torneoId: input.torneoId,
      tipoTarjeta: input.tipoTarjeta,
      fechasSuspension: input.fechasSuspension,
      observaciones: input.observaciones,
      // pendiente=true, cumplida=false por default (ver @default en el schema)
    },
  });
}

export async function actualizarSancion(id: number, input: ActualizarSancionInput) {
  const sancion = await prisma.sancion.findUnique({ where: { id } });
  if (!sancion) throw new NotFoundError('Sancion', id);

  if (input.cumplida === true && input.pendiente === undefined) {
    // Si marcan la sanción como cumplida y no dijeron nada de `pendiente`,
    // lo apagamos automáticamente — sería raro que quedara "cumplida Y
    // pendiente" a la vez sin que el caller lo haya pedido explícitamente.
    input = { ...input, pendiente: false };
  }
  if (input.pendiente === true && input.cumplida === undefined && sancion.cumplida) {
    throw new ValidationError('No se puede volver a marcar como pendiente una sanción ya cumplida sin aclarar cumplida=false explícitamente');
  }

  return prisma.sancion.update({ where: { id }, data: input });
}

export async function listarSancionados(torneoId: number, pendiente?: boolean) {
  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);

  return prisma.sancion.findMany({
    where: { torneoId, pendiente },
    include: { jugador: true, equipo: true },
    orderBy: [{ pendiente: 'desc' }, { createdAt: 'desc' }],
  });
}
