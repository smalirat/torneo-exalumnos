import { normalizar } from '../xlsxHelpers';

const MESES: Record<string, number> = {
  ENERO: 0,
  FEBRERO: 1,
  MARZO: 2,
  ABRIL: 3,
  MAYO: 4,
  JUNIO: 5,
  JULIO: 6,
  AGOSTO: 7,
  SEPTIEMBRE: 8,
  SETIEMBRE: 8,
  OCTUBRE: 9,
  NOVIEMBRE: 10,
  DICIEMBRE: 11,
};

/**
 * Tu Excel real describe la fecha de cada jornada como texto libre, sin año
 * ("Domingo 29 de Marzo"). Lo combinamos con el año de la Temporada del
 * torneo (que sí conocemos) para armar una Date real.
 */
export function parsearFechaEnEspanol(texto: string, anio: number): Date | null {
  const match = normalizar(texto).match(/(\d{1,2})\s+DE\s+([A-Z]+)/);
  if (!match) return null;

  const dia = Number(match[1]);
  const mes = MESES[match[2]];
  if (mes === undefined || dia < 1 || dia > 31) return null;

  return new Date(anio, mes, dia);
}
