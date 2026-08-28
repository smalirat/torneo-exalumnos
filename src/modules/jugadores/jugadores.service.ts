import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarJugadorInput, CrearJugadorInput } from './jugadores.validation';

export async function listarJugadores(equipoId?: number) {
  return prisma.jugador.findMany({ where: { equipoId }, include: { equipo: true }, orderBy: { nombre: 'asc' } });
}

export async function obtenerJugador(id: number) {
  const jugador = await prisma.jugador.findUnique({ where: { id }, include: { equipo: true } });
  if (!jugador) throw new NotFoundError('Jugador', id);
  return jugador;
}

export async function crearJugador(input: CrearJugadorInput) {
  if (input.equipoId !== undefined) {
    const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
    if (!equipo) throw new NotFoundError('Equipo', input.equipoId);
  }
  return prisma.jugador.create({ data: input });
}

export async function actualizarJugador(id: number, input: ActualizarJugadorInput) {
  await obtenerJugador(id);
  if (input.equipoId) {
    const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
    if (!equipo) throw new NotFoundError('Equipo', input.equipoId);
  }
  return prisma.jugador.update({ where: { id }, data: input });
}

export async function eliminarJugador(id: number) {
  await obtenerJugador(id);
  await prisma.jugador.delete({ where: { id } });
}
