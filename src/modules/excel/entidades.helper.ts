import {
  Prisma,
  Equipo,
  Jugador,
} from '@prisma/client';

import { prisma } from '../../lib/prisma';
import { normalizar } from './xlsxHelpers';

type Tx = Prisma.TransactionClient;

export async function buscarOCrearEquipo(
  db: Tx | typeof prisma,
  nombre: string,
): Promise<{
  equipo: Equipo;
  creado: boolean;
}> {
  const nombreLimpio = nombre.trim();

  const existente = await db.equipo.findFirst({
    where: {
      nombre: {
        equals: nombreLimpio,
        mode: 'insensitive',
      },
    },
  });

  if (existente) {
    return {
      equipo: existente,
      creado: false,
    };
  }

  const equipo = await db.equipo.create({
    data: {
      nombre: nombreLimpio,
    },
  });

  return {
    equipo,
    creado: true,
  };
}

export async function buscarOCrearJugador(
  db: Tx | typeof prisma,
  nombre: string,
  equipoId: number | null,
): Promise<{
  jugador: Jugador;
  creado: boolean;
}> {
  const nombreLimpio = nombre.trim();

  const existente = await db.jugador.findFirst({
    where: {
      nombre: {
        equals: nombreLimpio,
        mode: 'insensitive',
      },

      equipoId:
        equipoId ?? undefined,
    },
  });

  if (existente) {
    return {
      jugador: existente,
      creado: false,
    };
  }

  const jugador = await db.jugador.create({
    data: {
      nombre: nombreLimpio,
      equipoId,
    },
  });

  return {
    jugador,
    creado: true,
  };
}

function numeroZona(
  nombre: string,
): number | null {
  const match =
    normalizar(nombre)
      .match(/^ZONA\s*(\d+)$/);

  if (!match) {
    return null;
  }

  return Number(match[1]);
}

/**
 * Resuelve:
 *
 * "ZONA 1"
 * "CATEGORIA B"
 * "INTERZONAL"
 * "PROMOCION"
 * "FINAL"
 *
 * a:
 *
 * { categoriaId, zonaId }
 *
 * dentro del torneo indicado.
 *
 * REGLA:
 * nunca elegimos arbitrariamente
 * "la primera coincidencia".
 *
 * Si no podemos determinar el destino
 * de forma inequívoca, devolvemos null.
 *
 * El importador ya sabe manejar null:
 * agrega una advertencia y omite el bloque/fila.
 *
 * Es mucho más seguro omitir una fila
 * que asociarla a la categoría incorrecta.
 */
export async function resolverCategoriaZona(
  db: Tx | typeof prisma,
  torneoId: number,
  label: string,
): Promise<{
  categoriaId: number;
  zonaId: number | null;
} | null> {

  const labelNorm =
    normalizar(label);

  const categorias =
    await db.categoria.findMany({
      where: {
        torneoId,
      },

      include: {
        zonas: true,
      },
    });

  // ==========================================================
  // 1. CATEGORIA A / B / C
  // ==========================================================

  const matchCategoria =
    labelNorm.match(
      /CATEGOR[I ]A\s*["']?([ABC])["']?/,
    );

  if (matchCategoria) {
    const coincidencias =
      categorias.filter(
        (categoria) =>
          categoria.nombre ===
          matchCategoria[1],
      );

    if (coincidencias.length === 1) {
      return {
        categoriaId:
          coincidencias[0].id,

        zonaId: null,
      };
    }

    return null;
  }


  // ==========================================================
  // 2. ZONA 1 / ZONA 2 / ...
  // ==========================================================

  const matchZona =
    labelNorm.match(
      /^ZONA\s*(\d+)$/,
    );

  if (matchZona) {
    const numeroBuscado =
      Number(matchZona[1]);

    const coincidencias =
      categorias.flatMap(
        (categoria) =>
          categoria.zonas
            .filter(
              (zona) =>
                numeroZona(zona.nombre) ===
                numeroBuscado,
            )
            .map(
              (zona) => ({
                categoriaId:
                  categoria.id,

                zonaId:
                  zona.id,
              }),
            ),
      );

    // Solamente resolvemos si
    // existe UNA única Zona N
    // dentro del torneo.
    if (coincidencias.length === 1) {
      return coincidencias[0];
    }

    return null;
  }


  // ==========================================================
  // 3. INTERZONAL / PROMOCION / FINAL
  // ==========================================================

  if (
    labelNorm.includes('INTERZONAL') ||
    labelNorm.includes('PROMOCION') ||
    labelNorm.includes('FINAL')
  ) {
    /*
     * Caso normal:
     *
     * Categoria A
     *   Zona 1
     *   Zona 2
     *
     * Categoria B
     *
     * Categoria C
     *
     * Si aparece "FINAL INTERZONAL",
     * la única categoría zonificada
     * es A, así que se puede inferir
     * de forma segura.
     */
    const categoriasConZonas =
      categorias.filter(
        (categoria) =>
          categoria.zonas.length > 0,
      );

    if (
      categoriasConZonas.length === 1
    ) {
      return {
        categoriaId:
          categoriasConZonas[0].id,

        zonaId: null,
      };
    }

    /*
     * Caso trivial:
     *
     * si el torneo entero tiene
     * una única categoría,
     * tampoco hay ambigüedad.
     */
    if (categorias.length === 1) {
      return {
        categoriaId:
          categorias[0].id,

        zonaId: null,
      };
    }

    return null;
  }

  return null;
}