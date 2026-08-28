import { calcularTablaPosiciones } from '../../src/modules/standings/standings.calculator';
import { EquipoInscripto, PartidoResultado, PuntoRestadoInput } from '../../src/modules/standings/standings.types';

const equipo = (id: number, nombre: string): EquipoInscripto => ({ equipoId: id, nombre });

describe('calcularTablaPosiciones', () => {
  it('un equipo sin partidos jugados aparece con 0 en todo, sin romper el cálculo', () => {
    const equipos = [equipo(1, 'GULP'), equipo(2, 'BOCHA FC')];
    const resultado = calcularTablaPosiciones(equipos, [], []);

    expect(resultado).toHaveLength(2);
    for (const fila of resultado) {
      expect(fila).toMatchObject({ pj: 0, pg: 0, pe: 0, pp: 0, gf: 0, ge: 0, dif: 0, pr: 0, ptos: 0 });
    }
  });

  it('calcula PTOS, PJ, PG, PE, PP, GF, GE, DIF correctamente para un partido simple', () => {
    const equipos = [equipo(1, 'GULP'), equipo(2, 'BOCHA FC')];
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 3, golesVisitante: 1 },
    ];
    const resultado = calcularTablaPosiciones(equipos, partidos, []);

    const gulp = resultado.find((r) => r.equipoId === 1)!;
    const bocha = resultado.find((r) => r.equipoId === 2)!;

    expect(gulp).toMatchObject({ pj: 1, pg: 1, pe: 0, pp: 0, gf: 3, ge: 1, dif: 2, ptos: 3 });
    expect(bocha).toMatchObject({ pj: 1, pg: 0, pe: 0, pp: 1, gf: 1, ge: 3, dif: -2, ptos: 0 });
    expect(gulp.posicion).toBe(1);
    expect(bocha.posicion).toBe(2);
  });

  it('un empate reparte 1 punto a cada equipo', () => {
    const equipos = [equipo(1, 'GULP'), equipo(2, 'BOCHA FC')];
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 2, golesVisitante: 2 },
    ];
    const resultado = calcularTablaPosiciones(equipos, partidos, []);

    expect(resultado.find((r) => r.equipoId === 1)).toMatchObject({ pe: 1, ptos: 1 });
    expect(resultado.find((r) => r.equipoId === 2)).toMatchObject({ pe: 1, ptos: 1 });
  });

  it('desempata por diferencia de gol cuando los PTOS son iguales', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B'), equipo(3, 'C')];
    // A y B llegan a 3 puntos cada uno (le ganaron a C), pero A tiene mejor DIF
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 3, golesLocal: 5, golesVisitante: 0 }, // A: DIF +5
      { equipoLocalId: 2, equipoVisitanteId: 3, golesLocal: 1, golesVisitante: 0 }, // B: DIF +1
    ];
    const resultado = calcularTablaPosiciones(equipos, partidos, []);

    expect(resultado[0]).toMatchObject({ equipoId: 1, ptos: 3, dif: 5 });
    expect(resultado[1]).toMatchObject({ equipoId: 2, ptos: 3, dif: 1 });
    expect(resultado[2]).toMatchObject({ equipoId: 3 });
  });

  it('desempata por goles a favor cuando PTOS y DIF son iguales', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B')];
    // Ambos con PTOS=3 y DIF=+2, pero A metió más goles a favor
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 9, golesLocal: 4, golesVisitante: 2 },
      { equipoLocalId: 2, equipoVisitanteId: 9, golesLocal: 2, golesVisitante: 0 },
    ];
    const equiposConRival = [...equipos, equipo(9, 'RIVAL')];
    const resultado = calcularTablaPosiciones(equiposConRival, partidos, []);

    const a = resultado.find((r) => r.equipoId === 1)!;
    const b = resultado.find((r) => r.equipoId === 2)!;
    expect(a.ptos).toBe(b.ptos);
    expect(a.dif).toBe(b.dif);
    expect(a.gf).toBeGreaterThan(b.gf);
    expect(resultado.findIndex((r) => r.equipoId === 1)).toBeLessThan(
      resultado.findIndex((r) => r.equipoId === 2),
    );
  });

  it('si dos equipos empatan en PTOS/DIF/GF, desempata por el resultado entre sí', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B'), equipo(3, 'C'), equipo(4, 'D')];
    const partidos: PartidoResultado[] = [
      // A y B llegan exactamente igual en PTOS/DIF/GF...
      { equipoLocalId: 1, equipoVisitanteId: 3, golesLocal: 2, golesVisitante: 0 },
      { equipoLocalId: 2, equipoVisitanteId: 4, golesLocal: 2, golesVisitante: 0 },
      // ...pero A le ganó a B en el partido entre ellos
      { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 1, golesVisitante: 0 },
    ];
    const resultado = calcularTablaPosiciones(equipos, partidos, []);

    const posA = resultado.findIndex((r) => r.equipoId === 1);
    const posB = resultado.findIndex((r) => r.equipoId === 2);
    expect(posA).toBeLessThan(posB);
    expect(resultado[posA].empatadoSinResolver).toBe(false);
    expect(resultado[posB].empatadoSinResolver).toBe(false);
  });

  it('marca empatadoSinResolver cuando 2 equipos empatan en todo y no jugaron entre sí', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B'), equipo(3, 'C'), equipo(4, 'D')];
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 3, golesLocal: 2, golesVisitante: 0 },
      { equipoLocalId: 2, equipoVisitanteId: 4, golesLocal: 2, golesVisitante: 0 },
    ];
    const resultado = calcularTablaPosiciones(equipos, partidos, []);

    const a = resultado.find((r) => r.equipoId === 1)!;
    const b = resultado.find((r) => r.equipoId === 2)!;
    expect(a.empatadoSinResolver).toBe(true);
    expect(b.empatadoSinResolver).toBe(true);
    // comparten posición
    expect(a.posicion).toBe(b.posicion);
  });

  it('marca empatadoSinResolver cuando 3+ equipos empatan en todo', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B'), equipo(3, 'C')];
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 9, golesLocal: 1, golesVisitante: 0 },
      { equipoLocalId: 2, equipoVisitanteId: 9, golesLocal: 1, golesVisitante: 0 },
      { equipoLocalId: 3, equipoVisitanteId: 9, golesLocal: 1, golesVisitante: 0 },
    ];
    const equiposConRival = [...equipos, equipo(9, 'RIVAL')];
    const resultado = calcularTablaPosiciones(equiposConRival, partidos, []);

    const empatados = resultado.filter((r) => [1, 2, 3].includes(r.equipoId));
    expect(empatados.every((e) => e.empatadoSinResolver)).toBe(true);
    expect(new Set(empatados.map((e) => e.posicion)).size).toBe(1);
  });

  it('aplica PR (puntos restados) descontando del total de PTOS antes de ordenar', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B')];
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 1, golesVisitante: 0 },
    ];
    const puntosRestados: PuntoRestadoInput[] = [{ equipoId: 1, puntos: 5 }];
    const resultado = calcularTablaPosiciones(equipos, partidos, puntosRestados);

    const a = resultado.find((r) => r.equipoId === 1)!;
    expect(a.ptos).toBe(3 - 5); // -2
    expect(a.pr).toBe(5);
    // B (0 puntos, sin PR) queda por encima de A (-2 puntos)
    expect(resultado.findIndex((r) => r.equipoId === 2)).toBeLessThan(
      resultado.findIndex((r) => r.equipoId === 1),
    );
  });

  it('acumula varios PuntosRestados del mismo equipo', () => {
    const equipos = [equipo(1, 'A')];
    const puntosRestados: PuntoRestadoInput[] = [
      { equipoId: 1, puntos: 3 },
      { equipoId: 1, puntos: 2 },
    ];
    const resultado = calcularTablaPosiciones(equipos, [], puntosRestados);
    expect(resultado[0].pr).toBe(5);
    expect(resultado[0].ptos).toBe(-5);
  });

  it('recalcula desde cero: la corrección de un resultado no arrastra el valor viejo', () => {
    const equipos = [equipo(1, 'A'), equipo(2, 'B')];

    // "Resultado viejo": A ganaba 3-0
    const partidosViejos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 3, golesVisitante: 0 },
    ];
    const resultadoViejo = calcularTablaPosiciones(equipos, partidosViejos, []);
    expect(resultadoViejo.find((r) => r.equipoId === 1)!.ptos).toBe(3);

    // "Resultado corregido": en realidad fue 1-1. Como la función es pura y
    // el caller siempre le pasa el estado actual completo de los partidos
    // (no un delta), no hay forma de que el 3-0 viejo "contamine" el cálculo.
    const partidosCorregidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 1, golesVisitante: 1 },
    ];
    const resultadoNuevo = calcularTablaPosiciones(equipos, partidosCorregidos, []);
    const a = resultadoNuevo.find((r) => r.equipoId === 1)!;
    expect(a).toMatchObject({ pg: 0, pe: 1, gf: 1, ge: 1, dif: 0, ptos: 1 });
  });

  it('ignora partidos que referencian equipos no inscriptos en esta categoría/zona', () => {
    const equipos = [equipo(1, 'A')];
    const partidos: PartidoResultado[] = [
      { equipoLocalId: 1, equipoVisitanteId: 999, golesLocal: 2, golesVisitante: 1 },
    ];
    const resultado = calcularTablaPosiciones(equipos, partidos, []);
    // No debe explotar, y el equipo 1 sí suma su parte del partido
    expect(resultado).toHaveLength(1);
    expect(resultado[0]).toMatchObject({ pj: 1, pg: 1, gf: 2, ge: 1 });
  });
});
