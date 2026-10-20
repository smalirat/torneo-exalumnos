<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import InterzonalFields from './InterzonalFields.vue'
import { tournamentApi } from '../api/endpoints'
import type { Category, Match } from '../api/types'
import { defaultPairs, zoneOptions } from '../groups'
import { groupRounds } from '../rounds'
import { t } from '../i18n'

// Admin: interzonal (same category) and intercategory match days. The admin says which zones play each other
// and how many match days; the teams are crossed by the positions of the tables now (preview before creating).
const props = defineProps<{ tournamentId: number; categories: Category[] }>()
const emit = defineEmits<{ close: []; saved: [round: number | null] }>()

const options = computed(() => zoneOptions(props.categories))

const pairs = ref(defaultPairs(props.categories))
const rounds = ref(1)
const crossing = ref<'SAME' | 'INVERSE'>('SAME')
const firstRound = ref<number | null>(null)
const start = ref(nextSunday())
const minutes = ref(70)
const venue = ref('')
const preview = ref<Match[] | null>(null)
const error = ref('')
const busy = ref(false)

function nextSunday() {
  const d = new Date()
  d.setDate(d.getDate() + ((7 - d.getDay()) % 7 || 7))
  return `${d.toISOString().slice(0, 10)}T09:00`
}

const body = () => ({
  pairs: pairs.value, rounds: rounds.value, firstRound: firstRound.value || null,
  start: start.value ? `${start.value}:00` : null, minutesBetween: minutes.value,
  crossing: crossing.value, venue: venue.value.trim() || null,
})

async function run(save: boolean) {
  error.value = ''
  busy.value = true
  try {
    const matches = await tournamentApi.interzonal(props.tournamentId, !save, body())
    if (save) emit('saved', matches[0]?.round ?? null)
    else preview.value = matches
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    preview.value = null
  } finally {
    busy.value = false
  }
}

const previewRounds = computed(() => groupRounds(preview.value ?? []))
/** Any change makes the preview stale */
const touch = () => { preview.value = null }
</script>

<template>
  <BaseModal :title="t('interzonal.title')" width="680px" @close="emit('close')">
    <InterzonalFields v-model:pairs="pairs" v-model:rounds="rounds" v-model:crossing="crossing" :zones="options" @change="touch" />

    <div class="grid">
      <label>{{ t('interzonal.firstRound') }}<input v-model.number="firstRound" class="input" type="number" min="1" :placeholder="t('interzonal.auto')" @input="touch" /></label>
      <label>{{ t('newRound.start') }}<input v-model="start" class="input" type="datetime-local" @input="touch" /></label>
      <label>{{ t('newRound.minutes') }}<input v-model.number="minutes" class="input" type="number" min="0" @input="touch" /></label>
      <label>{{ t('newRound.venue') }}<input v-model="venue" class="input" maxlength="60" @input="touch" /></label>
    </div>

    <p class="muted small">{{ t('interzonal.help') }}</p>

    <div v-if="preview" class="preview">
      <div v-for="r in previewRounds" :key="r.key" class="round">
        <h4>{{ t('fixture.round', { n: r.number ?? '' }) }}<template v-if="r.label"> · {{ r.label }}</template></h4>
        <p v-for="(m, i) in r.matches" :key="i" class="line">
          <span>{{ m.homeTeam.name }}</span><span class="muted">vs</span><span>{{ m.awayTeam.name }}</span>
        </p>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <template #footer>
      <button type="button" class="btn secondary" @click="emit('close')">{{ t('tournaments.cancel') }}</button>
      <button v-if="!preview" type="button" class="btn" :disabled="busy || !pairs.length" @click="run(false)">{{ t('interzonal.preview') }}</button>
      <button v-else type="button" class="btn" :disabled="busy" @click="run(true)">{{ t('interzonal.create', { n: preview.length }) }}</button>
    </template>
  </BaseModal>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; margin: 12px 0 0; }
.grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.small { font-size: 12px; margin: 6px 0 0; }
.preview { margin-top: 12px; border-top: 1px solid var(--border); padding-top: 8px; max-height: 260px; overflow-y: auto; }
.round h4 { font-size: 13px; text-transform: uppercase; color: var(--muted); margin: 8px 0 4px; }
.line { display: grid; grid-template-columns: 1fr auto 1fr; gap: 10px; margin: 0; padding: 3px 0; font-size: 14px; }
.line span:last-child { text-align: right; }
</style>
