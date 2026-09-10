import { prisma } from '../../lib/prisma';

import {
  NotFoundError,
  ValidationError,
} from '../../utils/AppError';

import {
  ActualizarSancionInput,
  CrearSancionInput,
} from './sanciones.validation';


async function validarReferencias(
  jugadorId: number,
  equipoId: number,
  torneoId: number,
) {
  const [jugador, equipo, torneo] =
    await Promise.all([
      prisma.jugador.findUnique({
        where: { id: jugadorId },
      }),

      prisma.equipo.findUnique({
        where: { id: equipoId },
      }),

      prisma.torneo.findUnique({
        where: { id: torneoId },
      }),
    ]);

  if (!jugador) {
    throw new NotFoundError(
      'Jugador',
      jugadorId,
    );
  }

  if (!equipo) {
    throw new NotFoundError(
      'Equipo',
      equipoId,
    );
  }

  if (!torneo) {
    throw new NotFoundError(
      'Torneo',
      torneoId,
    );
  }
}


export async function crearSancion(
  input: CrearSancionInput,
) {
  await validarReferencias(
    input.jugadorId,
    input.equipoId,
    input.torneoId,
  );

  return prisma.sancion.create({
    data: {
      jugadorId:
        input.jugadorId,

      equipoId:
        input.equipoId,

      /*
       * IMPORTANTE:
       *
       * torneoId representa el torneo DONDE SE ORIGINÓ
       * la sanción.
       *
       * La vigencia de la suspensión se consulta por
       * temporada, no solamente por este torneo.
       */
      torneoId:
        input.torneoId,

      tipoTarjeta:
        input.tipoTarjeta,

      fechasSuspension:
        input.fechasSuspension,

      observaciones:
        input.observaciones,
    },
  });
}


export async function eliminarSancion(id: number): Promise<void> {
  const sancion = await prisma.sancion.findUnique({ where: { id } });
  if (!sancion) {
    throw new NotFoundError('Sancion', id);
  }
  await prisma.sancion.delete({ where: { id } });
}


export async function actualizarSancion(
  id: number,
  input: ActualizarSancionInput,
) {
  const sancion =
    await prisma.sancion.findUnique({
      where: { id },
    });

  if (!sancion) {
    throw new NotFoundError(
      'Sancion',
      id,
    );
  }

  if (
    input.estado === 'PENDIENTE' &&
    sancion.estado === 'CUMPLIDA'
  ) {
    throw new ValidationError(
      'No se puede volver a marcar como pendiente ' +
        'una sanción ya cumplida',
    );
  }

  return prisma.sancion.update({
    where: { id },
    data: input,
  });
}


/**
 * Aunque recibamos torneoId como contexto de navegación,
 * las sanciones se buscan por TEMPORADA.
 *
 * Ejemplo:
 *
 * APERTURA 2026 -> roja
 *
 * al entrar a:
 *
 * CLAUSURA 2026
 *
 * la sanción también aparece mientras siga
 * perteneciendo a la temporada 2026.
 */
export async function listarSancionados(
  torneoId: number,
  pendiente?: boolean,
) {
  const torneo =
    await prisma.torneo.findUnique({
      where: {
        id: torneoId,
      },
    });

  if (!torneo) {
    throw new NotFoundError(
      'Torneo',
      torneoId,
    );
  }

  // Sin filtro explícito mostramos lo vigente (pendientes + tribunal),
  // que es lo que usan la vista pública y el panel.
  const estados =
    pendiente === undefined
      ? (['PENDIENTE', 'EN_TRIBUNAL'] as const)
      : pendiente
        ? (['PENDIENTE', 'EN_TRIBUNAL'] as const)
        : (['CUMPLIDA'] as const);

  return prisma.sancion.findMany({
    where: {
      estado: { in: [...estados] },

      torneo: {
        temporadaId:
          torneo.temporadaId,
      },
    },

    include: {
      jugador: true,
      equipo: true,

      torneo: {
        include: {
          temporada: true,
        },
      },
    },

    orderBy: [
      {
        estado: 'desc',
      },

      {
        createdAt: 'desc',
      },
    ],
  });
}


/**
 * A diferencia de las sanciones,
 * las AMONESTACIONES son estrictamente
 * del torneo seleccionado.
 */
export async function listarAmonestados(
  torneoId: number,
) {
  const torneo =
    await prisma.torneo.findUnique({
      where: {
        id: torneoId,
      },
    });

  if (!torneo) {
    throw new NotFoundError(
      'Torneo',
      torneoId,
    );
  }

  return prisma.amonestacion.findMany({
    where: {
      torneoId,
    },

    include: {
      jugador: true,
      equipo: true,
    },

    orderBy: [
      {
        equipo: {
          nombre: 'asc',
        },
      },

      {
        cantidad: 'desc',
      },

      {
        jugador: {
          nombre: 'asc',
        },
      },
    ],
  });
}