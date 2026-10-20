import type { Category } from './api/types'
import { t } from './i18n'

/**
 * Name of a group (a zone of a category), the same everywhere:
 * - a single category in the edition: only the zone ("Zona 1"), its name ("Única"…) is never shown;
 * - several categories: "Categoría A · Zona 1", or "Categoría A" when it has one zone.
 * `short` drops the word "Categoría" for compact lists ("A · Zona 1").
 */
export function groupName(
  g: { categoryName: string; categoryCount: number; zoneName?: string | null; zoneCount: number },
  short = false,
): string {
  const zone = g.zoneCount > 1 && g.zoneName ? g.zoneName : null
  if (g.categoryCount <= 1 && zone) return zone
  const category = short ? g.categoryName : t('league.category', { name: g.categoryName })
  return zone ? `${category} · ${zone}` : category
}

/** Default interzonal pairs: the zones of each category two by two; with single-zone categories, the first two */
export function defaultPairs(categories: Category[]): { zoneA: number; zoneB: number }[] {
  const out: { zoneA: number; zoneB: number }[] = []
  for (const c of categories) {
    for (let i = 0; i + 1 < c.zones.length; i += 2) out.push({ zoneA: c.zones[i].id, zoneB: c.zones[i + 1].id })
  }
  const all = categories.flatMap((c) => c.zones)
  if (!out.length && all.length > 1) out.push({ zoneA: all[0].id, zoneB: all[1].id })
  return out
}

/** Every zone of the edition in order, with its label */
export function zoneOptions(categories: Category[], short = false) {
  return categories.flatMap((c) => c.zones.map((z) => ({
    id: z.id,
    categoryId: c.id,
    label: groupName({ categoryName: c.name, categoryCount: categories.length, zoneName: z.name, zoneCount: c.zoneCount }, short),
  })))
}
