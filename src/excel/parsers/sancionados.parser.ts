import { Matriz, encontrarFilaHeader, esFilaIgnorable, mapearColumnas, normalizar } from '../xlsxHelpers';

export interface FilaSancionado {
  jugadorNombre: string;
  equipoNombre: string;
  tipoTarjeta: 'ROJA' | 'AMARILLA';
  fechasSuspension: number;
  observaciones: string | null;
}

const COLUMNAS_EXPULSADOS: Record<string, string[]> = {
  JUGADOR: ['JUGADOR', 'NOMBRE'],
  EQUIPO: ['EQUIPO'],
  // "Fechas sanción" en tu Excel real (normalizar() ya saca la tilde)
  FECHAS: ['FECHAS SANCION', 'FECHAS', 'FECHAS SUSPENSION', 'FECHAS DE SUSPENSION'],
  OBSERVACIONES: ['OBSERVACIONES', 'OBS'],
};

const COLUMNAS_AMONESTADOS: Record<string, string[]> = {
  JUGADOR: ['JUGADOR', 'NOMBRE'],
  EQUIPO: ['EQUIPO'],
  AMARILLAS: ['AMARILLAS', 'CANTIDAD', 'TARJETAS'],
};

// Regla real confirmada en tu Excel (fila explicativa junto al header de
// EXPULSADOS): 4 amarillas acumuladas = 1 fecha de suspensión, 8 = 2 fechas.
function fechasPorAmonestaciones(cantidad: number): number {
  return Math.floor(cantidad / 4);
}

/**
 * Busca dentro de la matriz completa las dos secciones "EXPULSADOS" y
 * "AMONESTADOS" — el label real de la segunda sección tiene texto extra
 * ("AMONESTADOS Torneo APERTURA"), así que matcheamos por .includes(), no
 * por igualdad exacta.
 */
export function parsearSancionados(matriz: Matriz): FilaSancionado[] {
  const resultado: FilaSancionado[] = [];

  const idxExpulsados = matriz.findIndex((fila) => fila.some((c) => normalizar(c) === 'EXPULSADOS'));
  const idxAmonestados = matriz.findIndex((fila) => fila.some((c) => normalizar(c).includes('AMONESTADOS')));

  if (idxExpulsados !== -1) {
    const finBloque = idxAmonestados > idxExpulsados ? idxAmonestados : matriz.length;
    resultado.push(...parsearBloqueExpulsados(matriz.slice(idxExpulsados, finBloque)));
  }

  if (idxAmonestados !== -1) {
    resultado.push(...parsearBloqueAmonestados(matriz.slice(idxAmonestados)));
  }

  return resultado;
}

function parsearBloqueExpulsados(bloque: Matriz): FilaSancionado[] {
  const idxHeader = encontrarFilaHeader(bloque, ['JUGADOR', 'EQUIPO'], 2);
  if (idxHeader === -1) return [];
  const cols = mapearColumnas(bloque[idxHeader], COLUMNAS_EXPULSADOS);
  if (cols.JUGADOR === undefined || cols.EQUIPO === undefined) return [];

  const filas: FilaSancionado[] = [];
  for (let i = idxHeader + 1; i < bloque.length; i++) {
    const fila = bloque[i];
    if (esFilaIgnorable(fila)) continue;
    if (fila.some((c) => normalizar(c).includes('AMONESTADOS'))) break;

    const jugador = fila[cols.JUGADOR];
    const equipo = fila[cols.EQUIPO];
    if (!jugador || !equipo) continue;

    const fechasRaw = cols.FECHAS !== undefined ? fila[cols.FECHAS] : null;
    const fechas = typeof fechasRaw === 'number' ? fechasRaw : Number(fechasRaw) || 1; // roja directa: 1 fecha por default

    filas.push({
      jugadorNombre: String(jugador).trim(),
      equipoNombre: String(equipo).trim(),
      tipoTarjeta: 'ROJA',
      fechasSuspension: fechas,
      observaciones:
        cols.OBSERVACIONES !== undefined && fila[cols.OBSERVACIONES] ? String(fila[cols.OBSERVACIONES]).trim() : null,
    });
  }
  return filas;
}

function parsearBloqueAmonestados(bloque: Matriz): FilaSancionado[] {
  const idxHeader = encontrarFilaHeader(bloque, ['JUGADOR', 'EQUIPO'], 2);
  if (idxHeader === -1) return [];
  const cols = mapearColumnas(bloque[idxHeader], COLUMNAS_AMONESTADOS);
  if (cols.JUGADOR === undefined || cols.EQUIPO === undefined || cols.AMARILLAS === undefined) return [];

  const filas: FilaSancionado[] = [];
  for (let i = idxHeader + 1; i < bloque.length; i++) {
    const fila = bloque[i];
    if (esFilaIgnorable(fila)) continue;

    const jugador = fila[cols.JUGADOR];
    const equipo = fila[cols.EQUIPO];
    if (!jugador || !equipo) continue;

    const cantidadRaw = fila[cols.AMARILLAS];
    const cantidad = typeof cantidadRaw === 'number' ? cantidadRaw : Number(cantidadRaw) || 0;
    const fechas = fechasPorAmonestaciones(cantidad);

    // Solo generamos una Sancion si ya llegó al umbral (4, 8, 12...). Si
    // tiene 1-3 amarillas todavía no hay suspensión que registrar.
    if (fechas > 0) {
      filas.push({
        jugadorNombre: String(jugador).trim(),
        equipoNombre: String(equipo).trim(),
        tipoTarjeta: 'AMARILLA',
        fechasSuspension: fechas,
        observaciones: `${cantidad} amarillas acumuladas`,
      });
    }
  }
  return filas;
}
