<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { leagueApi } from '../api/endpoints'
import { isAdmin, isLoggedIn } from '../auth'
import { t } from '../i18n'

// Public pages (no sidebar, phone first): navy top bar with the league name, centered content.
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
</script>

<template>
  <div class="public">
    <header class="bar">
      <RouterLink to="/" class="brand"><AppIcon name="trophy" :size="24" /> <span>{{ leagueName || t('publicSite.brand') }}</span></RouterLink>
      <RouterLink v-if="isLoggedIn" :to="isAdmin ? '/ediciones' : '/equipos'" class="link">
        <AppIcon name="user" :size="18" /> {{ t('publicSite.panel') }}
      </RouterLink>
      <RouterLink v-else :to="{ path: '/login', query: { redirect: route.fullPath } }" class="link">
        <AppIcon name="login" :size="18" /> {{ t('nav.login') }}
      </RouterLink>
    </header>
    <slot name="hero" />
    <main class="content"><slot /></main>
  </div>
</template>

<style scoped>
.public { min-height: 100vh; background: var(--bg); }
.bar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 12px max(16px, calc((100% - 1100px) / 2)); background: var(--navy-dark); color: #fff;
}
.brand {
  display: inline-flex; align-items: center; gap: 8px; min-width: 0;
  font-family: var(--condensed); font-size: 20px; text-transform: uppercase;
}
.brand span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link { display: inline-flex; align-items: center; gap: 6px; font-weight: 500; font-size: 14px; white-space: nowrap; }
.content { max-width: 1100px; margin: 0 auto; padding: 20px 16px 48px; }
</style>
