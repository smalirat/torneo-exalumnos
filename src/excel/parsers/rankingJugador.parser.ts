import { Matriz, encontrarFilaHeader, esFilaIgnorable, mapearColumnas } from '../xlsxHelpers';

export interface FilaRankingJugador {
  jugadorNombre: string;
  equipoNombre: string;
  valor: number;
}

/**
 * Parser genérico para hojas con forma "JUGADOR | EQUIPO | <columna numérica>".
 * `sinonimosValor` son los nombres posibles de esa tercera columna
 * (GOLES / CANTIDAD DE VECES FIGURA / GOLES RECIBIDOS, etc).
 */
export function parsearRankingJugador(matriz: Matriz, sinonimosValor: string[]): FilaRankingJugador[] {
  const COLUMNAS: Record<string, string[]> = {
    JUGADOR: ['JUGADOR', 'NOMBRE'],
    EQUIPO: ['EQUIPO'],
    VALOR: sinonimosValor,
  };

  const idxHeader = encontrarFilaHeader(matriz, ['JUGADOR', 'EQUIPO'], 2);
  if (idxHeader === -1) return [];

  const cols = mapearColumnas(matriz[idxHeader], COLUMNAS);
  if (cols.JUGADOR === undefined || cols.EQUIPO === undefined || cols.VALOR === undefined) return [];

  const filas: FilaRankingJugador[] = [];
  for (let i = idxHeader + 1; i < matriz.length; i++) {
    const fila = matriz[i];
    if (esFilaIgnorable(fila)) continue;

    const jugador = fila[cols.JUGADOR];
    const equipo = fila[cols.EQUIPO];
    if (!jugador || !equipo) continue;

    const valorRaw = fila[cols.VALOR];
    const valor = typeof valorRaw === 'number' ? valorRaw : Number(valorRaw) || 0;

    filas.push({
      jugadorNombre: String(jugador).trim(),
      equipoNombre: String(equipo).trim(),
      valor,
    });
  }

  return filas;
}
