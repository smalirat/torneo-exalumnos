<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { leagueApi } from '../api/endpoints'
import { isAdmin, isLoggedIn } from '../auth'
import { t } from '../i18n'

// Public pages (no sidebar, phone first): one navy top bar with the school logo + league name on the left,
// the page's navigation (#nav slot, e.g. the home tabs) next to it and the login / panel link on the right.
// The #hero slot is a full-width band right under the bar.
const route = useRoute()
const leagueName = ref('')
onMounted(async () => {
  try {
    leagueName.value = (await leagueApi.info()).title
  } catch {
    leagueName.value = ''
  }
})
/** "TORNEO DE EXALUMNOS - COLEGIO MARIANISTA" → "TORNEO DE EXALUMNOS": the logo already says the school */
const shortName = computed(() => (leagueName.value || t('publicSite.brand')).split(' - ')[0])
</script>

<template>
  <div class="public">
    <header class="bar">
      <RouterLink to="/" class="brand" :title="leagueName">
        <img src="/images/institucional/logo-marianista.png" :alt="leagueName || t('publicSite.brand')" class="logo">
        <span class="name">{{ shortName }}</span>
      </RouterLink>
      <div class="nav"><slot name="nav" /></div>
      <RouterLink v-if="isLoggedIn" :to="isAdmin ? '/ediciones' : '/equipos'" class="link" :aria-label="t('publicSite.panel')">
        <AppIcon name="user" :size="18" /> <span class="link-text">{{ t('publicSite.panel') }}</span>
      </RouterLink>
      <RouterLink v-else :to="{ path: '/login', query: { redirect: route.fullPath } }" class="link" :aria-label="t('nav.login')">
        <AppIcon name="login" :size="18" /> <span class="link-text">{{ t('nav.login') }}</span>
      </RouterLink>
    </header>
    <slot name="hero" />
    <main class="content"><slot /></main>
  </div>
</template>

<style scoped>
.public { min-height: 100vh; background: var(--bg); }
.bar {
  display: flex; align-items: stretch; gap: 20px; min-height: 60px;
  padding: 0 max(16px, calc((100% - 1100px) / 2)); background: var(--navy-dark); color: #fff;
}
.brand { display: inline-flex; align-items: center; gap: 12px; min-width: 0; flex-shrink: 0; padding: 8px 0; }
.logo { height: 40px; width: auto; display: block; }
.name {
  font-family: var(--condensed); font-size: 18px; text-transform: uppercase; line-height: 1.05; max-width: 130px;
  padding-left: 12px; border-left: 1px solid rgba(255, 255, 255, 0.3);
}
.nav { flex: 1; min-width: 0; display: flex; align-items: stretch; }
.link { display: inline-flex; align-items: center; gap: 6px; font-weight: 500; font-size: 14px; white-space: nowrap; flex-shrink: 0; }
.content { max-width: 1100px; margin: 0 auto; padding: 20px 16px 48px; }
/* Narrow screens: logo + link on the first row, the navigation on its own row below */
@media (max-width: 1100px) {
  .bar { flex-wrap: wrap; row-gap: 0; justify-content: space-between; }
  .nav { order: 3; flex-basis: 100%; margin: 0 -8px; }
  .nav:empty { display: none; }
}
/* Phones: the panel / login link keeps only its icon so it fits next to the logo */
@media (max-width: 480px) {
  .bar { gap: 12px; }
  .link-text { display: none; }
  .link { padding: 0 4px; }
}
@media (max-width: 380px) {
  .logo { height: 34px; }
  .name { font-size: 15px; max-width: 110px; }
}
</style>
