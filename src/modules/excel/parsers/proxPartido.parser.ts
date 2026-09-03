import { Matriz, encontrarTodasFilasHeader, esFilaIgnorable, normalizar } from '../xlsxHelpers';

export interface FilaFixture {
  jornada: number;
  categoriaLabel: string;
  equipoLocal: string;
  equipoVisitante: string;
  golesLocal: number | null;
  golesVisitante: number | null;
  penalesLocal: number | null;
  penalesVisitante: number | null;
  campo: string | null;
  cancha: string | null;
  horario: string | null;
  fechaTexto: string | null;
  torneoLabel: string;
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

const REGEX_PLACEHOLDER = /^\d+\s*(ER|ERO|DO|TO|MO)?\s*(ZONA|GRUPO|CATEGORIA)/i;
const REGEX_LIBRE = /^LIBRE\b/i;
const REGEX_JORNADA = /FECHA\s*N[ ]?\.?\s*(\d+)/i;
const REGEX_TEXTO_FECHA = /\d{1,2}\s+DE\s+[A-Z ]+/i;

export function parsearFixture(matriz: Matriz): ResultadoParseoFixture {
  const filas: FilaFixture[] = [];
  const omitidas: FilaFixtureOmitida[] = [];

  const indicesHeader = encontrarTodasFilasHeader(matriz, ['CAMPO', 'CANCHA', 'HORARIO'], 2);
  if (indicesHeader.length === 0) {
    return {
      filas,
      omitidas: [{ motivo: 'FILA_INCOMPLETA', filaOriginal: -1, detalle: 'No se encontró ninguna fila de header (se buscaron columnas CAMPO/CANCHA/HORARIO)' }],
    };
  }

  let jornadaAnterior = 0;
  let torneoActual = '';

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
      omitidas.push({ motivo: 'FILA_INCOMPLETA', filaOriginal: idxHeader, detalle: 'Fila de header sin columna "vs"' });
      continue;
    }

    const colLocal = colVs - 2;
    const colGolesLocal = colVs - 1;
    const colVisitante = colVs + 1;
    const colGolesVisitante = colVs + 2;

    let jornada: number | null = null;
    let fechaTexto: string | null = null;
    
    // Buscamos hacia atrás con un rango extendido (10 filas) para capturar los títulos de TORNEO
    for (let l = idxHeader - 1; l >= Math.max(0, idxHeader - 10); l--) {
      const filaLabel = matriz[l];
      if (esFilaIgnorable(filaLabel)) continue;
      const textoCompleto = filaLabel.filter((c) => c !== null && c !== '').join(' ').toUpperCase();

      if (textoCompleto.includes('TORNEO APERTURA')) torneoActual = 'APERTURA';
      if (textoCompleto.includes('TORNEO CLAUSURA')) torneoActual = 'CLAUSURA';

      const matchJornada = textoCompleto.match(REGEX_JORNADA);
      if (matchJornada && !jornada) jornada = Number(matchJornada[1]);
      
      const matchFecha = textoCompleto.match(REGEX_TEXTO_FECHA);
      if (matchFecha && !fechaTexto) fechaTexto = matchFecha[0];
    }

    if (jornada === null) jornada = jornadaAnterior + 1;
    jornadaAnterior = jornada;

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
        continue;
      }

      const localTexto = String(local).trim();
      const visitanteTexto = String(visitante).trim();

      if (REGEX_LIBRE.test(localTexto) || REGEX_LIBRE.test(visitanteTexto)) {
        omitidas.push({ motivo: 'LIBRE', filaOriginal: i, detalle: `${localTexto} vs ${visitanteTexto}` });
        continue;
      }

      if (REGEX_PLACEHOLDER.test(localTexto) || REGEX_PLACEHOLDER.test(visitanteTexto)) {
        omitidas.push({ motivo: 'PLACEHOLDER_POSICION', filaOriginal: i, detalle: `${localTexto} vs ${visitanteTexto}` });
        continue;
      }

      const golesLocalRaw = String(fila[colGolesLocal] ?? '').trim();
      const golesVisitanteRaw = String(fila[colGolesVisitante] ?? '').trim();

      let gl = null, pl = null;
      if (golesLocalRaw !== '') {
        const matchLocal = golesLocalRaw.match(/^(\d+)(?:\s*\(\s*(\d+)\s*\))?/);
        if (matchLocal) {
          gl = Number(matchLocal[1]);
          if (matchLocal[2]) pl = Number(matchLocal[2]);
        }
      }

      let gv = null, pv = null;
      if (golesVisitanteRaw !== '') {
        const matchVis = golesVisitanteRaw.match(/^(\d+)(?:\s*\(\s*(\d+)\s*\))?/);
        if (matchVis) {
          gv = Number(matchVis[1]);
          if (matchVis[2]) pv = Number(matchVis[2]);
        }
      }

      filas.push({
        jornada,
        categoriaLabel: colCategoria !== -1 ? normalizar(fila[colCategoria]) : '',
        equipoLocal: localTexto,
        equipoVisitante: visitanteTexto,
        golesLocal: gl,
        golesVisitante: gv,
        penalesLocal: pl,
        penalesVisitante: pv,
        campo: colCampo !== -1 && fila[colCampo] ? String(fila[colCampo]).trim() : null,
        cancha: colCancha !== -1 && fila[colCancha] ? String(fila[colCancha]).trim() : null,
        horario: colHorario !== -1 && fila[colHorario] ? String(fila[colHorario]).trim() : null,
        fechaTexto,
        torneoLabel: torneoActual,
      });
    }
  }

  return { filas, omitidas };
}