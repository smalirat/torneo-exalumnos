<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import BaseModal from '../components/BaseModal.vue'
import TeamBadge from '../components/TeamBadge.vue'
import AppIcon from '../components/AppIcon.vue'
import InterzonalFields from '../components/InterzonalFields.vue'
import { tournamentApi } from '../api/endpoints'
import type { BuildDefinition, BuildProposal, BuildTeam, Tournament } from '../api/types'
import { editionBySlug, loadEditions, sortEditions } from '../editions'
import { panelEditionPath, PANEL } from '../paths'
import { t } from '../i18n'

// Admin: builds the edition from the results of another one (by default the previous). The server proposes
// the category of every team (positions, promotions); doubts come as questions. Then the teams are spread
// into the zones of each category and, optionally, the fixture is generated.
const route = useRoute()
const edition = computed(() => editionBySlug(route.params.edicion))
const editions = ref<Tournament[]>([])
const sourceId = ref<number | null>(null)
const proposal = ref<BuildProposal | null>(null)
const error = ref('')
const loading = ref(false)

interface Row extends BuildTeam { seed: number; target: number | null; answered: boolean }
const rows = ref<Row[]>([])

watch(edition, async (e) => {
  if (!e) return
  editions.value = sortEditions(await loadEditions()).filter((x) => x.id !== e.id)
  // Default source: the edition right before this one (the list is newest first)
  const all = sortEditions(await loadEditions())
  const i = all.findIndex((x) => x.id === e.id)
  sourceId.value = all[i + 1]?.id ?? editions.value[0]?.id ?? null
}, { immediate: true })

