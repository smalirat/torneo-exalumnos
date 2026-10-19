<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import CrestPicker from '../components/CrestPicker.vue'
import ConfirmButton from '../components/ConfirmButton.vue'
import InlineEdit from '../components/InlineEdit.vue'
import { authApi, requestApi, teamApi } from '../api/endpoints'
import { isAdmin } from '../auth'
import type { Decision, Player, TeamSummary } from '../api/types'
import { t } from '../i18n'

const teams = ref<TeamSummary[] | null>(null)
const newTeam = ref('')
const error = ref('')
/** Captain: after a change that needs approval */
const notice = ref('')
/** Captain: the organizer's latest answers to their requests */
const decisions = ref<Decision[]>([])

// Roster panel of the expanded team
const openTeamId = ref<number | null>(null)
const players = ref<Player[]>([])
const playerName = ref('')
const jerseyNo = ref<number | null>(null)


async function loadTeams() {
  teams.value = await teamApi.list()
  if (!isAdmin.value) decisions.value = (await requestApi.mine().catch(() => ({ decisions: [] as Decision[] }))).decisions
}
onMounted(loadTeams)

/** Captains' changes (except crest and jersey numbers) wait for the organizer */
function sentForApproval() {
  if (!isAdmin.value) notice.value = t('teams.sentForApproval')
}

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
  await loadTeams()
  sentForApproval()
})

const toggle = (teamId: number) => run(async () => {
  if (openTeamId.value === teamId) { openTeamId.value = null; return }
  players.value = await teamApi.roster(teamId)
  openTeamId.value = teamId
})

async function refreshRoster(teamId: number) {
  players.value = await teamApi.roster(teamId)
  const team = teams.value?.find((x) => x.id === teamId)
  if (team) team.playerCount = players.value.length
}

const addPlayer = (teamId: number) => run(async () => {
  await teamApi.addPlayer(teamId, playerName.value, jerseyNo.value ?? 0)
  playerName.value = ''
  jerseyNo.value = null
  await refreshRoster(teamId)
  sentForApproval()
})

const removePlayer = (teamId: number, p: Player) => run(async () => {
  await teamApi.deletePlayer(teamId, p.id)
  await refreshRoster(teamId)
  if (p.approved) sentForApproval() // withdrawing a pending addition is immediate
})

/** Jersey number: cosmetic, saved right away */
const setJersey = (teamId: number, p: Player, event: Event) => run(async () => {
  const value = Number((event.target as HTMLInputElement).value)
  if (!Number.isInteger(value) || value < 0 || value === p.jerseyNo) return
  Object.assign(p, await teamApi.setJersey(teamId, p.id, value))
})


const renameTeam = async (team: TeamSummary, name: string) => {
  await teamApi.rename(team.id, name)
  await loadTeams()
  if (team.approved) sentForApproval()
}

const deleteTeam = (team: TeamSummary) => run(async () => {
  await teamApi.remove(team.id)
  await loadTeams()
  if (team.approved) sentForApproval()
})

const decisionText = (d: Decision) => t(`requests.types.${d.type}`, { subject: d.subject ?? d.teamName })

// Invitation link for a pending captain (admin)
const invite = ref<{ teamId: number; teamName: string; url: string; copied: boolean } | null>(null)
const showInvite = (team: TeamSummary) => run(async () => {
  if (invite.value?.teamId === team.id) { invite.value = null; return }
  const res = await authApi.invite(team.id)
  invite.value = { teamId: team.id, teamName: res.teamName, url: `${window.location.origin}/invitacion/${res.token}`, copied: false }
})
async function copyInvite() {
  if (!invite.value) return
  try {
    await navigator.clipboard.writeText(invite.value.url)
    invite.value.copied = true
  } catch { /* the link stays visible to copy by hand */ }
}
const whatsappUrl = computed(() => invite.value
  ? `https://wa.me/?text=${encodeURIComponent(t('teams.whatsappText', { name: invite.value.teamName, url: invite.value.url }))}`
  : '')
/** A captain has a single team: once it exists the "add" form goes away */
const canAdd = computed(() => isAdmin.value || (teams.value?.length ?? 0) === 0)

