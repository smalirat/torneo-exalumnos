import { EstadoImportacion, EstadoPartido } from '@prisma/client';
import { prisma } from '../../../lib/prisma';
import { NotFoundError } from '../../../utils/AppError';
import { buscarHojaPorPatron, leerLibro, normalizar } from '../xlsxHelpers';
import { buscarOCrearEquipo, buscarOCrearJugador, resolverCategoriaZona } from '../entidades.helper';
import { parsearTablaPosiciones } from '../parsers/tablaPosiciones.parser';
import { parsearFixture } from '../parsers/proxPartido.parser';
import { parsearFechaEnEspanol } from '../parsers/fechaEspanol';
import { parsearRankingJugador } from '../parsers/rankingJugador.parser';
import { parsearSancionados, parsearAmonestados } from '../parsers/sancionados.parser';
import { establecerGoleador } from '../../goleadores/goleadores.service';
import { establecerFigura } from '../../figuras/figuras.service';
import { establecerImbatible } from '../../imbatibles/imbatibles.service';

export interface ResumenImportacion {
  equiposCreados: number;
  jugadoresCreados: number;
  partidosCreados: number;
  partidosActualizados: number;
  partidosOmitidos: {
    motivo: string;
    detalle: string;
  }[];
  puntosRestadosCargados: number;
  goleadoresActualizados: number;
  figurasActualizadas: number;
  imbatiblesActualizados: number;
  sancionesCreadas: number;

  amonestadosActualizados: number;

  advertencias: string[];
}

function resumenVacio(): ResumenImportacion {
  return {
    equiposCreados: 0,
    jugadoresCreados: 0,
    partidosCreados: 0,
    partidosActualizados: 0,
    partidosOmitidos: [],
    puntosRestadosCargados: 0,
    goleadoresActualizados: 0,
    figurasActualizadas: 0,
    imbatiblesActualizados: 0,
    sancionesCreadas: 0,
    amonestadosActualizados: 0,

    advertencias: [],
  };
}

// ---------- Tabla de posiciones (solo extraemos EQUIPO + PR) ----------
async function procesarTablaPosiciones(libro: ReturnType<typeof leerLibro>, torneoId: number, resumen: ResumenImportacion) {
  const hoja = buscarHojaPorPatron(libro, /^TABLA/i);
  if (!hoja) {
    resumen.advertencias.push('No se encontró hoja de tabla de posiciones (se esperaba un nombre que empiece con "Tabla").');
    return;
  }

  const bloques = parsearTablaPosiciones(hoja.matriz);
  for (const bloque of bloques) {
    const resuelto = await resolverCategoriaZona(prisma, torneoId, bloque.label);
    if (!resuelto) {
      resumen.advertencias.push(`Tabla de posiciones: no se pudo resolver "${bloque.label}" a una categoría/zona de este torneo.`);
      continue;
    }

    // Idempotencia: si reimportás el mismo Excel corregido, no queremos ir
    // acumulando PR de la corrida anterior — reemplazamos los PR de esa
    // categoría/zona por los que trae este archivo.
    await prisma.puntosRestados.deleteMany({ where: { categoriaId: resuelto.categoriaId, zonaId: resuelto.zonaId } });

    for (const filaEquipo of bloque.filas) {
      const { equipo, creado } = await buscarOCrearEquipo(prisma, filaEquipo.equipoNombre);
      if (creado) resumen.equiposCreados++;

      const yaInscripto = await prisma.inscripcionEquipo.findFirst({
        where: { equipoId: equipo.id, categoriaId: resuelto.categoriaId, zonaId: resuelto.zonaId },
      });
      if (!yaInscripto) {
        await prisma.inscripcionEquipo.create({
          data: { equipoId: equipo.id, categoriaId: resuelto.categoriaId, zonaId: resuelto.zonaId },
        });
      }

      if (filaEquipo.pr !== 0) {
        await prisma.puntosRestados.create({
          data: {
            equipoId: equipo.id,
            categoriaId: resuelto.categoriaId,
            zonaId: resuelto.zonaId,
            puntos: filaEquipo.pr,
            motivo: `Importado de Excel (hoja "${hoja.nombre}")`,
          },
        });
        resumen.puntosRestadosCargados++;
      }
    }
  }
}

