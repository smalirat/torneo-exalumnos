import { prisma } from '../../lib/prisma';
import { ConflictError, NotFoundError, ValidationError } from '../../utils/AppError';
import { crearTemporada } from '../temporadas/temporadas.service';
import { CrearTemporadaInput } from '../temporadas/temporadas.validation';
import { crearTorneo } from '../torneos/torneos.service';
import { CrearTorneoInput } from '../torneos/torneos.validation';
import { crearCategoria } from '../categorias/categorias.service';
import { CrearCategoriaInput } from '../categorias/categorias.validation';
import { crearZona } from '../zonas/zonas.service';
import { CrearZonaInput } from '../zonas/zonas.validation';

function nombreZonaCanonico(nombre: string): string {
  const match = nombre.trim().match(/^zona\s*0*(\d+)$/i);

  if (!match) {
    throw new ValidationError(
      'El nombre de la zona debe tener el formato "Zona N". Ejemplo: "Zona 1".',
    );
  }

  const numero = Number(match[1]);

  if (!Number.isInteger(numero) || numero <= 0) {
    throw new ValidationError('El número de zona debe ser un entero positivo.');
  }

  return `Zona ${numero}`;
}

function claveZona(nombre: string): string {
  const match = nombre.trim().match(/^zona\s*0*(\d+)$/i);

  if (match) {
    return `ZONA ${Number(match[1])}`;
  }

  return nombre.trim().toUpperCase();
}

function nombreZonaCanonicoSeguro(nombre: string): string | null {
  try {
    return nombreZonaCanonico(nombre);
  } catch {
    return null;
  }
}

// ------------------------------------------------------------
// Lectura completa para el panel
// ------------------------------------------------------------

export async function obtenerEstructuraAdmin() {
  const temporadas = await prisma.temporada.findMany({
    include: {
      torneos: {
        include: {
          categorias: {
            include: {
              zonas: {
                orderBy: { nombre: 'asc' },
              },
            },
            orderBy: { nombre: 'asc' },
          },
        },
        orderBy: { nombre: 'asc' },
      },
    },
    orderBy: { anio: 'desc' },
  });

  const problemas: string[] = [];

  // Además de mostrar la estructura, auditamos configuraciones viejas
  // que podrían ser ambiguas para el importador de Excel.
  for (const temporada of temporadas) {
    for (const torneo of temporada.torneos) {
      const zonasPorClave = new Map<
        string,
        Array<{ categoria: string; zona: string }>
      >();

      for (const categoria of torneo.categorias) {
        for (const zona of categoria.zonas) {
          const canonico = nombreZonaCanonicoSeguro(zona.nombre);

          if (!canonico) {
            problemas.push(
              `${temporada.anio} / ${torneo.nombre} / Categoría ${categoria.nombre}: ` +
                `la zona "${zona.nombre}" no usa el formato "Zona N".`,
            );
          }

          const clave = claveZona(zona.nombre);
          const existentes = zonasPorClave.get(clave) ?? [];

          existentes.push({
            categoria: categoria.nombre,
            zona: zona.nombre,
          });

          zonasPorClave.set(clave, existentes);
        }
      }

      for (const [clave, coincidencias] of zonasPorClave) {
        if (coincidencias.length <= 1) continue;

        const categoriasDistintas = new Set(
          coincidencias.map((x) => x.categoria),
        );

        if (categoriasDistintas.size > 1) {
          problemas.push(
            `${temporada.anio} / ${torneo.nombre}: ${clave} aparece en más de una categoría (` +
              `${[...categoriasDistintas]
                .map((c) => `Categoría ${c}`)
                .join(', ')}). ` +
              `El Excel no puede resolver esa zona de forma inequívoca.`,
          );
        } else {
          problemas.push(
            `${temporada.anio} / ${torneo.nombre} / Categoría ${
              [...categoriasDistintas][0]
            }: ${clave} está duplicada con variantes de escritura.`,
          );
        }
      }
    }
  }

  return {
    temporadas,
    problemas,
  };
}

// ------------------------------------------------------------
// Temporadas
// ------------------------------------------------------------

