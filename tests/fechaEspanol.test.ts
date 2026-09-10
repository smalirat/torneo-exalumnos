import { parsearFechaEnEspanol } from '../src/modules/excel/parsers/fechaEspanol';

describe('parsearFechaEnEspanol', () => {
  it('parsea "Domingo 29 de Marzo" + año -> Date real', () => {
    const fecha = parsearFechaEnEspanol('Domingo 29 de Marzo', 2026);
    expect(fecha).toEqual(new Date(2026, 2, 29)); // marzo = mes índice 2
  });

  it('funciona sin el nombre del día', () => {
    const fecha = parsearFechaEnEspanol('19 de Abril', 2026);
    expect(fecha).toEqual(new Date(2026, 3, 19));
  });

  it('funciona con tilde en el mes (setiembre/septiembre)', () => {
    expect(parsearFechaEnEspanol('5 de Septiembre', 2026)).toEqual(new Date(2026, 8, 5));
    expect(parsearFechaEnEspanol('5 de Setiembre', 2026)).toEqual(new Date(2026, 8, 5));
  });

  it('devuelve null si no puede parsear el texto', () => {
    expect(parsearFechaEnEspanol('texto sin fecha', 2026)).toBeNull();
    expect(parsearFechaEnEspanol('', 2026)).toBeNull();
  });

  it('devuelve null si el mes no es reconocido', () => {
    expect(parsearFechaEnEspanol('29 de Mesinventado', 2026)).toBeNull();
  });
});
