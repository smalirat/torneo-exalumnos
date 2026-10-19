import type { Category, TeamRef, Tournament } from './api/types'
import { editionSlug, groupSegments, teamSlug } from './slugs'

// Every URL of the app is built here (standard: lowercase Spanish words with hyphens, general → particular,
// readable slugs instead of ids, "?" only for filters). Public site at the root, organizer panel under /panel.

/** Sections of the public site; the fixture has two views: results (/fixture) and standings (/posiciones) */
export type PublicSection = 'inicio' | 'fixture' | 'posiciones' | 'goleadores' | 'sancionados' | 'definiciones' | 'historia'

/**
 * Public section of an edition (none → the current edition); the fixture and standings also take the group:
 * /fixture/2026-clausura/categoria-a, /posiciones/2026-apertura/zona-2, /goleadores/2026-clausura, /historia
 */
export function publicPath(section: PublicSection, edition?: Tournament | null, group?: { categories: Category[]; zoneId: number | null }) {
  if (section === 'inicio') return '/'
  if (section === 'historia') return '/historia'
  const parts: string[] = [section]
  if (edition) parts.push(editionSlug(edition))
  if (edition && group && (section === 'fixture' || section === 'posiciones')) parts.push(...groupSegments(group.categories, group.zoneId))
  return `/${parts.join('/')}`
}

/** Public page of a team: /equipos/41-gulp */
export const teamPath = (team: Pick<TeamRef, 'id' | 'name'>) => `/equipos/${teamSlug(team)}`

/** Pages of an edition in the panel: /panel/ediciones/2026-clausura[/clasificacion|fixture|rankings|definiciones|equipos] */
export type PanelEditionPage = 'inicio' | 'clasificacion' | 'fixture' | 'rankings' | 'definiciones' | 'equipos'
export function panelEditionPath(edition: Pick<Tournament, 'id' | 'seasonYear' | 'stage'>, page: PanelEditionPage = 'inicio') {
  const base = `/panel/ediciones/${editionSlug(edition)}`
  return page === 'inicio' ? base : `${base}/${page}`
}

export const PANEL = {
  editions: '/panel/ediciones',
  teams: '/panel/equipos',
  excel: '/panel/planilla',
  history: '/panel/historia',
  requests: '/panel/solicitudes',
  season: (year: number) => `/panel/temporadas/${year}`,
}

export const AUTH = { login: '/ingresar', register: '/registrarse', invite: (token: string) => `/invitacion/${token}` }

/**
 * Where to go after logging in: only a path of this app. "//evil.com" or "/\\evil.com" would leave the site
 * (protocol-relative URLs), so they are rejected.
 */
export function safeRedirect(value: unknown, fallback: string): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return fallback
  return value
}
