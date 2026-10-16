<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PublicLayout from '../layouts/PublicLayout.vue'
import FollowButton from '../components/FollowButton.vue'
import MatchCard from '../components/MatchCard.vue'
import PlayerRankingTable from '../components/PlayerRankingTable.vue'
import RoundNav from '../components/RoundNav.vue'
import ShareButton from '../components/ShareButton.vue'
import StandingsTable from '../components/StandingsTable.vue'
import ZonePicker from '../components/ZonePicker.vue'
import { playoffApi, seasonApi, tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, Match, ScorerRow, SeasonStatRow, StandingRow, Tournament } from '../api/types'
import { groupRounds, isDone } from '../rounds'
import { t } from '../i18n'

// Public page of a tournament (no login): Resultados / Posiciones / Próximos / Goleadores / Definiciones.
// Category, zone and tab live in the URL so a shared link opens exactly the same view.
const route = useRoute()
const router = useRouter()
const tournamentId = computed(() => Number(route.params.id))
const tournament = ref<Tournament | null>(null)
const categories = ref<Category[] | null>(null)
const zoneId = ref<number | null>(null)
const matches = ref<Match[]>([])
const standings = ref<StandingRow[]>([])
const specials = ref<Match[]>([])
const scorers = ref<SeasonStatRow[]>([])
const figures = ref<SeasonStatRow[]>([])
const notFound = ref(false)

const TABS = ['resultados', 'posiciones', 'proximos', 'goleadores', 'definiciones'] as const
type Tab = (typeof TABS)[number]
const tabs = computed(() => TABS.filter((k) => k !== 'definiciones' || specials.value.length > 0))
const tab = computed<Tab>({
  get: () => (TABS as readonly string[]).includes(route.query.tab as string) ? route.query.tab as Tab : 'resultados',
  set: (value) => router.replace({ query: { ...route.query, tab: value } }),
})

const played = computed(() => groupRounds(matches.value.filter(isDone)))
const upcoming = computed(() => groupRounds(matches.value.filter((m) => !isDone(m))))
const playedIndex = ref(0)
const upcomingIndex = ref(0)
const publicUrl = computed(() => `${window.location.origin}${route.fullPath}`)

watch(tournamentId, async (id) => {
  const all = await tournamentApi.list()
  tournament.value = all.find((x) => x.id === id) ?? null
  notFound.value = tournament.value === null
  if (!tournament.value) return
  const seasonId = tournament.value.seasonId
  const [cats, special, goals, figs] = await Promise.all([
    tournamentApi.categories(id),
    playoffApi.list(id),
    seasonId ? seasonApi.scorers(seasonId) : tournamentApi.scorers(id).then(rankScorers),
    seasonId ? seasonApi.figures(seasonId) : Promise.resolve([]),
  ])
  categories.value = cats
  specials.value = special
  scorers.value = goals
  figures.value = figs
}, { immediate: true })

watch(zoneId, async (z) => {
  if (!z) {
    matches.value = []
    standings.value = []
    return
  }
  ;[matches.value, standings.value] = await Promise.all([zoneApi.fixtures(z), zoneApi.standings(z)])
  playedIndex.value = Math.max(played.value.length - 1, 0) // latest round played
  upcomingIndex.value = 0 // next round to play
})

/** Tournament scorers (from match events) in the same shape as the annual rankings, with shared positions. */
function rankScorers(rows: ScorerRow[]): SeasonStatRow[] {
  const out: SeasonStatRow[] = []
  rows.forEach((r, i) => {
    const tied = i > 0 && rows[i - 1].goals === r.goals
    out.push({ position: tied ? out[i - 1].position : i + 1, player: r.player, team: r.team, value: r.goals })
  })
  return out
}

const specialGroups = computed(() => (['PROMOTION', 'FINAL'] as const)
  .map((type) => ({ type, matches: specials.value.filter((m) => m.type === type) }))
  .filter((g) => g.matches.length > 0))
</script>

