<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import PlayerRankingTable from '../components/PlayerRankingTable.vue'
import TabsNav from '../components/TabsNav.vue'
import TeamBadge from '../components/TeamBadge.vue'
import { seasonApi } from '../api/endpoints'
import type { Sanction, Season, SeasonStatRow } from '../api/types'
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
        <p v-if="sanctions.length === 0" class="muted">{{ t('season.empty') }}</p>
        <div v-else class="scroll">
          <table>
            <thead>
              <tr>
                <th class="left">{{ t('rankings.player') }}</th>
                <th class="left">{{ t('rankings.team') }}</th>
                <th class="left">{{ t('season.type') }}</th>
                <th>{{ t('season.suspended') }}</th>
                <th>{{ t('season.served') }}</th>
                <th>{{ t('season.pending') }}</th>
                <th>{{ t('season.status') }}</th>
                <th class="left">{{ t('season.notes') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in sanctions" :key="s.id">
                <td class="left">{{ s.player.fullName }}</td>
                <td class="left"><span class="team"><TeamBadge :name="s.team.name" :size="22" /> {{ s.team.name }}</span></td>
                <td class="left">{{ t(`season.types.${s.type}`) }}</td>
                <td>{{ s.matchesSuspended }}</td>
                <td>{{ s.matchesServed }}</td>
                <td class="strong">{{ s.status === 'SERVED' ? '—' : s.pending }}</td>
                <td><span :class="['status', s.status]">{{ t(`season.statuses.${s.status}`) }}</span></td>
                <td class="left small">{{ s.notes }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 12px; }
.tournaments { display: flex; gap: 8px; margin-bottom: 20px; flex-wrap: wrap; }
.chip { font-size: 13px; padding: 4px 12px; border-radius: 999px; background: #fff; color: var(--navy); font-weight: 500; box-shadow: var(--shadow); }
.scroll { overflow-x: auto; }
table { width: 100%; border-collapse: separate; border-spacing: 0 4px; font-size: 14px; }
th { font-weight: 500; text-align: center; padding: 4px 6px; }
td { text-align: center; padding: 6px; }
.left { text-align: left; }
tbody tr:nth-child(even) td { background: var(--row-alt); }
.team { display: inline-flex; align-items: center; gap: 8px; }
.strong { font-weight: 700; }
.small { font-size: 12px; color: var(--muted); }
.status { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--bg); color: var(--navy); }
.status.PENDING { background: #f6d55c; color: #5a4500; }
.status.TRIBUNAL { background: #e05a4f; color: #fff; }
</style>
