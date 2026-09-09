import { Prisma } from '@prisma/client';

import { prisma } from '../../lib/prisma';

import {
  ConflictError,
  NotFoundError,
  ValidationError,
} from '../../utils/AppError';

import {
  crearInscripcion,
  crearInscripcionConDb,
} from '../inscripciones/inscripciones.service';

type Db =
  | typeof prisma
  | Prisma.TransactionClient;

// ============================================================
// Resolver destino
// ============================================================

async function resolverDestino(
  db: Db,
  destino: string,
): Promise<{
  categoriaId: number;
  zonaId?: number;
  descripcion: string;
}> {
  const match =
    destino.match(
      /^(categoria|zona):(\d+)$/,
    );

  if (!match) {
    throw new ValidationError(
      'Destino de inscripción inválido.',
    );
  }

  const tipo = match[1];
  const id = Number(match[2]);

  // ----------------------------------------------------------
  // CATEGORIA DIRECTA
  // ----------------------------------------------------------

  if (tipo === 'categoria') {
    const categoria =
      await db.categoria.findUnique({
        where: {
          id,
        },

        include: {
          zonas: true,

          torneo: {
            include: {
              temporada: true,
            },
          },
        },
      });

    if (!categoria) {
      throw new NotFoundError(
        'Categoria',
        id,
      );
    }

    /*
     * Protección crítica:
     *
     * si tiene zonas, no permitimos apuntar
     * directamente a la categoría.
     */
    if (categoria.zonas.length > 0) {
      throw new ValidationError(
        `La Categoría ${categoria.nombre} tiene zonas. ` +
          `Elegí una zona específica.`,
      );
    }

    return {
      categoriaId: categoria.id,

      descripcion:
        `${categoria.torneo.temporada.anio} — ` +
        `${categoria.torneo.nombre} — ` +
        `Categoría ${categoria.nombre}`,
    };
  }

  // ----------------------------------------------------------
  // ZONA
  // ----------------------------------------------------------

  const zona =
    await db.zona.findUnique({
      where: {
        id,
      },

      include: {
        categoria: {
          include: {
            torneo: {
              include: {
                temporada: true,
              },
            },
          },
        },
      },
    });

  if (!zona) {
    throw new NotFoundError(
      'Zona',
      id,
    );
  }

  return {
    categoriaId:
      zona.categoriaId,

    zonaId:
      zona.id,

    descripcion:
      `${zona.categoria.torneo.temporada.anio} — ` +
      `${zona.categoria.torneo.nombre} — ` +
      `Categoría ${zona.categoria.nombre} — ` +
      `${zona.nombre}`,
  };
}

// ============================================================
// Datos necesarios para la pantalla
// ============================================================

export async function obtenerGestionEquiposAdmin() {
  const [
    equipos,
    categorias,
    inscripciones,
  ] = await Promise.all([
    prisma.equipo.findMany({
      orderBy: {
        nombre: 'asc',
      },
    }),

    prisma.categoria.findMany({
      include: {
        zonas: {
          orderBy: {
            nombre: 'asc',
          },
        },

        torneo: {
          include: {
            temporada: true,
          },
        },
      },

      orderBy: [
        {
          torneo: {
            temporada: {
              anio: 'desc',
            },
          },
        },

        {
          torneo: {
            nombre: 'asc',
          },
        },

        {
          nombre: 'asc',
        },
      ],
    }),

    prisma.inscripcionEquipo.findMany({
      include: {
        equipo: true,
        zona: true,

        categoria: {
          include: {
            torneo: {
              include: {
                temporada: true,
              },
            },
          },
        },
      },

      orderBy: [
        {
          categoria: {
            torneo: {
              temporada: {
                anio: 'desc',
              },
            },
          },
        },

        {
          equipo: {
            nombre: 'asc',
          },
        },
      ],
    }),
  ]);

  /*
   * El frontend no recibe categoriaId + zonaId
   * como campos independientes.
   *
   * Recibe destinos seguros:
   *
   * categoria:4
   *
   * o
   *
   * zona:7
   */
  const destinos =
    categorias.flatMap(
      (categoria) => {
        const prefijo =
          `${categoria.torneo.temporada.anio} — ` +
          `${categoria.torneo.nombre} — ` +
          `Categoría ${categoria.nombre}`;

        if (
          categoria.zonas.length === 0
        ) {
          return [
            {
              value:
                `categoria:${categoria.id}`,

              label:
                `${prefijo} — Sin zonas`,
            },
          ];
        }

        return categoria.zonas.map(
          (zona) => ({
            value:
              `zona:${zona.id}`,

            label:
              `${prefijo} — ${zona.nombre}`,
          }),
        );
      },
    );

  return {
    equipos,
    destinos,
    inscripciones,
  };
}

