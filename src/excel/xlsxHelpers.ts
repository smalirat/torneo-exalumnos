import * as XLSX from 'xlsx';

export type Fila = (string | number | null)[];
export type Matriz = Fila[];

/** trim + mayúsculas + sin tildes + colapsa espacios. Se usa para matchear
 * nombres de equipos, jugadores, headers y labels de zona/categoría sin que
 * un espacio extra, una tilde o una diferencia de mayúsculas rompa el
 * matching (confirmado con el Excel real: "Categoría"/"CATEGORIA",
 * "Fechas sanción"/"FECHAS SANCION", etc. conviven en el mismo archivo). */
export function normalizar(valor: unknown): string {
  if (valor === null || valor === undefined) return '';
  return String(valor)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // quita diacríticos (tildes, diéresis)
    .trim()
    .toUpperCase()
    .replace(/\s+/g, ' ');
}

export function leerLibro(buffer: Buffer): XLSX.WorkBook {
  return XLSX.read(buffer, { type: 'buffer' });
}

export function hojaComoMatriz(libro: XLSX.WorkBook, nombreHoja: string): Matriz | null {
  const hoja = libro.Sheets[nombreHoja];
  if (!hoja) return null;
  return XLSX.utils.sheet_to_json<Fila>(hoja, { header: 1, raw: true, defval: null });
}

/** Busca por nombre de hoja probando una lista de sinónimos (case-insensitive,
 * ignora espacios extra) — así no rompe si tu Excel tiene "Prox Partido" en
 * vez de "Próximo Partido" o viceversa. */
export function buscarHoja(libro: XLSX.WorkBook, sinonimos: string[]): { nombre: string; matriz: Matriz } | null {
  const nombresDisponibles = libro.SheetNames;
  for (const sinonimo of sinonimos) {
    const encontrado = nombresDisponibles.find((n) => normalizar(n) === normalizar(sinonimo));
    if (encontrado) {
      const matriz = hojaComoMatriz(libro, encontrado);
      if (matriz) return { nombre: encontrado, matriz };
    }
  }
  // fallback: matcheo parcial (contains) por si el nombre real tiene texto de más
  for (const sinonimo of sinonimos) {
    const encontrado = nombresDisponibles.find((n) => normalizar(n).includes(normalizar(sinonimo)));
    if (encontrado) {
      const matriz = hojaComoMatriz(libro, encontrado);
      if (matriz) return { nombre: encontrado, matriz };
    }
  }
  return null;
}

/** Busca la primera hoja cuyo nombre matchea un patrón regex — útil cuando
 * el nombre de la hoja incluye datos variables (ej. "Tabla Apertura",
 * "Tabla Clausura 2025"): buscamos por prefijo/patrón en vez de nombre exacto. */
export function buscarHojaPorPatron(libro: XLSX.WorkBook, patron: RegExp): { nombre: string; matriz: Matriz } | null {
  const encontrado = libro.SheetNames.find((n) => patron.test(n));
  if (!encontrado) return null;
  const matriz = hojaComoMatriz(libro, encontrado);
  return matriz ? { nombre: encontrado, matriz } : null;
}

/**
 * Encuentra el índice de la fila que funciona como header: la primera fila
 * que contenga al menos `minMatches` de las columnas esperadas (por nombre,
 * normalizado). Devuelve -1 si no la encuentra.
 */
export function encontrarFilaHeader(matriz: Matriz, columnasEsperadas: string[], minMatches = 2): number {
  for (let i = 0; i < matriz.length; i++) {
    const celdas = matriz[i].map(normalizar);
    const matches = columnasEsperadas.filter((col) => celdas.includes(normalizar(col))).length;
    if (matches >= minMatches) return i;
  }
  return -1;
}

/** Como encontrarFilaHeader, pero devuelve TODAS las filas que matchean, no
 * solo la primera. Necesario para hojas donde el header se repite por cada
 * bloque (ej. "Prox Partido": un header de Campo/Cancha/Horario/Equipo/vs
 * por cada "Fecha Nº"). */
export function encontrarTodasFilasHeader(matriz: Matriz, columnasEsperadas: string[], minMatches = 2): number[] {
  const indices: number[] = [];
  for (let i = 0; i < matriz.length; i++) {
    const celdas = matriz[i].map(normalizar);
    const matches = columnasEsperadas.filter((col) => celdas.includes(normalizar(col))).length;
    if (matches >= minMatches) indices.push(i);
  }
  return indices;
}

/**
 * Dado un índice de fila header, arma un mapa columnCanonica -> índice de
 * columna, probando cada sinónimo. mapaSinonimos = { EQUIPO: ['EQUIPO','EQUIPOS'], ... }
 */
export function mapearColumnas(
  filaHeader: Fila,
  mapaSinonimos: Record<string, string[]>,
): Record<string, number> {
  const celdas = filaHeader.map(normalizar);
  const resultado: Record<string, number> = {};
  for (const [canonica, sinonimos] of Object.entries(mapaSinonimos)) {
    for (const sinonimo of sinonimos) {
      const idx = celdas.indexOf(normalizar(sinonimo));
      if (idx !== -1) {
        resultado[canonica] = idx;
        break;
      }
    }
  }
  return resultado;
}

/** True si la fila está vacía o es una fila de "Control" (checksum que
 * vimos en tu Excel real — puede aparecer en cualquier columna, no
 * necesariamente la primera, así que buscamos en toda la fila). */
export function esFilaIgnorable(fila: Fila): boolean {
  if (fila.every((c) => c === null || c === '')) return true;
  if (fila.some((c) => normalizar(c) === 'CONTROL')) return true;
  return false;
}
