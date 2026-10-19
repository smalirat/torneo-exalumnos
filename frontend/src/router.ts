import { createRouter, createWebHistory, type RouteLocationRaw, type RouteRecordRaw } from 'vue-router'
import { seasonApi } from './api/endpoints'
import { findEdition, loadEditions } from './editions'
import { AUTH, PANEL, panelEditionPath, publicPath, type PanelEditionPage, type PublicSection } from './paths'

// URL scheme (see paths.ts): public site at the root, organizer panel under /panel, readable slugs.
const LeagueHome = () => import('./views/LeagueHomeView.vue')

/** Public sections: /fixture/2026-clausura/categoria-a, /goleadores/2026-clausura… (no edition → the current one) */
const publicSection = (section: PublicSection, withGroup = false): RouteRecordRaw => ({
  path: withGroup ? `/${section}/:edicion?/:grupo*` : `/${section}/:edicion?`,
  component: LeagueHome,
  meta: { section },
})

/** Pages of an edition in the panel: /panel/ediciones/2026-clausura[/page] */
const panelEdition = (page: PanelEditionPage, component: () => Promise<unknown>, title: string): RouteRecordRaw => ({
  path: page === 'inicio' ? '/panel/ediciones/:edicion' : `/panel/ediciones/:edicion/${page}`,
  component,
  meta: { title, panelPage: page },
})

// ---- old URLs (shared links keep working): resolved to the new ones
const LEGACY_TABS: Record<string, PublicSection> = {
  fixture: 'fixture', goleadores: 'goleadores', sancionados: 'sancionados', definiciones: 'definiciones', historia: 'historia',
}

async function legacyEdition(id: unknown) {
  return (await loadEditions()).find((e) => e.id === Number(id)) ?? null
}

const legacy: RouteRecordRaw[] = [
  { path: '/ver', redirect: '/' },
  {
    path: '/ver/:id',
    component: LeagueHome,
    beforeEnter: async (to): Promise<RouteLocationRaw> => {
      const edition = await legacyEdition(to.params.id)
      const tab = typeof to.query.tab === 'string' ? LEGACY_TABS[to.query.tab] : undefined
      if (!tab) return '/'
      const section = tab === 'fixture' && to.query.vista === 'posiciones' ? 'posiciones' : tab
      return publicPath(section, edition)
    },
  },
  {
    path: '/campeonatos/:id/:page(clasificacion|fixture|rankings|definiciones|equipos)?',
    component: LeagueHome,
    beforeEnter: async (to): Promise<RouteLocationRaw> => {
      const edition = await legacyEdition(to.params.id)
      return edition ? panelEditionPath(edition, (to.params.page as PanelEditionPage) || 'inicio') : PANEL.editions
    },
  },
  {
    path: '/temporadas/:id',
    component: LeagueHome,
    beforeEnter: async (to): Promise<RouteLocationRaw> => {
      const season = (await seasonApi.list()).find((s) => s.id === Number(to.params.id))
      return season ? PANEL.season(season.year) : PANEL.editions
    },
  },
  { path: '/ediciones', redirect: PANEL.editions },
  { path: '/planilla', redirect: PANEL.excel },
  { path: '/solicitudes', redirect: PANEL.requests },
  { path: '/equipos', redirect: PANEL.teams },
  { path: '/login', redirect: (to) => ({ path: AUTH.login, query: to.query }) },
  { path: '/registro', redirect: (to) => ({ path: AUTH.register, query: to.query }) },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    // ---- public site
    { path: '/', component: LeagueHome, meta: { section: 'inicio' } },
    publicSection('fixture', true),
    publicSection('posiciones', true),
    publicSection('goleadores'),
    publicSection('sancionados'),
    publicSection('definiciones'),
    { path: '/historia', component: LeagueHome, meta: { section: 'historia' } },
    { path: '/equipos/:equipo', component: () => import('./views/TeamView.vue') },

    // ---- account
    { path: AUTH.login, component: () => import('./views/LoginView.vue'), props: { mode: 'login' } },
    { path: AUTH.register, component: () => import('./views/LoginView.vue'), props: { mode: 'register' } },
    { path: '/invitacion/:token', component: () => import('./views/InviteView.vue') },

    // ---- organizer panel
    { path: '/panel', redirect: PANEL.editions },
    { path: PANEL.editions, component: () => import('./views/TournamentsView.vue') },
    panelEdition('inicio', () => import('./views/TournamentHomeView.vue'), 'nav.home'),
    panelEdition('clasificacion', () => import('./views/StandingsView.vue'), 'nav.standings'),
    panelEdition('fixture', () => import('./views/FixtureView.vue'), 'nav.fixture'),
    panelEdition('rankings', () => import('./views/RankingsView.vue'), 'nav.rankings'),
    panelEdition('definiciones', () => import('./views/PlayoffsView.vue'), 'nav.playoffs'),
    panelEdition('equipos', () => import('./views/TournamentTeamsView.vue'), 'nav.tournamentTeams'),
    { path: '/panel/temporadas/:anio(\\d+)', component: () => import('./views/SeasonView.vue') },
    { path: PANEL.teams, component: () => import('./views/TeamsView.vue') },
    { path: PANEL.excel, component: () => import('./views/ExcelView.vue') },
    { path: PANEL.history, component: () => import('./views/HistoryView.vue') },
    { path: PANEL.requests, component: () => import('./views/RequestsView.vue') },

    ...legacy,
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFoundView.vue') },
  ],
})

// Routes naming an edition by slug: make sure the editions are loaded so views can resolve it synchronously
router.beforeEach(async (to) => {
  if (typeof to.params.edicion === 'string' && to.params.edicion) await findEdition(to.params.edicion)
})
