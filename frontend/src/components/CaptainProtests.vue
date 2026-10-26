<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { protestApi, teamApi, tournamentApi } from '../api/endpoints'
import type { Match, Protest } from '../api/types'
import { t } from '../i18n'

// Captain: protests of matches of their team (the rules: after the match, the facts and a precise request). The
// organizer answers before the next match day and the answer is final.
const props = defineProps<{ teamId: number }>()

const protests = ref<Protest[]>([])
const matches = ref<Match[]>([])
const open = ref(false)
const matchId = ref<number | null>(null)
const facts = ref('')
const request = ref('')
const error = ref('')
const busy = ref(false)
const sent = ref(false)

async function load() {
  protests.value = await protestApi.mine()
  // Played (or suspended) matches of the team in the editions it is registered in, newest first
  const regs = await teamApi.registrations(props.teamId)
  const all = (await Promise.all(regs.map((r) => tournamentApi.fixtures(r.tournament.id)))).flat()
  matches.value = all
    .filter((m) => (m.homeTeam.id === props.teamId || m.awayTeam.id === props.teamId) && (m.status === 'FINISHED' || m.suspended))
    .sort((a, b) => (b.scheduledAt ?? '').localeCompare(a.scheduledAt ?? ''))
}
onMounted(load)

const label = (m: Match) => {
  const when = m.scheduledAt ? new Date(m.scheduledAt).toLocaleDateString('es-AR') : ''
  return `${m.round ? t('fixture.round', { n: m.round }) + ' · ' : ''}${when} · ${m.homeTeam.name} ${m.homeGoals}-${m.awayGoals} ${m.awayTeam.name}`
}
const openOnes = computed(() => protests.value.filter((p) => p.status === 'OPEN').length)

async function send() {
  busy.value = true
  error.value = ''
  try {
    await protestApi.create({ matchId: matchId.value!, facts: facts.value.trim(), request: request.value.trim() })
    open.value = false
    matchId.value = null
    facts.value = ''
    request.value = ''
    sent.value = true
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="card protests">
    <header>
      <h2>{{ t('protests.title') }} <span v-if="openOnes" class="count">{{ openOnes }}</span></h2>
      <button v-if="!open && matches.length" type="button" class="btn secondary small-btn" @click="open = true; sent = false">{{ t('protests.new') }}</button>
    </header>
    <p v-if="sent" class="notice">{{ t('protests.sent') }}</p>

    <form v-if="open" class="form" @submit.prevent="send">
      <label>{{ t('protests.match') }}
        <select v-model="matchId" class="select" required>
          <option :value="null" disabled>{{ t('protests.pickMatch') }}</option>
          <option v-for="m in matches" :key="m.id" :value="m.id">{{ label(m) }}</option>
        </select>
      </label>
      <label>{{ t('protests.facts') }}<textarea v-model="facts" class="input" rows="3" maxlength="2000" required /></label>
      <label>{{ t('protests.request') }}<textarea v-model="request" class="input" rows="2" maxlength="1000" required /></label>
      <p class="muted small">{{ t('protests.help') }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <button type="button" class="btn secondary" @click="open = false">{{ t('tournaments.cancel') }}</button>
        <button class="btn" :disabled="busy">{{ t('protests.send') }}</button>
      </div>
    </form>

    <p v-if="!protests.length && !open" class="muted small">{{ t('protests.empty') }}</p>
    <ul class="list">
      <li v-for="p in protests" :key="p.id">
        <div class="head">
          <strong>{{ p.match }}</strong>
          <span :class="['status', p.status]">{{ t(`protests.statuses.${p.status}`) }}</span>
        </div>
        <p class="muted small">{{ p.request }}</p>
        <p v-if="p.resolution" class="resolution">{{ t('protests.answer') }}: {{ p.resolution }}</p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.protests { margin-top: 16px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
h2 { font-size: 16px; color: var(--navy); margin: 0; }
.count { font-size: 12px; background: var(--navy); color: #fff; border-radius: 999px; padding: 1px 8px; margin-left: 4px; }
.form { display: flex; flex-direction: column; gap: 8px; margin: 10px 0; }
.form label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
textarea.input { resize: vertical; font: inherit; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
.list { list-style: none; margin: 8px 0 0; padding: 0; }
.list li { padding: 8px 0; border-bottom: 1px solid var(--border); }
.head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.small { font-size: 13px; margin: 4px 0 0; }
.status { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; }
.status.OPEN { background: #fff4e0; color: #8a5300; }
.status.RESOLVED { background: var(--bg); color: var(--navy); }
.resolution { margin: 4px 0 0; font-size: 14px; }
.notice { color: var(--navy); font-weight: 500; font-size: 14px; }
.small-btn { padding: 6px 12px; font-size: 13px; }
</style>
