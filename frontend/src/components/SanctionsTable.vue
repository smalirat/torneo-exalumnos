<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { Sanction } from '../api/types'
import { t } from '../i18n'

// One list of sanctions. Served ones drop the "pending" and "status" columns (always "—" / "Cumplida").
defineProps<{ rows: Sanction[]; served?: boolean }>()
</script>

<template>
  <p v-if="rows.length === 0" class="muted">{{ t('season.empty') }}</p>
  <div v-else class="scroll">
    <table>
      <thead>
        <tr>
          <th class="left">{{ t('rankings.player') }}</th>
          <th class="left">{{ t('rankings.team') }}</th>
          <th class="left">{{ t('season.type') }}</th>
          <th>{{ t('season.suspended') }}</th>
          <th>{{ t('season.served') }}</th>
          <template v-if="!served">
            <th>{{ t('season.pending') }}</th>
            <th>{{ t('season.status') }}</th>
          </template>
          <th class="left">{{ t('season.notes') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in rows" :key="s.id">
          <td class="left">{{ s.player.fullName }}</td>
          <td class="left"><span class="team"><TeamBadge :name="s.team.name" :size="22" /> {{ s.team.name }}</span></td>
          <td class="left">{{ t(`season.types.${s.type}`) }}</td>
          <td>{{ s.matchesSuspended }}</td>
          <td>{{ s.matchesServed }}</td>
          <template v-if="!served">
            <td class="strong">{{ s.pending }}</td>
            <td><span :class="['status', s.status]">{{ t(`season.statuses.${s.status}`) }}</span></td>
          </template>
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
.status { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.status.PENDING { background: var(--danger); color: #fff; }
.status.TRIBUNAL { background: #fff; color: var(--danger); box-shadow: inset 0 0 0 1px var(--danger); }
</style>
