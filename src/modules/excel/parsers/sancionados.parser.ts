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
  tipoTarjeta: 'ROJA' | 'AMARILLA';
  fechasSuspension: number | null;
  fechasCumplidas: number; 
  estado: 'PENDIENTE' | 'CUMPLIDA' | 'EN_TRIBUNAL';
  observaciones: string | null;
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

  CUMPLIDA: [
    'CUMPLIDA',
    'FECHAS CUMPLIDAS'
  ],

  PENDIENTE: [
    'PENDIENTE',
    'ESTADO'
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


// ============================================================
// AMONESTADOS
// ============================================================

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

    const fechasRaw = cols.FECHAS !== undefined ? fila[cols.FECHAS] : null;
    const fechas = (fechasRaw === null || fechasRaw === '') ? null : Number(fechasRaw);

    const cumplidasRaw = cols.CUMPLIDA !== undefined ? fila[cols.CUMPLIDA] : null;
    const fechasCumplidas = typeof cumplidasRaw === 'number' ? cumplidasRaw : Number(cumplidasRaw) || 0;

    let estadoReal: 'PENDIENTE' | 'CUMPLIDA' | 'EN_TRIBUNAL' = 'PENDIENTE';
    if (cols.PENDIENTE !== undefined && fila[cols.PENDIENTE] != null) {
      const rawPendiente = String(fila[cols.PENDIENTE]).trim().toUpperCase();
      if (rawPendiente.includes('TRIBUNAL')) {
        estadoReal = 'EN_TRIBUNAL';
      } else if (rawPendiente.includes('CUMPLI') || rawPendiente === '0') {
        estadoReal = 'CUMPLIDA';
      }
    }

    let observacionesReal: string | null = null;
    if (cols.OBSERVACIONES !== undefined && fila[cols.OBSERVACIONES] != null) {
      const textoObs = String(fila[cols.OBSERVACIONES]).trim();
      if (textoObs !== '') {
        observacionesReal = textoObs;
      }
    }

    let tipoTarjeta: 'ROJA' | 'AMARILLA' = 'ROJA';
    if (observacionesReal && observacionesReal.toUpperCase().includes('AMARILLA')) {
      tipoTarjeta = 'AMARILLA';
    }

    filas.push({
      jugadorNombre: String(jugador).trim(),
      equipoNombre: String(equipo).trim(),
      tipoTarjeta: tipoTarjeta, 
      fechasSuspension: fechas, 
      fechasCumplidas: fechasCumplidas,
      estado: estadoReal,
      observaciones: observacionesReal,
    });
  }

  return filas;
}