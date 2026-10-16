<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import TeamBadge from '../components/TeamBadge.vue'
import { tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, Match, StandingRow } from '../api/types'
import { t } from '../i18n'
import { isAdmin } from '../auth'

const route = useRoute()
const router = useRouter()
const tournamentId = computed(() => Number(route.params.id))

const categories = ref<Category[] | null>(null)
const categoryId = ref<number | null>(null)
const zoneId = ref<number | null>(null)
const rows = ref<StandingRow[]>([])
const matches = ref<Match[]>([])

const category = computed(() => categories.value?.find((c) => c.id === categoryId.value) ?? null)

watch(tournamentId, async (id) => {
  categories.value = await tournamentApi.categories(id)
  const fromQuery = Number(route.query.categoria)
  categoryId.value = categories.value.find((c) => c.id === fromQuery)?.id ?? categories.value[0]?.id ?? null
}, { immediate: true })

// Changing category resets the zone to its first one and keeps the URL shareable
watch(categoryId, (id) => {
  zoneId.value = category.value?.zones[0]?.id ?? null
  if (id) router.replace({ query: { categoria: id } })
})

async function reloadZone() {
  const id = zoneId.value
  if (!id) { rows.value = []; matches.value = []; return }
  ;[rows.value, matches.value] = await Promise.all([zoneApi.standings(id), zoneApi.fixtures(id)])
}
watch(zoneId, reloadZone)

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

const formatDate = (d: string | null) =>
  d ? new Date(d).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
</script>

<template>
  <TournamentLayout>
    <p v-if="categories === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="categories.length === 0" class="muted">{{ t('standings.noCategories') }}</p>

    <div v-else class="layout">
      <section class="card table-card">
        <div class="selectors">
          <select v-model="categoryId" class="select" :aria-label="t('standings.category')">
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <!-- Zone selector only when the category has more than one zone -->
          <select
            v-if="category && category.zoneCount > 1" v-model="zoneId" class="select" :aria-label="t('standings.zone')"
          >
            <option v-for="z in category.zones" :key="z.id" :value="z.id">{{ z.name }}</option>
          </select>
        </div>

        <p v-if="rows.length === 0" class="muted">{{ t('standings.noTeams') }}</p>
        <table v-else>
          <thead>
            <tr>
              <th>{{ t('standings.pos') }}</th>
              <th class="left">{{ t('standings.team') }}</th>
              <th>{{ t('standings.pts') }}</th>
              <th>{{ t('standings.played') }}</th>
              <th>{{ t('standings.won') }}</th>
              <th>{{ t('standings.drawn') }}</th>
              <th>{{ t('standings.lost') }}</th>
              <th>{{ t('standings.gf') }}</th>
              <th>{{ t('standings.ga') }}</th>
              <th>{{ t('standings.diff') }}</th>
              <th :title="t('standings.presenceHint')">{{ t('standings.presence') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.team.id">
              <td><span class="pos">{{ r.position }}</span></td>
              <td class="left"><span class="team"><TeamBadge :name="r.team.name" /> {{ r.team.name }}</span></td>
              <td class="pts">{{ r.points }}</td>
              <td>{{ r.played }}</td>
              <td>{{ r.won }}</td>
              <td>{{ r.drawn }}</td>
              <td>{{ r.lost }}</td>
              <td>{{ r.goalsFor }}</td>
              <td>{{ r.goalsAgainst }}</td>
              <td>{{ r.goalDiff }}</td>
              <td>{{ r.presencePoints }}</td>
            </tr>
          </tbody>
        </table>
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
          <div v-for="m in matches" :key="m.id" class="match">
            <span class="date muted">{{ formatDate(m.scheduledAt) }}</span>
            <div class="line">
              <span class="side"><TeamBadge :name="m.homeTeam.name" :size="22" /> {{ m.homeTeam.name }}</span>
              <strong v-if="m.status === 'FINISHED'" class="score">
                {{ m.homeGoals }} - {{ m.awayGoals }}
              </strong>
              <span v-else class="score muted">vs</span>
              <span class="side right">{{ m.awayTeam.name }} <TeamBadge :name="m.awayTeam.name" :size="22" /></span>
            </div>
            <span v-if="!m.homePresent || !m.awayPresent" class="wo">{{ t('standings.walkover') }}</span>
          </div>
        </div>
      </aside>
    </div>
  </TournamentLayout>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr); gap: 20px; align-items: start; }
.selectors { display: flex; gap: 10px; margin-bottom: 14px; }
.selectors .select { width: auto; min-width: 180px; border-radius: 999px; }
table { width: 100%; border-collapse: separate; border-spacing: 0 4px; font-size: 14px; }
th { font-weight: 500; text-align: center; padding: 4px 6px; }
td { text-align: center; padding: 6px; }
.left { text-align: left; }
tbody tr:nth-child(even) td { background: var(--row-alt); }
.pos {
  display: inline-flex; width: 40px; height: 40px; align-items: center; justify-content: center;
  background: var(--navy); color: #fff; border-radius: 6px;
}
.team { display: inline-flex; align-items: center; gap: 8px; }
.pts { font-weight: 700; font-size: 15px; }
.games { background: #fff; border-radius: var(--radius); overflow: hidden; box-shadow: var(--shadow); }
.games header { background: var(--navy); color: #fff; font-size: 20px; padding: 18px; text-align: center; }
.games-body { padding: 12px 16px; }
.match { padding: 10px 0; border-bottom: 1px solid var(--border); font-size: 14px; }
.match:last-child { border-bottom: 0; }
.date { font-size: 12px; }
.line { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 8px; margin-top: 4px; }
.side { display: inline-flex; align-items: center; gap: 6px; }
.side.right { justify-content: flex-end; }
.score { min-width: 44px; text-align: center; }
.wo { font-size: 11px; color: var(--danger); font-weight: 700; }
.tools { padding: 14px 16px 0; }
.tool-buttons { display: flex; gap: 8px; justify-content: center; }
.tool-form { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.tool-form label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.small { font-size: 13px; }
</style>