export async function crearTemporadaDesdeAdmin(
  input: CrearTemporadaInput,
) {
  const existente = await prisma.temporada.findUnique({
    where: { anio: input.anio },
  });

  if (existente) {
    throw new ConflictError(`La temporada ${input.anio} ya existe.`);
  }

  return crearTemporada(input);
}

// ------------------------------------------------------------
// Torneos
// ------------------------------------------------------------

export async function crearTorneoDesdeAdmin(input: CrearTorneoInput) {
  const temporada = await prisma.temporada.findUnique({
    where: { id: input.temporadaId },
  });

  if (!temporada) {
    throw new NotFoundError('Temporada', input.temporadaId);
  }

  const existente = await prisma.torneo.findUnique({
    where: {
      temporadaId_nombre: {
        temporadaId: input.temporadaId,
        nombre: input.nombre,
      },
    },
  });

  if (existente) {
    throw new ConflictError(
      `Ya existe el torneo ${input.nombre} en la temporada ${temporada.anio}.`,
    );
  }

  return crearTorneo(input);
}

// ------------------------------------------------------------
// Categorías
// ------------------------------------------------------------

export async function crearCategoriaDesdeAdmin(
  input: CrearCategoriaInput,
) {
  const torneo = await prisma.torneo.findUnique({
    where: { id: input.torneoId },
    include: {
      temporada: true,
    },
  });

  if (!torneo) {
    throw new NotFoundError('Torneo', input.torneoId);
  }

  const existente = await prisma.categoria.findUnique({
    where: {
      torneoId_nombre: {
        torneoId: input.torneoId,
        nombre: input.nombre,
      },
    },
  });

  if (existente) {
    throw new ConflictError(
      `Ya existe la Categoría ${input.nombre} en ` +
        `${torneo.nombre} ${torneo.temporada.anio}.`,
    );
  }

  return crearCategoria(input);
}

// ------------------------------------------------------------
// Zonas
// ------------------------------------------------------------

export async function crearZonaDesdeAdmin(input: CrearZonaInput) {
  const categoria = await prisma.categoria.findUnique({
    where: { id: input.categoriaId },
    include: {
      torneo: {
        include: {
          temporada: true,
        },
      },
      zonas: true,
    },
  });

  if (!categoria) {
    throw new NotFoundError('Categoria', input.categoriaId);
  }

  const nombre = nombreZonaCanonico(input.nombre);

  /*
   * IMPORTANTE:
   *
   * El Excel trae labels como "ZONA 1" sin decir necesariamente
   * "Categoría A - Zona 1".
   *
   * Por lo tanto, dentro del MISMO torneo no permitimos:
   *
   * Categoria A -> Zona 1
   * Categoria B -> Zona 1
   *
   * porque resolverCategoriaZona() no tendría forma confiable de saber
   * cuál de las dos quiso decir el Excel.
   */
  const zonasMismoTorneo = await prisma.zona.findMany({
    where: {
      categoria: {
        torneoId: categoria.torneoId,
      },
    },
    include: {
      categoria: true,
    },
  });

  const conflicto = zonasMismoTorneo.find(
    (zona) => claveZona(zona.nombre) === claveZona(nombre),
  );

  if (conflicto) {
    if (conflicto.categoriaId === categoria.id) {
      throw new ConflictError(
        `La ${nombre} ya existe en la Categoría ${categoria.nombre}.`,
      );
    }

    throw new ConflictError(
      `No se puede crear ${nombre} en la Categoría ${categoria.nombre}: ` +
        `ya existe en la Categoría ${conflicto.categoria.nombre} del mismo torneo. ` +
        `Eso vuelve ambiguos los labels del Excel.`,
    );
  }

  /*
   * Otra protección importante:
   *
   * Una categoría que todavía no tiene zonas se considera "directa".
   *
   * Si ya tiene equipos/partidos/puntos cargados directamente contra la
   * categoría, no permitimos que el admin de repente cree su primera zona,
   * porque convertiría:
   *
   * Categoria A
   *
   * en:
   *
   * Categoria A
   *   Zona 1
   *
   * dejando datos anteriores sin saber a qué zona pertenecen.
   */
  if (categoria.zonas.length === 0) {
    const [
      inscripcionesDirectas,
      partidosDirectos,
      puntosDirectos,
    ] = await Promise.all([
      prisma.inscripcionEquipo.count({
        where: {
          categoriaId: categoria.id,
          zonaId: null,
        },
      }),

      prisma.partido.count({
        where: {
          categoriaId: categoria.id,
          zonaId: null,
        },
      }),

      prisma.puntosRestados.count({
        where: {
          categoriaId: categoria.id,
          zonaId: null,
        },
      }),
    ]);

    if (
      inscripcionesDirectas +
        partidosDirectos +
        puntosDirectos >
      0
    ) {
      throw new ConflictError(
        `La Categoría ${categoria.nombre} ya tiene datos cargados sin zona. ` +
          `No se puede convertir en una categoría zonificada desde el panel ` +
          `porque dejaría datos estructuralmente ambiguos.`,
      );
    }
  }

  return crearZona({
    ...input,
    nombre,
  });
}

