// Tipos de entrada/salida del cálculo de standings. Son deliberadamente
// simples (no son los modelos de Prisma) para que standings.calculator.ts
// sea una función pura, testeable sin base de datos.

export interface EquipoInscripto {
  equipoId: number;
  nombre: string;
}

export interface PartidoResultado {
  equipoLocalId: number;
  equipoVisitanteId: number;
  golesLocal: number;
  golesVisitante: number;
}

export interface PuntoPresentismoInput {
  equipoId: number;
  puntos: number;
}

export interface FilaStandings {
  equipoId: number;
  nombre: string;
  posicion: number;
  pj: number;
  pg: number;
  pe: number;
  pp: number;
  gf: number;
  ge: number;
  dif: number;
  pr: number;
  ptos: number;
  // true si, tras aplicar PTOS → DIF → GF → resultado entre sí, el equipo
  // sigue empatado con otro/s y no hay un criterio para desempatarlo
  // (3+ equipos empatados, o 2 que no jugaron entre sí). Ver DECISIONES.md #2.
  empatadoSinResolver: boolean;
}
