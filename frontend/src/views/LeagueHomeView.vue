<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PublicLayout from '../layouts/PublicLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import ChampionsCollage from '../components/ChampionsCollage.vue'
import ChampionsHistory from '../components/ChampionsHistory.vue'
import GroupNav from '../components/GroupNav.vue'
import LeagueTable from '../components/LeagueTable.vue'
import MatchCard from '../components/MatchCard.vue'
import MatchCarousel from '../components/MatchCarousel.vue'
import PlayerRankingTable from '../components/PlayerRankingTable.vue'
import RoundNav from '../components/RoundNav.vue'
import SanctionsTable from '../components/SanctionsTable.vue'
import ScoreCard from '../components/ScoreCard.vue'
import ShareButton from '../components/ShareButton.vue'
import YellowCardsByTeam from '../components/YellowCardsByTeam.vue'
import { championApi, collageApi, playoffApi, seasonApi, tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, CategoryHistory, CollagePhoto, Match, Sanction, ScorerRow, SeasonStatRow, StandingRow, Tournament, YellowTally } from '../api/types'
import { isAdmin, isLoggedIn } from '../auth'
import { currentEdition, sortEditions } from '../editions'
import { dayLabel, groupRounds, isDone } from '../rounds'
import { splitSanctions } from '../sanctions'
import { t } from '../i18n'

// Home of the league (no login needed). "/" shows the current edition; "/ver/:id" a given one.
// Inicio = Última fecha + Tabla de posiciones + Próximos partidos (league-site style, navy/white).
// Category, zone and tab live in the URL so a shared link opens exactly the same view.
const route = useRoute()
const router = useRouter()
const editions = ref<Tournament[] | null>(null)
const tournament = ref<Tournament | null>(null)
const categories = ref<Category[] | null>(null)
const zoneId = ref<number | null>(null)
const matches = ref<Match[]>([])
const standings = ref<StandingRow[]>([])
const specials = ref<Match[]>([])
const scorers = ref<SeasonStatRow[]>([])
const figures = ref<SeasonStatRow[]>([])
const sanctions = ref<Sanction[]>([])
const yellows = ref<YellowTally[]>([])
const split = computed(() => splitSanctions(sanctions.value))
// Champions of every year: league-wide, not tied to the edition
const history = ref<CategoryHistory[]>([])
const photos = ref<CollagePhoto[]>([])
championApi.history().then((h) => { history.value = h })
collageApi.list().then((p) => { photos.value = p })
const notFound = ref(false)
const fixtureIndex = ref(0)

const TABS = ['inicio', 'fixture', 'goleadores', 'sancionados', 'definiciones', 'historia'] as const
type Tab = (typeof TABS)[number]
// Sanctions are kept per season and yellow cards per edition (Excel): the tab shows when there is any of them
const tabs = computed(() => TABS.filter((k) =>
  (k !== 'definiciones' || specials.value.length > 0)
  && (k !== 'sancionados' || tournament.value?.seasonId != null || yellows.value.length > 0)
  && (k !== 'historia' || history.value.length > 0 || photos.value.length > 0 || isAdmin.value)))
const tab = computed<Tab>({
  get: () => (TABS as readonly string[]).includes(route.query.tab as string) ? route.query.tab as Tab : 'inicio',
  set: (value) => router.replace({ query: { ...route.query, tab: value === 'inicio' ? undefined : value } }),
})

const requestedId = computed(() => (route.params.id ? Number(route.params.id) : null))
const sortedEditions = computed(() => sortEditions(editions.value ?? []))

watch(requestedId, async (id) => {
  editions.value ??= await tournamentApi.list()
  tournament.value = id === null ? currentEdition(editions.value) : editions.value.find((x) => x.id === id) ?? null
  notFound.value = id !== null && tournament.value === null
  categories.value = null
  zoneId.value = null
  if (!tournament.value) return
  const tid = tournament.value.id
  const seasonId = tournament.value.seasonId
  const [cats, special, goals, figs, sanc, yel] = await Promise.all([
    tournamentApi.categories(tid),
    playoffApi.list(tid),
    seasonId ? seasonApi.scorers(seasonId) : tournamentApi.scorers(tid).then(rankScorers),
    seasonId ? seasonApi.figures(seasonId) : Promise.resolve([]),
    seasonId ? seasonApi.sanctions(seasonId) : Promise.resolve([]),
    tournamentApi.yellowCards(tid),
  ])
  categories.value = cats
  specials.value = special
  scorers.value = goals
  figures.value = figs
  sanctions.value = sanc
  yellows.value = yel
}, { immediate: true })

