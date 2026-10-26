<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TeamBadge from './TeamBadge.vue'
import AppIcon from './AppIcon.vue'
import BaseModal from './BaseModal.vue'
import ConfirmButton from './ConfirmButton.vue'
import { DEFAULT_SUSPENSION, matchApi, seasonApi, teamApi, tournamentApi } from '../api/endpoints'
import type { EventType, Match, Player, Sanction } from '../api/types'
import { editionsCache } from '../editions'
import { t } from '../i18n'

// Admin editor for a match: presence, score (typed, or derived from per-player events), penalties for drawn
// definitions, who played, and the match data (date, round, field, pitch, note). Also: suspend a match being
// played, swap its time with another one, and (after a red card) create the sanction.
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
  const finished = props.match.status === 'FINISHED'
  const [home, away, existing, appeared] = await Promise.all([
    teamApi.players(props.match.homeTeam.id, true),
    teamApi.players(props.match.awayTeam.id, true),
    finished ? matchApi.events(props.match.id) : Promise.resolve([]),
    finished ? matchApi.appearances(props.match.id) : Promise.resolve([]),
  ])
  played.value = appeared.map((a) => a.playerId)
  // Current players first; the ones that left the team after (they may have played this match)
  const order = (list: Player[]) => [...list.filter((p) => !p.gone), ...list.filter((p) => p.gone)]
  rosters.value = { home: order(home), away: order(away) }
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

// Who played, ticked from the match sheet. Players with goals or cards played anyway (ticked and locked).
const played = ref<number[]>([])
const withEvents = computed(() => new Set(events.value.map((e) => e.playerId)))
const isPlayed = (id: number) => played.value.includes(id) || withEvents.value.has(id)
function togglePlayed(id: number) {
  played.value = played.value.includes(id) ? played.value.filter((x) => x !== id) : [...played.value, id]
}
/** The ones that left the team only show up if they played this match */
const sheet = (side: Side) => rosters.value[side].filter((p) => !p.gone || isPlayed(p.id))
const playedCount = (side: Side) => sheet(side).filter((p) => isPlayed(p.id)).length
function setAll(side: Side, on: boolean) {
  const ids = rosters.value[side].filter((p) => !p.gone).map((p) => p.id)
  played.value = on ? [...new Set([...played.value, ...ids])] : played.value.filter((x) => !ids.includes(x))
}

const playerName = (side: Side, id: number) => rosters.value[side].find((p) => p.id === id)?.fullName ?? '?'
const sideEvents = (side: Side) => events.value.map((e, i) => ({ ...e, i })).filter((e) => e.side === side)

// ---- sanctions for the sent-off players (the tribunal decides the final number of match days)
interface SanctionDraft { playerId: number; name: string; type: Sanction['type']; matches: number; create: boolean }
const sanctionDrafts = ref<SanctionDraft[] | null>(null)
const seasonId = computed(() => editionsCache.value?.find((e) => e.id === props.match.tournamentId)?.seasonId ?? null)

/** A red card is a direct red; two yellows of the same player in the match, a double yellow */
function offerSanctions() {
  const drafts: SanctionDraft[] = []
  if (!walkover.value && seasonId.value) {
    const byPlayer = new Map<number, { side: Side; reds: number; yellows: number }>()
    for (const e of events.value) {
      const x = byPlayer.get(e.playerId) ?? { side: e.side, reds: 0, yellows: 0 }
      if (e.type === 'RED_CARD') x.reds++
      if (e.type === 'YELLOW_CARD') x.yellows++
      byPlayer.set(e.playerId, x)
    }
    for (const [playerId, x] of byPlayer) {
      const type: Sanction['type'] | null = x.reds > 0 ? 'DIRECT_RED' : x.yellows >= 2 ? 'DOUBLE_YELLOW' : null
      if (type) drafts.push({ playerId, name: playerName(x.side, playerId), type, matches: DEFAULT_SUSPENSION[type], create: true })
    }
  }
  if (drafts.length === 0) emit('saved')
  else sanctionDrafts.value = drafts
}

