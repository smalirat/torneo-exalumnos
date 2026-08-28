import { parsearFixture } from '../../src/modules/excel-import/parsers/proxPartido.parser';
import { Matriz } from '../../src/modules/excel-import/xlsxHelpers';

// Estructura real confirmada contra el Excel real de la liga: el header
// ("Campo | Cancha | Horario | Equipo | | vs | Equipo | | Categoría") se
// repite una vez por cada "Fecha Nº N", con una fila-label (jornada + fecha
// en texto libre) unas filas antes.
function bloqueFixture(labelFila: (string | number | null)[], filas: Matriz): Matriz {
  return [
    labelFila,
    [null],
    ['Campo', 'Cancha', 'Horario', 'Equipo', null, 'vs', 'Equipo', null, 'Categoría', null],
    [null],
    ...filas,
  ];
}

describe('parsearFixture', () => {
  it('parsea un bloque de fecha normal, incluyendo jornada y fecha en texto', () => {
    const matriz = bloqueFixture(
      ['FECHA Nº 1', null, null, null, null, null, 'Domingo 29 de Marzo', null, null, null],
      [['Siberia', 3, '9:00 hs', 'GULP', null, 'vs', 'BOCHA FC', null, 'Zona 1', null]],
    );

    const { filas, omitidas } = parsearFixture(matriz);

    expect(omitidas).toHaveLength(0);
    expect(filas).toEqual([
      {
        jornada: 1,
        categoriaLabel: 'ZONA 1',
        equipoLocal: 'GULP',
        equipoVisitante: 'BOCHA FC',
        campo: 'Siberia',
        cancha: '3',
        horario: '9:00 hs',
        fechaTexto: '29 de Marzo',
      },
    ]);
  });

  it('parsea múltiples bloques de fecha en la misma hoja, cada uno con su propio header', () => {
    const matriz = [
      ...bloqueFixture(
        ['FECHA Nº 1', null, null, null, null, null, 'Domingo 29 de Marzo', null, null, null],
        [['Siberia', 3, '9:00 hs', 'GULP', null, 'vs', 'BOCHA FC', null, 'Zona 1', null]],
      ),
      [null],
      [null],
      ...bloqueFixture(
        ['FECHA Nº 2', null, null, null, null, null, 'Domingo 19 de Abril', null, null, null],
        [['Anexo', 1, '10:10 hs', 'LA NAVE', null, 'vs', 'DOBLAS', null, 'Zona 1', null]],
      ),
    ];

    const { filas } = parsearFixture(matriz);
    expect(filas).toHaveLength(2);
    expect(filas[0].jornada).toBe(1);
    expect(filas[1].jornada).toBe(2);
    expect(filas[1].fechaTexto).toBe('19 de Abril');
  });

  it('un bloque sin "Fecha Nº" (ej. Promoción Final Interzonal) sigue la numeración del bloque anterior + 1', () => {
    const matriz = [
      ...bloqueFixture(
        ['FECHA Nº 11', null, null, null, null, null, 'Domingo 5 de Julio', null, null, null],
        [['Siberia', 3, '9:00 hs', 'GULP', null, 'vs', 'BOCHA FC', null, 'Zona 1', null]],
      ),
      [null],
      [null],
      ...bloqueFixture(
        ['Promoción Final Interzonal', null, null, null, null, null, 'Domingo 19 de Julio', null, null, null],
        [['Siberia', 3, '10:10hs', 'DOBLAS', null, 'vs', 'LA NAVE', null, 'Final interzonal', null]],
      ),
    ];

    const { filas } = parsearFixture(matriz);
    expect(filas[1].jornada).toBe(12);
  });

  it('detecta filas "LIBRE" (bye) y no genera partido', () => {
    const matriz = bloqueFixture(
      ['FECHA Nº 1', null, null, null, null, null, 'Domingo 29 de Marzo', null, null, null],
      [
        ['Anexo', 1, '9:00 hs', 'LIBRE', null, null, null, null, null, null],
        ['Anexo', 2, '9:00 hs', 'LIBRE: LA NAVE', null, null, null, null, null, null],
      ],
    );

    const { filas, omitidas } = parsearFixture(matriz);
    expect(filas).toHaveLength(0);
    expect(omitidas.filter((o) => o.motivo === 'LIBRE')).toHaveLength(2);
  });

  it('detecta placeholders de posición de tabla (fase interzonal) y no genera partido', () => {
    const matriz = bloqueFixture(
      ['FECHA Nº 12', null, null, null, null, null, 'Domingo 12 de Julio', null, null, null],
      [['Anexo', 1, '10:10 hs', '1ero ZONA 2', null, 'vs', '10mo ZONA 1', null, 'Interzonal', null]],
    );

    const { filas, omitidas } = parsearFixture(matriz);
    expect(filas).toHaveLength(0);
    expect(omitidas[0].motivo).toBe('PLACEHOLDER_POSICION');
  });

  it('omite filas sin ambos equipos cargados (fecha futura no definida)', () => {
    const matriz = bloqueFixture(
      ['FECHA Nº 1', null, null, null, null, null, 'Domingo 29 de Marzo', null, null, null],
      [['Anexo', 1, '9:00 hs', null, null, null, null, null, null, null]],
    );
    const { filas } = parsearFixture(matriz);
    expect(filas).toHaveLength(0);
  });

  it('devuelve un warning si no encuentra ninguna fila de header', () => {
    const matriz: Matriz = [['esto no tiene headers reconocibles']];
    const { filas, omitidas } = parsearFixture(matriz);
    expect(filas).toHaveLength(0);
    expect(omitidas[0].motivo).toBe('FILA_INCOMPLETA');
  });
});