watch(sourceId, async (id) => {
  proposal.value = null
  rows.value = []
  error.value = ''
  done.value = null
  if (!id || !edition.value) return
  loading.value = true
  try {
    const p = await tournamentApi.buildProposal(edition.value.id, id)
    proposal.value = p
    rows.value = p.teams.map((r, seed) => ({ ...r, seed, target: r.included ? r.categoryId : null, answered: !r.question }))
    definitions.value = p.definitions.map((d) => ({ ...d }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    loading.value = false
  }
})

const questions = computed(() => rows.value.filter((r) => r.question))
const unanswered = computed(() => questions.value.filter((r) => !r.answered).length)
const answerAll = () => { for (const r of rows.value) r.answered = true }

/** Teams of each category in seeding order (the order the zones are filled in) */
const columns = computed(() => (proposal.value?.categories ?? []).map((c) => ({
  ...c,
  rows: rows.value.filter((r) => r.target === c.id).sort((a, b) => a.seed - b.seed),
})))
const out = computed(() => rows.value.filter((r) => r.target === null))

function setTarget(r: Row, value: string) {
  r.target = value === '' ? null : Number(value)
  r.answered = true
}

/** "Zona 1 · 3º" */
const origin = (r: BuildTeam) => (r.sourceGroup ? `${r.sourceGroup} · ${r.position}º` : t('build.notInSource'))
const zonesText = (n: number) => (n === 1 ? t('build.oneZone') : t('build.zones', { n }))
const reasonText = (r: BuildTeam) => t(`build.reasons.${r.reason}`, { rival: r.detail ?? '' })

// --- options and apply ---
const zoneMode = ref<'BALANCED' | 'RANDOM'>('BALANCED')
const withFixture = ref(true)
const fixtureStart = ref(nextSunday())
const minutes = ref(70)
const confirming = ref(false)
const applying = ref(false)
const done = ref<{ teams: number; matches: number; definitions: number } | null>(null)
// Interzonal / intercategory match days crossed by seeding (same fields as the Interzonales button of the fixture)
const interzonal = ref(false)
const severalZones = computed(() => (proposal.value?.zones.length ?? 0) > 1)
const izPairs = ref<{ zoneA: number; zoneB: number }[]>([])
const izRounds = ref(1)
const izCrossing = ref<'SAME' | 'INVERSE'>('SAME')
watch(proposal, (p) => {
  if (!p) return
  // Same default as the fixture: zones of each category two by two (zones come in category order)
  const byCategory = new Map<number, number[]>()
  const out: { zoneA: number; zoneB: number }[] = []
  for (const z of p.zones) {
    if (!byCategory.has(z.categoryId)) byCategory.set(z.categoryId, [])
    byCategory.get(z.categoryId)!.push(z.id)
  }
  for (const ids of byCategory.values()) for (let i = 0; i + 1 < ids.length; i += 2) out.push({ zoneA: ids[i], zoneB: ids[i + 1] })
  if (!out.length && p.zones.length > 1) out.push({ zoneA: p.zones[0].id, zoneB: p.zones[1].id })
  izPairs.value = out
})

// --- definitions (promotions / finals) by positions of the new tables; copied from the source when they map
const definitions = ref<BuildDefinition[]>([])
function addDefinition() {
  const zones = proposal.value?.zones ?? []
  if (!zones.length) return
  definitions.value.push({
    type: 'PROMOTION', homeZoneId: zones[0].id, homePosition: 1,
    awayZoneId: zones[Math.min(1, zones.length - 1)].id, awayPosition: zones.length > 1 ? 1 : 2,
  })
}
const removeDefinition = (i: number) => definitions.value.splice(i, 1)

function nextSunday() {
  const d = new Date()
  d.setDate(d.getDate() + ((7 - d.getDay()) % 7 || 7))
  return `${d.toISOString().slice(0, 10)}T09:00`
}

const canApply = computed(() => !!proposal.value && !proposal.value.targetHasMatches && unanswered.value === 0
  && rows.value.some((r) => r.target !== null))

async function apply() {
  if (!edition.value) return
  applying.value = true
  error.value = ''
  try {
    const assignments = rows.value.filter((r) => r.target !== null).sort((a, b) => a.seed - b.seed)
      .map((r) => ({ teamId: r.team.id, categoryId: r.target! }))
    done.value = await tournamentApi.build(edition.value.id, {
      assignments, zoneMode: zoneMode.value,
      fixtureStart: withFixture.value && fixtureStart.value ? `${fixtureStart.value}:00` : null,
      minutesBetween: minutes.value,
      interzonal: severalZones.value && interzonal.value && izPairs.value.length
        ? { pairs: izPairs.value, rounds: izRounds.value, crossing: izCrossing.value } : null,
      definitions: definitions.value,
    })
    confirming.value = false
    if (proposal.value) proposal.value.targetHasMatches = done.value.matches > 0
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    confirming.value = false
  } finally {
    applying.value = false
  }
}

const standingsPath = computed(() => (edition.value ? panelEditionPath(edition.value, 'clasificacion') : PANEL.editions))
const homePath = computed(() => (edition.value ? panelEditionPath(edition.value) : PANEL.editions))
</script>

<template>
  <TournamentLayout>
    <section class="card head">
      <label class="source">
        {{ t('build.source') }}
        <select v-model="sourceId" class="select">
          <option v-for="e in editions" :key="e.id" :value="e.id">{{ e.name }}</option>
        </select>
      </label>
      <p class="muted small">{{ t('build.help') }}</p>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <div v-if="done" class="card notice-card">
      <p>{{ t('build.done', { teams: done.teams, matches: done.matches, definitions: done.definitions }) }}</p>
      <RouterLink :to="standingsPath" class="btn">{{ t('build.goStandings') }}</RouterLink>
    </div>

    <template v-if="proposal && !done">
      <p v-if="proposal.targetHasMatches" class="card warn">{{ t('build.hasMatches') }}</p>

      <!-- Questions: the proposal is a guess for these teams -->
      <section v-if="questions.length" class="card questions">
        <header>
          <h2>{{ t('build.questionsTitle', { n: unanswered }) }}</h2>
          <button v-if="unanswered" type="button" class="link" @click="answerAll">{{ t('build.acceptAll') }}</button>
        </header>
        <div v-for="r in questions" :key="r.team.id" :class="['q', { answered: r.answered }]">
          <TeamBadge :team="r.team" :size="28" />
          <div class="q-text">
            <strong>{{ r.team.name }}</strong>
            <span class="muted">{{ origin(r) }} — {{ t(`build.questions.${r.reason}`, { rival: r.detail ?? '' }) }}</span>
          </div>
          <select class="select" :value="r.target ?? ''" :aria-label="t('build.category')" @change="setTarget(r, ($event.target as HTMLSelectElement).value)">
            <option v-for="c in proposal.categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            <option value="">{{ t('build.out') }}</option>
          </select>
          <button v-if="!r.answered" type="button" class="btn secondary small-btn" @click="r.answered = true">{{ t('build.ok') }}</button>
          <span v-else class="ok-mark">✓</span>
        </div>
      </section>

      <div class="columns">
        <section v-for="c in columns" :key="c.id" class="card column">
          <header>
            <h2>{{ c.name }}</h2>
            <span class="muted">{{ t('build.count', { n: c.rows.length, zones: zonesText(c.zoneCount) }) }}</span>
          </header>
          <p v-if="c.rows.length === 0" class="muted small">{{ t('build.emptyCategory') }}</p>
          <div v-for="r in c.rows" :key="r.team.id" :class="['row', { pending: r.question && !r.answered }]">
            <TeamBadge :team="r.team" :size="24" />
            <div class="row-text">
              <span class="team">{{ r.team.name }}</span>
              <span class="muted tiny">{{ origin(r) }} · <span :class="['reason', r.reason]">{{ reasonText(r) }}</span></span>
            </div>
            <select class="select compact" :value="r.target ?? ''" :aria-label="t('build.category')" @change="setTarget(r, ($event.target as HTMLSelectElement).value)">
              <option v-for="x in proposal.categories" :key="x.id" :value="x.id">{{ x.name }}</option>
              <option value="">{{ t('build.out') }}</option>
            </select>
          </div>
        </section>
      </div>

      <section v-if="out.length" class="card">
        <h2 class="small-title">{{ t('build.outTitle', { n: out.length }) }}</h2>
        <div v-for="r in out" :key="r.team.id" class="row">
          <TeamBadge :team="r.team" :size="24" />
          <div class="row-text">
            <span class="team">{{ r.team.name }}</span>
            <span class="muted tiny">{{ origin(r) }} · {{ reasonText(r) }}</span>
          </div>
          <select class="select compact" value="" :aria-label="t('build.category')" @change="setTarget(r, ($event.target as HTMLSelectElement).value)">
            <option v-for="x in proposal.categories" :key="x.id" :value="x.id">{{ x.name }}</option>
            <option value="">{{ t('build.out') }}</option>
          </select>
        </div>
      </section>

      <section class="card options">
        <h2 class="small-title">{{ t('build.zonesTitle') }}</h2>
        <label class="radio"><input v-model="zoneMode" type="radio" value="BALANCED" /> {{ t('build.balanced') }}</label>
        <label class="radio"><input v-model="zoneMode" type="radio" value="RANDOM" /> {{ t('build.random') }}</label>
        <p class="muted tiny">
          {{ t('build.zonesHelp') }} <RouterLink :to="homePath">{{ t('build.editZones') }}</RouterLink>
        </p>
        <h2 class="small-title">{{ t('build.fixtureTitle') }}</h2>
        <label class="radio"><input v-model="withFixture" type="checkbox" /> {{ t('build.withFixture') }}</label>
        <div v-if="withFixture" class="fixture-fields">
          <label>{{ t('standings.start') }}<input v-model="fixtureStart" class="input" type="datetime-local" required /></label>
          <label>{{ t('standings.minutesBetween') }}<input v-model.number="minutes" class="input" type="number" min="0" /></label>
        </div>
        <template v-if="severalZones">
          <h2 class="small-title">{{ t('interzonal.title') }}</h2>
          <label class="radio"><input v-model="interzonal" type="checkbox" /> {{ t('build.interzonal') }}</label>
          <div v-if="interzonal" class="iz">
            <InterzonalFields v-model:pairs="izPairs" v-model:rounds="izRounds" v-model:crossing="izCrossing" :zones="proposal.zones" />
            <p class="muted tiny">{{ t('build.interzonalHelp') }}</p>
          </div>
        </template>

        <h2 class="small-title">{{ t('build.definitionsTitle') }}</h2>
        <p class="muted tiny">{{ proposal.definitions.length ? t('build.definitionsCopied', { name: proposal.sourceName }) : t('build.definitionsHelp') }}</p>
        <div v-for="(d, i) in definitions" :key="i" class="def">
          <select v-model="d.type" class="select compact" :aria-label="t('build.definitionType')">
            <option value="PROMOTION">{{ t('playoffs.types.PROMOTION') }}</option>
            <option value="FINAL">{{ t('playoffs.types.FINAL') }}</option>
          </select>
          <span class="side">
            <input v-model.number="d.homePosition" class="input pos" type="number" min="1" :aria-label="t('build.position')" />
            <span class="muted">º</span>
            <select v-model="d.homeZoneId" class="select compact" :aria-label="t('build.zone')">
              <option v-for="z in proposal.zones" :key="z.id" :value="z.id">{{ z.label }}</option>
            </select>
          </span>
          <span class="muted">vs</span>
          <span class="side">
            <input v-model.number="d.awayPosition" class="input pos" type="number" min="1" :aria-label="t('build.position')" />
            <span class="muted">º</span>
            <select v-model="d.awayZoneId" class="select compact" :aria-label="t('build.zone')">
              <option v-for="z in proposal.zones" :key="z.id" :value="z.id">{{ z.label }}</option>
            </select>
          </span>
          <button type="button" class="icon-btn" :title="t('build.removeDefinition')" :aria-label="t('build.removeDefinition')" @click="removeDefinition(i)">
            <AppIcon name="close" :size="18" />
          </button>
        </div>
        <button type="button" class="add-def" @click="addDefinition"><AppIcon name="plus" :size="16" /> {{ t('build.addDefinition') }}</button>

        <div class="apply">
          <p v-if="unanswered" class="muted small">{{ t('build.answerFirst', { n: unanswered }) }}</p>
          <button class="btn" :disabled="!canApply" @click="confirming = true">{{ t('build.apply') }}</button>
        </div>
      </section>
    </template>

    <BaseModal v-if="confirming && proposal" :title="t('build.confirmTitle')" width="480px" @close="confirming = false">
      <ul class="summary">
        <li v-for="c in columns" :key="c.id">{{ t('build.summaryRow', { name: c.name, n: c.rows.length, zones: zonesText(c.zoneCount) }) }}</li>
      </ul>
      <p class="muted small">{{ withFixture ? t('build.confirmWithFixture') : t('build.confirmNoFixture') }}</p>
      <p v-if="severalZones && interzonal" class="muted small">{{ t('build.confirmInterzonal') }}</p>
      <p v-if="definitions.length" class="muted small">{{ t('build.confirmDefinitions', { n: definitions.length }) }}</p>
      <template #footer>
        <button type="button" class="btn secondary" @click="confirming = false">{{ t('tournaments.cancel') }}</button>
        <button type="button" class="btn" :disabled="applying" @click="apply">{{ t('build.apply') }}</button>
      </template>
    </BaseModal>
  </TournamentLayout>
</template>

<style scoped>
.card { margin-bottom: 16px; }
.head { display: flex; flex-direction: column; gap: 6px; }
.source { display: flex; align-items: center; gap: 10px; font-weight: 500; flex-wrap: wrap; }
.source .select { width: auto; min-width: 220px; }
.small { font-size: 13px; margin: 0; }
.tiny { font-size: 12px; }
.warn { color: var(--danger); font-weight: 500; }
.notice-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.notice-card p { margin: 0; font-weight: 500; color: var(--navy); }
.questions header, .column header { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.questions h2, .column h2 { font-size: 17px; margin: 0; color: var(--navy); }
.q { display: flex; align-items: center; gap: 10px; padding: 8px 6px; border-radius: 8px; background: #fff7e6; margin-bottom: 6px; flex-wrap: wrap; }
.q.answered { background: var(--bg); }
.q-text { flex: 1; min-width: 180px; display: flex; flex-direction: column; font-size: 14px; }
.q .select { width: auto; min-width: 120px; }
.ok-mark { color: #1e7d45; font-weight: 700; width: 24px; text-align: center; }
.columns { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; align-items: start; }
.column { margin-bottom: 0; }
.row { display: flex; align-items: center; gap: 8px; padding: 6px 2px; border-bottom: 1px solid var(--border); }
.row:last-child { border-bottom: 0; }
.row.pending { background: #fff7e6; }
.row-text { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.team { font-size: 14px; font-weight: 500; }
.select.compact { width: auto; min-width: 90px; padding: 4px 8px; font-size: 13px; }
.reason.ASCENDS, .reason.PROMO_WON { color: #1e7d45; }
.reason.DESCENDS, .reason.PROMO_LOST { color: var(--danger); }
.reason.PROMO_PENDING, .reason.NEW, .reason.NO_RULE, .reason.WITHDRAWN { color: #a86b00; }
.small-title { font-size: 16px; margin: 4px 0 8px; color: var(--navy); }
.options { margin-top: 16px; display: flex; flex-direction: column; gap: 6px; }
.radio { display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; }
.fixture-fields { display: flex; gap: 12px; flex-wrap: wrap; }
.fixture-fields label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
.apply { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 10px; flex-wrap: wrap; }
a { color: var(--navy); font-weight: 500; text-decoration: underline; }
.link { background: none; border: 0; color: var(--navy); font: inherit; font-weight: 500; cursor: pointer; padding: 0; text-decoration: underline; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.def { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 6px 0; border-bottom: 1px solid var(--border); }
.def .side { display: inline-flex; align-items: center; gap: 4px; }
.input.pos { width: 64px; padding: 4px 8px; }
.add-def {
  align-self: flex-start; display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px; border-radius: 999px; cursor: pointer;
  border: 1px dashed var(--border); background: none; color: var(--navy); font: inherit; font-size: 13px; font-weight: 500; margin-top: 4px;
}
.iz { margin: 4px 0 6px 26px; }
.summary { margin: 0 0 10px; padding-left: 18px; }
</style>