async function procesarFixture(libro: ReturnType<typeof leerLibro>, torneoId: number, resumen: ResumenImportacion) {
  const hojaProx = buscarHojaPorPatron(libro, /PROX|FIXTURE/i);
  const hojaJug = buscarHojaPorPatron(libro, /PART JUG/i);

  const hojas = [hojaProx, hojaJug].filter((h) => h !== null);

  if (hojas.length === 0) {
    resumen.advertencias.push('No se encontró hoja de fixture ni de partidos jugados (se esperaba un nombre tipo "Prox Partido" o "Part Jug").');
    return;
  }

  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId }, include: { temporada: true } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);

  for (const hoja of hojas) {
    const { filas, omitidas } = parsearFixture(hoja.matriz);
    resumen.partidosOmitidos.push(...omitidas.map((o) => ({ motivo: o.motivo, detalle: `[${hoja.nombre}] ${o.detalle}` })));

    for (const filaFixture of filas) {
      let torneoDestinoId = torneoId;
      const nombreTorneoBase = torneo.nombre.toUpperCase();

      // Si el Excel marca explícitamente un torneo distinto, lo buscamos en la misma temporada
      if (filaFixture.torneoLabel && filaFixture.torneoLabel !== nombreTorneoBase) {
        // Asumiendo que Prisma acepta el string si coincide con el Enum NombreTorneo
        const torneoAlternativo = await prisma.torneo.findFirst({
          where: { nombre: filaFixture.torneoLabel as any, temporadaId: torneo.temporadaId }
        });
        if (torneoAlternativo) {
          torneoDestinoId = torneoAlternativo.id;
        }
      }

      const resuelto = await resolverCategoriaZona(prisma, torneoDestinoId, filaFixture.categoriaLabel);
      if (!resuelto) {
        resumen.advertencias.push(
          `Fixture (${hoja.nombre}): no se pudo resolver categoría/zona para "${filaFixture.categoriaLabel}" (${filaFixture.equipoLocal} vs ${filaFixture.equipoVisitante}, jornada ${filaFixture.jornada}).`,
        );
        continue;
      }

      const fecha = filaFixture.fechaTexto ? parsearFechaEnEspanol(filaFixture.fechaTexto, torneo.temporada.anio) : null;
      if (!fecha) {
        resumen.advertencias.push(
          `Fixture (${hoja.nombre}): no se pudo determinar la fecha calendario (${filaFixture.equipoLocal} vs ${filaFixture.equipoVisitante}, jornada ${filaFixture.jornada}, texto="${filaFixture.fechaTexto}") — se omite.`,
        );
        continue;
      }

      const { equipo: local, creado: creadoLocal } = await buscarOCrearEquipo(prisma, filaFixture.equipoLocal);
      if (creadoLocal) resumen.equiposCreados++;
      const { equipo: visitante, creado: creadoVisitante } = await buscarOCrearEquipo(prisma, filaFixture.equipoVisitante);
      if (creadoVisitante) resumen.equiposCreados++;

      const estado = (filaFixture.golesLocal !== null && filaFixture.golesVisitante !== null) 
        ? EstadoPartido.JUGADO 
        : EstadoPartido.PENDIENTE;

      const labelNorm = normalizar(filaFixture.categoriaLabel);
      const esPlayoff = labelNorm.includes('PROMOCION') || labelNorm.includes('FINAL');

      const data = {
        categoriaId: resuelto.categoriaId,
        zonaId: resuelto.zonaId,
        equipoLocalId: local.id,
        equipoVisitanteId: visitante.id,
        fecha,
        jornada: esPlayoff ? 999 : filaFixture.jornada,
        campo: filaFixture.campo,
        cancha: filaFixture.cancha,
        horario: filaFixture.horario,
        golesLocal: filaFixture.golesLocal,
        golesVisitante: filaFixture.golesVisitante,
        penalesLocal: filaFixture.penalesLocal,
        penalesVisitante: filaFixture.penalesVisitante,
        estado,
      };

      const existente = await prisma.partido.findFirst({
        where: { fecha, equipoLocalId: local.id, equipoVisitanteId: visitante.id },
      });

      if (existente) {
        await prisma.partido.update({ where: { id: existente.id }, data });
        resumen.partidosActualizados++;
      } else {
        await prisma.partido.create({ data });
        resumen.partidosCreados++;
      }
    }
  }
}