watch(zoneId, async (z) => {
  if (!z) {
    matches.value = []
    standings.value = []
    return
  }
  ;[matches.value, standings.value] = await Promise.all([zoneApi.fixtures(z), zoneApi.standings(z)])
  // Fixture opens on the first round that still has matches to play; otherwise the last one
  const pending = allRounds.value.findIndex((r) => r.matches.some((m) => !isDone(m) && !m.withdrawal))
  fixtureIndex.value = pending >= 0 ? pending : Math.max(allRounds.value.length - 1, 0)
})

const allRounds = computed(() => groupRounds(matches.value))
// Walkovers of a team that left the edition are not shown as "last / next round" (they stay in the fixture)
const highlighted = computed(() => matches.value.filter((m) => !m.withdrawal))
const lastRound = computed(() => groupRounds(highlighted.value.filter(isDone)).at(-1) ?? null)
const nextRound = computed(() => groupRounds(highlighted.value.filter((m) => !isDone(m)))[0] ?? null)

/** "Categoría A · Zona 2" (zone only when the category has several) */
const groupLabel = computed(() => {
  const cat = categories.value?.find((c) => c.zones.some((z) => z.id === zoneId.value))
  if (!cat) return ''
  const zone = cat.zoneCount > 1 ? cat.zones.find((z) => z.id === zoneId.value)?.name : null
  return [t('league.category', { name: cat.name }), zone].filter(Boolean).join(' · ')
})

const roundTitle = (r: { number: number | null; label: string | null }) =>
  r.number != null ? t('fixture.round', { n: r.number }) : r.label ?? ''

function subtitle(r: { number: number | null; label: string | null; day: string | null }) {
  return [groupLabel.value, roundTitle(r), r.day ? dayLabel(r.day) : null].filter(Boolean).join(' · ')
}

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

const publicUrl = computed(() => tournament.value
  ? `${window.location.origin}/ver/${tournament.value.id}${route.fullPath.includes('?') ? route.fullPath.slice(route.fullPath.indexOf('?')) : ''}`
  : window.location.href)

function changeEdition(event: Event) {
  router.push(`/ver/${(event.target as HTMLSelectElement).value}`)
}
</script>

