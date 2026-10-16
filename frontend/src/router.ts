import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./views/TournamentsView.vue') },
    { path: '/equipos', component: () => import('./views/TeamsView.vue') },
    { path: '/login', component: () => import('./views/LoginView.vue'), props: { mode: 'login' } },
    { path: '/registro', component: () => import('./views/LoginView.vue'), props: { mode: 'register' } },
    {
      path: '/campeonatos/:id',
      component: () => import('./views/TournamentHomeView.vue'),
      meta: { title: 'nav.home' },
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