// ---------- Goleadores / Figuras / Imbatibles ----------
async function procesarGoleadores(libro: ReturnType<typeof leerLibro>, torneoId: number, resumen: ResumenImportacion) {
  const hoja = buscarHojaPorPatron(libro, /GOLEADOR/i);
  if (!hoja) {
    resumen.advertencias.push('No se encontró hoja de goleadores.');
    return;
  }
  const filas = parsearRankingJugador(hoja.matriz, ['GOLES']);
  for (const f of filas) {
    const { equipo, creado: ce } = await buscarOCrearEquipo(prisma, f.equipoNombre);
    if (ce) resumen.equiposCreados++;
    const { jugador, creado: cj } = await buscarOCrearJugador(prisma, f.jugadorNombre, equipo.id);
    if (cj) resumen.jugadoresCreados++;
    await establecerGoleador(jugador.id, torneoId, f.valor);
    resumen.goleadoresActualizados++;
  }
}

async function procesarFiguras(libro: ReturnType<typeof leerLibro>, torneoId: number, resumen: ResumenImportacion) {
  const hoja = buscarHojaPorPatron(libro, /FIGURA/i);
  if (!hoja) {
    resumen.advertencias.push('No se encontró hoja de figuras.');
    return;
  }
  const filas = parsearRankingJugador(hoja.matriz, ['CANTIDAD DE VECES FIGURA', 'VECES', 'CANTIDAD']);
  for (const f of filas) {
    const { equipo, creado: ce } = await buscarOCrearEquipo(prisma, f.equipoNombre);
    if (ce) resumen.equiposCreados++;
    const { jugador, creado: cj } = await buscarOCrearJugador(prisma, f.jugadorNombre, equipo.id);
    if (cj) resumen.jugadoresCreados++;
    await establecerFigura(jugador.id, torneoId, f.valor);
    resumen.figurasActualizadas++;
  }
}

async function procesarImbatibles(libro: ReturnType<typeof leerLibro>, torneoId: number, resumen: ResumenImportacion) {
  const hoja = buscarHojaPorPatron(libro, /IMBATIBLE/i);
  if (!hoja) {
    resumen.advertencias.push('No se encontró hoja de imbatibles.');
    return;
  }
  const filas = parsearRankingJugador(hoja.matriz, ['GOLES RECIBIDOS', 'GOLES EN CONTRA']);
  for (const f of filas) {
    const { equipo, creado: ce } = await buscarOCrearEquipo(prisma, f.equipoNombre);
    if (ce) resumen.equiposCreados++;
    const { jugador, creado: cj } = await buscarOCrearJugador(prisma, f.jugadorNombre, equipo.id);
    if (cj) resumen.jugadoresCreados++;
    await establecerImbatible(jugador.id, torneoId, f.valor);
    resumen.imbatiblesActualizados++;
  }
}

// ---------- Sancionados + Amonestados ----------

