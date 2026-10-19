import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./views/LeagueHomeView.vue') },
    { path: '/ediciones', component: () => import('./views/TournamentsView.vue') },
    { path: '/equipos', component: () => import('./views/TeamsView.vue') },
    { path: '/planilla', component: () => import('./views/ExcelView.vue') },
    { path: '/historia', component: () => import('./views/HistoryView.vue') },
    { path: '/solicitudes', component: () => import('./views/RequestsView.vue') },
    { path: '/ver', redirect: '/' },
    { path: '/ver/:id', component: () => import('./views/LeagueHomeView.vue') },
    { path: '/temporadas/:id', component: () => import('./views/SeasonView.vue') },
    { path: '/equipos/:teamId', component: () => import('./views/TeamView.vue') },
    { path: '/login', component: () => import('./views/LoginView.vue'), props: { mode: 'login' } },
    { path: '/registro', component: () => import('./views/LoginView.vue'), props: { mode: 'register' } },
    { path: '/invitacion/:token', component: () => import('./views/InviteView.vue') },
    {
      path: '/campeonatos/:id',
      component: () => import('./views/TournamentHomeView.vue'),
      meta: { title: 'nav.home' },
    },
    {
      path: '/campeonatos/:id/fixture',
      component: () => import('./views/FixtureView.vue'),
      meta: { title: 'nav.fixture' },
    },
    {
      path: '/campeonatos/:id/rankings',
      component: () => import('./views/RankingsView.vue'),
      meta: { title: 'nav.rankings' },
    },
    {
      path: '/campeonatos/:id/definiciones',
      component: () => import('./views/PlayoffsView.vue'),
      meta: { title: 'nav.playoffs' },
    },
    {
      path: '/campeonatos/:id/equipos',
      component: () => import('./views/TournamentTeamsView.vue'),
      meta: { title: 'nav.tournamentTeams' },
    },
    {
      path: '/campeonatos/:id/clasificacion',
      component: () => import('./views/StandingsView.vue'),
      meta: { title: 'nav.standings' },
    },
  ],
})
