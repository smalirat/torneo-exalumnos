<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import MatchCard from '../components/MatchCard.vue'
import StandingsTable from '../components/StandingsTable.vue'
import CrestPicker from '../components/CrestPicker.vue'
import TeamBadge from '../components/TeamBadge.vue'
import { currentUser, isAdmin } from '../auth'
import { teamApi, zoneApi } from '../api/endpoints'
import type { Match, StandingRow, TeamRef, TeamRegistration } from '../api/types'
import { groupName } from '../groups'
import { isLoggedIn } from '../auth'
import { PANEL, publicPath, teamPath } from '../paths'
import { teamIdFromSlug } from '../slugs'
import { t } from '../i18n'

// A team's season: every tournament it is registered in, its zone table, next match and results
const route = useRoute()
const router = useRouter()
/** /equipos/41-gulp (an old /equipos/41 also works and is turned into the full one) */
const teamId = computed(() => teamIdFromSlug(String(route.params.equipo)) ?? 0)

interface Season { reg: TeamRegistration; rows: StandingRow[]; matches: Match[] }

const team = ref<TeamRef | null>(null)
const seasons = ref<Season[] | null>(null)

watch(teamId, async (id) => {
  const [info, regs] = await Promise.all([teamApi.get(id), teamApi.registrations(id)])
  team.value = info
  if (route.path !== teamPath(info)) router.replace(teamPath(info))
  seasons.value = await Promise.all(regs.map(async (reg) => {
    if (!reg.zoneId) return { reg, rows: [], matches: [] }
    const [rows, fixtures] = await Promise.all([zoneApi.standings(reg.zoneId), zoneApi.fixtures(reg.zoneId)])
    return { reg, rows, matches: fixtures.filter((m) => m.homeTeam.id === id || m.awayTeam.id === id) }
  }))
}, { immediate: true })

/** Admin or the team's own captain can change the crest */
const canEdit = computed(() => isAdmin.value || (currentUser.value?.teamIds.includes(teamId.value) ?? false))

const placeLabel = (r: TeamRegistration) =>
  groupName({ ...r, categoryName: r.categoryName ?? '' })
const position = (s: Season) => s.rows.find((r) => r.team.id === teamId.value)?.position
const nextMatch = (s: Season) => s.matches.find((m) => m.status !== 'FINISHED') ?? null
const results = (s: Season) => s.matches.filter((m) => m.status === 'FINISHED').reverse()
</script>

<template>
  <GlobalLayout>
    <RouterLink v-if="isLoggedIn" :to="PANEL.teams" class="back"><AppIcon name="chevron" class="flip" :size="18" /> {{ t('teams.title') }}</RouterLink>
    <header v-if="team" class="header">
      <CrestPicker v-if="canEdit" :team="team" :size="64" @change="(crest) => team && (team.crest = crest)" />
      <TeamBadge v-else :team="team" :size="64" />
      <h1>{{ team.name }}</h1>
    </header>

    <p v-if="seasons === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="seasons.length === 0" class="card muted">{{ t('teamPage.noTournaments') }}</p>

    <section v-for="s in seasons ?? []" :key="s.reg.registrationId" class="card season">
      <header class="season-head">
        <RouterLink :to="publicPath('fixture', s.reg.tournament)" class="tour">
          <AppIcon name="trophy" /> {{ s.reg.tournament.name }}
        </RouterLink>
        <span v-if="s.reg.zoneId" class="chip">
          {{ placeLabel(s.reg) }}<template v-if="position(s)"> · {{ t('teamPage.position', { n: position(s)! }) }}</template>
        </span>
        <span v-else class="chip pending">{{ t('teamPage.unassigned') }}</span>
      </header>

      <template v-if="s.reg.zoneId">
        <div class="grid">
          <div>
            <h3>{{ t('teamPage.next') }}</h3>
            <MatchCard v-if="nextMatch(s)" :match="nextMatch(s)!" />
            <p v-else class="muted small">{{ t('teamPage.noNext') }}</p>
            <h3>{{ t('teamPage.results') }}</h3>
            <MatchCard v-for="m in results(s)" :key="m.id" :match="m" />
            <p v-if="results(s).length === 0" class="muted small">{{ t('teamPage.noResults') }}</p>
          </div>
          <div>
            <h3>{{ t('nav.standings') }}</h3>
            <StandingsTable :rows="s.rows" :highlight-team-id="teamId" />
          </div>
        </div>
      </template>
    </section>
  </GlobalLayout>
</template>

<style scoped>
.back { display: inline-flex; align-items: center; gap: 4px; color: var(--muted); font-size: 14px; }
.flip { transform: rotate(180deg); }
.header { display: flex; align-items: center; gap: 16px; margin: 12px 0 24px; }
.header h1 { font-size: 30px; color: var(--navy); }
.season { margin-bottom: 20px; }
.season-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.tour { display: inline-flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 700; color: var(--navy); }
.chip { font-size: 13px; padding: 4px 12px; border-radius: 999px; background: var(--bg); color: var(--navy); font-weight: 500; }
.chip.pending { color: var(--muted); }
.grid { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(0, 2fr); gap: 24px; }
h3 { font-size: 13px; text-transform: uppercase; color: var(--muted); margin: 14px 0 4px; }
.small { font-size: 13px; }
</style>
