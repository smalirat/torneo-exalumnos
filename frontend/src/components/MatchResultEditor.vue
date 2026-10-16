<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TeamBadge from './TeamBadge.vue'
import AppIcon from './AppIcon.vue'
import BaseModal from './BaseModal.vue'
import ConfirmButton from './ConfirmButton.vue'
import { matchApi, teamApi } from '../api/endpoints'
import type { EventType, Match, Player } from '../api/types'
import { t } from '../i18n'

// Admin editor for a match: presence, score (typed, or derived from per-player events), penalties for drawn
// definitions, and the match data (date, round, field, pitch, note).
const props = defineProps<{ match: Match }>()
const emit = defineEmits<{ saved: []; close: [] }>()

type Side = 'home' | 'away'
interface Draft { side: Side; playerId: number; type: EventType }

const EVENT_TYPES: EventType[] = ['GOAL', 'OWN_GOAL', 'YELLOW_CARD', 'RED_CARD']

const rosters = ref<Record<Side, Player[]>>({ home: [], away: [] })
const present = ref<Record<Side, boolean>>({ home: props.match.homePresent, away: props.match.awayPresent })
const events = ref<Draft[]>([])
const picked = ref<Record<Side, number | null>>({ home: null, away: null })
const penalties = ref<Record<Side, number | null>>({ home: props.match.homePenalties, away: props.match.awayPenalties })
const goals = ref<Record<Side, number | null>>({ home: null, away: null })
const details = ref({
  when: props.match.scheduledAt?.slice(0, 16) ?? '',
  round: props.match.round,
  roundLabel: props.match.roundLabel ?? '',
  venue: props.match.venue ?? '',
  pitch: props.match.pitch ?? '',
  notes: props.match.notes ?? '',
})
const error = ref('')
const busy = ref(false)

const teams = computed(() => ({ home: props.match.homeTeam, away: props.match.awayTeam }))
const walkover = computed(() => !present.value.home || !present.value.away)

const score = computed(() => {
  if (walkover.value) return { home: present.value.home ? 3 : 0, away: present.value.away ? 3 : 0 }
  if (typedScore.value) return { home: goals.value.home!, away: goals.value.away! }
  const count = (side: Side, type: EventType) => events.value.filter((e) => e.side === side && e.type === type).length
  return {
    home: count('home', 'GOAL') + count('away', 'OWN_GOAL'),
    away: count('away', 'GOAL') + count('home', 'OWN_GOAL'),
  }
})
const typedScore = computed(() => goals.value.home != null && goals.value.away != null)
const needsPenalties = computed(() =>
  props.match.type !== 'REGULAR' && !walkover.value && score.value.home === score.value.away)

onMounted(async () => {
  const [home, away, existing] = await Promise.all([
    teamApi.players(props.match.homeTeam.id),
    teamApi.players(props.match.awayTeam.id),
    props.match.status === 'FINISHED' ? matchApi.events(props.match.id) : Promise.resolve([]),
  ])
  rosters.value = { home, away }
  picked.value = { home: home[0]?.id ?? null, away: away[0]?.id ?? null }
  events.value = existing.map((e) => ({
    side: e.team.id === props.match.homeTeam.id ? 'home' : 'away', playerId: e.player.id, type: e.type,
  }))
  // A played match without goal detail (e.g. from the Excel) keeps its typed score
  const hasGoalEvents = existing.some((e) => e.type === 'GOAL' || e.type === 'OWN_GOAL')
  if (props.match.status === 'FINISHED' && !hasGoalEvents && props.match.homePresent && props.match.awayPresent) {
    goals.value = { home: props.match.homeGoals, away: props.match.awayGoals }
  }
})

function addEvent(side: Side, type: EventType) {
  const playerId = picked.value[side]
  if (playerId) events.value.push({ side, playerId, type })
}

const playerName = (side: Side, id: number) => rosters.value[side].find((p) => p.id === id)?.fullName ?? '?'
const sideEvents = (side: Side) => events.value.map((e, i) => ({ ...e, i })).filter((e) => e.side === side)

