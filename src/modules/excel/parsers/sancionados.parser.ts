import {
  Matriz,
  encontrarFilaHeader,
  esFilaIgnorable,
  mapearColumnas,
  normalizar,
} from '../xlsxHelpers';


export interface FilaSancionado {
  jugadorNombre: string;
  equipoNombre: string;

  tipoTarjeta:
    | 'ROJA'
    | 'AMARILLA';

  fechasSuspension: number;

  observaciones:
    string | null;
}


export interface FilaAmonestado {
  jugadorNombre: string;
  equipoNombre: string;
  amarillas: number;
}


const COLUMNAS_EXPULSADOS:
  Record<string, string[]> = {

  JUGADOR: [
    'JUGADOR',
    'NOMBRE',
  ],

  EQUIPO: [
    'EQUIPO',
  ],

  FECHAS: [
    'FECHAS SANCION',
    'FECHAS',
    'FECHAS SUSPENSION',
    'FECHAS DE SUSPENSION',
  ],

  OBSERVACIONES: [
    'OBSERVACIONES',
    'OBS',
  ],
};


const COLUMNAS_AMONESTADOS:
  Record<string, string[]> = {

  JUGADOR: [
    'JUGADOR',
    'NOMBRE',
  ],

  EQUIPO: [
    'EQUIPO',
  ],

  AMARILLAS: [
    'AMARILLAS',
    'CANTIDAD',
    'TARJETAS',
  ],
};


function fechasPorAmonestaciones(
  cantidad: number,
): number {
  return Math.floor(
    cantidad / 4,
  );
}


// ============================================================
// AMONESTADOS
// ============================================================

/**
 * Lee TODOS los jugadores que figuran debajo de:
 *
 * AMONESTADOS Torneo APERTURA
 *
 * incluyendo:
 *
 * 1 amarilla
 * 2 amarillas
 * 3 amarillas
 * 4 amarillas
 * ...
 *
 * Esto es distinto de parsearSancionados(),
 * porque tener amarillas no implica necesariamente
 * estar suspendido.
 */
export function parsearAmonestados(
  matriz: Matriz,
): FilaAmonestado[] {

  const idxAmonestados =
    matriz.findIndex(
      (fila) =>
        fila.some(
          (celda) =>
            normalizar(celda)
              .includes(
                'AMONESTADOS',
              ),
        ),
    );

  if (idxAmonestados === -1) {
    return [];
  }

  const bloque =
    matriz.slice(
      idxAmonestados,
    );

  const idxHeader =
    encontrarFilaHeader(
      bloque,
      [
        'JUGADOR',
        'EQUIPO',
      ],
      2,
    );

  if (idxHeader === -1) {
    return [];
  }

  const cols =
    mapearColumnas(
      bloque[idxHeader],
      COLUMNAS_AMONESTADOS,
    );

  if (
    cols.JUGADOR === undefined ||
    cols.EQUIPO === undefined ||
    cols.AMARILLAS === undefined
  ) {
    return [];
  }

  const resultado:
    FilaAmonestado[] = [];

  for (
    let i = idxHeader + 1;
    i < bloque.length;
    i++
  ) {
    const fila =
      bloque[i];

    if (
      esFilaIgnorable(fila)
    ) {
      continue;
    }

    const jugador =
      fila[cols.JUGADOR];

    const equipo =
      fila[cols.EQUIPO];

    if (
      !jugador ||
      !equipo
    ) {
      continue;
    }

    const cantidadRaw =
      fila[cols.AMARILLAS];

    const cantidad =
      typeof cantidadRaw ===
      'number'
        ? cantidadRaw
        : Number(
            cantidadRaw,
          ) || 0;

    /*
     * Una fila con 0 amarillas no aporta
     * nada a la tabla.
     */
    if (cantidad <= 0) {
      continue;
    }

    resultado.push({
      jugadorNombre:
        String(jugador)
          .trim(),

      equipoNombre:
        String(equipo)
          .trim(),

      amarillas:
        cantidad,
    });
  }

  return resultado;
}


