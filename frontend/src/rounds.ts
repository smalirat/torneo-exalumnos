import type { Match } from './api/types'
import { t } from './i18n'

export interface Round { key: string; number: number | null; label: string | null; day: string | null; matches: Match[] }

/**
 * Matches grouped by "Fecha N" when they are numbered (league data), otherwise by calendar day.
 * Keeps the incoming order (matches arrive sorted by date, undated last).
 */
export function groupRounds(matches: Match[]): Round[] {
  const out: Round[] = []
  const byKey = new Map<string, Round>()
  for (const m of matches) {
    const day = m.scheduledAt?.slice(0, 10) ?? null
    const key = m.round != null ? `r${m.round}` : `d${day}`
    let round = byKey.get(key)
    if (!round) {
      round = { key, number: m.round, label: m.roundLabel, day, matches: [] }
      byKey.set(key, round)
      out.push(round)
    }
    round.matches.push(m)
  }
  return out
}

/** "Domingo, 1 de noviembre" (or "Sin fecha asignada") */
export function dayLabel(day: string | null) {
  if (!day) return t('fixture.noDate')
  const text = new Date(`${day}T00:00`).toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' })
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** Already played. A walkover loaded ahead of its date is still upcoming (it shows its W.O. mark there). */
export const isDone = (m: Match) => m.status === 'FINISHED' && !m.advanceWalkover