async function remove() {
  error.value = ''
  try {
    await matchApi.remove(props.match.id)
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

async function saveDetails() {
  busy.value = true
  error.value = ''
  try {
    const d = details.value
    await matchApi.updateDetails(props.match.id, {
      scheduledAt: d.when ? `${d.when}:00` : null,
      round: d.round || null,
      roundLabel: d.roundLabel || null,
      venue: d.venue || null,
      pitch: d.pitch || null,
      notes: d.notes || null,
    })
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}

async function save() {
  busy.value = true
  error.value = ''
  try {
    await matchApi.score(props.match.id, {
      homePresent: present.value.home,
      awayPresent: present.value.away,
      events: walkover.value ? [] : events.value.map(({ playerId, type }) => ({ playerId, type })),
      homeGoals: !walkover.value && typedScore.value ? goals.value.home : null,
      awayGoals: !walkover.value && typedScore.value ? goals.value.away : null,
      homePenalties: needsPenalties.value ? penalties.value.home : null,
      awayPenalties: needsPenalties.value ? penalties.value.away : null,
    })
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseModal :title="t('results.title')" width="820px" @close="emit('close')">
    <form @submit.prevent="save">

      <div class="scoreboard">
        <span class="team"><TeamBadge :name="teams.home.name" :size="40" /> {{ teams.home.name }}</span>
        <strong class="score">{{ score.home }} - {{ score.away }}</strong>
        <span class="team right">{{ teams.away.name }} <TeamBadge :name="teams.away.name" :size="40" /></span>
      </div>
      <p v-if="walkover" class="hint">{{ t('results.walkoverHint') }}</p>
      <div v-else class="typed">
        <span>{{ t('results.score') }}</span>
        <input v-model.number="goals.home" class="input" type="number" min="0" :aria-label="`${t('results.score')} ${teams.home.name}`" />
        <span>-</span>
        <input v-model.number="goals.away" class="input" type="number" min="0" :aria-label="`${t('results.score')} ${teams.away.name}`" />
        <small class="muted">{{ t('results.scoreHelp') }}</small>
      </div>

      <div class="sides">
        <section v-for="side in (['home', 'away'] as const)" :key="side" class="side">
          <label class="check">
            <input v-model="present[side]" type="checkbox" /> {{ t('results.showedUp') }}
          </label>

          <template v-if="!walkover">
            <ul class="events">
              <li v-for="e in sideEvents(side)" :key="e.i">
                <span :class="['tag', e.type]">{{ t(`results.types.${e.type}`) }}</span>
                <span class="pname">{{ playerName(side, e.playerId) }}</span>
                <button type="button" class="icon-btn" :title="t('teams.removePlayer')" @click="events.splice(e.i, 1)">
                  <AppIcon name="close" :size="16" />
                </button>
              </li>
            </ul>
            <p v-if="rosters[side].length === 0" class="muted small">{{ t('results.noRoster') }}</p>
            <template v-else>
              <select v-model="picked[side]" class="select" :aria-label="t('results.player')">
                <option v-for="p in rosters[side]" :key="p.id" :value="p.id">{{ p.jerseyNo }} · {{ p.fullName }}</option>
              </select>
              <div class="type-buttons">
                <button v-for="type in EVENT_TYPES" :key="type" type="button" :class="['btn', 'secondary', 'small-btn', type]"
                        @click="addEvent(side, type)">
                  + {{ t(`results.types.${type}`) }}
                </button>
              </div>
            </template>
          </template>
        </section>
      </div>

      <div v-if="needsPenalties" class="penalties">
        <span>{{ t('results.penalties') }}</span>
        <input v-model.number="penalties.home" class="input" type="number" min="0" required :aria-label="teams.home.name" />
        <span>-</span>
        <input v-model.number="penalties.away" class="input" type="number" min="0" required :aria-label="teams.away.name" />
      </div>

      <details class="details">
        <summary>{{ t('results.details') }}</summary>
        <div class="details-grid">
          <label>{{ t('results.when') }}<input v-model="details.when" class="input" type="datetime-local" /></label>
          <label>{{ t('results.round') }}<input v-model.number="details.round" class="input" type="number" min="1" /></label>
          <label>{{ t('results.roundLabel') }}<input v-model="details.roundLabel" class="input" maxlength="80" /></label>
          <label>{{ t('results.venue') }}<input v-model="details.venue" class="input" maxlength="60" /></label>
          <label>{{ t('results.pitch') }}<input v-model="details.pitch" class="input" maxlength="20" /></label>
          <label class="wide">{{ t('results.notes') }}<input v-model="details.notes" class="input" maxlength="500" /></label>
        </div>
        <button type="button" class="btn secondary" :disabled="busy" @click="saveDetails">{{ t('results.saveDetails') }}</button>
      </details>

      <p v-if="error" class="error">{{ error }}</p>
      <footer class="actions">
        <ConfirmButton :label="t('results.delete')" :message="t('results.deleteConfirm')" @confirm="remove" />
        <span class="spacer" />
        <button type="button" class="btn secondary" @click="emit('close')">{{ t('standings.cancel') }}</button>
        <button class="btn" :disabled="busy">{{ t('standings.save') }}</button>
      </footer>
    </form>
  </BaseModal>
</template>

<style scoped>
.scoreboard { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 12px; padding: 10px 0; }
.team { display: inline-flex; align-items: center; gap: 10px; font-size: 17px; font-weight: 500; }
.team.right { justify-content: flex-end; }
.score { font-size: 32px; color: var(--navy); min-width: 90px; text-align: center; }
.hint { text-align: center; color: var(--danger); font-size: 14px; }
.typed { display: flex; align-items: center; justify-content: center; gap: 8px; flex-wrap: wrap; font-weight: 500; }
.typed .input { width: 64px; text-align: center; }
.typed small { flex-basis: 100%; text-align: center; font-weight: 400; }
.details { margin-top: 16px; border-top: 1px solid var(--border); padding-top: 10px; }
.details summary { cursor: pointer; font-weight: 500; color: var(--navy); }
.details-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin: 12px 0; }
.details-grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.details-grid .wide { grid-column: 1 / -1; }
.sides { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 8px; }
.side { background: var(--row-alt); border-radius: 10px; padding: 12px; }
.check { display: flex; align-items: center; gap: 8px; font-weight: 500; margin-bottom: 8px; }
.events { list-style: none; margin: 0 0 10px; padding: 0; }
.events li { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.pname { flex: 1; }
.tag { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: #dde2ec; color: var(--navy); }
.tag.YELLOW_CARD { background: #f6d55c; color: #5a4500; }
.tag.RED_CARD { background: #e05a4f; color: #fff; }
.tag.OWN_GOAL { background: #c9cfdb; }
.type-buttons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.small-btn { padding: 4px 10px; font-size: 13px; }
.small { font-size: 13px; }
.penalties { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 16px; font-weight: 500; }
.penalties .input { width: 70px; text-align: center; }
.actions { display: flex; align-items: center; gap: 10px; margin-top: 18px; }
.spacer { flex: 1; }
</style>
