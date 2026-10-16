<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { isLoggedIn } from '../auth'
import { t } from '../i18n'

// Public pages (no sidebar, phone first): maroon top bar, centered content.
// The #hero slot is a full-width band right under the bar.
const route = useRoute()
</script>

<template>
  <div class="public">
    <header class="bar">
      <RouterLink to="/ver" class="brand"><AppIcon name="trophy" :size="24" /> {{ t('publicSite.brand') }}</RouterLink>
      <RouterLink v-if="isLoggedIn" to="/" class="link">{{ t('publicSite.panel') }}</RouterLink>
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
  padding: 12px max(16px, calc((100% - 960px) / 2)); background: var(--public-dark); color: #fff;
}
.brand { display: inline-flex; align-items: center; gap: 8px; font-family: var(--condensed); font-size: 22px; text-transform: uppercase; }
.link { display: inline-flex; align-items: center; gap: 6px; font-weight: 500; font-size: 14px; }
.content { max-width: 960px; margin: 0 auto; padding: 20px 16px 40px; }
</style>
