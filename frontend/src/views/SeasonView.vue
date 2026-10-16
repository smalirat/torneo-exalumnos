<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import PlayerRankingTable from '../components/PlayerRankingTable.vue'
import TabsNav from '../components/TabsNav.vue'
import SanctionsTable from '../components/SanctionsTable.vue'
import { seasonApi } from '../api/endpoints'
import type { Sanction, Season, SeasonStatRow } from '../api/types'
import { splitSanctions } from '../sanctions'
import { t } from '../i18n'

// Annual data of a season: scorers, figures, goalkeepers (totals, e.g. from the Excel) and sanctions
const route = useRoute()
const seasonId = computed(() => Number(route.params.id))
const season = ref<Season | null>(null)
const tab = ref('scorers')
const tabs = [
  { key: 'scorers', label: t('season.scorers') },
  { key: 'figures', label: t('season.figures') },
  { key: 'goalkeepers', label: t('season.goalkeepers') },
  { key: 'sanctions', label: t('season.sanctions') },
]
const rankings = ref<Record<string, SeasonStatRow[]>>({ scorers: [], figures: [], goalkeepers: [] })
const sanctions = ref<Sanction[]>([])
const split = computed(() => splitSanctions(sanctions.value))

watch(seasonId, async (id) => {
  const [seasons, scorers, figures, goalkeepers, sanc] = await Promise.all([
    seasonApi.list(), seasonApi.scorers(id), seasonApi.figures(id), seasonApi.goalkeepers(id), seasonApi.sanctions(id)])
  season.value = seasons.find((s) => s.id === id) ?? null
  rankings.value = { scorers, figures, goalkeepers }
  sanctions.value = sanc
}, { immediate: true })

const valueLabel = computed(() => ({
  scorers: t('season.goals'), figures: t('season.times'), goalkeepers: t('season.conceded'),
} as Record<string, string>)[tab.value])
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ season ? t('season.title', { year: season.year }) : '' }}</h1>
    <div v-if="season" class="tournaments">
      <RouterLink v-for="tour in season.tournaments" :key="tour.id" :to="`/campeonatos/${tour.id}`" class="chip">
        {{ tour.name }}
      </RouterLink>
    </div>
    <section class="card">
      <TabsNav v-model="tab" :tabs="tabs" />
      <PlayerRankingTable v-if="tab !== 'sanctions'" :rows="rankings[tab] ?? []" :value-label="valueLabel" />
      <template v-else>
        <h3 class="sub">{{ t('season.openSanctions') }}</h3>
        <SanctionsTable :rows="split.open" />
        <h3 class="sub">{{ t('season.servedSanctions') }}</h3>
        <SanctionsTable :rows="split.served" served />
      </template>
    </section>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 12px; }
.tournaments { display: flex; gap: 8px; margin-bottom: 20px; flex-wrap: wrap; }
.sub { font-size: 15px; color: var(--navy); margin: 12px 0 4px; }
.chip { font-size: 13px; padding: 4px 12px; border-radius: 999px; background: #fff; color: var(--navy); font-weight: 500; box-shadow: var(--shadow); }
</style>