// ============================================================
// SANCIONADOS
// ============================================================

export function parsearSancionados(
  matriz: Matriz,
): FilaSancionado[] {

  const resultado:
    FilaSancionado[] = [];


  const idxExpulsados =
    matriz.findIndex(
      (fila) =>
        fila.some(
          (celda) =>
            normalizar(celda) ===
            'EXPULSADOS',
        ),
    );


  const idxAmonestados =
    matriz.findIndex(
      (fila) =>
        fila.some(
          (celda) =>
            normalizar(celda)
              .includes(
                'AMONESTADOS',
              ),
        ),
    );


  // ==========================================================
  // EXPULSADOS
  // ==========================================================

  if (idxExpulsados !== -1) {
    const finBloque =
      idxAmonestados >
      idxExpulsados
        ? idxAmonestados
        : matriz.length;

    resultado.push(
      ...parsearBloqueExpulsados(
        matriz.slice(
          idxExpulsados,
          finBloque,
        ),
      ),
    );
  }


  // ==========================================================
  // SUSPENSIONES POR ACUMULACIÓN DE AMARILLAS
  // ==========================================================

  const amonestados =
    parsearAmonestados(
      matriz,
    );

  for (
    const amonestado
    of amonestados
  ) {
    const fechas =
      fechasPorAmonestaciones(
        amonestado.amarillas,
      );

    /*
     * 1, 2 o 3 amarillas:
     *
     * aparecen en la tabla de amonestados,
     * pero NO constituyen todavía
     * una suspensión.
     */
    if (fechas <= 0) {
      continue;
    }

    resultado.push({
      jugadorNombre:
        amonestado.jugadorNombre,

      equipoNombre:
        amonestado.equipoNombre,

      tipoTarjeta:
        'AMARILLA',

      fechasSuspension:
        fechas,

      observaciones:
        `${amonestado.amarillas} amarillas acumuladas`,
    });
  }


  return resultado;
}


// ============================================================
// EXPULSADOS
// ============================================================

function parsearBloqueExpulsados(
  bloque: Matriz,
): FilaSancionado[] {

  const idxHeader =
    encontrarFilaHeader(
      bloque,
      [
        'JUGADOR',
        'EQUIPO',
      ],
      2,
    );

  if (idxHeader === -1) {
    return [];
  }


  const cols =
    mapearColumnas(
      bloque[idxHeader],
      COLUMNAS_EXPULSADOS,
    );


  if (
    cols.JUGADOR === undefined ||
    cols.EQUIPO === undefined
  ) {
    return [];
  }


  const filas:
    FilaSancionado[] = [];


  for (
    let i = idxHeader + 1;
    i < bloque.length;
    i++
  ) {

    const fila =
      bloque[i];


    if (
      esFilaIgnorable(fila)
    ) {
      continue;
    }


    if (
      fila.some(
        (celda) =>
          normalizar(celda)
            .includes(
              'AMONESTADOS',
            ),
      )
    ) {
      break;
    }


    const jugador =
      fila[cols.JUGADOR];

    const equipo =
      fila[cols.EQUIPO];


    if (
      !jugador ||
      !equipo
    ) {
      continue;
    }


    const fechasRaw =
      cols.FECHAS !== undefined
        ? fila[cols.FECHAS]
        : null;


    const fechas =
      typeof fechasRaw ===
      'number'
        ? fechasRaw
        : Number(
            fechasRaw,
          ) || 1;


    filas.push({
      jugadorNombre:
        String(jugador)
          .trim(),

      equipoNombre:
        String(equipo)
          .trim(),

      tipoTarjeta:
        'ROJA',

      fechasSuspension:
        fechas,

      observaciones:
        cols.OBSERVACIONES !==
          undefined &&
        fila[
          cols.OBSERVACIONES
        ]
          ? String(
              fila[
                cols.OBSERVACIONES
              ],
            ).trim()
          : null,
    });
  }


  return filas;
}