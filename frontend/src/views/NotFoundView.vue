<script setup lang="ts">
import PublicLayout from '../layouts/PublicLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import { t } from '../i18n'
</script>

<template>
  <PublicLayout>
    <div class="card empty">
      <span class="empty-icon"><AppIcon name="flag" :size="34" /></span>
      <h1>{{ t('notFound.title') }}</h1>
      <p class="muted">{{ t('notFound.text') }}</p>
      <RouterLink to="/" class="btn">{{ t('notFound.home') }}</RouterLink>
    </div>
  </PublicLayout>
</template>

<style scoped>
.empty { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; padding: 40px 20px; }
.empty h1 { font-size: 22px; color: var(--navy); }
.empty-icon {
  width: 80px; height: 80px; border-radius: 50%; background: #dde2ec; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center;
}
</style>
