import { EstadoPartido, Partido } from '@prisma/client';
import { prisma } from '../../lib/prisma';
import { ConflictError, NotFoundError, ValidationError } from '../../utils/AppError';
import { validarCategoriaYZona } from '../common/validarCategoriaZona';
import { CrearPartidoInput, ActualizarPartidoInput } from './partidos.validation';

/**
 * Si el caller no manda `estado` explícitamente, lo inferimos:
 * hay resultado cargado -> JUGADO, si no -> PENDIENTE.
 * (SUSPENDIDO siempre hay que setearlo a mano, no se infiere solo).
 */
function inferirEstado(
  estadoExplicito: EstadoPartido | undefined,
  golesLocal: number | null | undefined,
  golesVisitante: number | null | undefined,
): EstadoPartido {
  if (estadoExplicito) return estadoExplicito;
  return golesLocal != null && golesVisitante != null ? EstadoPartido.JUGADO : EstadoPartido.PENDIENTE;
}

async function validarEquiposExisten(ids: number[]): Promise<void> {
  const unicos = [...new Set(ids)];
  const equipos = await prisma.equipo.findMany({ where: { id: { in: unicos } } });
  if (equipos.length !== unicos.length) {
    const encontrados = new Set(equipos.map((e) => e.id));
    const faltante = unicos.find((id) => !encontrados.has(id));
    throw new NotFoundError('Equipo', faltante);
  }
}

/**
 * Caso borde de la spec: "Partido duplicado (misma fecha, mismos equipos) →
 * debe rechazarse". Esto también está protegido por el @@unique del schema
 * (defensa en profundidad), pero acá lo chequeamos antes para poder devolver
 * un mensaje claro en vez de depender de que el errorHandler traduzca el
 * P2002 de Prisma.
 */
async function validarNoDuplicado(
  fecha: Date,
  equipoLocalId: number,
  equipoVisitanteId: number,
  excluirId?: number,
): Promise<void> {
  const existente = await prisma.partido.findFirst({
    where: {
      fecha,
      equipoLocalId,
      equipoVisitanteId,
      ...(excluirId !== undefined ? { id: { not: excluirId } } : {}),
    },
  });
  if (existente) {
    throw new ConflictError(
      'Ya existe un partido cargado con esa fecha y esos equipos (local/visitante). Si es una corrección, usá PUT /partidos/{id}.',
    );
  }
}

export async function crearPartido(input: CrearPartidoInput): Promise<Partido> {
  await validarCategoriaYZona(input.categoriaId, input.zonaId);
  await validarEquiposExisten([input.equipoLocalId, input.equipoVisitanteId]);
  await validarNoDuplicado(input.fecha, input.equipoLocalId, input.equipoVisitanteId);

  const estado = inferirEstado(input.estado, input.golesLocal, input.golesVisitante);

  return prisma.partido.create({
    data: {
      categoriaId: input.categoriaId,
      zonaId: input.zonaId,
      equipoLocalId: input.equipoLocalId,
      equipoVisitanteId: input.equipoVisitanteId,
      golesLocal: input.golesLocal,
      golesVisitante: input.golesVisitante,
      fecha: input.fecha,
      jornada: input.jornada,
      campo: input.campo,
      cancha: input.cancha,
      horario: input.horario,
      estado,
    },
  });
}

export async function actualizarPartido(id: number, input: ActualizarPartidoInput): Promise<Partido> {
  const actual = await prisma.partido.findUnique({ where: { id } });
  if (!actual) {
    throw new NotFoundError('Partido', id);
  }

  const equipoLocalId = input.equipoLocalId ?? actual.equipoLocalId;
  const equipoVisitanteId = input.equipoVisitanteId ?? actual.equipoVisitanteId;
  if (equipoLocalId === equipoVisitanteId) {
    throw new ValidationError('El equipo local y el equipo visitante no pueden ser el mismo');
  }

  const categoriaId = input.categoriaId ?? actual.categoriaId;
  const zonaId = input.zonaId !== undefined ? input.zonaId : (actual.zonaId ?? undefined);
  if (input.categoriaId !== undefined || input.zonaId !== undefined) {
    await validarCategoriaYZona(categoriaId, zonaId ?? undefined);
  }

  if (input.equipoLocalId !== undefined || input.equipoVisitanteId !== undefined) {
    await validarEquiposExisten([equipoLocalId, equipoVisitanteId]);
  }

  const fecha = input.fecha ?? actual.fecha;
  if (input.fecha !== undefined || input.equipoLocalId !== undefined || input.equipoVisitanteId !== undefined) {
    await validarNoDuplicado(fecha, equipoLocalId, equipoVisitanteId, id);
  }

  // Importante para el caso borde "corrección de resultado ya cargado":
  // golesLocal/golesVisitante pueden venir explícitamente en `input` (incluso
  // como null, para "deshacer" un resultado y volver a PENDIENTE). Si no
  // vinieron en el body, mantenemos el valor actual.
  const golesLocal = 'golesLocal' in input ? input.golesLocal : actual.golesLocal;
  const golesVisitante = 'golesVisitante' in input ? input.golesVisitante : actual.golesVisitante;
  const estado = inferirEstado(input.estado, golesLocal, golesVisitante);

  return prisma.partido.update({
    where: { id },
    data: {
      categoriaId,
      zonaId,
      equipoLocalId,
      equipoVisitanteId,
      golesLocal,
      golesVisitante,
      fecha,
      jornada: input.jornada ?? actual.jornada,
      campo: input.campo ?? actual.campo,
      cancha: input.cancha ?? actual.cancha,
      horario: input.horario ?? actual.horario,
      estado,
    },
  });
}

export interface FiltrosPartidos {
  equipoId?: number;
  categoriaId?: number;
  jornada?: number;
}

export async function listarPartidos(filtros: FiltrosPartidos) {
  return prisma.partido.findMany({
    where: {
      categoriaId: filtros.categoriaId,
      jornada: filtros.jornada,
      ...(filtros.equipoId !== undefined
        ? { OR: [{ equipoLocalId: filtros.equipoId }, { equipoVisitanteId: filtros.equipoId }] }
        : {}),
    },
    include: { equipoLocal: true, equipoVisitante: true, categoria: true, zona: true },
    orderBy: [{ fecha: 'asc' }, { jornada: 'asc' }],
  });
}
