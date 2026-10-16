<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import TeamBadge from '../components/TeamBadge.vue'
import { teamApi, tournamentApi } from '../api/endpoints'
import type { Player, TeamSummary, Tournament } from '../api/types'
import { t } from '../i18n'

const teams = ref<TeamSummary[] | null>(null)
const tournaments = ref<Tournament[]>([])
const newTeam = ref('')
const error = ref('')

// Roster panel of the expanded team
const openTeamId = ref<number | null>(null)
const players = ref<Player[]>([])
const playerName = ref('')
const jerseyNo = ref<number | null>(null)
const registerTournamentId = ref<number | null>(null)
const notice = ref('')

const openTournaments = computed(() => tournaments.value.filter((x) => x.status === 'OPEN' || x.status === 'DRAFT'))

onMounted(async () => {
  ;[teams.value, tournaments.value] = await Promise.all([teamApi.list(), tournamentApi.list()])
})

async function run(action: () => Promise<void>) {
  error.value = ''
  notice.value = ''
  try {
    await action()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

const addTeam = () => run(async () => {
  await teamApi.create(newTeam.value)
  newTeam.value = ''
  teams.value = await teamApi.list()
})

const toggle = (teamId: number) => run(async () => {
  if (openTeamId.value === teamId) { openTeamId.value = null; return }
  players.value = await teamApi.players(teamId)
  openTeamId.value = teamId
  registerTournamentId.value = openTournaments.value[0]?.id ?? null
})

async function refreshRoster(teamId: number) {
  players.value = await teamApi.players(teamId)
  const team = teams.value?.find((x) => x.id === teamId)
  if (team) team.playerCount = players.value.length
}

const addPlayer = (teamId: number) => run(async () => {
  await teamApi.addPlayer(teamId, playerName.value, jerseyNo.value ?? 0)
  playerName.value = ''
  jerseyNo.value = null
  await refreshRoster(teamId)
})

const removePlayer = (teamId: number, playerId: number) => run(async () => {
  await teamApi.deletePlayer(teamId, playerId)
  await refreshRoster(teamId)
})

const registerTeam = (teamId: number) => run(async () => {
  if (!registerTournamentId.value) return
  await teamApi.registerIn(teamId, registerTournamentId.value)
  notice.value = t('teams.registered')
})

const countLabel = (n: number) => (n === 1 ? t('teams.onePlayer') : t('teams.players', { n }))
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('teams.title') }}</h1>
    <div class="card">
      <form class="add" @submit.prevent="addTeam">
        <input v-model="newTeam" class="input" :placeholder="t('teams.namePlaceholder')" required />
        <button class="btn">{{ t('teams.add') }}</button>
      </form>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="notice" class="notice">{{ notice }}</p>

      <p v-if="teams === null" class="muted">{{ t('common.loading') }}</p>
      <template v-else>
        <p class="total">{{ t('teams.total', { n: teams.length }) }}</p>
        <p v-if="teams.length === 0" class="muted">{{ t('teams.empty') }}</p>
        <div v-for="team in teams" :key="team.id" class="team">
          <div class="row">
            <TeamBadge :name="team.name" :size="54" />
            <div class="info">
              <span class="name">{{ team.name }}</span>
              <span class="muted small">{{ countLabel(team.playerCount) }}</span>
            </div>
            <button class="round" :title="t('teams.roster')" :aria-expanded="openTeamId === team.id" @click="toggle(team.id)">
              <AppIcon name="group" />
            </button>
          </div>

          <div v-if="openTeamId === team.id" class="roster">
            <p v-if="players.length === 0" class="muted">{{ t('teams.noPlayers') }}</p>
            <ul>
              <li v-for="p in players" :key="p.id">
                <span class="jersey">{{ p.jerseyNo }}</span>
                <span class="pname">{{ p.fullName }}</span>
                <button class="icon-btn" :title="t('teams.removePlayer')" @click="removePlayer(team.id, p.id)">
                  <AppIcon name="close" :size="18" />
                </button>
              </li>
            </ul>
            <form class="add-player" @submit.prevent="addPlayer(team.id)">
              <input v-model.number="jerseyNo" class="input num" type="number" min="0" :placeholder="t('teams.jerseyNo')" />
              <input v-model="playerName" class="input" :placeholder="t('teams.playerName')" required />
              <button class="btn secondary">{{ t('teams.addPlayer') }}</button>
            </form>
            <form v-if="openTournaments.length" class="register" @submit.prevent="registerTeam(team.id)">
              <label for="reg-tournament" class="muted small">{{ t('teams.registerIn') }}</label>
              <select id="reg-tournament" v-model="registerTournamentId" class="select">
                <option v-for="tour in openTournaments" :key="tour.id" :value="tour.id">{{ tour.name }}</option>
              </select>
              <button class="btn">{{ t('teams.register') }}</button>
            </form>
          </div>
        </div>
      </template>
    </div>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
.add { display: flex; gap: 10px; }
.add .input { border: 2px solid var(--navy); }
.total { font-size: 13px; font-weight: 700; color: var(--muted); margin: 10px 0 4px; }
.notice { color: #1e7d45; font-size: 14px; }
.team { border-bottom: 1px solid var(--border); }
.team:last-child { border-bottom: 0; }
.row { display: flex; align-items: center; gap: 16px; padding: 12px 6px; }
.info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.name { font-size: 16px; }
.small { font-size: 13px; }
.round {
  width: 40px; height: 40px; border-radius: 50%; border: 0; background: var(--bg); color: var(--navy);
  cursor: pointer; display: inline-flex; align-items: center; justify-content: center;
}
.roster { padding: 4px 6px 16px 76px; }
.roster ul { list-style: none; margin: 0 0 10px; padding: 0; }
.roster li { display: flex; align-items: center; gap: 10px; padding: 6px 0; border-bottom: 1px dashed var(--border); }
.jersey { width: 30px; text-align: center; font-weight: 700; color: var(--navy); }
.pname { flex: 1; }
.icon-btn { background: none; border: 0; color: var(--muted); cursor: pointer; display: inline-flex; }
.add-player, .register { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
.num { width: 80px; flex-shrink: 0; }
.register .select { width: auto; min-width: 200px; }
</style>
