import { NombreTorneo } from '@prisma/client';
import { prisma } from '../../../lib/prisma';
import { buscarHojaPorPatron, leerLibro } from '../xlsxHelpers';
import { buscarOCrearEquipo } from '../entidades.helper';
import { FilaHistoriaOmitida, parsearHistoria } from '../parsers/historia.parser';

export interface ResumenHistoria {
  temporadasCreadas: number;
  torneosCreados: number;
  categoriasCreadas: number;
  equiposCreados: number;
  campeonesCreados: number;
  omitidos: FilaHistoriaOmitida[];
  advertencias: string[];
}

// La hoja HISTORIA no dice si cada título fue Apertura o Clausura:
// se atribuye al APERTURA del año para tener un Torneo al cual colgar
// la Categoría (el Campeon cuelga de Categoria -> Torneo -> Temporada).
// Los equipos viejos se crean sin jugadores (solo nombre).
const TORNEO_HISTORICO = NombreTorneo.APERTURA;

export async function importarHistoria(buffer: Buffer): Promise<ResumenHistoria> {
  const resumen: ResumenHistoria = {
    temporadasCreadas: 0,
    torneosCreados: 0,
    categoriasCreadas: 0,
    equiposCreados: 0,
    campeonesCreados: 0,
    omitidos: [],
    advertencias: [],
  };

  const libro = leerLibro(buffer);
  const hoja = buscarHojaPorPatron(libro, /HISTORIA/i);
  if (!hoja) {
    resumen.advertencias.push('No se encontró hoja HISTORIA.');
    return resumen;
  }

  const { filas, omitidos } = parsearHistoria(hoja.matriz);
  resumen.omitidos.push(...omitidos);

  for (const fila of filas) {
    let temporada = await prisma.temporada.findUnique({ where: { anio: fila.anio } });
    if (!temporada) {
      temporada = await prisma.temporada.create({ data: { anio: fila.anio } });
      resumen.temporadasCreadas++;
    }

    let torneo = await prisma.torneo.findFirst({
      where: { temporadaId: temporada.id, nombre: TORNEO_HISTORICO },
    });
    if (!torneo) {
      torneo = await prisma.torneo.create({
        data: { temporadaId: temporada.id, nombre: TORNEO_HISTORICO },
      });
      resumen.torneosCreados++;
    }

    let categoria = await prisma.categoria.findFirst({
      where: { torneoId: torneo.id, nombre: fila.categoria },
    });
    if (!categoria) {
      categoria = await prisma.categoria.create({
        data: { torneoId: torneo.id, nombre: fila.categoria },
      });
      resumen.categoriasCreadas++;
    }

    const { equipo, creado: equipoCreado } = await buscarOCrearEquipo(prisma, fila.equipoNombre);
    if (equipoCreado) resumen.equiposCreados++;

    // Idempotencia: reimportar el mismo archivo no duplica (@@unique
    // Campeon[anio, categoriaId] como respaldo a nivel DB).
    const existente = await prisma.campeon.findFirst({
      where: { anio: fila.anio, categoriaId: categoria.id },
    });
    if (existente) {
      resumen.omitidos.push({
        anio: fila.anio,
        categoria: fila.categoria,
        equipoRaw: fila.equipoNombre,
        motivo: 'YA_CARGADO',
      });
      continue;
    }

    await prisma.campeon.create({
      data: { anio: fila.anio, categoriaId: categoria.id, equipoId: equipo.id },
    });
    resumen.campeonesCreados++;
  }

  return resumen;
}
