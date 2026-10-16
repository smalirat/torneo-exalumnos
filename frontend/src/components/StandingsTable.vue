<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { StandingRow } from '../api/types'
import { t } from '../i18n'

// Zone table. `highlightTeamId` marks one row (e.g. the captain's team).
defineProps<{ rows: StandingRow[]; highlightTeamId?: number }>()
</script>

<template>
  <p v-if="rows.length === 0" class="muted">{{ t('standings.noTeams') }}</p>
  <div v-else class="scroll">
    <table>
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
        <tr v-for="r in rows" :key="r.team.id" :class="{ highlight: r.team.id === highlightTeamId }">
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
  </div>
</template>

<style scoped>
.scroll { overflow-x: auto; }
table { width: 100%; border-collapse: separate; border-spacing: 0 4px; font-size: 14px; }
th { font-weight: 500; text-align: center; padding: 4px 6px; }
td { text-align: center; padding: 6px; }
.left { text-align: left; }
tbody tr:nth-child(even) td { background: var(--row-alt); }
tbody tr.highlight td { background: #e3e9f7; font-weight: 500; }
.pos {
  display: inline-flex; width: 40px; height: 40px; align-items: center; justify-content: center;
  background: var(--navy); color: #fff; border-radius: 6px;
}
.team { display: inline-flex; align-items: center; gap: 8px; }
.pts { font-weight: 700; font-size: 15px; }
</style>
