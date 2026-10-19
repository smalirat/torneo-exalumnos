<script setup lang="ts">
import { computed } from 'vue'
import TeamBadge from './TeamBadge.vue'
import type { Match } from '../api/types'
import { t } from '../i18n'

// One match line: date, both teams, score (or "vs"), walkover / penalties marks.
// `clickable` makes it look interactive; the parent handles @click. `hints: false` drops explanatory text
// (public pages show just "W.O.").
const props = withDefaults(defineProps<{ match: Match; clickable?: boolean; dateFormat?: 'full' | 'time' | 'none'; hints?: boolean }>(), {
  clickable: false,
  dateFormat: 'full',
  hints: true,
})

const date = computed(() => {
  const d = props.match.scheduledAt
  if (!d || props.dateFormat === 'none') return ''
  const opts: Intl.DateTimeFormatOptions = props.dateFormat === 'time'
    ? { hour: '2-digit', minute: '2-digit' }
    : { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }
  return new Date(d).toLocaleString('es-AR', opts)
})
const walkover = computed(() => !props.match.homePresent || !props.match.awayPresent)
const isWinner = (teamId: number) => props.match.winner?.id === teamId

/** "Siberia · Cancha 3 · Interzonal" */
const place = computed(() => [
  props.match.venue,
  props.match.pitch ? `${t('results.pitch')} ${props.match.pitch}` : null,
  props.match.roundLabel,
].filter(Boolean).join(' · '))
</script>

<template>
  <div :class="['match', { clickable }]" :title="clickable ? t('results.load') : undefined">
    <span v-if="date || place" class="date muted">{{ [date, place].filter(Boolean).join(' · ') }}</span>
    <div class="line">
      <span :class="['side', { win: isWinner(match.homeTeam.id) }]">
        <TeamBadge :team="match.homeTeam" :size="22" /> {{ match.homeTeam.name }}
      </span>
      <span v-if="match.status === 'FINISHED'" class="score">
        <strong>{{ match.homeGoals }} - {{ match.awayGoals }}</strong>
        <small v-if="match.homePenalties !== null" class="muted">({{ match.homePenalties }}-{{ match.awayPenalties }} {{ t('playoffs.pens') }})</small>
      </span>
      <span v-else class="score muted">vs</span>
      <span :class="['side', 'right', { win: isWinner(match.awayTeam.id) }]">
        {{ match.awayTeam.name }} <TeamBadge :team="match.awayTeam" :size="22" />
      </span>
    </div>
    <span v-if="walkover && match.status === 'FINISHED'" class="wo">
      {{ t('standings.walkover') }}<span v-if="hints && match.advanceWalkover" class="muted pending"> · {{ t('standings.advanceWalkover') }}</span>
    </span>
    <p v-if="match.notes" class="note">{{ match.notes }}</p>
  </div>
</template>

<style scoped>
.match { padding: 10px 4px; border-bottom: 1px solid var(--border); font-size: 14px; }
.match:last-child { border-bottom: 0; }
.match.clickable { cursor: pointer; }
.match.clickable:hover { background: var(--row-alt); }
.date { font-size: 12px; }
.line { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 8px; margin-top: 4px; }
.side { display: inline-flex; align-items: center; gap: 6px; }
.side.right { justify-content: flex-end; }
.side.win { font-weight: 700; }
.score { min-width: 54px; display: flex; flex-direction: column; align-items: center; }
.wo { font-size: 11px; color: var(--danger); font-weight: 700; }
.pending { font-weight: 500; }
.note { margin: 4px 0 0; font-size: 12px; color: var(--muted); font-style: italic; }
</style>
