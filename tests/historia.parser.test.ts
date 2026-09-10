import { parsearHistoria } from '../src/modules/excel/parsers/historia.parser';
import { Matriz } from '../src/modules/excel/xlsxHelpers';

// Layout real de la hoja HISTORIA: 3 bloques lado a lado (A/B/C), cada
// uno con `Año | Equipo Campeon` + columnas de ranking que se ignoran.
// Los bloques tienen distinta longitud.
const matrizReal: Matriz = [
  ['LISTADO DE CAMPEONES', null, null, null, 'Maximos CAMPEONES', null, null, null, 'LISTADO DE CAMPEONES'],
  ['Categoria "A"', null, null, null, 'Categoria "A"', null, null, null, 'Categoria "B"'],
  ['Torneo de Exalumnos', null, null, null, 'Torneo de Exalumnos', null, null, null, 'Torneo de Exalumnos'],
  [],
  ['Año', 'Equipo Campeon', null, null, 'Torneos', 'Equipo', null, null, 'Año', 'Equipo Campeon'],
  [2026, 'Proximamente!!', null, null, 8, 'MERENGUETENGUE', null, null, 2026, 'Proximamente!!'],
  [2025, 'S.N.', null, null, 6, 'LA GRAN FIESTARDI', null, null, 2025, 'LA NAVE'],
  [2024, 'TARRE', null, null, 4, 'GRANATE', null, null, 2024, 'GULP'],
  [2020, null, null, null, 1, 'LA PANDILLA', null, null, 2020, null],
  [2019, 'LA GRAN FIESTARDI', null, null, 1, 'ELPIDIO GONZALEZ', null, null, 2019, 'LA BANDA'],
  [2011, 'MERENGUETENGUE'],
  [1993, 'CASAMATA'],
];

describe('parsearHistoria', () => {
  it('extrae (año, categoría, campeón) de cada bloque ignorando el ranking', () => {
    const { filas } = parsearHistoria(matrizReal);
    expect(filas).toContainEqual({ anio: 2024, categoria: 'A', equipoNombre: 'TARRE' });
    expect(filas).toContainEqual({ anio: 2024, categoria: 'B', equipoNombre: 'GULP' });
    expect(filas).toContainEqual({ anio: 2019, categoria: 'A', equipoNombre: 'LA GRAN FIESTARDI' });
    expect(filas).toContainEqual({ anio: 2019, categoria: 'B', equipoNombre: 'LA BANDA' });
    // Los bloques largos (A) llegan más abajo que los cortos (B)
    expect(filas).toContainEqual({ anio: 2011, categoria: 'A', equipoNombre: 'MERENGUETENGUE' });
    expect(filas).toContainEqual({ anio: 1993, categoria: 'A', equipoNombre: 'CASAMATA' });
    expect(filas.find((f) => f.categoria === 'B' && f.anio === 2011)).toBeUndefined();
    // Ninguna fila trae columnas del ranking
    expect(filas.every((f) => Object.keys(f).sort().join() === 'anio,categoria,equipoNombre')).toBe(true);
  });

  it('omite placeholders (Proximamente!!, S.N.) y años sin campeón, y los reporta', () => {
    const { filas, omitidos } = parsearHistoria(matrizReal);
    // 2026 x2 (A y B) + S.N. 2025 (A) + 2020 x2 (A y B, sin campeón)
    expect(filas.find((f) => f.anio === 2026)).toBeUndefined();
    expect(filas.find((f) => f.equipoNombre === 'S.N.')).toBeUndefined();
    expect(omitidos).toContainEqual({ anio: 2026, categoria: 'A', equipoRaw: 'Proximamente!!', motivo: 'PLACEHOLDER' });
    expect(omitidos).toContainEqual({ anio: 2025, categoria: 'A', equipoRaw: 'S.N.', motivo: 'PLACEHOLDER' });
    expect(omitidos).toContainEqual({ anio: 2020, categoria: 'A', equipoRaw: null, motivo: 'SIN_CAMPEON' });
    expect(omitidos).toContainEqual({ anio: 2020, categoria: 'B', equipoRaw: null, motivo: 'SIN_CAMPEON' });
  });

  it('devuelve vacío si no hay bloques de categoría', () => {
    expect(parsearHistoria([['nada'], ['útil']])).toEqual({ filas: [], omitidos: [] });
  });

  it('ignora años inválidos sin cortar el bloque', () => {
    const matriz: Matriz = [
      ['Categoria "C"'],
      ['Año', 'Equipo Campeon'],
      ['no-un-año', 'FULANO'],
      [2025, 'BOCHA FC'],
    ];
    const { filas } = parsearHistoria(matriz);
    expect(filas).toEqual([{ anio: 2025, categoria: 'C', equipoNombre: 'BOCHA FC' }]);
  });
});
