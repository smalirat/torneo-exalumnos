import { EstadoPartido } from '@prisma/client';
import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { validarCategoriaYZona } from '../common/validarCategoriaZona';
import { calcularTablaPosiciones } from './standings.calculator';
import { EquipoInscripto, FilaStandings, PartidoResultado, PuntoPresentismoInput } from './standings.types';

/**
 * Devuelve la tabla de posiciones de una categoría (y, si aplica, zona).
 * Toda la aritmética vive en standings.calculator.ts (función pura); acá
 * solo resolvemos qué datos hay que traer de la base.
 */
export async function obtenerStandings(categoriaId: number, zonaId?: number): Promise<FilaStandings[]> {
await validarCategoriaYZona(categoriaId, zonaId);
  const zonaFiltro = zonaId ?? null;

  const [inscripciones, partidosJugados, puntosPresentismo] = await Promise.all([
    prisma.inscripcionEquipo.findMany({
      where: { categoriaId, zonaId: zonaFiltro },
      include: { equipo: true },
    }),
    prisma.partido.findMany({
      where: {
        categoriaId,
        OR: [
          { zonaId: zonaFiltro },
          { zonaId: null } // <-- INCLUYE LOS INTERZONALES Y PROMOCIONES
        ],
        // <-- EXCLUYE LAS PROMOCIONES/FINALES (las guardaremos con jornada 999)
        jornada: { lt: 900 },
        estado: EstadoPartido.JUGADO,
        golesLocal: { not: null },
        golesVisitante: { not: null },
      },
    }),
    prisma.puntosPresentismo.findMany({
      where: { categoriaId, zonaId: zonaFiltro },
    }),
  ]);

  const equipos: EquipoInscripto[] = inscripciones.map((i) => ({
    equipoId: i.equipoId,
    nombre: i.equipo.nombre,
  }));

  const partidos: PartidoResultado[] = partidosJugados.map((p) => ({
    equipoLocalId: p.equipoLocalId,
    equipoVisitanteId: p.equipoVisitanteId,
    // el where ya garantiza que no son null, pero TS no lo sabe
    golesLocal: p.golesLocal as number,
    golesVisitante: p.golesVisitante as number,
  }));

  const pr: PuntoPresentismoInput[] = puntosPresentismo.map((p) => ({
    equipoId: p.equipoId,
    puntos: p.puntos,
  }));

  return calcularTablaPosiciones(equipos, partidos, pr);
}

/**
 * Stats de un solo equipo (GET /equipos/{id}/stats). Reusa obtenerStandings
 * y filtra la fila correspondiente — evita duplicar la lógica de cálculo.
 */
export async function obtenerStatsEquipo(
  equipoId: number,
  categoriaId: number,
  zonaId?: number,
): Promise<FilaStandings> {
  const tabla = await obtenerStandings(categoriaId, zonaId);
  const fila = tabla.find((f) => f.equipoId === equipoId);
  if (!fila) {
    throw new NotFoundError('Inscripción del equipo en esa categoría/zona');
  }
  return fila;
}
