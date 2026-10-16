<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { SeasonStatRow } from '../api/types'
import { t } from '../i18n'

// Annual player ranking (scorers, figures, goalkeepers): position, player, team and one value column
defineProps<{ rows: SeasonStatRow[]; valueLabel: string }>()
</script>

<template>
  <p v-if="rows.length === 0" class="muted">{{ t('season.empty') }}</p>
  <div v-else class="scroll">
    <table>
      <thead>
        <tr>
          <th>{{ t('standings.pos') }}</th>
          <th class="left">{{ t('rankings.player') }}</th>
          <th class="left">{{ t('rankings.team') }}</th>
          <th>{{ valueLabel }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.player.id">
          <td><span class="pos">{{ r.position }}</span></td>
          <td class="left">{{ r.player.fullName }}</td>
          <td class="left"><span class="team"><TeamBadge :team="r.team" :size="22" /> {{ r.team.name }}</span></td>
          <td class="strong">{{ r.value }}</td>
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
.pos {
  display: inline-flex; width: 30px; height: 30px; align-items: center; justify-content: center;
  background: var(--navy); color: #fff; border-radius: 6px;
}
.team { display: inline-flex; align-items: center; gap: 8px; }
.strong { font-weight: 700; }
</style>
