import { Prisma } from '@prisma/client';

import { prisma } from '../../lib/prisma';
import {
  ConflictError,
  NotFoundError,
  ValidationError,
} from '../../utils/AppError';

import { CrearInscripcionInput } from './inscripciones.validation';

type Db =
  | typeof prisma
  | Prisma.TransactionClient;

export async function listarInscripciones(filtros: {
  equipoId?: number;
  categoriaId?: number;
  zonaId?: number;
}) {
  return prisma.inscripcionEquipo.findMany({
    where: filtros,

    include: {
      equipo: true,

      categoria: {
        include: {
          torneo: true,
        },
      },

      zona: true,
    },
  });
}

/**
 * Versión interna reutilizable dentro de transacciones.
 *
 * Es útil, por ejemplo, para:
 *
 * crear equipo
 *      +
 * inscribirlo
 *
 * como una única operación atómica.
 */
export async function crearInscripcionConDb(
  db: Db,
  input: CrearInscripcionInput,
) {
  const equipo = await db.equipo.findUnique({
    where: {
      id: input.equipoId,
    },
  });

  if (!equipo) {
    throw new NotFoundError(
      'Equipo',
      input.equipoId,
    );
  }

  const categoria =
    await db.categoria.findUnique({
      where: {
        id: input.categoriaId,
      },

      include: {
        zonas: true,
        torneo: true,
      },
    });

  if (!categoria) {
    throw new NotFoundError(
      'Categoria',
      input.categoriaId,
    );
  }

  // ==========================================================
  // Validación Categoría / Zona
  // ==========================================================

  if (categoria.zonas.length > 0) {
    /*
     * Si la categoría está zonificada:
     *
     * Categoría A
     *   Zona 1
     *   Zona 2
     *
     * no permitimos:
     *
     * Equipo X -> Categoría A
     *
     * Tiene que ser:
     *
     * Equipo X -> Categoría A -> Zona 1
     */
    if (input.zonaId === undefined) {
      throw new ValidationError(
        `La Categoría ${categoria.nombre} está dividida en zonas. ` +
          `Debés indicar una zona concreta.`,
      );
    }
  } else {
    /*
     * Categoría directa:
     *
     * Categoría B
     *
     * no debería recibir zonaId.
     */
    if (input.zonaId !== undefined) {
      throw new ValidationError(
        `La Categoría ${categoria.nombre} no utiliza zonas.`,
      );
    }
  }

  if (input.zonaId !== undefined) {
    const zona = await db.zona.findUnique({
      where: {
        id: input.zonaId,
      },
    });

    if (!zona) {
      throw new NotFoundError(
        'Zona',
        input.zonaId,
      );
    }

    if (
      zona.categoriaId !==
      input.categoriaId
    ) {
      throw new ValidationError(
        'La zona indicada no pertenece a la categoría indicada.',
      );
    }
  }

  // ==========================================================
  // Un equipo solamente puede jugar UNA categoría/zona
  // dentro del mismo torneo.
  // ==========================================================

  const inscripcionExistenteEnTorneo =
    await db.inscripcionEquipo.findFirst({
      where: {
        equipoId: input.equipoId,

        categoria: {
          torneoId: categoria.torneoId,
        },
      },

      include: {
        categoria: true,
        zona: true,
      },
    });

  if (inscripcionExistenteEnTorneo) {
    throw new ConflictError(
      `El equipo "${equipo.nombre}" ya está inscripto en este torneo ` +
        `(Categoría ${inscripcionExistenteEnTorneo.categoria.nombre}` +
        `${
          inscripcionExistenteEnTorneo.zona
            ? `, ${inscripcionExistenteEnTorneo.zona.nombre}`
            : ''
        }). ` +
        `Un equipo no puede jugar dos categorías o zonas distintas dentro del mismo torneo.`,
    );
  }

  return db.inscripcionEquipo.create({
    data: input,
  });
}

export async function crearInscripcion(
  input: CrearInscripcionInput,
) {
  return prisma.$transaction(
    async (tx) =>
      crearInscripcionConDb(
        tx,
        input,
      ),
  );
}

export async function eliminarInscripcion(
  id: number,
) {
  const inscripcion =
    await prisma.inscripcionEquipo.findUnique({
      where: {
        id,
      },
    });

  if (!inscripcion) {
    throw new NotFoundError(
      'InscripcionEquipo',
      id,
    );
  }

  await prisma.inscripcionEquipo.delete({
    where: {
      id,
    },
  });
}