import { Matriz, encontrarTodasFilasHeader, esFilaIgnorable, normalizar } from '../xlsxHelpers';

export interface FilaFixture {
  jornada: number;
  categoriaLabel: string; // texto crudo de la columna "Categoría" de esa fila; se resuelve después con resolverCategoriaZona
  equipoLocal: string;
  equipoVisitante: string;
  campo: string | null;
  cancha: string | null;
  horario: string | null;
  fechaTexto: string | null; // texto crudo ("Domingo 29 de Marzo"); el service lo combina con el año de la Temporada
}

export interface FilaFixtureOmitida {
  motivo: 'LIBRE' | 'PLACEHOLDER_POSICION' | 'FILA_INCOMPLETA';
  filaOriginal: number;
  detalle: string;
}

export interface ResultadoParseoFixture {
  filas: FilaFixture[];
  omitidas: FilaFixtureOmitida[];
}

// Un equipo "placeholder" referencia una posición en la tabla que todavía no
// está definida (fase interzonal/promoción) — ej. "1ero ZONA 2", "10mo
// ZONA 1". No podemos crear ese Partido todavía: falta resolver standings.
const REGEX_PLACEHOLDER = /^\d+\s*(ER|ERO|DO|TO|MO)?\s*(ZONA|GRUPO|CATEGORIA)/i;
const REGEX_LIBRE = /^LIBRE\b/i; // cubre "LIBRE" y "LIBRE: EQUIPO X"
const REGEX_JORNADA = /FECHA\s*N[ºO°]?\.?\s*(\d+)/i;
const REGEX_TEXTO_FECHA = /\d{1,2}\s+DE\s+[A-ZÑ]+/i;

/**
 * El fixture real repite el header ("Campo | Cancha | Horario | Equipo | |
 * vs | Equipo | | Categoría") una vez por cada "Fecha Nº N" (y también antes
 * de bloques especiales como "Promoción Final Interzonal" que no tienen
 * numeración). Por eso el parser ancla en TODAS las filas de header, no solo
 * la primera, y usa la celda "vs" para ubicar las columnas de equipo local/
 * visitante (que comparten el mismo texto de header "Equipo").
 */
export function parsearFixture(matriz: Matriz): ResultadoParseoFixture {
  const filas: FilaFixture[] = [];
  const omitidas: FilaFixtureOmitida[] = [];

  const indicesHeader = encontrarTodasFilasHeader(matriz, ['CAMPO', 'CANCHA', 'HORARIO'], 2);
  if (indicesHeader.length === 0) {
    return {
      filas,
      omitidas: [
        {
          motivo: 'FILA_INCOMPLETA',
          filaOriginal: -1,
          detalle: 'No se encontró ninguna fila de header (se buscaron columnas CAMPO/CANCHA/HORARIO)',
        },
      ],
    };
  }

  let jornadaAnterior = 0;

  for (let b = 0; b < indicesHeader.length; b++) {
    const idxHeader = indicesHeader[b];
    const filaHeader = matriz[idxHeader];
    const celdasHeader = filaHeader.map(normalizar);

    const colVs = celdasHeader.indexOf('VS');
    const colCampo = celdasHeader.indexOf('CAMPO');
    const colCancha = celdasHeader.indexOf('CANCHA');
    const colHorario = celdasHeader.indexOf('HORARIO');
    const colCategoria = celdasHeader.indexOf('CATEGORIA');

    if (colVs === -1) {
      omitidas.push({
        motivo: 'FILA_INCOMPLETA',
        filaOriginal: idxHeader,
        detalle: 'Fila de header sin columna "vs" — no se puede distinguir equipo local de visitante',
      });
      continue;
    }
    const colLocal = colVs - 2; // patrón observado: Equipo | (vacío) | vs
    const colVisitante = colVs + 1;

    // Buscamos hacia atrás la fila-label más cercana (jornada + fecha en texto),
    // saltando filas vacías, sin pasarnos del bloque anterior.
    let jornada: number | null = null;
    let fechaTexto: string | null = null;
    for (let l = idxHeader - 1; l >= Math.max(0, idxHeader - 6); l--) {
      const filaLabel = matriz[l];
      if (esFilaIgnorable(filaLabel)) continue;
      const textoCompleto = filaLabel.filter((c) => c !== null && c !== '').join(' ');
      const matchJornada = textoCompleto.match(REGEX_JORNADA);
      if (matchJornada) jornada = Number(matchJornada[1]);
      const matchFecha = textoCompleto.match(REGEX_TEXTO_FECHA);
      if (matchFecha) fechaTexto = matchFecha[0];
      break; // la primera fila no-vacía hacia atrás es la label; no seguimos buscando
    }
    // Bloques especiales (ej. "Promoción Final Interzonal") no traen número
    // de fecha explícito — seguimos la numeración del bloque anterior + 1.
    if (jornada === null) jornada = jornadaAnterior + 1;
    jornadaAnterior = jornada;

    // Los datos de este bloque van desde idxHeader+1 hasta el próximo header
    // (o fin de la hoja), salteando filas vacías intermedias.
    const finBloque = b + 1 < indicesHeader.length ? indicesHeader[b + 1] : matriz.length;

    for (let i = idxHeader + 1; i < finBloque; i++) {
      const fila = matriz[i];
      if (esFilaIgnorable(fila)) continue;

      const local = fila[colLocal];
      const visitante = fila[colVisitante];

      if (!local || !visitante) {
        const localTexto = local ? String(local).trim() : null;
        if (localTexto && REGEX_LIBRE.test(localTexto)) {
          omitidas.push({ motivo: 'LIBRE', filaOriginal: i, detalle: localTexto });
        }
        continue; // fila sin ambos equipos (bye, o fecha futura sin definir)
      }

      const localTexto = String(local).trim();
      const visitanteTexto = String(visitante).trim();

      if (REGEX_LIBRE.test(localTexto) || REGEX_LIBRE.test(visitanteTexto)) {
        omitidas.push({ motivo: 'LIBRE', filaOriginal: i, detalle: `${localTexto} vs ${visitanteTexto}` });
        continue;
      }

      if (REGEX_PLACEHOLDER.test(localTexto) || REGEX_PLACEHOLDER.test(visitanteTexto)) {
        omitidas.push({
          motivo: 'PLACEHOLDER_POSICION',
          filaOriginal: i,
          detalle: `${localTexto} vs ${visitanteTexto} (posición de tabla aún no definida — cargar a mano cuando se conozcan los equipos)`,
        });
        continue;
      }

      filas.push({
        jornada,
        categoriaLabel: colCategoria !== -1 ? normalizar(fila[colCategoria]) : '',
        equipoLocal: localTexto,
        equipoVisitante: visitanteTexto,
        campo: colCampo !== -1 && fila[colCampo] ? String(fila[colCampo]).trim() : null,
        cancha: colCancha !== -1 && fila[colCancha] ? String(fila[colCancha]).trim() : null,
        horario: colHorario !== -1 && fila[colHorario] ? String(fila[colHorario]).trim() : null,
        fechaTexto,
      });
    }
  }

  return { filas, omitidas };
}
