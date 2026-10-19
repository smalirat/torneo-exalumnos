<script setup lang="ts">
import { computed } from 'vue'
import TeamBadge from './TeamBadge.vue'
import type { YellowTally } from '../api/types'
import { t } from '../i18n'

// Accumulated yellow cards of a tournament ("AMONESTADOS" in the Excel), one small card per team
const props = defineProps<{ rows: YellowTally[] }>()

const teams = computed(() => {
  const byTeam = new Map<number, { team: YellowTally['team']; players: YellowTally[] }>()
  for (const r of props.rows) {
    const g = byTeam.get(r.team.id) ?? { team: r.team, players: [] }
    g.players.push(r)
    byTeam.set(r.team.id, g)
  }
  const groups = [...byTeam.values()].sort((a, b) => a.team.name.localeCompare(b.team.name, 'es'))
  for (const g of groups) {
    g.players.sort((a, b) => b.yellowCards - a.yellowCards || a.player.fullName.localeCompare(b.player.fullName, 'es'))
  }
  return groups
})
</script>

<template>
  <p v-if="rows.length === 0" class="muted">{{ t('season.empty') }}</p>
  <div v-else class="grid">
    <article v-for="g in teams" :key="g.team.id" class="team-card">
      <header>
        <TeamBadge :team="g.team" :size="26" />
        <h3>{{ g.team.name }}</h3>
      </header>
      <table>
        <thead>
          <tr>
            <th class="left">{{ t('rankings.player') }}</th>
            <th :title="t('rankings.yellowCount')"><span class="card-icon" aria-hidden="true" /></th>
            <th :title="t('rankings.suspensions')">{{ t('rankings.suspensionsShort') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="y in g.players" :key="y.player.id">
            <td class="left">{{ y.player.fullName }}</td>
            <td>{{ y.yellowCards }}</td>
            <td :class="{ strong: y.suspensions > 0 }">{{ y.suspensions }}</td>
          </tr>
        </tbody>
      </table>
    </article>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 12px; }
.team-card { border: 1px solid #e3e7ef; border-radius: var(--radius); padding: 12px; background: #fff; }
header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
h3 { font-size: 15px; color: var(--navy); }
table { width: 100%; border-collapse: separate; border-spacing: 0 2px; font-size: 14px; }
th { font-weight: 500; text-align: center; padding: 2px 6px; font-size: 12px; color: var(--muted); }
td { text-align: center; padding: 4px 6px; }
th:not(.left), td:not(.left) { width: 44px; }
.left { text-align: left; }
tbody tr:nth-child(even) td { background: var(--row-alt); }
.strong { font-weight: 700; color: var(--danger); }
.card-icon { display: inline-block; width: 10px; height: 14px; border-radius: 2px; background: #f6c400; vertical-align: middle; }
</style>
