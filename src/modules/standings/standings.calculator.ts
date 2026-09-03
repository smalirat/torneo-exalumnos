import {
  EquipoInscripto,
  FilaStandings,
  PartidoResultado,
  PuntoRestadoInput,
} from './standings.types';

interface StatsAcumuladas {
  equipoId: number;
  nombre: string;
  pj: number;
  pg: number;
  pe: number;
  pp: number;
  gf: number;
  ge: number;
}

/**
 * Calcula la tabla de posiciones a partir de:
 * - los equipos inscriptos en esa categoría/zona (incluye equipos sin partidos jugados)
 * - los partidos YA JUGADOS de esa categoría/zona (el caller filtra por estado=JUGADO
 *   antes de llamar acá — esta función no sabe nada de "estado", solo suma lo que le pasan)
 * - los puntos restados (PR) de esa categoría/zona
 *
 * Regla de desempate (ver DECISIONES.md #2, pendiente de tu confirmación):
 *   1. PTOS (puntos por partidos + PR)
 *   2. DIF (GF - GE)
 *   3. GF
 *   4. Si quedan exactamente 2 equipos empatados tras 1-3: resultado entre sí
 *   5. Si el empate involucra 3+ equipos, o 2 que no jugaron entre sí: se
 *      marca `empatadoSinResolver = true` en vez de inventar un criterio
 *      (sorteo / alfabético) que no confirmaste.
 *
 * Es una función PURA: no toca la base de datos, no tiene I/O. Así se puede
 * testear exhaustivamente sin levantar Postgres.
 */

