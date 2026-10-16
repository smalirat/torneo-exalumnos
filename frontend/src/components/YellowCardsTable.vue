<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { YellowTally } from '../api/types'
import { t } from '../i18n'

// Accumulated yellow cards of a tournament ("AMONESTADOS" in the Excel): 4 yellows = 1 match suspension
defineProps<{ rows: YellowTally[] }>()
</script>

<template>
  <p v-if="rows.length === 0" class="muted">{{ t('season.empty') }}</p>
  <div v-else class="scroll">
    <table>
      <thead>
        <tr>
          <th class="left">{{ t('rankings.player') }}</th>
          <th class="left">{{ t('rankings.team') }}</th>
          <th>{{ t('rankings.yellowCount') }}</th>
          <th>{{ t('rankings.suspensions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="y in rows" :key="y.player.id">
          <td class="left">{{ y.player.fullName }}</td>
          <td class="left"><span class="team"><TeamBadge :team="y.team" :size="22" /> {{ y.team.name }}</span></td>
          <td>{{ y.yellowCards }}</td>
          <td :class="{ strong: y.suspensions > 0 }">{{ y.suspensions }}</td>
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
.team { display: inline-flex; align-items: center; gap: 8px; }
.strong { font-weight: 700; color: var(--danger); }
</style>
