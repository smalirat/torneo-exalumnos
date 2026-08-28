import { Prisma, Equipo, Jugador } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { normalizar } from './xlsxHelpers';

type Tx = Prisma.TransactionClient;

export async function buscarOCrearEquipo(
  db: Tx | typeof prisma,
  nombre: string,
): Promise<{ equipo: Equipo; creado: boolean }> {
  const nombreLimpio = nombre.trim();
  const existente = await db.equipo.findFirst({
    where: { nombre: { equals: nombreLimpio, mode: 'insensitive' } },
  });
  if (existente) return { equipo: existente, creado: false };
  const equipo = await db.equipo.create({ data: { nombre: nombreLimpio } });
  return { equipo, creado: true };
}

export async function buscarOCrearJugador(
  db: Tx | typeof prisma,
  nombre: string,
  equipoId: number | null,
): Promise<{ jugador: Jugador; creado: boolean }> {
  const nombreLimpio = nombre.trim();
  const existente = await db.jugador.findFirst({
    where: {
      nombre: { equals: nombreLimpio, mode: 'insensitive' },
      equipoId: equipoId ?? undefined,
    },
  });
  if (existente) return { jugador: existente, creado: false };
  const jugador = await db.jugador.create({ data: { nombre: nombreLimpio, equipoId } });
  return { jugador, creado: true };
}

/**
 * Resuelve un label de bloque ("ZONA 1", "CATEGORIA B", "INTERZONAL") a un
 * { categoriaId, zonaId } dentro de un torneo. Devuelve null si no matchea
 * nada — el caller debe loguear un warning y SALTEAR el bloque, no explotar
 * toda la importación por una hoja con un label que no reconocemos.
 */
export async function resolverCategoriaZona(
  db: Tx | typeof prisma,
  torneoId: number,
  label: string,
): Promise<{ categoriaId: number; zonaId: number | null } | null> {
  const labelNorm = normalizar(label);

  const categorias = await db.categoria.findMany({ where: { torneoId }, include: { zonas: true } });

  // 1) "CATEGORIA A" / "CATEGORÍA B" -> matchea por nombre de categoría exacto
  const matchCategoria = labelNorm.match(/CATEGOR[IÍ]A\s*["']?([ABC])["']?/);
  if (matchCategoria) {
    const categoria = categorias.find((c) => c.nombre === matchCategoria[1]);
    if (categoria) return { categoriaId: categoria.id, zonaId: null };
  }

  // 2) "ZONA 1" / "ZONA 2" -> busca una zona con ese nombre en cualquier
  // categoría del torneo. Si el torneo tiene una sola categoría con zonas,
  // esto resuelve sin ambigüedad. Si tenés VARIAS categorías con "Zona 1"
  // cada una, este heurístico no alcanza — avisame y lo ajustamos pidiendo
  // que el nombre de zona incluya la categoría (ej. "A - Zona 1").
  const matchZona = labelNorm.match(/ZONA\s*(\d+)/);
  if (matchZona) {
    for (const categoria of categorias) {
      const zona = categoria.zonas.find((z) => normalizar(z.nombre) === `ZONA ${matchZona[1]}`);
      if (zona) return { categoriaId: categoria.id, zonaId: zona.id };
    }
  }

  return null;
}
