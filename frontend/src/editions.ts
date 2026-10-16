import type { Tournament } from './api/types'

const STAGE_ORDER: Record<string, number> = { CLAUSURA: 2, APERTURA: 1 }

/** Editions of the league, newest first: season year, then Clausura before Apertura, then newest created. */
export function sortEditions(list: Tournament[]): Tournament[] {
  return [...list].sort((a, b) =>
    (b.seasonYear ?? 0) - (a.seasonYear ?? 0)
    || (STAGE_ORDER[b.stage ?? ''] ?? 0) - (STAGE_ORDER[a.stage ?? ''] ?? 0)
    || b.id - a.id)
}

/** The edition the home page shows: the newest one being played, otherwise the newest one. */
export function currentEdition(list: Tournament[]): Tournament | null {
  const sorted = sortEditions(list)
  return sorted.find((x) => x.status === 'LIVE') ?? sorted[0] ?? null
}
