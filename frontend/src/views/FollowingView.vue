<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import FollowButton from '../components/FollowButton.vue'
import { meApi } from '../api/endpoints'
import type { Tournament } from '../api/types'
import { t } from '../i18n'

// "Campeonatos que sigo": followed tournaments with a link to their public page
const tournaments = ref<Tournament[] | null>(null)
onMounted(async () => { tournaments.value = await meApi.follows() })
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('follow.title') }}</h1>
    <div v-if="tournaments && tournaments.length === 0" class="card empty">
      <AppIcon name="bell" :size="48" />
      <p class="muted">{{ t('follow.empty') }}</p>
    </div>
    <div class="list">
      <article v-for="tour in tournaments ?? []" :key="tour.id" class="card item">
        <RouterLink :to="`/ver/${tour.id}`" class="name">
          <AppIcon name="trophy" /> {{ tour.name }}
        </RouterLink>
        <span v-if="tour.seasonYear" class="muted small">{{ t('season.title', { year: tour.seasonYear }) }}</span>
        <FollowButton :tournament-id="tour.id" />
      </article>
    </div>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: var(--navy); text-align: center; }
.list { display: flex; flex-direction: column; gap: 12px; }
.item { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.name { display: inline-flex; align-items: center; gap: 10px; font-size: 18px; font-weight: 500; color: var(--navy); flex: 1; }
.small { font-size: 13px; }
</style>
