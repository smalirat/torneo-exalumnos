<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import RoundNav from '../components/RoundNav.vue'
import MatchCard from '../components/MatchCard.vue'
import MatchResultEditor from '../components/MatchResultEditor.vue'
import ZonePicker from '../components/ZonePicker.vue'
import RoundBuilder from '../components/RoundBuilder.vue'
import InterzonalBuilder from '../components/InterzonalBuilder.vue'
import BaseModal from '../components/BaseModal.vue'
import AppIcon from '../components/AppIcon.vue'
import { tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, Match, TeamRef } from '../api/types'
import { isAdmin } from '../auth'
import { editionBySlug } from '../editions'
import { t } from '../i18n'
import { groupRounds } from '../rounds'

// Zone fixture by "fecha" (match day): ‹ Fecha N › navigation, results and upcoming matches
const route = useRoute()
const tournamentId = computed(() => editionBySlug(route.params.edicion)?.id ?? 0)
const categories = ref<Category[] | null>(null)
const zoneId = ref<number | null>(null)
const matches = ref<Match[]>([])
const index = ref(0)
const editing = ref<Match | null>(null)

const rounds = computed(() => groupRounds(matches.value))
const round = computed(() => rounds.value[index.value] ?? null)

watch(tournamentId, async (id) => { categories.value = await tournamentApi.categories(id) }, { immediate: true })

const teams = ref<TeamRef[]>([])

async function load(keepIndex = false) {
  matches.value = zoneId.value ? await zoneApi.fixtures(zoneId.value) : []
  teams.value = zoneId.value ? (await zoneApi.registrations(zoneId.value)).map((r) => r.team) : []
  if (keepIndex) return
  // Open on the first round that still has matches to play; otherwise the last one
  const pending = rounds.value.findIndex((r) => r.matches.some((m) => m.status !== 'FINISHED'))
  index.value = pending >= 0 ? pending : Math.max(rounds.value.length - 1, 0)
}
watch(zoneId, () => load())

async function onSaved() {
  editing.value = null
  await load(true)
}

// --- admin: build the fixture (a whole match day, or everyone against everyone) ---
const building = ref(false)
async function onRoundSaved(roundNumber: number) {
  building.value = false
  await load()
  // Show the match day just created
  const i = rounds.value.findIndex((r) => r.number === roundNumber)
  if (i >= 0) index.value = i
}

// Interzonal / intercategory match days (the whole edition)
const crossing = ref(false)
const zoneCount = computed(() => (categories.value ?? []).reduce((n, c) => n + c.zones.length, 0))
async function onCrossSaved(roundNumber: number | null) {
  crossing.value = false
  await load()
  const i = rounds.value.findIndex((r) => r.number === roundNumber)
  if (i >= 0) index.value = i
}

const generating = ref(false)
const gen = ref({ start: '', minutes: 70 })
const genError = ref('')
function openGenerate() {
  const d = new Date()
  d.setDate(d.getDate() + ((7 - d.getDay()) % 7 || 7))
  gen.value = { start: `${d.toISOString().slice(0, 10)}T09:00`, minutes: 70 }
  genError.value = ''
  generating.value = true
}
async function generate() {
  genError.value = ''
  try {
    await zoneApi.roundRobin(zoneId.value!, `${gen.value.start}:00`, gen.value.minutes)
    generating.value = false
    await load()
  } catch (e) {
    genError.value = e instanceof Error ? e.message : t('common.genericError')
  }
}
</script>

<template>
  <TournamentLayout>
    <p v-if="categories === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="categories.length === 0" class="muted">{{ t('standings.noCategories') }}</p>

    <section v-else class="card fixture">
      <div class="head">
        <ZonePicker v-model="zoneId" :categories="categories" />
        <div v-if="isAdmin && zoneId" class="tools">
          <button class="btn secondary" :disabled="teams.length < 2" @click="openGenerate">{{ t('standings.generate') }}</button>
          <button v-if="zoneCount > 1" class="btn secondary" @click="crossing = true">{{ t('interzonal.open') }}</button>
          <button class="btn" :disabled="teams.length < 2" @click="building = true">
            <AppIcon name="plus" :size="18" /> {{ t('newRound.open') }}
          </button>
        </div>
      </div>
      <p v-if="isAdmin && zoneId && teams.length < 2" class="muted small-left">{{ t('standings.needTwoTeams') }}</p>

      <p v-if="rounds.length === 0" class="muted empty">{{ t('standings.noGames') }}</p>
      <template v-else>
        <RoundNav v-model="index" :rounds="rounds" />

        <div class="list">
          <MatchCard
            v-for="m in round?.matches ?? []" :key="m.id" :match="m" date-format="time"
            :clickable="isAdmin" @click="isAdmin && (editing = m)"
          />
        </div>
        <p class="muted small">{{ t('fixture.progress', { played: round?.matches.filter((m) => m.status === 'FINISHED').length ?? 0, total: round?.matches.length ?? 0 }) }}</p>
      </template>
    </section>

    <MatchResultEditor v-if="editing" :match="editing" @saved="onSaved" @close="editing = null" />
    <InterzonalBuilder v-if="crossing && categories" :tournament-id="tournamentId" :categories="categories" @saved="onCrossSaved" @close="crossing = false" />
    <RoundBuilder v-if="building && zoneId" :zone-id="zoneId" :teams="teams" :matches="matches" @saved="onRoundSaved" @close="building = false" />
    <BaseModal v-if="generating" :title="t('standings.generate')" width="440px" @close="generating = false">
      <p class="muted">{{ t('fixture.generateHelp', { n: teams.length }) }}</p>
      <form id="generate-form" class="gen" @submit.prevent="generate">
        <label>{{ t('standings.start') }}<input v-model="gen.start" class="input" type="datetime-local" required /></label>
        <label>{{ t('standings.minutesBetween') }}<input v-model.number="gen.minutes" class="input" type="number" min="0" required /></label>
      </form>
      <p v-if="genError" class="error">{{ genError }}</p>
      <template #footer>
        <button type="button" class="btn secondary" @click="generating = false">{{ t('tournaments.cancel') }}</button>
        <button class="btn" form="generate-form">{{ t('standings.save') }}</button>
      </template>
    </BaseModal>
  </TournamentLayout>
</template>

<style scoped>
.fixture { max-width: 760px; }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.tools { display: flex; gap: 8px; flex-wrap: wrap; }
.small-left { font-size: 13px; margin-top: 8px; }
.gen { display: flex; flex-direction: column; gap: 10px; }
.gen label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.empty { margin-top: 16px; }
.list { margin-top: 8px; }
.small { font-size: 13px; text-align: center; margin-top: 12px; }
</style>