const countLabel = (n: number) => (n === 1 ? t('teams.onePlayer') : t('teams.players', { n }))
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('teams.title') }}</h1>
    <div class="card">
      <form v-if="canAdd" class="add" @submit.prevent="addTeam">
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
            <CrestPicker :team="team" :size="54" @change="(crest) => (team.crest = crest)" />
            <div class="info">
              <span class="name">
                <InlineEdit :value="team.name" :save="(name) => renameTeam(team, name)">
                  <RouterLink :to="`/equipos/${team.id}`" class="team-link">{{ team.name }}</RouterLink>
                </InlineEdit>
              </span>
              <span v-if="!team.approved || team.pendingName || team.deletionRequested" class="review small">
                <template v-if="!team.approved">{{ t('teams.inReview') }}</template>
                <template v-if="team.pendingName">{{ t('teams.pendingName', { name: team.pendingName }) }}</template>
                <template v-if="team.deletionRequested">{{ t('teams.deletionRequested') }}</template>
              </span>
              <span class="muted small">
                {{ countLabel(team.playerCount) }}
                <template v-if="isAdmin">
                  ·
                  <span v-if="team.captainPending" class="pending">{{ t('teams.captainPending') }}</span>
                  <span v-else>{{ t('teams.captain', { name: team.captainUsername ?? '' }) }}</span>
                </template>
              </span>
            </div>
            <button
              v-if="isAdmin && team.captainPending" class="btn secondary small-btn" :aria-expanded="invite?.teamId === team.id"
              @click="showInvite(team)"
            >{{ t('teams.invite') }}</button>
            <ConfirmButton
              icon :label="t('teams.delete')" :message="t('teams.deleteConfirm', { name: team.name })"
              @confirm="deleteTeam(team)"
            />
            <button class="round" :title="t('teams.roster')" :aria-expanded="openTeamId === team.id" @click="toggle(team.id)">
              <AppIcon name="group" />
            </button>
          </div>

          <div v-if="invite?.teamId === team.id" class="invite">
            <p class="small"><strong>{{ t('teams.inviteTitle', { name: team.name }) }}</strong></p>
            <div class="invite-row">
              <input class="input" :value="invite.url" readonly @focus="($event.target as HTMLInputElement).select()" />
              <button class="btn" @click="copyInvite">{{ invite.copied ? t('teams.copied') : t('teams.copy') }}</button>
              <a class="btn secondary" :href="whatsappUrl" target="_blank" rel="noopener">{{ t('teams.whatsapp') }}</a>
            </div>
            <p class="muted small">{{ t('teams.inviteHelp') }}</p>
          </div>

          <div v-if="openTeamId === team.id" class="roster">
            <p v-if="players.length === 0" class="muted">{{ t('teams.noPlayers') }}</p>
            <ul>
              <li v-for="p in players" :key="p.id" :class="{ waiting: !p.approved || p.removalRequested }">
                <input
                  class="jersey" type="number" min="0" :value="p.jerseyNo" :aria-label="t('teams.jerseyNo')"
                  @change="setJersey(team.id, p, $event)"
                >
                <span class="pname">
                  {{ p.fullName }}
                  <small v-if="!p.approved" class="state">{{ t('teams.playerPending') }}</small>
                  <small v-else-if="p.removalRequested" class="state">{{ t('teams.playerRemovalPending') }}</small>
                </span>
                <button
                  v-if="!p.removalRequested" class="icon-btn" :title="t('teams.removePlayer')" @click="removePlayer(team.id, p)"
                >
                  <AppIcon name="close" :size="18" />
                </button>
              </li>
            </ul>
            <form class="add-player" @submit.prevent="addPlayer(team.id)">
              <input v-model.number="jerseyNo" class="input num" type="number" min="0" :placeholder="t('teams.jerseyNo')" />
              <input v-model="playerName" class="input" :placeholder="t('teams.playerName')" required />
              <button class="btn secondary">{{ t('teams.addPlayer') }}</button>
            </form>
          </div>
        </div>
      </template>
    </div>

    <section v-if="!isAdmin && decisions.length" class="card answers">
      <h2>{{ t('teams.answersTitle') }}</h2>
      <ul>
        <li v-for="(d, i) in decisions" :key="i">
          <span :class="['verdict', { ok: d.approved }]">{{ d.approved ? t('teams.approved') : t('teams.rejected') }}</span>
          <span>{{ decisionText(d) }}</span>
          <span v-if="d.reason" class="muted">— {{ d.reason }}</span>
        </li>
      </ul>
    </section>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
.add { display: flex; gap: 10px; }
.add .input { border: 2px solid var(--navy); }
.total { font-size: 13px; font-weight: 700; color: var(--muted); margin: 10px 0 4px; }
.team { border-bottom: 1px solid var(--border); }
.team:last-child { border-bottom: 0; }
.row { display: flex; align-items: center; gap: 16px; padding: 12px 6px; }
.info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.name { font-size: 16px; }
.team-link:hover { color: var(--navy); text-decoration: underline; }
.small { font-size: 13px; }
.round {
  width: 40px; height: 40px; border-radius: 50%; border: 0; background: var(--bg); color: var(--navy);
  cursor: pointer; display: inline-flex; align-items: center; justify-content: center;
}
.roster { padding: 4px 6px 16px 76px; }
.roster ul { list-style: none; margin: 0 0 10px; padding: 0; }
.roster li { display: flex; align-items: center; gap: 10px; padding: 6px 0; border-bottom: 1px dashed var(--border); }
.jersey {
  width: 52px; text-align: center; font: inherit; font-weight: 700; color: var(--navy); border: 1px solid transparent;
  border-radius: 6px; background: transparent; padding: 2px 4px;
}
.jersey:hover, .jersey:focus { border-color: var(--border); background: #fff; outline: none; }
.waiting .pname { color: var(--muted); }
.state { margin-left: 6px; font-size: 12px; font-weight: 700; color: #b26a00; }
.review { color: #b26a00; font-weight: 700; display: flex; gap: 10px; flex-wrap: wrap; }
.notice { color: #8a6500; background: #fff6d6; border-radius: 8px; padding: 8px 12px; font-size: 14px; }
.answers { margin-top: 16px; }
.answers h2 { font-size: 16px; color: var(--navy); margin-bottom: 8px; }
.answers ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; font-size: 14px; }
.answers li { display: flex; gap: 8px; flex-wrap: wrap; align-items: baseline; }
.verdict { font-size: 12px; font-weight: 700; padding: 1px 8px; border-radius: 999px; background: #fde3e0; color: var(--danger); }
.verdict.ok { background: #dff3e6; color: #1e7d45; }
.pname { flex: 1; }
.add-player { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
.num { width: 80px; flex-shrink: 0; }
.pending { font-weight: 700; color: #b26a00; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.invite { margin: 0 6px 14px 76px; padding: 12px; border-radius: 10px; background: var(--bg); }
.invite p { margin: 0 0 8px; }
.invite-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.invite-row .input { flex: 1; min-width: 220px; font-size: 13px; }
@media (max-width: 600px) { .invite, .roster { margin-left: 0; padding-left: 6px; } }
</style>
