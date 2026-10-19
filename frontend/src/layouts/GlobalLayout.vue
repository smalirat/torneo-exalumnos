<script setup lang="ts">
import { watch } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import UserFooter from './UserFooter.vue'
import { PANEL } from '../paths'
import { t } from '../i18n'
import { isAdmin, isLoggedIn } from '../auth'
import { pendingRequests, refreshPendingRequests } from '../requests'

watch(isAdmin, (admin) => { if (admin) refreshPendingRequests() }, { immediate: true })
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <p class="section">{{ t('nav.organize') }}</p>
      <RouterLink to="/" class="item" exact-active-class="active">
        <AppIcon name="home" /> {{ t('nav.leagueHome') }}
      </RouterLink>
      <RouterLink v-if="isLoggedIn" :to="PANEL.editions" class="item" active-class="active">
        <AppIcon name="trophy" /> {{ t('nav.myTournaments') }}
      </RouterLink>
      <RouterLink v-if="isLoggedIn" :to="PANEL.teams" class="item" active-class="active">
        <AppIcon name="shield" /> {{ t('nav.teams') }}
      </RouterLink>
      <RouterLink v-if="isAdmin" :to="PANEL.excel" class="item" active-class="active">
        <AppIcon name="table" /> {{ t('nav.excel') }}
      </RouterLink>
      <RouterLink v-if="isAdmin" :to="PANEL.requests" class="item" active-class="active">
        <AppIcon name="check" /> {{ t('nav.requests') }}
        <span v-if="pendingRequests" class="badge" :aria-label="t('requests.pendingCount', { n: pendingRequests })">{{ pendingRequests }}</span>
      </RouterLink>
      <RouterLink :to="PANEL.history" class="item" active-class="active">
        <AppIcon name="star" /> {{ t('nav.history') }}
      </RouterLink>
      <UserFooter />
    </aside>
    <main class="content"><slot /></main>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; }
.sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--navy);
  color: #fff;
  padding: 20px 12px;
  display: flex;
  flex-direction: column;
  border-radius: 0 20px 20px 0;
}
.section { font-size: 12px; font-weight: 700; opacity: 0.8; margin: 4px 12px 8px; }
.item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  font-weight: 500;
  opacity: 0.9;
}
.item:hover { background: rgba(255, 255, 255, 0.08); }
.item.active { background: rgba(255, 255, 255, 0.15); opacity: 1; }
.badge {
  margin-left: auto; min-width: 22px; height: 22px; padding: 0 7px; border-radius: 999px; background: #f6c400;
  color: #3d3000; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center;
}
.content { flex: 1; padding: 28px 36px; min-width: 0; }
</style>