async function procesarSancionados(
  libro: ReturnType<typeof leerLibro>,
  torneoId: number,
  resumen: ResumenImportacion,
) {
  const hoja =
    buscarHojaPorPatron(
      libro,
      /SANCIONAD/i,
    );

  if (!hoja) {
    resumen.advertencias.push(
      'No se encontró hoja de sancionados.',
    );

    return;
  }

  const amonestados =
    parsearAmonestados(
      hoja.matriz,
    );


  for (
    const fila
    of amonestados
  ) {

    const {
      equipo,
      creado: equipoCreado,
    } =
      await buscarOCrearEquipo(
        prisma,
        fila.equipoNombre,
      );


    if (equipoCreado) {
      resumen.equiposCreados++;
    }


    const {
      jugador,
      creado: jugadorCreado,
    } =
      await buscarOCrearJugador(
        prisma,
        fila.jugadorNombre,
        equipo.id,
      );


    if (jugadorCreado) {
      resumen.jugadoresCreados++;
    }


    await prisma.amonestacion.upsert({
      where: {
        jugadorId_torneoId: {
          jugadorId:
            jugador.id,

          torneoId,
        },
      },

      create: {
        jugadorId:
          jugador.id,

        equipoId:
          equipo.id,

        torneoId,

        cantidad:
          fila.amarillas,
      },

      update: {
        equipoId:
          equipo.id,

        cantidad:
          fila.amarillas,
      },
    });


    resumen
      .amonestadosActualizados++;
  }


  const sanciones =
    parsearSancionados(
      hoja.matriz,
    );


  for (
    const fila
    of sanciones
  ) {

    const {
      equipo,
      creado: equipoCreado,
    } =
      await buscarOCrearEquipo(
        prisma,
        fila.equipoNombre,
      );


    if (equipoCreado) {
      resumen.equiposCreados++;
    }


    const {
      jugador,
      creado: jugadorCreado,
    } =
      await buscarOCrearJugador(
        prisma,
        fila.jugadorNombre,
        equipo.id,
      );


    if (jugadorCreado) {
      resumen.jugadoresCreados++;
    }


    const existente =
      await prisma.sancion.findFirst({
        where: {
          jugadorId:
            jugador.id,

          torneoId,

          tipoTarjeta:
            fila.tipoTarjeta,

          fechasSuspension:
            fila.fechasSuspension,
        },
      });


    if (existente) {
      continue;
    }


    await prisma.sancion.create({
      data: {
        jugadorId:
          jugador.id,

        equipoId:
          equipo.id,
          
        torneoId,

        tipoTarjeta:
          fila.tipoTarjeta,

        fechasSuspension:
          fila.fechasSuspension,

        observaciones:
          fila.observaciones ??
          undefined,
      },
    });


    resumen.sancionesCreadas++;
  }
}

export interface ArchivoImportacion {
  buffer: Buffer;
  nombreOriginal: string;
  rutaGuardada: string;
}

export async function importarExcel(torneoId: number, archivo: ArchivoImportacion): Promise<ResumenImportacion> {
  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);

  const importacion = await prisma.importacionExcel.create({
    data: {
      nombreArchivo: archivo.nombreOriginal,
      rutaArchivo: archivo.rutaGuardada,
      torneoId,
      estado: EstadoImportacion.PROCESANDO,
    },
  });

  const resumen = resumenVacio();

  try {
    const libro = leerLibro(archivo.buffer);

    // Cada hoja se procesa independiente: si una falla o no existe, las
    // demás igual se procesan (se ve reflejado en `advertencias`, no tira
    // la importación entera abajo).
    await procesarTablaPosiciones(libro, torneoId, resumen);
    await procesarFixture(libro, torneoId, resumen);
    await procesarGoleadores(libro, torneoId, resumen);
    await procesarFiguras(libro, torneoId, resumen);
    await procesarImbatibles(libro, torneoId, resumen);
    await procesarSancionados(libro, torneoId, resumen);

    await prisma.importacionExcel.update({
      where: { id: importacion.id },
      data: {
        estado: EstadoImportacion.COMPLETADO,
        filasCreadas: resumen.partidosCreados + resumen.equiposCreados + resumen.jugadoresCreados + resumen.sancionesCreadas,
        filasActualizadas:
          resumen.partidosActualizados +
          resumen.goleadoresActualizados +
          resumen.figurasActualizadas +
          resumen.imbatiblesActualizados +
          resumen.amonestadosActualizados,
      },
    });
  } catch (error) {
    await prisma.importacionExcel.update({
      where: { id: importacion.id },
      data: { estado: EstadoImportacion.ERROR, detalleError: error instanceof Error ? error.message : String(error) },
    });
    throw error;
  }

  return resumen;
}
