<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import { tournamentApi } from '../api/endpoints'
import type { Tournament } from '../api/types'
import { t } from '../i18n'

// Public entry point: every tournament, grouped by season (newest first)
const tournaments = ref<Tournament[] | null>(null)
onMounted(async () => { tournaments.value = await tournamentApi.list() })

const groups = computed(() => {
  const bySeason = new Map<number | null, Tournament[]>()
  for (const tour of tournaments.value ?? []) {
    const list = bySeason.get(tour.seasonYear) ?? []
    list.push(tour)
    bySeason.set(tour.seasonYear, list)
  }
  return [...bySeason.entries()].sort(([a], [b]) => (b ?? 0) - (a ?? 0))
})
</script>

<template>
  <PublicLayout>
    <template #hero>
      <section class="hero"><h1>{{ t('publicSite.title') }}</h1></section>
    </template>
    <p v-if="tournaments && tournaments.length === 0" class="card muted">{{ t('publicSite.empty') }}</p>
    <section v-for="[year, list] in groups" :key="year ?? 'none'" class="season">
      <h2>{{ year ? t('season.title', { year }) : t('publicSite.otherTournaments') }}</h2>
      <div class="grid">
        <RouterLink v-for="tour in list" :key="tour.id" :to="`/ver/${tour.id}`" class="card tournament">
          <span class="icon"><AppIcon name="trophy" :size="26" /></span>
          <span class="name">{{ tour.name }}</span>
          <span :class="['status', tour.status]">{{ t(`tournaments.statuses.${tour.status}`) }}</span>
        </RouterLink>
      </div>
    </section>
  </PublicLayout>
</template>

<style scoped>
.hero { background: var(--public); color: #fff; padding: 28px max(16px, calc((100% - 960px) / 2)); }
.hero h1 { font-family: var(--condensed); text-transform: uppercase; font-size: 34px; }
.season { margin-bottom: 24px; }
.season h2 { font-family: var(--condensed); text-transform: uppercase; color: var(--public); font-size: 22px; margin-bottom: 10px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.tournament { display: flex; align-items: center; gap: 12px; }
.tournament:hover { box-shadow: 0 2px 8px rgba(139, 15, 8, 0.18); }
.icon {
  width: 44px; height: 44px; flex-shrink: 0; border-radius: 50%; background: var(--public); color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
}
.name { flex: 1; font-weight: 500; font-size: 17px; }
.status { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--bg); color: var(--muted); }
.status.LIVE { background: var(--public); color: #fff; }
</style>
