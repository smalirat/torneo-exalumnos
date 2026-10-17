import type { Tournament } from './api/types'
import { t } from './i18n'

const STAGE_ORDER: Record<string, number> = { CLAUSURA: 2, APERTURA: 1 }

/** Editions of the league, newest first: season year, then Clausura before Apertura, then newest created. */
export function sortEditions(list: Tournament[]): Tournament[] {
  return [...list].sort((a, b) =>
    (b.seasonYear ?? 0) - (a.seasonYear ?? 0)
    || (STAGE_ORDER[b.stage ?? ''] ?? 0) - (STAGE_ORDER[a.stage ?? ''] ?? 0)
    || b.id - a.id)
}

/** Registration period label: "1/3/2026 – 20/3/2026", "Hasta el 20/3/2026" or "Desde el 1/3/2026" */
export function registrationLabel(start: string | null, end: string | null): string {
  const fmt = (d: string) => new Date(`${d}T00:00`).toLocaleDateString('es-AR')
  if (start && end) return `${fmt(start)} – ${fmt(end)}`
  if (end) return t('tournaments.until', { date: fmt(end) })
  return start ? t('tournaments.since', { date: fmt(start) }) : ''
}

/** The edition the home page shows: the active one (registration open or being played), otherwise the newest. */
export function currentEdition(list: Tournament[]): Tournament | null {
  const sorted = sortEditions(list)
  return sorted.find((x) => x.status === 'OPEN' || x.status === 'LIVE') ?? sorted[0] ?? null
}
