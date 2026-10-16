<script setup lang="ts">
import { computed } from 'vue'
import TeamBadge from './TeamBadge.vue'
import type { Sanction } from '../api/types'
import { t } from '../i18n'

// Sanctions of a season in two tables: still to serve (pending / tribunal) and already served
const props = defineProps<{ sanctions: Sanction[] }>()
const groups = computed(() => [
  { key: 'open', title: t('season.openSanctions'), rows: props.sanctions.filter((s) => s.status !== 'SERVED') },
  { key: 'served', title: t('season.servedSanctions'), rows: props.sanctions.filter((s) => s.status === 'SERVED') },
].filter((g) => g.rows.length > 0))
</script>

<template>
  <p v-if="sanctions.length === 0" class="muted">{{ t('season.empty') }}</p>
  <div v-for="g in groups" :key="g.key" class="group">
    <h3>{{ g.title }} <span class="count">{{ g.rows.length }}</span></h3>
    <div class="scroll">
      <table>
        <thead>
          <tr>
            <th class="left">{{ t('rankings.player') }}</th>
            <th class="left">{{ t('rankings.team') }}</th>
            <th class="left">{{ t('season.type') }}</th>
            <th>{{ t('season.suspended') }}</th>
            <th>{{ t('season.served') }}</th>
            <template v-if="g.key === 'open'">
              <th>{{ t('season.pending') }}</th>
              <th>{{ t('season.status') }}</th>
            </template>
            <th class="left">{{ t('season.notes') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in g.rows" :key="s.id">
            <td class="left">{{ s.player.fullName }}</td>
            <td class="left"><span class="team"><TeamBadge :name="s.team.name" :size="22" /> {{ s.team.name }}</span></td>
            <td class="left">{{ t(`season.types.${s.type}`) }}</td>
            <td>{{ s.matchesSuspended }}</td>
            <td>{{ s.matchesServed }}</td>
            <template v-if="g.key === 'open'">
              <td class="strong">{{ s.pending }}</td>
              <td><span :class="['status', s.status]">{{ t(`season.statuses.${s.status}`) }}</span></td>
            </template>
            <td class="left small">{{ s.notes }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.group + .group { margin-top: 20px; }
h3 { font-size: 15px; font-weight: 700; color: var(--navy); margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
.count { font-size: 12px; font-weight: 700; padding: 1px 8px; border-radius: 999px; background: var(--bg); color: var(--muted); }
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
