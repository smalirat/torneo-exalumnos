<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import { meApi } from '../api/endpoints'
import type { AppNotification, Notifications } from '../api/types'
import { refreshUnread } from '../notifications'
import { t } from '../i18n'

// News of the followed tournaments. Opening the page marks everything as read (the list keeps the highlight).
const data = ref<Notifications | null>(null)

onMounted(async () => {
  data.value = await meApi.notifications()
  if (data.value.unread > 0) {
    await meApi.markSeen()
    await refreshUnread()
  }
})

const ICONS = { FIXTURE: 'calendar', RESULT: 'chart', IMPORT: 'table' } as const
const icon = (type: AppNotification['type']) => ICONS[type]
const formatDate = (d: string) => new Date(d).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' })
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('notifications.title') }}</h1>
    <div v-if="data && data.items.length === 0" class="card empty">
      <AppIcon name="bell" :size="48" />
      <p class="muted">{{ t('notifications.empty') }}</p>
      <RouterLink to="/" class="btn secondary">{{ t('notifications.findTournaments') }}</RouterLink>
    </div>
    <ul v-else-if="data" class="card list">
      <li v-for="n in data.items" :key="n.id" :class="{ unread: n.unread }">
        <span class="icon"><AppIcon :name="icon(n.type)" :size="20" /></span>
        <div class="body">
          <RouterLink :to="`/ver/${n.tournamentId}`" class="tournament">{{ n.tournamentName }}</RouterLink>
          <p>{{ n.message }}</p>
          <span class="muted small">{{ formatDate(n.createdAt) }}</span>
        </div>
      </li>
    </ul>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: var(--navy); text-align: center; }
.list { list-style: none; margin: 0; padding: 8px 16px; }
.list li { display: flex; gap: 14px; padding: 12px 0; border-bottom: 1px solid var(--border); }
.list li:last-child { border-bottom: 0; }
.list li.unread .body p { font-weight: 700; }
.icon {
  width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%; background: var(--bg); color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center;
}
.unread .icon { background: var(--navy); color: #fff; }
.body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.body p { margin: 0; overflow-wrap: anywhere; }
.tournament { font-size: 13px; font-weight: 500; color: var(--navy); }
.small { font-size: 12px; }
</style>