async function createSanctions() {
  busy.value = true
  error.value = ''
  try {
    for (const d of sanctionDrafts.value ?? []) {
      if (!d.create) continue
      await seasonApi.createSanction(seasonId.value!, {
        playerId: d.playerId, tournamentId: props.match.tournamentId, type: d.type, matchesSuspended: d.matches,
        matchesServed: 0, status: null, notes: null,
      })
    }
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}

// ---- suspended while being played: score so far and minutes left
const suspending = ref(false)
const remaining = ref(30)
async function suspend() {
  busy.value = true
  error.value = ''
  try {
    await matchApi.suspend(props.match.id, {
      homeGoals: goals.value.home ?? score.value.home, awayGoals: goals.value.away ?? score.value.away, remainingMinutes: remaining.value,
    })
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
async function unsuspend() {
  busy.value = true
  try {
    await matchApi.unsuspend(props.match.id)
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}

// ---- swap the time with another match of the edition (captains agreed; the organizer makes it official)
const others = ref<Match[]>([])
const swapWith = ref<number | null>(null)
async function loadOthers() {
  if (others.value.length) return
  const all = await tournamentApi.fixtures(props.match.tournamentId)
  others.value = all.filter((m) => m.id !== props.match.id && m.status !== 'FINISHED' && m.scheduledAt)
}
const otherLabel = (m: Match) => {
  const when = m.scheduledAt ? new Date(m.scheduledAt).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
  return `${when} · ${m.homeTeam.name} vs ${m.awayTeam.name}${m.venue ? ` · ${m.venue}` : ''}`
}
/** The same match day first (that is where swaps happen) */
const swapOptions = computed(() => {
  const day = props.match.scheduledAt?.slice(0, 10)
  return [...others.value].sort((a, b) => Number(b.scheduledAt?.slice(0, 10) === day) - Number(a.scheduledAt?.slice(0, 10) === day)
    || (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''))
})
async function swap() {
  if (!swapWith.value) return
  busy.value = true
  error.value = ''
  try {
    await matchApi.swapSchedule(props.match.id, swapWith.value)
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
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
      played: walkover.value ? [] : [...new Set([...played.value, ...withEvents.value])],
    })
    offerSanctions()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseModal :title="t('results.title')" width="820px" @close="emit('close')">
    <div v-if="sanctionDrafts" class="sanctions">
      <h3>{{ t('results.sanctionsTitle') }}</h3>
      <p class="muted small">{{ t('results.sanctionsHelp') }}</p>
      <div v-for="d in sanctionDrafts" :key="d.playerId" class="sanction-row">
        <label class="check"><input v-model="d.create" type="checkbox" /> {{ d.name }}</label>
        <span class="muted">{{ t(`season.types.${d.type}`) }}</span>
        <label class="fechas">{{ t('results.sanctionMatches') }}<input v-model.number="d.matches" class="input mins" type="number" min="1" max="20" /></label>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <footer class="actions">
        <span class="spacer" />
        <button type="button" class="btn secondary" @click="emit('saved')">{{ t('results.sanctionsSkip') }}</button>
        <button type="button" class="btn" :disabled="busy" @click="createSanctions">{{ t('results.sanctionsCreate') }}</button>
      </footer>
    </div>
    <form v-else @submit.prevent="save">

      <div class="scoreboard">
        <span class="team"><TeamBadge :team="teams.home" :size="40" /> {{ teams.home.name }}</span>
        <strong class="score">{{ score.home }} - {{ score.away }}</strong>
        <span class="team right">{{ teams.away.name }} <TeamBadge :team="teams.away" :size="40" /></span>
      </div>
      <p v-if="match.suspended && match.status !== 'FINISHED'" class="suspended">
        {{ t('results.suspendedInfo', { n: match.remainingMinutes ?? 0 }) }}
        <button type="button" class="link" :disabled="busy" @click="unsuspend">{{ t('results.unsuspend') }}</button>
      </p>
      <p v-if="walkover" class="hint">{{ present.home || present.away ? t('results.walkoverHint') : t('results.noShowHint') }}</p>
      <div v-else class="typed">
        <span>{{ t('results.score') }}</span>
        <input v-model.number="goals.home" class="input" type="number" min="0" :aria-label="`${t('results.score')} ${teams.home.name}`" />
        <span>-</span>
        <input v-model.number="goals.away" class="input" type="number" min="0" :aria-label="`${t('results.score')} ${teams.away.name}`" />
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
                <option v-for="p in rosters[side]" :key="p.id" :value="p.id">{{ p.jerseyNo }} · {{ p.fullName }}<template v-if="p.gone"> {{ t('results.gonePlayer') }}</template></option>
              </select>
              <div class="type-buttons">
                <button v-for="type in EVENT_TYPES" :key="type" type="button" :class="['btn', 'secondary', 'small-btn', type]"
                        @click="addEvent(side, type)">
                  + {{ t(`results.types.${type}`) }}
                </button>
              </div>

              <div class="played">
                <div class="played-head">
                  <strong>{{ t('results.played', { n: playedCount(side) }) }}</strong>
                  <button type="button" class="link" @click="setAll(side, true)">{{ t('results.playedAll') }}</button>
                  <button type="button" class="link" @click="setAll(side, false)">{{ t('results.playedNone') }}</button>
                </div>
                <label
                  v-for="p in sheet(side)" :key="p.id" :class="['chip', { on: isPlayed(p.id), locked: withEvents.has(p.id) }]"
                  :title="withEvents.has(p.id) ? t('results.playedLocked') : undefined"
                >
                  <input
                    type="checkbox" :checked="isPlayed(p.id)" :disabled="withEvents.has(p.id)"
                    @change="togglePlayed(p.id)"
                  >
                  {{ p.jerseyNo }} · {{ p.fullName }}
                </label>
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
        <div v-if="match.status !== 'FINISHED'" class="swap" @focusin="loadOthers" @mouseenter="loadOthers">
          <label>
            {{ t('results.swapWith') }}
            <select v-model="swapWith" class="select" @focus="loadOthers">
              <option :value="null" disabled>{{ t('results.swapPick') }}</option>
              <option v-for="m in swapOptions" :key="m.id" :value="m.id">{{ otherLabel(m) }}</option>
            </select>
          </label>
          <button type="button" class="btn secondary" :disabled="busy || !swapWith" @click="swap">{{ t('results.swap') }}</button>
        </div>
        <div v-if="match.status !== 'FINISHED'" class="suspend">
          <button v-if="!suspending" type="button" class="btn secondary" @click="suspending = true">{{ t('results.suspend') }}</button>
          <template v-else>
            <span class="muted small">{{ t('results.suspendHelp', { home: goals.home ?? score.home, away: goals.away ?? score.away }) }}</span>
            <label>{{ t('results.remaining') }}<input v-model.number="remaining" class="input mins" type="number" min="1" max="60" /></label>
            <button type="button" class="btn" :disabled="busy" @click="suspend">{{ t('results.suspendConfirm') }}</button>
          </template>
        </div>
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
.swap, .suspend { display: flex; align-items: flex-end; gap: 10px; flex-wrap: wrap; margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--border); }
.swap label, .suspend label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.swap .select { min-width: 280px; }
.input.mins { width: 80px; }
.suspended { background: #fff4e0; color: #8a5300; border-radius: 8px; padding: 8px 12px; font-size: 14px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.suspended .link, .link { background: none; border: 0; padding: 0; color: var(--navy); font: inherit; font-size: 13px; text-decoration: underline; cursor: pointer; }
.sanctions h3 { font-size: 16px; color: var(--navy); margin: 0 0 4px; }
.sanction-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; padding: 8px 0; border-bottom: 1px solid var(--border); }
.sanction-row .fechas { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); }
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
.played { margin-top: 12px; border-top: 1px solid var(--border); padding-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; }
.played-head { width: 100%; display: flex; align-items: center; gap: 12px; font-size: 13px; }
.played-head .link { background: none; border: 0; padding: 0; color: var(--navy); font: inherit; font-size: 12px; text-decoration: underline; cursor: pointer; }
.chip {
  display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px 3px 6px; border-radius: 999px; cursor: pointer;
  border: 1px solid var(--border); background: #fff; font-size: 13px; color: var(--muted);
}
.chip input { margin: 0; }
.chip.on { border-color: var(--navy); color: var(--text); }
.chip.locked { cursor: default; background: var(--bg); }
.small-btn { padding: 4px 10px; font-size: 13px; }
.small { font-size: 13px; }
.penalties { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 16px; font-weight: 500; }
.penalties .input { width: 70px; text-align: center; }
.actions { display: flex; align-items: center; gap: 10px; margin-top: 18px; }
.spacer { flex: 1; }
</style>
