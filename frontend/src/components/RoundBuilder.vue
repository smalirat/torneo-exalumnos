<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import AppIcon from './AppIcon.vue'
import { zoneApi } from '../api/endpoints'
import type { Match, TeamRef } from '../api/types'
import { t } from '../i18n'

// Admin: a whole match day ("Fecha N") of a zone at once. Pairs of teams, the time of the first match and the
// minutes between matches, the field and pitch. "Armar cruces" pairs the teams that are free in this round,
// preferring rivals they have not played yet.
const props = defineProps<{ zoneId: number; teams: TeamRef[]; matches: Match[] }>()
const emit = defineEmits<{ close: []; saved: [round: number] }>()

interface Pair { home: number | null; away: number | null }

const nextRound = computed(() => Math.max(0, ...props.matches.map((m) => m.round ?? 0)) + 1)
const round = ref<number>(nextRound.value)
const start = ref(nextSunday())
const minutes = ref(70)
const venue = ref('')
const pitch = ref('')
const pairs = ref<Pair[]>([{ home: null, away: null }])
const error = ref('')
const busy = ref(false)

function nextSunday() {
  const d = new Date()
  d.setDate(d.getDate() + ((7 - d.getDay()) % 7 || 7))
  return `${d.toISOString().slice(0, 10)}T09:00`
}

/** Teams that already have a match in this round (saved ones and the rows being built) */
const busyInRound = computed(() => {
  const ids = new Set<number>()
  for (const m of props.matches) if (m.round === round.value) { ids.add(m.homeTeam.id); ids.add(m.awayTeam.id) }
  return ids
})
const used = computed(() => {
  const ids = new Set(busyInRound.value)
  for (const p of pairs.value) { if (p.home) ids.add(p.home); if (p.away) ids.add(p.away) }
  return ids
})
const free = computed(() => props.teams.filter((x) => !used.value.has(x.id)))

const playedBefore = computed(() => {
  const set = new Set<string>()
  for (const m of props.matches) set.add([m.homeTeam.id, m.awayTeam.id].sort((a, b) => a - b).join('-'))
  return set
})

function autoPair() {
  const filled = pairs.value.filter((p) => p.home && p.away)
  const taken = new Set(busyInRound.value)
  for (const p of filled) { taken.add(p.home!); taken.add(p.away!) }
  const left = props.teams.map((x) => x.id).filter((id) => !taken.has(id))
  const out: Pair[] = [...filled]
  while (left.length >= 2) {
    const home = left.shift()!
    let i = left.findIndex((away) => !playedBefore.value.has([home, away].sort((a, b) => a - b).join('-')))
    if (i < 0) i = 0
    out.push({ home, away: left.splice(i, 1)[0] })
  }
  pairs.value = out.length ? out : [{ home: null, away: null }]
}

const addRow = () => pairs.value.push({ home: null, away: null })
const removeRow = (i: number) => pairs.value.splice(i, 1)

/** Time of the i-th match: the first one plus i times the gap */
function timeOf(i: number): string | null {
  if (!start.value) return null
  const d = new Date(`${start.value}:00`)
  d.setMinutes(d.getMinutes() + i * minutes.value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
const timeLabel = (i: number) => timeOf(i)?.slice(11, 16) ?? ''

const ready = computed(() => pairs.value.some((p) => p.home && p.away))
const resting = computed(() => free.value.filter(() => pairs.value.every((p) => p.home && p.away)))

async function save() {
  error.value = ''
  const rows = pairs.value.filter((p) => p.home && p.away)
  if (rows.some((p) => p.home === p.away)) { error.value = t('newRound.sameTeam'); return }
  busy.value = true
  try {
    await zoneApi.addRound(props.zoneId, rows.map((p, i) => ({
      homeTeamId: p.home!, awayTeamId: p.away!, scheduledAt: timeOf(i), round: round.value || null,
      venue: venue.value.trim() || null, pitch: pitch.value.trim() || null,
    })))
    emit('saved', round.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseModal :title="t('newRound.title')" width="640px" @close="emit('close')">
    <div class="grid">
      <label>{{ t('newRound.round') }}<input v-model.number="round" class="input" type="number" min="1" required /></label>
      <label>{{ t('newRound.start') }}<input v-model="start" class="input" type="datetime-local" /></label>
      <label>{{ t('newRound.minutes') }}<input v-model.number="minutes" class="input" type="number" min="0" /></label>
      <label>{{ t('newRound.venue') }}<input v-model="venue" class="input" maxlength="60" /></label>
      <label>{{ t('newRound.pitch') }}<input v-model="pitch" class="input" maxlength="20" /></label>
    </div>

    <div class="pairs-head">
      <h3>{{ t('newRound.matches') }}</h3>
      <button type="button" class="btn secondary small-btn" @click="autoPair">
        <AppIcon name="auto" :size="16" /> {{ t('newRound.autoPair') }}
      </button>
    </div>
    <div v-for="(p, i) in pairs" :key="i" class="pair">
      <span class="time">{{ timeLabel(i) }}</span>
      <select v-model="p.home" class="select" :aria-label="t('standings.home')">
        <option :value="null" disabled>{{ t('standings.home') }}</option>
        <option v-for="x in teams" :key="x.id" :value="x.id" :disabled="x.id !== p.home && used.has(x.id)">{{ x.name }}</option>
      </select>
      <span class="vs">vs</span>
      <select v-model="p.away" class="select" :aria-label="t('standings.away')">
        <option :value="null" disabled>{{ t('standings.away') }}</option>
        <option v-for="x in teams" :key="x.id" :value="x.id" :disabled="x.id !== p.away && used.has(x.id)">{{ x.name }}</option>
      </select>
      <button type="button" class="icon-btn" :title="t('newRound.removeRow')" :aria-label="t('newRound.removeRow')" @click="removeRow(i)">
        <AppIcon name="close" :size="18" />
      </button>
    </div>
    <button type="button" class="add-row" @click="addRow"><AppIcon name="plus" :size="16" /> {{ t('newRound.addRow') }}</button>
    <p v-if="resting.length" class="muted small">{{ t('newRound.resting', { names: resting.map((x) => x.name).join(', ') }) }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <template #footer>
      <button type="button" class="btn secondary" @click="emit('close')">{{ t('tournaments.cancel') }}</button>
      <button type="button" class="btn" :disabled="busy || !ready" @click="save">{{ t('newRound.save') }}</button>
    </template>
  </BaseModal>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin-bottom: 14px; }
.grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.pairs-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.pairs-head h3 { font-size: 15px; margin: 0; color: var(--navy); }
.pair { display: grid; grid-template-columns: 44px 1fr auto 1fr 36px; align-items: center; gap: 8px; margin-bottom: 6px; }
.time { font-size: 13px; color: var(--muted); font-variant-numeric: tabular-nums; }
.vs { font-size: 13px; color: var(--muted); }
.add-row {
  display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px; border-radius: 999px; cursor: pointer; margin: 4px 0 8px;
  border: 1px dashed var(--border); background: none; color: var(--navy); font: inherit; font-size: 13px; font-weight: 500;
}
.small { font-size: 13px; }
.small-btn { padding: 6px 12px; font-size: 13px; }
@media (max-width: 560px) {
  .pair { grid-template-columns: 1fr auto 1fr 32px; }
  .time { display: none; }
}
</style>
