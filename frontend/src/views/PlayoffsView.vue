<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import TeamBadge from '../components/TeamBadge.vue'
import SlotPicker from '../components/SlotPicker.vue'
import MatchResultEditor from '../components/MatchResultEditor.vue'
import { playoffApi } from '../api/endpoints'
import type { Match, SlotReq, SlotRes } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'

// Promotions and finals of a tournament
const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const matches = ref<Match[] | null>(null)
const error = ref('')
const editing = ref<Match | null>(null)

const showForm = ref(false)
const form = ref({ type: 'PROMOTION' as 'PROMOTION' | 'FINAL', when: '', home: null as SlotReq | null, away: null as SlotReq | null })

const load = async () => { matches.value = await playoffApi.list(tournamentId.value) }
watch(tournamentId, load, { immediate: true })

async function run(action: () => Promise<unknown>) {
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

const create = () => run(async () => {
  await playoffApi.create(tournamentId.value, {
    type: form.value.type,
    scheduledAt: form.value.when ? `${form.value.when}:00` : null,
    home: form.value.home!,
    away: form.value.away!,
  })
  showForm.value = false
})

const resolve = (m: Match) => run(() => playoffApi.resolve(m.id))

/** "3º · A · Zona 2 (Apertura)"; the zone only when the category has more than one. */
function sourceLabel(s: SlotRes | null) {
  if (!s) return ''
  const zone = s.zoneCount > 1 ? ` · ${s.zoneName}` : ''
  const tour = s.tournamentId === tournamentId.value ? '' : ` (${s.tournamentName})`
  return `${s.position}º · ${s.categoryName}${zone}${tour}`
}

const formatDate = (d: string | null) =>
  d ? new Date(d).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''

async function onSaved() {
  editing.value = null
  await load()
}
</script>

<template>
  <TournamentLayout>
    <div class="top">
      <h1>{{ t('playoffs.title') }}</h1>
      <button v-if="isAdmin && !showForm" class="btn" @click="showForm = true">{{ t('playoffs.new') }}</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <form v-if="showForm" class="card new" @submit.prevent="create">
      <div class="row">
        <label class="field-inline">{{ t('playoffs.type') }}
          <select v-model="form.type" class="select">
            <option value="PROMOTION">{{ t('playoffs.types.PROMOTION') }}</option>
            <option value="FINAL">{{ t('playoffs.types.FINAL') }}</option>
          </select>
        </label>
        <label class="field-inline">{{ t('standings.when') }}
          <input v-model="form.when" class="input" type="datetime-local" />
        </label>
      </div>
      <div class="slots">
        <SlotPicker v-model="form.home" :label="t('standings.home')" :default-tournament-id="tournamentId" />
        <SlotPicker v-model="form.away" :label="t('standings.away')" :default-tournament-id="tournamentId" />
      </div>
      <div class="actions">
        <button type="button" class="btn secondary" @click="showForm = false">{{ t('standings.cancel') }}</button>
        <button class="btn" :disabled="!form.home || !form.away">{{ t('tournaments.create') }}</button>
      </div>
    </form>

    <p v-if="matches && matches.length === 0" class="card muted">{{ t('playoffs.empty') }}</p>
    <div class="list">
      <article
        v-for="m in matches ?? []" :key="m.id" :class="['card', 'match', { editable: isAdmin }]"
        :title="isAdmin ? t('results.load') : undefined" @click="isAdmin && (editing = m)"
      >
        <header class="meta">
          <span :class="['tag', m.type]">{{ t(`playoffs.types.${m.type}`) }}</span>
          <span class="muted">{{ formatDate(m.scheduledAt) }}</span>
          <button
            v-if="isAdmin && m.status !== 'FINISHED' && (m.homeSource || m.awaySource)"
            class="btn secondary small-btn" @click.stop="resolve(m)"
          >{{ t('playoffs.resolve') }}</button>
        </header>
        <div class="line">
          <div class="side">
            <span class="team"><TeamBadge :team="m.homeTeam" :size="34" /> {{ m.homeTeam.name }}</span>
            <span class="source">{{ sourceLabel(m.homeSource) }}</span>
          </div>
          <div class="result">
            <template v-if="m.status === 'FINISHED'">
              <strong>{{ m.homeGoals }} - {{ m.awayGoals }}</strong>
              <span v-if="m.homePenalties !== null" class="pens">({{ m.homePenalties }} - {{ m.awayPenalties }} {{ t('playoffs.pens') }})</span>
            </template>
            <span v-else class="muted">vs</span>
          </div>
          <div class="side right">
            <span class="team">{{ m.awayTeam.name }} <TeamBadge :team="m.awayTeam" :size="34" /></span>
            <span class="source">{{ sourceLabel(m.awaySource) }}</span>
          </div>
        </div>
        <p v-if="m.winner" class="winner">{{ t('playoffs.winner', { team: m.winner.name }) }}</p>
      </article>
    </div>

    <MatchResultEditor v-if="editing" :match="editing" @saved="onSaved" @close="editing = null" />
  </TournamentLayout>
</template>

<style scoped>
.top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
h1 { font-size: 26px; color: var(--navy); }
.new { margin-bottom: 20px; }
.row, .slots { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 12px; }
.field-inline { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); min-width: 200px; }
.actions { display: flex; justify-content: flex-end; gap: 10px; }
.list { display: flex; flex-direction: column; gap: 14px; }
.match.editable { cursor: pointer; }
.match.editable:hover { box-shadow: 0 2px 10px rgba(20, 39, 90, 0.15); }
.meta { display: flex; align-items: center; gap: 12px; font-size: 13px; margin-bottom: 10px; }
.meta .btn { margin-left: auto; }
.tag { font-size: 11px; font-weight: 700; padding: 3px 10px; border-radius: 999px; color: #fff; background: var(--navy); text-transform: uppercase; }
.tag.PROMOTION { background: var(--violet); }
.line { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 16px; }
.side { display: flex; flex-direction: column; gap: 4px; }
.side.right { align-items: flex-end; }
.team { display: inline-flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 500; }
.source { font-size: 12px; color: var(--muted); }
.result { display: flex; flex-direction: column; align-items: center; min-width: 110px; }
.result strong { font-size: 24px; color: var(--navy); }
.pens { font-size: 12px; color: var(--muted); }
.winner { margin: 10px 0 0; text-align: center; font-weight: 500; color: #1e7d45; }
.small-btn { padding: 4px 12px; font-size: 13px; }
</style>
