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

/** Every zone of the edition in order, with its label */
export function zoneOptions(categories: Category[], short = false) {
  return categories.flatMap((c) => c.zones.map((z) => ({
    id: z.id,
    categoryId: c.id,
    label: groupName({ categoryName: c.name, categoryCount: categories.length, zoneName: z.name, zoneCount: c.zoneCount }, short),
  })))
}
