import { Matriz, normalizar } from '../xlsxHelpers';

export interface FilaTablaPosiciones {
  equipoNombre: string;
  pr: number;
}

export interface BloqueTablaPosiciones {
  label: string;
  filas: FilaTablaPosiciones[];
}

// Un label de bloque es una celda (no necesariamente en la columna A: en tu
// Excel real "Zona 1" y "Zona 2" están LADO A LADO en la misma fila) que
// matchea "ZONA N" o "CATEGORIA X".
const REGEX_LABEL = /^(ZONA\s*\d+|CATEGORIA\s*["']?[ABC]["']?)$/;

interface LabelEncontrado {
  fila: number;
  col: number;
  label: string;
}

function encontrarLabels(matriz: Matriz): LabelEncontrado[] {
  const labels: LabelEncontrado[] = [];
  for (let f = 0; f < matriz.length; f++) {
    for (let c = 0; c < matriz[f].length; c++) {
      const valor = normalizar(matriz[f][c]);
      if (REGEX_LABEL.test(valor)) labels.push({ fila: f, col: c, label: valor });
    }
  }
  return labels;
}

/**
 * Parsea la hoja de tabla de posiciones. Solo extrae EQUIPO + PR (el resto
 * de las columnas — PJ/PG/PE/PP/GF/GE/DIF — las recalculamos nosotros desde
 * Partido, no las tomamos del Excel, para no tener dos fuentes de verdad).
 *
 * Soporta DOS layouts confirmados contra Excels reales:
 *  - Zonas lado a lado en la misma fila (Tabla Apertura 2026: "Zona 1" y
 *    "Zona 2" comparten fila, cada una con su propio bloque de columnas)
 *  - Categorías apiladas verticalmente (Tabla Clausura 2025: "Categoría A",
 *    después "Categoría B", cada una debajo de la anterior)
 * porque busca labels en CUALQUIER celda, no solo la columna A.
 */
export function parsearTablaPosiciones(matriz: Matriz): BloqueTablaPosiciones[] {
  const labels = encontrarLabels(matriz);
  const bloques: BloqueTablaPosiciones[] = [];

  for (const { fila, col, label } of labels) {
    // El bloque termina en la columna del PRÓXIMO label de la MISMA fila
    // (caso "lado a lado"), o al final de la fila si no hay otro.
    const siguienteEnFila = labels.find((l) => l.fila === fila && l.col > col);
    // Si no hay otro label después en la misma fila, el bloque llega hasta
    // el final de la fila de DATOS (no de la fila del label — pueden tener
    // longitudes distintas). slice() con un límite grande simplemente se
    // achica solo al tamaño real de cada fila.
    const colFin = siguienteEnFila ? siguienteEnFila.col : Number.MAX_SAFE_INTEGER;

    // Buscamos el header (EQUIPO + PR) en alguna de las próximas filas,
    // pero SOLO mirando el rango de columnas [col, colFin) de este bloque.
    let idxHeader = -1;
    let colEquipoRel = -1;
    let colPrRel = -1;
    for (let h = fila + 1; h <= Math.min(fila + 4, matriz.length - 1); h++) {
      const sub = matriz[h].slice(col, colFin).map(normalizar);
      const idxEquipo = sub.indexOf('EQUIPO');
      if (idxEquipo !== -1) {
        idxHeader = h;
        colEquipoRel = idxEquipo;
        colPrRel = sub.indexOf('PR');
        break;
      }
    }
    if (idxHeader === -1) continue; // no encontramos header cerca de este label, lo salteamos

    const filasBloque: FilaTablaPosiciones[] = [];
    for (let d = idxHeader + 1; d < matriz.length; d++) {
      const sub = matriz[d].slice(col, colFin);
      if (sub.every((c) => c === null || c === '')) break; // fin del bloque
      if (sub.map(normalizar).includes('CONTROL')) break; // fila de checksum, no es un equipo

      const equipoNombre = sub[colEquipoRel];
      if (!equipoNombre || String(equipoNombre).trim() === '') continue;

      const prRaw = colPrRel !== -1 ? sub[colPrRel] : null;
      const pr = typeof prRaw === 'number' ? prRaw : Number(prRaw) || 0;
      filasBloque.push({ equipoNombre: String(equipoNombre).trim(), pr });
    }

    bloques.push({ label, filas: filasBloque });
  }

  return bloques;
}