// ------------------------------------------------------------
// Bajas (solo se permiten si no hay datos debajo)
// ------------------------------------------------------------

export async function eliminarTemporadaDesdeAdmin(id: number) {
  const temporada = await prisma.temporada.findUnique({
    where: { id },
    include: { torneos: { select: { id: true } } },
  });
  if (!temporada) throw new NotFoundError('Temporada', id);

  if (temporada.torneos.length > 0) {
    throw new ConflictError(
      `No se puede eliminar la temporada ${temporada.anio}: ` +
        `todavía tiene ${temporada.torneos.length} torneo(s). Eliminá primero la estructura inferior.`,
    );
  }

  return prisma.temporada.delete({ where: { id } });
}

export async function eliminarTorneoDesdeAdmin(id: number) {
  const torneo = await prisma.torneo.findUnique({
    where: { id },
    include: { categorias: { select: { id: true } } },
  });
  if (!torneo) throw new NotFoundError('Torneo', id);

  if (torneo.categorias.length > 0) {
    throw new ConflictError(
      `No se puede eliminar el torneo ${torneo.nombre}: ` +
        `todavía tiene ${torneo.categorias.length} categoría(s).`,
    );
  }

  return prisma.torneo.delete({ where: { id } });
}

export async function eliminarCategoriaDesdeAdmin(id: number) {
  const categoria = await prisma.categoria.findUnique({
    where: { id },
    include: {
      zonas: { select: { id: true } },
      inscripciones: { select: { id: true } },
      partidos: { select: { id: true } },
      campeones: { select: { id: true } },
      puntosRestados: { select: { id: true } },
    },
  });
  if (!categoria) throw new NotFoundError('Categoria', id);

  const dependencias = [
    ['zonas', categoria.zonas.length],
    ['equipos inscriptos', categoria.inscripciones.length],
    ['partidos', categoria.partidos.length],
    ['campeones', categoria.campeones.length],
    ['quitas de puntos', categoria.puntosRestados.length],
  ] as const;

  const presentes = dependencias
    .filter(([, cantidad]) => cantidad > 0)
    .map(([nombre, cantidad]) => `${nombre} (${cantidad})`);

  if (presentes.length > 0) {
    throw new ConflictError(
      `No se puede eliminar la Categoría ${categoria.nombre}: ` +
        `todavía tiene ${presentes.join(', ')}.`,
    );
  }

  return prisma.categoria.delete({ where: { id } });
}

export async function eliminarZonaDesdeAdmin(id: number) {
  const zona = await prisma.zona.findUnique({
    where: { id },
    include: {
      inscripciones: { select: { id: true } },
      partidos: { select: { id: true } },
      puntosRestados: { select: { id: true } },
    },
  });
  if (!zona) throw new NotFoundError('Zona', id);

  const dependencias = [
    ['equipos inscriptos', zona.inscripciones.length],
    ['partidos', zona.partidos.length],
    ['quitas de puntos', zona.puntosRestados.length],
  ] as const;

  const presentes = dependencias
    .filter(([, cantidad]) => cantidad > 0)
    .map(([nombre, cantidad]) => `${nombre} (${cantidad})`);

  if (presentes.length > 0) {
    throw new ConflictError(
      `No se puede eliminar ${zona.nombre}: ` +
        `todavía tiene ${presentes.join(', ')}.`,
    );
  }

  return prisma.zona.delete({ where: { id } });
}