export function calcularTablaPosiciones(
  equipos: EquipoInscripto[],
  partidosJugados: PartidoResultado[],
  puntosRestados: PuntoRestadoInput[],
): FilaStandings[] {
  const stats = new Map<number, StatsAcumuladas>();
  for (const equipo of equipos) {
    stats.set(equipo.equipoId, {
      equipoId: equipo.equipoId,
      nombre: equipo.nombre,
      pj: 0,
      pg: 0,
      pe: 0,
      pp: 0,
      gf: 0,
      ge: 0,
    });
  }

  for (const partido of partidosJugados) {
    const local = stats.get(partido.equipoLocalId);
    const visitante = stats.get(partido.equipoVisitanteId);
    // Si NINGUNO de los dos equipos está inscripto en esta categoría/zona,
    // el partido no nos corresponde (es responsabilidad del caller filtrar
    // bien). Pero si SOLO uno de los dos está ausente (dato inconsistente,
    // o un cruce interzonal fuera de este alcance), igual sumamos la parte
    // del equipo que sí conocemos — no descartamos el partido entero.
    if (!local && !visitante) continue;

    if (local) {
      local.pj += 1;
      local.gf += partido.golesLocal;
      local.ge += partido.golesVisitante;
      if (partido.golesLocal > partido.golesVisitante) local.pg += 1;
      else if (partido.golesLocal < partido.golesVisitante) local.pp += 1;
      else local.pe += 1;
    }

    if (visitante) {
      visitante.pj += 1;
      visitante.gf += partido.golesVisitante;
      visitante.ge += partido.golesLocal;
      if (partido.golesVisitante > partido.golesLocal) visitante.pg += 1;
      else if (partido.golesVisitante < partido.golesLocal) visitante.pp += 1;
      else visitante.pe += 1;
    }
  }

  const prPorEquipo = new Map<number, number>();
  for (const pr of puntosRestados) {
    prPorEquipo.set(pr.equipoId, (prPorEquipo.get(pr.equipoId) ?? 0) + pr.puntos);
  }

  const filas: Omit<FilaStandings, 'posicion' | 'empatadoSinResolver'>[] = [...stats.values()].map(
    (s) => {
      const pr = prPorEquipo.get(s.equipoId) ?? 0;
      const ptosPartidos = s.pg * 3 + s.pe * 1;
      return {
        equipoId: s.equipoId,
        nombre: s.nombre,
        pj: s.pj,
        pg: s.pg,
        pe: s.pe,
        pp: s.pp,
        gf: s.gf,
        ge: s.ge,
        dif: s.gf - s.ge,
        pr,
        ptos: ptosPartidos + pr,
      };
    },
  );

  // Orden base: PTOS desc, DIF desc, GF desc. Alfabético SOLO como criterio
  // de estabilidad para que el sort sea determinístico — no es un criterio
  // deportivo. Se reemplaza por head-to-head más abajo cuando aplica.
  filas.sort((a, b) => {
    if (b.ptos !== a.ptos) return b.ptos - a.ptos;
    if (b.dif !== a.dif) return b.dif - a.dif;
    if (b.gf !== a.gf) return b.gf - a.gf;
    return a.nombre.localeCompare(b.nombre);
  });

  // Agrupamos en bloques de equipos con (ptos, dif, gf) idénticos y
  // resolvemos head-to-head SOLO cuando el bloque tiene exactamente 2 equipos.
  const resultado: FilaStandings[] = [];
  let i = 0;
  while (i < filas.length) {
    let j = i + 1;
    while (
      j < filas.length &&
      filas[j].ptos === filas[i].ptos &&
      filas[j].dif === filas[i].dif &&
      filas[j].gf === filas[i].gf
    ) {
      j += 1;
    }
    const bloque = filas.slice(i, j);

    if (bloque.length === 2) {
      const [a, b] = bloque;
      const entreSi = partidosJugados.filter(
        (p) =>
          (p.equipoLocalId === a.equipoId && p.equipoVisitanteId === b.equipoId) ||
          (p.equipoLocalId === b.equipoId && p.equipoVisitanteId === a.equipoId),
      );
      if (entreSi.length > 0) {
        let ptosA = 0;
        let ptosB = 0;
        for (const p of entreSi) {
          const golesA = p.equipoLocalId === a.equipoId ? p.golesLocal : p.golesVisitante;
          const golesB = p.equipoLocalId === a.equipoId ? p.golesVisitante : p.golesLocal;
          if (golesA > golesB) ptosA += 3;
          else if (golesA < golesB) ptosB += 3;
          else {
            ptosA += 1;
            ptosB += 1;
          }
        }
        if (ptosA !== ptosB) {
          const ordenados = ptosA > ptosB ? [a, b] : [b, a];
          for (const equipo of ordenados) {
            resultado.push({ ...equipo, posicion: 0, empatadoSinResolver: false });
          }
          i = j;
          continue;
        }
      }
      // No jugaron entre sí, o empataron también el/los partido/s entre ellos:
      // queda empate sin resolver.
      for (const equipo of bloque) {
        resultado.push({ ...equipo, posicion: 0, empatadoSinResolver: true });
      }
    } else if (bloque.length > 2) {
      for (const equipo of bloque) {
        resultado.push({ ...equipo, posicion: 0, empatadoSinResolver: true });
      }
    } else {
      resultado.push({ ...bloque[0], posicion: 0, empatadoSinResolver: false });
    }

    i = j;
  }

  // Asignamos posición numérica final. Dos equipos empatadosSinResolver
  // comparten el mismo número de posición (como en cualquier tabla real);
  // el siguiente equipo salta el número correspondiente.
  let posicionActual = 1;
  for (let idx = 0; idx < resultado.length; idx++) {
    if (idx > 0) {
      const prev = resultado[idx - 1];
      const curr = resultado[idx];
      const mismoBloque =
        prev.ptos === curr.ptos &&
        prev.dif === curr.dif &&
        prev.gf === curr.gf &&
        prev.empatadoSinResolver &&
        curr.empatadoSinResolver;
      if (!mismoBloque) posicionActual = idx + 1;
    }
    resultado[idx].posicion = posicionActual;
  }

  return resultado;
}