<template>
  <PublicLayout>
    <template #hero>
      <section v-if="tournament" class="hero">
        <div class="hero-inner">
          <div class="hero-main">
            <p v-if="tournament?.seasonYear" class="kicker">{{ t('season.title', { year: tournament.seasonYear }) }}</p>
            <h1>{{ tournament?.name ?? t('league.noEditionTitle') }}</h1>
            <div v-if="tournament" class="hero-actions">
              <span class="status">{{ t(`tournaments.statuses.${tournament.status}`) }}</span>
              <span v-if="tournament.status === 'OPEN' && tournament.registrationEnd" class="until">
                {{ t('league.registrationUntil', { date: new Date(`${tournament.registrationEnd}T00:00`).toLocaleDateString('es-AR') }) }}
              </span>
              <ShareButton :url="publicUrl" :title="tournament.name" tone="light" />
            </div>
          </div>
          <label v-if="sortedEditions.length > 1 && tournament" class="edition">
            <span>{{ t('league.edition') }}</span>
            <select :value="tournament.id" @change="changeEdition">
              <option v-for="e in sortedEditions" :key="e.id" :value="e.id">{{ e.name }}</option>
            </select>
          </label>
        </div>
        <nav v-if="tournament" class="tabs" role="tablist">
          <button
            v-for="key in tabs" :key="key" type="button" role="tab" :aria-selected="tab === key"
            :class="['tab', { active: tab === key }]" @click="tab = key"
          >{{ t(`league.tabs.${key}`) }}</button>
        </nav>
      </section>
    </template>

    <!-- Nothing loaded yet -->
    <div v-if="editions && !tournament && !notFound" class="card empty">
      <span class="empty-icon"><AppIcon name="trophy" :size="34" /></span>
      <h2>{{ t('league.emptyTitle') }}</h2>
      <p class="muted">{{ isAdmin ? t('league.emptyAdmin') : t('league.emptyText') }}</p>
      <div v-if="isAdmin" class="empty-actions">
        <RouterLink to="/ediciones" class="btn"><AppIcon name="plus" :size="18" /> {{ t('league.createEdition') }}</RouterLink>
        <RouterLink to="/planilla" class="btn secondary"><AppIcon name="table" :size="18" /> {{ t('league.importExcel') }}</RouterLink>
      </div>
      <RouterLink v-else-if="!isLoggedIn" :to="{ path: '/login', query: { redirect: '/' } }" class="btn secondary">
        <AppIcon name="login" :size="18" /> {{ t('nav.login') }}
      </RouterLink>
    </div>
    <p v-else-if="notFound" class="card muted">{{ t('publicSite.notFound') }}</p>

    <template v-else-if="tournament">
      <p v-if="categories && categories.length === 0" class="card muted">{{ t('publicSite.noCategories') }}</p>

      <GroupNav v-if="categories?.length && ['inicio', 'fixture'].includes(tab)" v-model="zoneId" :categories="categories" />

      <!-- INICIO -->
      <template v-if="tab === 'inicio' && categories?.length">
        <section class="block">
          <header class="block-head">
            <div>
              <h2>{{ t('league.lastRound') }}</h2>
              <p v-if="lastRound" class="sub">{{ subtitle(lastRound) }}</p>
            </div>
            <button v-if="lastRound" type="button" class="more" @click="tab = 'fixture'">{{ t('league.seeFixture') }} ›</button>
          </header>
          <MatchCarousel v-if="lastRound" :matches="lastRound.matches" />
          <p v-else class="muted">{{ t('publicSite.noResults') }}</p>
        </section>

        <section class="block">
          <header class="block-head"><h2>{{ t('league.table') }}</h2></header>
          <div class="table-card"><LeagueTable :rows="standings" /></div>
          <p class="muted note">{{ t('publicSite.standingsHelp') }}</p>
        </section>

        <section class="block">
          <header class="block-head">
            <div>
              <h2>{{ t('league.nextRound') }}</h2>
              <p v-if="nextRound" class="sub">{{ subtitle(nextRound) }}</p>
            </div>
            <button v-if="nextRound" type="button" class="more" @click="tab = 'fixture'">{{ t('league.seeFixture') }} ›</button>
          </header>
          <div v-if="nextRound" class="next-grid">
            <ScoreCard v-for="m in nextRound.matches" :key="m.id" :match="m" />
          </div>
          <p v-else class="muted">{{ t('publicSite.noUpcoming') }}</p>
        </section>
      </template>

      <!-- FIXTURE -->
      <section v-else-if="tab === 'fixture' && categories?.length" class="card">
        <p v-if="allRounds.length === 0" class="muted">{{ t('standings.noGames') }}</p>
        <template v-else>
          <RoundNav v-model="fixtureIndex" :rounds="allRounds" />
          <MatchCard v-for="m in allRounds[fixtureIndex]?.matches ?? []" :key="m.id" :match="m" date-format="time" />
        </template>
      </section>

      <!-- GOLEADORES -->
      <section v-else-if="tab === 'goleadores'" class="rankings">
        <div class="card">
          <h2 class="card-title">{{ t('publicSite.scorers') }}</h2>
          <PlayerRankingTable :rows="scorers.slice(0, 20)" :value-label="t('season.goals')" />
        </div>
        <div v-if="figures.length" class="card">
          <h2 class="card-title">{{ t('publicSite.figures') }}</h2>
          <PlayerRankingTable :rows="figures.slice(0, 20)" :value-label="t('season.times')" />
        </div>
        <p v-if="tournament.seasonYear" class="muted note">{{ t('publicSite.annualHelp', { year: tournament.seasonYear }) }}</p>
      </section>

      <!-- SANCIONADOS -->
      <section v-else-if="tab === 'sancionados'" class="rankings">
        <template v-if="tournament.seasonId">
          <div class="card">
            <h2 class="card-title">{{ t('publicSite.openSanctions') }} <span class="count">{{ split.open.length }}</span></h2>
            <SanctionsTable :rows="split.open" />
          </div>
          <div class="card">
            <h2 class="card-title">{{ t('publicSite.servedSanctions') }} <span class="count">{{ split.served.length }}</span></h2>
            <SanctionsTable :rows="split.served" served />
          </div>
          <p class="muted note">{{ t('publicSite.sanctionsHelp', { year: tournament.seasonYear ?? '' }) }}</p>
        </template>
        <div class="card">
          <h2 class="card-title">{{ t('publicSite.yellows', { name: tournament.name }) }}</h2>
          <YellowCardsByTeam :rows="yellows" />
          <p class="muted note">{{ t('publicSite.yellowsHelp') }}</p>
        </div>
      </section>

      <!-- DEFINICIONES -->
      <section v-else-if="tab === 'definiciones'" class="card">
        <template v-for="g in specialGroups" :key="g.type">
          <h2 class="card-title">{{ t(`publicSite.special.${g.type}`) }}</h2>
          <MatchCard v-for="m in g.matches" :key="m.id" :match="m" />
        </template>
      </section>

      <!-- HISTORIA -->
      <section v-else-if="tab === 'historia'" class="history">
        <ChampionsCollage v-model:photos="photos" />
        <ChampionsHistory :history="history" />
      </section>
    </template>
  </PublicLayout>
