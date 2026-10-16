<script setup lang="ts">
import { computed } from 'vue'
import TeamBadge from './TeamBadge.vue'
import type { Match } from '../api/types'
import { t } from '../i18n'

// Compact match card (two lines: team + score). Upcoming matches show the time instead of the score.
const props = defineProps<{ match: Match }>()
const played = computed(() => props.match.status === 'FINISHED')
const walkover = computed(() => played.value && (!props.match.homePresent || !props.match.awayPresent))
const winner = (home: boolean) => props.match.winner?.id === (home ? props.match.homeTeam.id : props.match.awayTeam.id)
const when = computed(() => {
  const d = props.match.scheduledAt
  if (!d) return t('fixture.noDate')
  return new Date(d).toLocaleString('es-AR', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
})
</script>

<template>
  <article class="score-card">
    <div v-for="home in [true, false]" :key="String(home)" :class="['row', { win: played && winner(home) }]">
      <TeamBadge :name="home ? match.homeTeam.name : match.awayTeam.name" :size="20" />
      <span class="team">{{ home ? match.homeTeam.name : match.awayTeam.name }}</span>
      <span v-if="played" class="goals">
        <small v-if="match.homePenalties !== null" class="pens">({{ home ? match.homePenalties : match.awayPenalties }})</small>
        {{ home ? match.homeGoals : match.awayGoals }}
      </span>
    </div>
    <footer class="meta">
      <span v-if="walkover" class="wo">{{ t('standings.walkover') }}</span>
      <span v-if="!played || match.advanceWalkover">{{ when }}</span>
      <span v-if="match.pitch || match.venue" class="place">{{ [match.venue, match.pitch ? `${t('results.pitch')} ${match.pitch}` : null].filter(Boolean).join(' · ') }}</span>
    </footer>
  </article>
</template>

<style scoped>
.score-card {
  background: #fff; border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px;
  display: flex; flex-direction: column; gap: 6px; min-width: 0;
}
.row { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.team { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.goals { font-weight: 500; font-variant-numeric: tabular-nums; }
.row.win .team, .row.win .goals { font-weight: 700; color: var(--navy); }
.pens { color: var(--muted); font-size: 11px; }
.meta { display: flex; gap: 8px; flex-wrap: wrap; font-size: 11px; color: var(--muted); min-height: 14px; }
.wo { color: var(--danger); font-weight: 700; }
</style>
