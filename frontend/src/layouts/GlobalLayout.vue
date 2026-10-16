<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
import UserFooter from './UserFooter.vue'
import { t } from '../i18n'
import { isAdmin, isLoggedIn } from '../auth'
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <p class="section">{{ t('nav.organize') }}</p>
      <RouterLink to="/" class="item" exact-active-class="active">
        <AppIcon name="home" /> {{ t('nav.leagueHome') }}
      </RouterLink>
      <RouterLink v-if="isLoggedIn" to="/ediciones" class="item" active-class="active">
        <AppIcon name="trophy" /> {{ t('nav.myTournaments') }}
      </RouterLink>
      <RouterLink v-if="isLoggedIn" to="/equipos" class="item" active-class="active">
        <AppIcon name="shield" /> {{ t('nav.teams') }}
      </RouterLink>
      <RouterLink v-if="isAdmin" to="/planilla" class="item" active-class="active">
        <AppIcon name="table" /> {{ t('nav.excel') }}
      </RouterLink>
      <RouterLink to="/historia" class="item" active-class="active">
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
.content { flex: 1; padding: 28px 36px; min-width: 0; }
</style>