<template>
  <PublicLayout>
    <template #hero>
      <section class="hero">
        <div class="hero-inner">
          <p v-if="tournament?.seasonYear" class="kicker">{{ t('season.title', { year: tournament.seasonYear }) }}</p>
          <h1>{{ tournament?.name ?? '' }}</h1>
          <div v-if="tournament" class="hero-actions">
            <span class="status">{{ t(`tournaments.statuses.${tournament.status}`) }}</span>
            <ShareButton :url="publicUrl" :title="tournament.name" tone="light" />
            <FollowButton :tournament-id="tournament.id" tone="light" />
          </div>
        </div>
        <nav v-if="tournament" class="tabs" role="tablist">
          <button
            v-for="key in tabs" :key="key" type="button" role="tab" :aria-selected="tab === key"
            :class="['tab', { active: tab === key }]" @click="tab = key"
          >{{ t(`publicSite.tabs.${key}`) }}</button>
        </nav>
      </section>
    </template>

    <p v-if="notFound" class="card muted">{{ t('publicSite.notFound') }}</p>
    <template v-else-if="tournament">
      <p v-if="categories && categories.length === 0" class="card muted">{{ t('publicSite.noCategories') }}</p>

      <section v-if="['resultados', 'posiciones', 'proximos'].includes(tab) && categories?.length" class="card">
        <ZonePicker v-model="zoneId" :categories="categories" />

        <template v-if="tab === 'resultados'">
          <p v-if="played.length === 0" class="muted empty">{{ t('publicSite.noResults') }}</p>
          <template v-else>
            <RoundNav v-model="playedIndex" :rounds="played" tone="public" />
            <MatchCard v-for="m in played[playedIndex]?.matches ?? []" :key="m.id" :match="m" date-format="time" />
          </template>
        </template>

        <template v-else-if="tab === 'posiciones'">
          <h2 class="section-title">{{ t('publicSite.tabs.posiciones') }}</h2>
          <StandingsTable :rows="standings" />
          <p class="muted small">{{ t('publicSite.standingsHelp') }}</p>
        </template>

        <template v-else>
          <p v-if="upcoming.length === 0" class="muted empty">{{ t('publicSite.noUpcoming') }}</p>
          <template v-else>
            <RoundNav v-model="upcomingIndex" :rounds="upcoming" tone="public" />
            <MatchCard v-for="m in upcoming[upcomingIndex]?.matches ?? []" :key="m.id" :match="m" date-format="time" />
          </template>
        </template>
      </section>

      <section v-else-if="tab === 'goleadores'" class="rankings">
        <div class="card">
          <h2 class="section-title">{{ t('publicSite.scorers') }}</h2>
          <PlayerRankingTable :rows="scorers.slice(0, 20)" :value-label="t('season.goals')" />
        </div>
        <div v-if="figures.length" class="card">
          <h2 class="section-title">{{ t('publicSite.figures') }}</h2>
          <PlayerRankingTable :rows="figures.slice(0, 20)" :value-label="t('season.times')" />
        </div>
        <p v-if="tournament.seasonYear" class="muted small">{{ t('publicSite.annualHelp', { year: tournament.seasonYear }) }}</p>
      </section>

      <section v-else-if="tab === 'definiciones'" class="card">
        <template v-for="g in specialGroups" :key="g.type">
          <h2 class="section-title">{{ t(`publicSite.special.${g.type}`) }}</h2>
          <MatchCard v-for="m in g.matches" :key="m.id" :match="m" />
        </template>
      </section>
    </template>
  </PublicLayout>
</template>

<style scoped>
.hero { background: var(--public); color: #fff; }
.hero-inner { padding: 24px max(16px, calc((100% - 960px) / 2)) 16px; }
.kicker { margin: 0 0 4px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.08em; opacity: 0.85; }
.hero h1 { font-family: var(--condensed); text-transform: uppercase; font-size: clamp(28px, 7vw, 44px); line-height: 1.05; }
.hero-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
.status { font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); }
.tabs {
  display: flex; gap: 4px; overflow-x: auto; scrollbar-width: none;
  padding: 0 max(8px, calc((100% - 960px) / 2)); background: var(--public-dark);
}
.tab {
  flex-shrink: 0; background: none; border: 0; border-bottom: 3px solid transparent; color: rgba(255, 255, 255, 0.8);
  font-family: var(--condensed); font-size: 17px; text-transform: uppercase; padding: 12px 12px 9px; cursor: pointer;
}
.tab.active { color: #fff; border-bottom-color: #fff; }
.section-title { font-family: var(--condensed); text-transform: uppercase; color: var(--public); font-size: 20px; margin: 16px 0 8px; }
.empty { margin-top: 16px; }
.small { font-size: 13px; margin-top: 10px; }
.rankings { display: flex; flex-direction: column; gap: 16px; }
.rankings .section-title { margin-top: 0; }
:deep(.pos) { background: var(--public) !important; }
</style>
