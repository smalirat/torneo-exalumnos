<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { Sanction } from '../api/types'
import { t } from '../i18n'

// One list of sanctions. Served ones drop the "pending" and "status" columns (always "—" / "Cumplida").
// A suspension is the person's: it shows the team they play for now ("recibida en X" if they got it elsewhere) and,
// when it comes from an earlier season than `year`, which one.
defineProps<{ rows: Sanction[]; served?: boolean; year?: number | null; admin?: boolean }>()
// admin: one more match served, or the total of match days changed (the tribunal decided)
const emit = defineEmits<{ change: [sanction: Sanction, matchesSuspended: number, matchesServed: number] }>()
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
          <th v-if="admin && !served">{{ t('season.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in rows" :key="s.id">
          <td class="left">{{ s.player.fullName }}</td>
          <td class="left">
            <span class="team"><TeamBadge :team="s.team" :size="22" /> {{ s.team.name }}</span>
            <small v-if="s.receivedWith" class="from">{{ t('season.receivedWith', { team: s.receivedWith.name }) }}</small>
          </td>
          <td class="left">
            {{ t(`season.types.${s.type}`) }}
            <small v-if="year && s.seasonYear !== year" class="from">{{ t('season.fromSeason', { year: s.seasonYear }) }}</small>
          </td>
          <td>{{ s.matchesSuspended }}</td>
          <td>{{ s.matchesServed }}</td>
          <template v-if="!served">
            <td class="strong">{{ s.pending }}</td>
            <td><span :class="['status', s.status]">{{ t(`season.statuses.${s.status}`) }}</span></td>
          </template>
          <td class="left small">{{ s.notes }}</td>
          <td v-if="admin && !served" class="actions">
            <button type="button" class="btn secondary small-btn" @click="emit('change', s, s.matchesSuspended, s.matchesServed + 1)">
              {{ t('season.serveOne') }}
            </button>
            <label class="total">{{ t('season.total') }}
              <input
                class="input" type="number" min="1" max="30" :value="s.matchesSuspended"
                @change="emit('change', s, Number(($event.target as HTMLInputElement).value), s.matchesServed)"
              >
            </label>
          </td>
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
.actions { white-space: nowrap; }
.actions .total { display: inline-flex; align-items: center; gap: 4px; margin-left: 6px; font-size: 12px; color: var(--muted); }
.actions .input { width: 56px; padding: 2px 6px; }
.small-btn { padding: 4px 10px; font-size: 12px; }
.from { display: block; font-size: 12px; color: var(--muted); }
.small { font-size: 12px; color: var(--muted); }
.status { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.status.PENDING { background: var(--danger); color: #fff; }
.status.TRIBUNAL { background: #fff; color: var(--danger); box-shadow: inset 0 0 0 1px var(--danger); }
</style>
