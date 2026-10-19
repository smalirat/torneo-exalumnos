import type { Category, TeamRef, Tournament } from './api/types'

/** "Categoría Única" → "categoria-unica": lowercase, no accents, words joined by hyphens (URL-safe) */
export function slugify(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

/** Edition: "2026-clausura" when it belongs to a season (one Apertura/Clausura per year), else "edicion-12" */
export function editionSlug(t: Pick<Tournament, 'id' | 'seasonYear' | 'stage'>): string {
  return t.seasonYear != null && t.stage ? `${t.seasonYear}-${t.stage.toLowerCase()}` : `edicion-${t.id}`
}

/** Team: "41-gulp" — the id keeps it unique and stable even if the name changes */
export function teamSlug(team: Pick<TeamRef, 'id' | 'name'>): string {
  const name = slugify(team.name)
  return name ? `${team.id}-${name}` : String(team.id)
}

/** Id of a team from its slug ("41-gulp", or just "41") */
export function teamIdFromSlug(slug: string): number | null {
  const m = /^(\d+)(?:-|$)/.exec(slug)
  return m ? Number(m[1]) : null
}

const categorySlug = (c: Category) => `categoria-${slugify(c.name)}`

/**
 * Path segments of a group, same naming as the labels (groups.ts): a single category → just the zone
 * ("zona-1"); several → "categoria-a", plus the zone when it has more than one ("categoria-a/zona-2").
 */
export function groupSegments(categories: Category[], zoneId: number | null): string[] {
  for (const c of categories) {
    const z = c.zones.find((x) => x.id === zoneId)
    if (!z) continue
    if (categories.length <= 1) return c.zoneCount > 1 ? [slugify(z.name)] : []
    return c.zoneCount > 1 ? [categorySlug(c), slugify(z.name)] : [categorySlug(c)]
  }
  return []
}

/** Zone named by path segments (see groupSegments); null when they do not match (then the first one is shown) */
export function zoneFromSegments(categories: Category[], segments: string[]): number | null {
  const [first, second] = segments
  if (!first) return null
  if (categories.length <= 1) {
    const zones = categories[0]?.zones ?? []
    return zones.find((z) => slugify(z.name) === first)?.id ?? null
  }
  const c = categories.find((x) => categorySlug(x) === first)
  if (!c) return null
  return (second ? c.zones.find((z) => slugify(z.name) === second) : c.zones[0])?.id ?? null
}
