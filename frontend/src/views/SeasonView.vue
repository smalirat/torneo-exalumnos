<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import PlayerRankingTable from '../components/PlayerRankingTable.vue'
import TabsNav from '../components/TabsNav.vue'
import SanctionsTable from '../components/SanctionsTable.vue'
import { seasonApi } from '../api/endpoints'
import type { Sanction, Season, SeasonStatRow } from '../api/types'
import { isAdmin } from '../auth'
import { splitSanctions } from '../sanctions'
import { panelEditionPath } from '../paths'
import { t } from '../i18n'

// Annual data of a season: scorers, figures, goalkeepers (totals, e.g. from the Excel) and sanctions
const route = useRoute()
/** /panel/temporadas/2026: the season of that year */
const year = computed(() => Number(route.params.anio))
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

watch(year, async (y) => {
  season.value = (await seasonApi.list()).find((s) => s.year === y) ?? null
  const id = season.value?.id
  if (!id) return
  const [scorers, figures, goalkeepers, sanc] = await Promise.all([
    seasonApi.scorers(id), seasonApi.figures(id), seasonApi.goalkeepers(id), seasonApi.sanctions(id)])
  rankings.value = { scorers, figures, goalkeepers }
  sanctions.value = sanc
}, { immediate: true })

// Admin: match days served (one at a time) or the total the tribunal decided; done when all are served
const sanctionError = ref('')
async function changeSanction(s: Sanction, matchesSuspended: number, matchesServed: number) {
  sanctionError.value = ''
  if (!Number.isInteger(matchesSuspended) || matchesSuspended < 1) return
  try {
    const updated = await seasonApi.updateSanction(s.id, {
      playerId: s.player.id, tournamentId: s.tournamentId, type: s.type, matchesSuspended,
      matchesServed: Math.min(matchesServed, matchesSuspended),
      status: s.status === 'TRIBUNAL' ? 'TRIBUNAL' : null, notes: s.notes,
    })
    sanctions.value = sanctions.value.map((x) => (x.id === s.id ? { ...updated, team: x.team, receivedWith: x.receivedWith } : x))
  } catch (e) {
    sanctionError.value = e instanceof Error ? e.message : String(e)
  }
}

const valueLabel = computed(() => ({
  scorers: t('season.goals'), figures: t('season.times'), goalkeepers: t('season.conceded'),
} as Record<string, string>)[tab.value])
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ season ? t('season.title', { year: season.year }) : '' }}</h1>
    <div v-if="season" class="tournaments">
      <RouterLink v-for="tour in season.tournaments" :key="tour.id" :to="panelEditionPath(tour)" class="chip">
        {{ tour.name }}
      </RouterLink>
    </div>
    <section class="card">
      <TabsNav v-model="tab" :tabs="tabs" />
      <PlayerRankingTable v-if="tab !== 'sanctions'" :rows="rankings[tab] ?? []" :value-label="valueLabel" />
      <template v-else>
        <h3 class="sub">{{ t('season.openSanctions') }}</h3>
        <p v-if="sanctionError" class="error">{{ sanctionError }}</p>
        <SanctionsTable :rows="split.open" :year="year" :admin="isAdmin" @change="changeSanction" />
        <h3 class="sub">{{ t('season.servedSanctions') }}</h3>
        <SanctionsTable :rows="split.served" served :year="year" />
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