// ============================================================
// Inscribir equipo existente
// ============================================================

export async function inscribirEquipoExistenteDesdeAdmin(
  input: {
    equipoId: number;
    destino: string;
  },
) {
  const destino =
    await resolverDestino(
      prisma,
      input.destino,
    );

  return crearInscripcion({
    equipoId:
      input.equipoId,

    categoriaId:
      destino.categoriaId,

    zonaId:
      destino.zonaId,
  });
}

// ============================================================
// Crear equipo + inscribirlo
// ============================================================

export async function crearEquipoEInscribirDesdeAdmin(
  input: {
    nombre: string;
    destino: string;
  },
) {
  const nombre =
    input.nombre.trim();

  /*
   * El schema de Prisma tiene @unique(nombre),
   * pero PostgreSQL normalmente diferencia mayúsculas.
   *
   * No queremos:
   *
   * "Los Andes"
   * "LOS ANDES"
   * "los andes"
   *
   * como tres equipos.
   */
  const equipoConMismoNombre =
    await prisma.equipo.findFirst({
      where: {
        nombre: {
          equals: nombre,
          mode: 'insensitive',
        },
      },
    });

  if (equipoConMismoNombre) {
    throw new ConflictError(
      `Ya existe un equipo llamado "${equipoConMismoNombre.nombre}". ` +
        `Usá la opción "Inscribir equipo existente".`,
    );
  }

  /*
   * Todo junto en una transacción:
   *
   * - resolver destino
   * - crear equipo
   * - crear inscripción
   *
   * Si la inscripción falla,
   * NO queda un equipo huérfano creado.
   */
  return prisma.$transaction(
    async (tx) => {
      const destino =
        await resolverDestino(
          tx,
          input.destino,
        );

      /*
       * Volvemos a comprobar el nombre dentro
       * de la transacción.
       */
      const existente =
        await tx.equipo.findFirst({
          where: {
            nombre: {
              equals: nombre,
              mode: 'insensitive',
            },
          },
        });

      if (existente) {
        throw new ConflictError(
          `Ya existe un equipo llamado "${existente.nombre}".`,
        );
      }

      const equipo =
        await tx.equipo.create({
          data: {
            nombre,
          },
        });

      const inscripcion =
        await crearInscripcionConDb(
          tx,
          {
            equipoId:
              equipo.id,

            categoriaId:
              destino.categoriaId,

            zonaId:
              destino.zonaId,
          },
        );

      return {
        equipo,
        inscripcion,
        destino:
          destino.descripcion,
      };
    },
  );
}

// ============================================================
// Eliminar equipo (solo si no tiene datos asociados)
// ============================================================

export async function eliminarEquipoDesdeAdmin(id: number) {
  const equipo = await prisma.equipo.findUnique({
    where: { id },
    include: {
      inscripciones: { select: { id: true } },
      jugadores: { select: { id: true } },
      delegados: { select: { id: true } },
      partidosLocal: { select: { id: true } },
      partidosVisitante: { select: { id: true } },
    },
  });
  if (!equipo) throw new NotFoundError('Equipo', id);

  const dependencias = [
    ['inscripciones', equipo.inscripciones.length],
    ['jugadores', equipo.jugadores.length],
    ['usuarios delegados', equipo.delegados.length],
    ['partidos como local', equipo.partidosLocal.length],
    ['partidos como visitante', equipo.partidosVisitante.length],
  ] as const;

  const presentes = dependencias
    .filter(([, cantidad]) => cantidad > 0)
    .map(([nombre, cantidad]) => `${nombre} (${cantidad})`);

  if (presentes.length > 0) {
    throw new ConflictError(
      `No se puede eliminar el equipo "${equipo.nombre}": ` +
        `todavía tiene ${presentes.join(', ')}.`,
    );
  }

  return prisma.equipo.delete({ where: { id } });
}