<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { Sanction } from '../api/types'
import { t } from '../i18n'

// Sanctions of a season (pending first, as the API sends them)
defineProps<{ sanctions: Sanction[] }>()
</script>

<template>
  <p v-if="sanctions.length === 0" class="muted">{{ t('season.empty') }}</p>
  <div v-else class="scroll">
    <table>
      <thead>
        <tr>
          <th class="left">{{ t('rankings.player') }}</th>
          <th class="left">{{ t('rankings.team') }}</th>
          <th class="left">{{ t('season.type') }}</th>
          <th>{{ t('season.suspended') }}</th>
          <th>{{ t('season.served') }}</th>
          <th>{{ t('season.pending') }}</th>
          <th>{{ t('season.status') }}</th>
          <th class="left">{{ t('season.notes') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in sanctions" :key="s.id">
          <td class="left">{{ s.player.fullName }}</td>
          <td class="left"><span class="team"><TeamBadge :name="s.team.name" :size="22" /> {{ s.team.name }}</span></td>
          <td class="left">{{ t(`season.types.${s.type}`) }}</td>
          <td>{{ s.matchesSuspended }}</td>
          <td>{{ s.matchesServed }}</td>
          <td class="strong">{{ s.status === 'SERVED' ? '—' : s.pending }}</td>
          <td><span :class="['status', s.status]">{{ t(`season.statuses.${s.status}`) }}</span></td>
          <td class="left small">{{ s.notes }}</td>
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
.strong { font-weight: 700; }
.small { font-size: 12px; color: var(--muted); }
.status { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--bg); color: var(--navy); white-space: nowrap; }
.status.PENDING { background: #f6d55c; color: #5a4500; }
.status.TRIBUNAL { background: #e05a4f; color: #fff; }
</style>
