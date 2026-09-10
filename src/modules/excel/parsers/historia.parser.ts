import { Matriz, normalizar } from '../xlsxHelpers';

export interface FilaHistoria {
  anio: number;
  categoria: 'A' | 'B' | 'C';
  equipoNombre: string;
}

export interface FilaHistoriaOmitida {
  anio: number | null;
  categoria: 'A' | 'B' | 'C' | null;
  equipoRaw: string | null;
  motivo: 'PLACEHOLDER' | 'SIN_CAMPEON' | 'YA_CARGADO';
}

export interface ResultadoParseoHistoria {
  filas: FilaHistoria[];
  omitidos: FilaHistoriaOmitida[];
}

// La hoja HISTORIA trae 3 bloques lado a lado (A, B, C), cada uno con
// `Año | Equipo Campeon` más columnas de ranking precalculado que
// ignoramos (el ranking lo recalculamos desde Campeon). Los bloques
// tienen distinta longitud (A llega a 1993, C empieza en 2025), así que
// cada bloque se recorre por separado hasta agotar sus filas.
const REGEX_CATEGORIA = /CATEGORIA\s*["']?([ABC])["']?/;

// Valores que no son un campeón real: edición en curso, año sin torneo
// o campeón desconocido. No se importan (y se reportan como omitidos).
const PLACEHOLDERS = new Set(['PROXIMAMENTE!!', 'S.N.', 'S/N', 'SN', 'NN']);

function esAnioValido(valor: unknown): valor is number {
  const anio = typeof valor === 'number' ? valor : Number(valor);
  return Number.isInteger(anio) && anio >= 1900 && anio <= 2100;
}

function aAnio(valor: unknown): number {
  return typeof valor === 'number' ? valor : Number(valor);
}

export function parsearHistoria(matriz: Matriz): ResultadoParseoHistoria {
  const filas: FilaHistoria[] = [];
  const omitidos: FilaHistoriaOmitida[] = [];

  // 1. Ubicamos cada bloque por su label "Categoria X" (puede estar en
  // cualquier columna: en el Excel real A/B/C están lado a lado).
  const bloques: { col: number; categoria: 'A' | 'B' | 'C' }[] = [];
  for (let f = 0; f < matriz.length; f++) {
    for (let c = 0; c < matriz[f].length; c++) {
      const match = normalizar(matriz[f][c]).match(REGEX_CATEGORIA);
      if (match) bloques.push({ col: c, categoria: match[1] as 'A' | 'B' | 'C' });
    }
  }
  if (bloques.length === 0) return { filas, omitidos };

  // 2. Fila de headers (`Año | Equipo Campeon ...`): a partir de acá
  // empiezan los datos en cada bloque.
  const idxHeader = matriz.findIndex((fila) => fila.some((c) => normalizar(c) === 'ANO'));
  const inicio = idxHeader === -1 ? 0 : idxHeader + 1;

  // 3. Recorremos cada bloque por separado (longitudes distintas).
  for (const { col, categoria } of bloques) {
    for (let f = inicio; f < matriz.length; f++) {
      const fila = matriz[f];
      const anioRaw = col < fila.length ? fila[col] : null;
      const campeonRaw = col + 1 < fila.length ? fila[col + 1] : null;

      if (!esAnioValido(anioRaw)) continue;
      const anio = aAnio(anioRaw);

      const equipoNombre =
        campeonRaw === null || campeonRaw === '' ? null : String(campeonRaw).trim();

      if (!equipoNombre) {
        // Año sin campeón (ej. 2020, sin torneo): se saltea la fila pero
        // se sigue recorriendo el bloque.
        omitidos.push({ anio, categoria, equipoRaw: null, motivo: 'SIN_CAMPEON' });
        continue;
      }

      if (PLACEHOLDERS.has(normalizar(equipoNombre))) {
        omitidos.push({ anio, categoria, equipoRaw: equipoNombre, motivo: 'PLACEHOLDER' });
        continue;
      }

      filas.push({ anio, categoria, equipoNombre });
    }
  }

  return { filas, omitidos };
}
