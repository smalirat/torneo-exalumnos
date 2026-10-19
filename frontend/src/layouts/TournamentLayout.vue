<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import UserFooter from './UserFooter.vue'
import type { Tournament } from '../api/types'
import { editionBySlug, loadEditions } from '../editions'
import { PANEL, panelEditionPath, publicPath, type PanelEditionPage } from '../paths'
import { t } from '../i18n'
import { isAdmin } from '../auth'

// Sidebar + breadcrumb shared by every page inside a tournament
const route = useRoute()
const tournament = ref<Tournament | null>(null)

// The router guard loaded the editions for this slug; refreshed so an edited name shows up
watch(() => route.params.edicion, async (slug) => {
  await loadEditions(true)
  tournament.value = editionBySlug(slug)
}, { immediate: true })

/** Link to a page of this edition in the panel */
const page = (p: PanelEditionPage) => (tournament.value ? panelEditionPath(tournament.value, p) : PANEL.editions)

const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '')
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="logo"><AppIcon name="trophy" :size="26" /></span>
        <span class="name">{{ tournament?.name }}</span>
      </div>
      <RouterLink :to="page('inicio')" class="item" exact-active-class="active">
        <AppIcon name="home" /> {{ t('nav.home') }}
      </RouterLink>
      <RouterLink :to="page('clasificacion')" class="item" active-class="active">
        <AppIcon name="chart" /> {{ t('nav.standings') }}
      </RouterLink>
      <RouterLink :to="page('fixture')" class="item" active-class="active">
        <AppIcon name="calendar" /> {{ t('nav.fixture') }}
      </RouterLink>
      <RouterLink :to="page('rankings')" class="item" active-class="active">
        <AppIcon name="star" /> {{ t('nav.rankings') }}
      </RouterLink>
      <RouterLink :to="page('definiciones')" class="item" active-class="active">
        <AppIcon name="flag" /> {{ t('nav.playoffs') }}
      </RouterLink>
      <RouterLink v-if="isAdmin" :to="page('equipos')" class="item" active-class="active">
        <AppIcon name="shield" /> {{ t('nav.tournamentTeams') }}
      </RouterLink>
      <UserFooter />
    </aside>
    <main class="content">
      <header class="top">
        <nav class="crumbs">
          <RouterLink :to="PANEL.editions">{{ t('nav.myTournaments') }}</RouterLink>
          <span>/</span>
          <RouterLink :to="page('inicio')">{{ tournament?.name }}</RouterLink>
          <span>/</span>
          <strong>{{ t(pageTitle) }}</strong>
        </nav>
        <div class="actions">
          <RouterLink :to="tournament ? publicPath('fixture', tournament) : '/'" class="public-link"><AppIcon name="eye" :size="18" /> {{ t('nav.publicPage') }}</RouterLink>
        </div>
      </header>
      <slot :tournament="tournament" />
    </main>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; }
.sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--navy);
  color: #fff;
  padding: 24px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-radius: 0 20px 20px 0;
}
.brand { display: flex; align-items: center; gap: 12px; margin: 0 12px 28px; }
.logo {
  width: 46px; height: 46px; background: #fff; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center;
}
.name { font-size: 26px; font-weight: 500; overflow-wrap: anywhere; }
.item {
  display: flex; align-items: center; gap: 14px;
  padding: 16px 20px; border-radius: 10px; font-size: 16px; font-weight: 500;
}
.item:hover { background: rgba(255, 255, 255, 0.08); }
.item.active { background: #fff; color: var(--navy); }
.content { flex: 1; padding: 20px 36px; min-width: 0; }
.top { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 20px; }
.crumbs { display: flex; gap: 10px; font-size: 13px; color: var(--muted); flex-wrap: wrap; }
.actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.public-link { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 500; color: var(--navy); margin-right: 4px; }
.crumbs strong { color: var(--text); font-weight: 500; }
</style>
