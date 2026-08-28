import { parsearTablaPosiciones } from '../../src/modules/excel-import/parsers/tablaPosiciones.parser';
import { parsearRankingJugador } from '../../src/modules/excel-import/parsers/rankingJugador.parser';
import { parsearSancionados } from '../../src/modules/excel-import/parsers/sancionados.parser';
import { Matriz } from '../../src/modules/excel-import/xlsxHelpers';

describe('parsearTablaPosiciones', () => {
  it('extrae EQUIPO + PR de zonas apiladas verticalmente (formato "Categoría")', () => {
    const matriz: Matriz = [
      ['CATEGORIA "A"'],
      ['POSICION', 'EQUIPO', 'PTOS', 'PJ', 'PR'],
      [1, 'GULP', 12, 5, 0],
      [2, 'BOCHA FC', 10, 5, 3],
      [null, null, null, null, null],
      ['CATEGORIA "B"'],
      ['POSICION', 'EQUIPO', 'PTOS', 'PJ', 'PR'],
      [1, 'LOS PIBES', 8, 4, 0],
    ];

    const bloques = parsearTablaPosiciones(matriz);

    expect(bloques).toHaveLength(2);
    expect(bloques[0]).toMatchObject({
      label: 'CATEGORIA "A"',
      filas: [
        { equipoNombre: 'GULP', pr: 0 },
        { equipoNombre: 'BOCHA FC', pr: 3 },
      ],
    });
    expect(bloques[1]).toMatchObject({ label: 'CATEGORIA "B"', filas: [{ equipoNombre: 'LOS PIBES', pr: 0 }] });
  });

  it('extrae EQUIPO + PR de zonas LADO A LADO en la misma fila (formato real "Tabla Apertura")', () => {
    const matriz: Matriz = [
      ['ZONA 1', null, null, null, null, null, null, null, null, null, null, null, null, null, null, 'ZONA 2'],
      [null],
      [
        'POSICION', 'EQUIPO', 'PTOS', 'PJ', 'PG', 'PE', 'PP', 'GF', 'GE', 'DIF', 'PR', null, null, null, null,
        'POSICION', 'EQUIPO', 'PTOS', 'PJ', 'PG', 'PE', 'PP', 'GF', 'GE', 'DIF', 'PR',
      ],
      [1, 'GULP', 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, 1, 'DESAMPARADOS', 0, 0, 0, 0, 0, 0, 0, 0, 0],
      [2, 'BOCHA FC', 0, 0, 0, 0, 0, 0, 0, 0, 3, null, null, null, null, 2, 'LA CHISPA', 0, 0, 0, 0, 0, 0, 0, 0, 0],
      [null, null, null, null, null, null, null, null, null, null, null, null, 'Control', 0, 0],
    ];

    const bloques = parsearTablaPosiciones(matriz);

    expect(bloques).toHaveLength(2);
    expect(bloques.find((b) => b.label === 'ZONA 1')?.filas).toEqual([
      { equipoNombre: 'GULP', pr: 0 },
      { equipoNombre: 'BOCHA FC', pr: 3 },
    ]);
    expect(bloques.find((b) => b.label === 'ZONA 2')?.filas).toEqual([
      { equipoNombre: 'DESAMPARADOS', pr: 0 },
      { equipoNombre: 'LA CHISPA', pr: 0 },
    ]);
  });

  it('no incluye la fila de Control como si fuera un equipo, aunque no empiece en la columna A', () => {
    const matriz: Matriz = [
      ['CATEGORIA "A"'],
      ['EQUIPO', 'PR'],
      ['GULP', 0],
      [null, null, 'Control', 0],
    ];
    const bloques = parsearTablaPosiciones(matriz);
    expect(bloques[0].filas).toHaveLength(1);
  });
});

describe('parsearRankingJugador', () => {
  it('parsea goleadores con la columna GOLES', () => {
    const matriz: Matriz = [
      ['JUGADOR', 'EQUIPO', 'GOLES'],
      ['Juan Pérez', 'GULP', 8],
      ['Pedro Gómez', 'BOCHA FC', 5],
    ];
    const filas = parsearRankingJugador(matriz, ['GOLES']);
    expect(filas).toEqual([
      { jugadorNombre: 'Juan Pérez', equipoNombre: 'GULP', valor: 8 },
      { jugadorNombre: 'Pedro Gómez', equipoNombre: 'BOCHA FC', valor: 5 },
    ]);
  });

  it('devuelve array vacío si no encuentra headers reconocibles', () => {
    const matriz: Matriz = [['nada reconocible acá']];
    expect(parsearRankingJugador(matriz, ['GOLES'])).toEqual([]);
  });
});

describe('parsearSancionados', () => {
  it('parsea EXPULSADOS como ROJA', () => {
    const matriz: Matriz = [
      ['EXPULSADOS'],
      ['JUGADOR', 'EQUIPO', 'FECHAS'],
      ['Juan Pérez', 'GULP', 2],
    ];
    const filas = parsearSancionados(matriz);
    expect(filas).toEqual([
      {
        jugadorNombre: 'Juan Pérez',
        equipoNombre: 'GULP',
        tipoTarjeta: 'ROJA',
        fechasSuspension: 2,
        observaciones: null,
      },
    ]);
  });

  it('convierte AMONESTADOS (amarillas) a fechas de suspensión: 4=1, 8=2', () => {
    const matriz: Matriz = [
      ['AMONESTADOS'],
      ['JUGADOR', 'EQUIPO', 'AMARILLAS'],
      ['Juan Pérez', 'GULP', 4],
      ['Pedro Gómez', 'BOCHA FC', 8],
      ['Luis Ruiz', 'LOS PIBES', 2], // todavía no llega al umbral
    ];
    const filas = parsearSancionados(matriz);

    expect(filas).toEqual([
      {
        jugadorNombre: 'Juan Pérez',
        equipoNombre: 'GULP',
        tipoTarjeta: 'AMARILLA',
        fechasSuspension: 1,
        observaciones: '4 amarillas acumuladas',
      },
      {
        jugadorNombre: 'Pedro Gómez',
        equipoNombre: 'BOCHA FC',
        tipoTarjeta: 'AMARILLA',
        fechasSuspension: 2,
        observaciones: '8 amarillas acumuladas',
      },
    ]);
    // Luis Ruiz con 2 amarillas no genera Sancion todavía
    expect(filas.find((f) => f.jugadorNombre === 'Luis Ruiz')).toBeUndefined();
  });

  it('reconoce el label real "AMONESTADOS Torneo APERTURA" (con texto extra)', () => {
    const matriz: Matriz = [
      ['AMONESTADOS Torneo APERTURA'],
      ['JUGADOR', 'EQUIPO', 'AMARILLAS'],
      ['Juan Pérez', 'GULP', 4],
    ];
    const filas = parsearSancionados(matriz);
    expect(filas).toHaveLength(1);
    expect(filas[0].tipoTarjeta).toBe('AMARILLA');
  });

  it('parsea ambas secciones cuando están en la misma hoja', () => {
    const matriz: Matriz = [
      ['EXPULSADOS'],
      ['JUGADOR', 'EQUIPO', 'FECHAS'],
      ['Juan Pérez', 'GULP', 1],
      ['AMONESTADOS'],
      ['JUGADOR', 'EQUIPO', 'AMARILLAS'],
      ['Pedro Gómez', 'BOCHA FC', 4],
    ];
    const filas = parsearSancionados(matriz);
    expect(filas).toHaveLength(2);
    expect(filas.map((f) => f.tipoTarjeta).sort()).toEqual(['AMARILLA', 'ROJA']);
  });
});