</template>

<style scoped>
.hero { background: var(--navy); color: #fff; }
.hero-inner {
  display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap;
  padding: 24px max(16px, calc((100% - 1100px) / 2)) 18px;
}
.kicker { margin: 0 0 4px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.08em; opacity: 0.8; }
.hero h1 { font-family: var(--condensed); text-transform: uppercase; font-size: clamp(30px, 7vw, 48px); line-height: 1.05; }
.hero-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
.status { font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); }
.until { font-size: 13px; opacity: 0.85; }
.edition { display: flex; flex-direction: column; gap: 4px; font-size: 12px; text-transform: uppercase; letter-spacing: 0.06em; opacity: 0.95; }
.edition select {
  font: inherit; font-size: 15px; text-transform: none; letter-spacing: 0; color: #fff; background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.6); border-radius: 999px; padding: 7px 14px; cursor: pointer;
}
.edition option { color: var(--text); }
.tabs {
  display: flex; gap: 4px; overflow-x: auto; scrollbar-width: none;
  padding: 0 max(8px, calc((100% - 1100px) / 2)); background: var(--navy-dark);
}
.tab {
  flex-shrink: 0; background: none; border: 0; border-bottom: 3px solid transparent; color: rgba(255, 255, 255, 0.75);
  font-family: var(--condensed); font-size: 17px; text-transform: uppercase; padding: 12px 12px 9px; cursor: pointer;
}
.tab.active { color: #fff; border-bottom-color: #fff; }

.block { margin-bottom: 32px; }
.block-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; margin-bottom: 12px; }
.block-head h2 { font-family: var(--condensed); text-transform: uppercase; color: var(--navy); font-size: clamp(26px, 5vw, 34px); line-height: 1; }
.sub { margin: 4px 0 0; font-size: 13px; color: var(--muted); font-weight: 500; }
.more { background: none; border: 0; color: var(--navy); font: inherit; font-size: 14px; font-weight: 500; cursor: pointer; white-space: nowrap; }
.table-card { background: #fff; border-radius: var(--radius); box-shadow: var(--shadow); }
.note { font-size: 13px; margin-top: 10px; }
.next-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.card-title { font-family: var(--condensed); text-transform: uppercase; color: var(--navy); font-size: 22px; margin-bottom: 8px; }
.rankings { display: flex; flex-direction: column; gap: 16px; }
.history :deep(.category) { margin-bottom: 16px; }
.rankings > .note { margin-top: -6px; }
.count {
  font-family: 'Roboto', system-ui, sans-serif; font-size: 13px; font-weight: 700; vertical-align: middle;
  padding: 2px 9px; border-radius: 999px; background: var(--bg); color: var(--muted);
}
.empty { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; padding: 40px 20px; }
.empty h2 { font-size: 20px; color: var(--navy); }
.empty-icon {
  width: 80px; height: 80px; border-radius: 50%; background: #dde2ec; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center;
}
.empty-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; margin-top: 8px; }
</style>
