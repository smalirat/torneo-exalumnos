<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import MatchCard from '../components/MatchCard.vue'
import MatchResultEditor from '../components/MatchResultEditor.vue'
import StandingsTable from '../components/StandingsTable.vue'
import ZonePicker from '../components/ZonePicker.vue'
import { tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, Match, StandingRow } from '../api/types'
import { editionBySlug } from '../editions'
import { t } from '../i18n'
import { isAdmin } from '../auth'

const route = useRoute()
const tournamentId = computed(() => editionBySlug(route.params.edicion)?.id ?? 0)

const categories = ref<Category[] | null>(null)
const zoneId = ref<number | null>(null)
const rows = ref<StandingRow[]>([])
const matches = ref<Match[]>([])

watch(tournamentId, async (id) => {
  categories.value = await tournamentApi.categories(id)
}, { immediate: true })

async function reloadZone() {
  const id = zoneId.value
  if (!id) { rows.value = []; matches.value = []; return }
  ;[rows.value, matches.value] = await Promise.all([zoneApi.standings(id), zoneApi.fixtures(id)])
}
watch(zoneId, reloadZone)

// --- admin: result editor ---
const editing = ref<Match | null>(null)
async function onSaved() {
  editing.value = null
  await reloadZone()
}

// --- admin: fixture tools ---
const tool = ref<'generate' | 'add' | null>(null)
const toolError = ref('')
const nextSunday = () => {
  const d = new Date()
  d.setDate(d.getDate() + ((7 - d.getDay()) % 7 || 7))
  return `${d.toISOString().slice(0, 10)}T10:00`
}
const gen = ref({ start: nextSunday(), minutes: 90 })
const add = ref({ home: null as number | null, away: null as number | null, when: '' })

function openTool(which: 'generate' | 'add') {
  toolError.value = ''
  tool.value = tool.value === which ? null : which
  add.value = { home: rows.value[0]?.team.id ?? null, away: rows.value[1]?.team.id ?? null, when: '' }
}

async function runTool(action: () => Promise<unknown>) {
  toolError.value = ''
  try {
    await action()
    tool.value = null
    await reloadZone()
  } catch (e) {
    toolError.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

const generate = () => runTool(() => zoneApi.roundRobin(zoneId.value!, `${gen.value.start}:00`, gen.value.minutes))
const addMatch = () => runTool(() =>
  zoneApi.addMatch(zoneId.value!, add.value.home!, add.value.away!, add.value.when ? `${add.value.when}:00` : null))
</script>

<template>
  <TournamentLayout>
    <p v-if="categories === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="categories.length === 0" class="muted">{{ t('standings.noCategories') }}</p>

    <div v-else class="layout">
      <section class="card table-card">
        <ZonePicker v-model="zoneId" :categories="categories" class="selectors" />
        <StandingsTable :rows="rows" />
      </section>

      <aside class="games">
        <header>{{ t('standings.games') }}</header>
        <div v-if="isAdmin && zoneId" class="tools">
          <p v-if="rows.length < 2" class="muted small">{{ t('standings.needTwoTeams') }}</p>
          <template v-else>
            <div class="tool-buttons">
              <button class="btn secondary" @click="openTool('generate')">{{ t('standings.generate') }}</button>
              <button class="btn" @click="openTool('add')">{{ t('standings.addMatch') }}</button>
            </div>
            <form v-if="tool === 'generate'" class="tool-form" @submit.prevent="generate">
              <label>{{ t('standings.start') }}<input v-model="gen.start" class="input" type="datetime-local" required /></label>
              <label>{{ t('standings.minutesBetween') }}<input v-model.number="gen.minutes" class="input" type="number" min="0" required /></label>
              <button class="btn">{{ t('standings.save') }}</button>
            </form>
            <form v-if="tool === 'add'" class="tool-form" @submit.prevent="addMatch">
              <label>{{ t('standings.home') }}
                <select v-model="add.home" class="select" required>
                  <option v-for="r in rows" :key="r.team.id" :value="r.team.id">{{ r.team.name }}</option>
                </select>
              </label>
              <label>{{ t('standings.away') }}
                <select v-model="add.away" class="select" required>
                  <option v-for="r in rows" :key="r.team.id" :value="r.team.id">{{ r.team.name }}</option>
                </select>
              </label>
              <label>{{ t('standings.when') }}<input v-model="add.when" class="input" type="datetime-local" /></label>
              <button class="btn">{{ t('standings.save') }}</button>
            </form>
            <p v-if="toolError" class="error">{{ toolError }}</p>
          </template>
        </div>
        <div class="games-body">
          <p v-if="matches.length === 0" class="muted">{{ t('standings.noGames') }}</p>
          <MatchCard v-for="m in matches" :key="m.id" :match="m" :clickable="isAdmin" @click="isAdmin && (editing = m)" />
        </div>
      </aside>
    </div>
    <MatchResultEditor v-if="editing" :match="editing" @saved="onSaved" @close="editing = null" />
  </TournamentLayout>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr); gap: 20px; align-items: start; }
.selectors { margin-bottom: 14px; }
.games { background: #fff; border-radius: var(--radius); overflow: hidden; box-shadow: var(--shadow); }
.games header { background: var(--navy); color: #fff; font-size: 20px; padding: 18px; text-align: center; }
.games-body { padding: 12px 16px; }
.tools { padding: 14px 16px 0; }
.tool-buttons { display: flex; gap: 8px; justify-content: center; }
.tool-form { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.tool-form label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.small { font-size: 13px; }
</style>
