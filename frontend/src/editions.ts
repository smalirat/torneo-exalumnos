import { ref } from 'vue'
import { tournamentApi } from './api/endpoints'
import type { Tournament } from './api/types'
import { t } from './i18n'
import { editionSlug } from './slugs'

/** Every edition, loaded once and refreshed when a slug is not found (URLs name editions by slug) */
export const editionsCache = ref<Tournament[] | null>(null)

export async function loadEditions(force = false): Promise<Tournament[]> {
  if (!editionsCache.value || force) editionsCache.value = await tournamentApi.list()
  return editionsCache.value
}

/** The edition named by a URL slug ("2026-clausura"), reloading the list once if it is not there */
export async function findEdition(slug: string): Promise<Tournament | null> {
  const find = (list: Tournament[]) => list.find((e) => editionSlug(e) === slug) ?? null
  return find(await loadEditions()) ?? find(await loadEditions(true))
}

/** Synchronous lookup once the router guard loaded the list */
export function editionBySlug(slug: unknown): Tournament | null {
  return typeof slug === 'string' ? editionsCache.value?.find((e) => editionSlug(e) === slug) ?? null : null
}

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
