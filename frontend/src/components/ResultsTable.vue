<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import TeamBadge from './TeamBadge.vue'
import type { Match } from '../api/types'
import type { Round } from '../rounds'
import { t } from '../i18n'

// Results of one round, played and to play: Local | ‹ Fecha N ▾ › | Visitante. v-model is the round index.
const props = defineProps<{ rounds: Round[] }>()
const index = defineModel<number>({ required: true })
const round = computed(() => props.rounds[index.value] ?? null)

/** Walkover of a team that left the edition: the row stays in place, dimmed, the team that left struck through */
const left = (m: Match, home: boolean) => m.withdrawal && !(home ? m.homePresent : m.awayPresent)

const roundName = (r: Round, i: number) =>
  r.number != null ? t('fixture.round', { n: r.number }) : r.label ?? t('fixture.round', { n: i + 1 })

const played = (m: Match) => m.status === 'FINISHED' && !m.advanceWalkover
const walkover = (m: Match) => !m.homePresent || !m.awayPresent
const won = (m: Match, home: boolean) => m.winner?.id === (home ? m.homeTeam.id : m.awayTeam.id)
const time = (m: Match) => (m.scheduledAt
  ? new Date(m.scheduledAt).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  : '')
const place = (m: Match) => [m.venue, m.pitch ? `${t('results.pitch')} ${m.pitch}` : null].filter(Boolean).join(' · ')
</script>

<template>
  <div class="results">
    <table>
      <thead>
        <tr>
          <th class="side home"><span class="label">{{ t('fixture.home') }}</span></th>
          <th class="center">
            <span class="round-nav">
              <button type="button" class="step" :disabled="index <= 0" :title="t('fixture.previous')" @click="index--">
                <AppIcon name="chevron" :size="16" class="flip" />
              </button>
              <label class="pick">
                <span class="sr-only">{{ t('fixture.rounds') }}</span>
                <select v-model="index">
                  <option v-for="(r, i) in rounds" :key="r.key" :value="i">{{ roundName(r, i) }}</option>
                </select>
                <AppIcon name="chevron" :size="14" class="down" />
              </label>
              <button type="button" class="step" :disabled="index >= rounds.length - 1" :title="t('fixture.next')" @click="index++">
                <AppIcon name="chevron" :size="16" />
              </button>
            </span>
          </th>
          <th class="side away"><span class="label">{{ t('fixture.away') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in round?.matches ?? []" :key="m.id" :class="{ withdrawal: m.withdrawal }">
          <td class="side home">
            <span :class="['team', { win: !m.withdrawal && played(m) && won(m, true), left: left(m, true) }]">{{ m.homeTeam.name }} <TeamBadge :team="m.homeTeam" :size="26" /></span>
          </td>
          <td class="center">
            <span v-if="m.withdrawal" class="abandon">{{ t('fixture.withdrawal') }}</span>
            <template v-else-if="played(m) || m.advanceWalkover">
              <span class="score">
                <small v-if="m.homePenalties != null" class="pen">({{ m.homePenalties }})</small>
                <b :class="{ win: won(m, true) }">{{ m.homeGoals }}</b>
                <span class="dash">-</span>
                <b :class="{ win: won(m, false) }">{{ m.awayGoals }}</b>
                <small v-if="m.awayPenalties != null" class="pen">({{ m.awayPenalties }})</small>
              </span>
              <small v-if="walkover(m)" class="wo">{{ t('standings.walkover') }}</small>
            </template>
            <template v-else>
              <span class="time">{{ time(m) || 'vs' }}</span>
              <small v-if="place(m)" class="place">{{ place(m) }}</small>
            </template>
          </td>
          <td class="side away">
            <span :class="['team', { win: !m.withdrawal && played(m) && won(m, false), left: left(m, false) }]"><TeamBadge :team="m.awayTeam" :size="26" /> {{ m.awayTeam.name }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!round" class="muted empty">{{ t('standings.noGames') }}</p>
  </div>
</template>

<style scoped>
.results { background: #fff; border-radius: var(--radius); box-shadow: var(--shadow); overflow: hidden; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; }
th {
  background: var(--navy); color: #fff; font-family: var(--condensed); font-size: 16px; font-weight: 700;
  padding: 10px 8px; text-transform: none;
}
td { padding: 14px 10px; border-bottom: 1px solid var(--border); vertical-align: middle; }
tbody tr:last-child td { border-bottom: 0; }
.side { width: 40%; }
.center { width: 20%; text-align: center; }
.home { text-align: right; }
.away { text-align: left; }
.team { display: inline-flex; align-items: center; gap: 10px; font-weight: 500; }
.team.win { font-weight: 700; color: var(--navy); }
.score { display: inline-flex; align-items: baseline; gap: 6px; font-family: var(--condensed); font-size: 20px; color: var(--text); }
.score b { font-weight: 700; color: var(--muted); }
.score b.win { color: var(--navy); }
.dash { color: var(--muted); }
.pen { font-size: 11px; color: var(--muted); font-family: inherit; }
.wo, .place { display: block; font-size: 11px; color: var(--muted); margin-top: 2px; }
.wo { color: var(--danger); font-weight: 700; }
.time { font-family: var(--condensed); font-size: 18px; color: var(--navy); }

.round-nav { display: inline-flex; align-items: center; gap: 6px; }
.step {
  width: 26px; height: 26px; border: 0; border-radius: 50%; background: transparent; color: #fff; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
}
.step:hover:not(:disabled) { background: rgba(255, 255, 255, 0.15); }
.step:disabled { opacity: 0.3; cursor: default; }
.pick { position: relative; display: inline-flex; align-items: center; }
.pick select {
  appearance: none; background: transparent; border: 0; color: #fff; font: inherit; font-weight: 700;
  padding: 2px 18px 2px 4px; cursor: pointer;
}
.pick select option { color: var(--text); }
.down { position: absolute; right: 0; transform: rotate(90deg); pointer-events: none; }
.flip { transform: rotate(180deg); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
.empty { padding: 20px; }
/* Walkover of a team that left the edition: same row, dimmed; the team that left struck through */
.withdrawal td { background: var(--row-alt); }
.withdrawal .team { color: var(--muted); font-weight: 400; }
.withdrawal .team.left { text-decoration: line-through; }
.withdrawal :deep(img) { opacity: 0.5; }
.abandon { font-size: 12px; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
@media (max-width: 600px) {
  td { padding: 12px 6px; }
  .side { width: 31%; }
  .center { width: 38%; }
  th .label { display: none; }
  .time { font-size: 16px; }
  .team { gap: 6px; font-size: 13px; }
  .score { font-size: 18px; gap: 4px; }
  th { font-size: 14px; }
}
</style>
