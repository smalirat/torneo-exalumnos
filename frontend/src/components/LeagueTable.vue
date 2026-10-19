<script setup lang="ts">
import TeamBadge from './TeamBadge.vue'
import type { StandingRow } from '../api/types'
import { teamPath } from '../paths'
import { t } from '../i18n'

// Public standings table (league style): solid header, plain rows, points last. Scrolls sideways on phones
// with the position and team columns pinned.
defineProps<{ rows: StandingRow[] }>()
</script>

<template>
  <p v-if="rows.length === 0" class="muted empty">{{ t('standings.noTeams') }}</p>
  <div v-else class="scroll">
    <table>
      <thead>
        <tr>
          <th class="pin pos">#</th>
          <th class="pin team left">{{ t('standings.team') }}</th>
          <th :title="t('league.playedHint')">{{ t('league.played') }}</th>
          <th>{{ t('standings.won') }}</th>
          <th>{{ t('standings.drawn') }}</th>
          <th>{{ t('standings.lost') }}</th>
          <th>{{ t('standings.gf') }}</th>
          <th>{{ t('standings.ga') }}</th>
          <th>{{ t('standings.diff') }}</th>
          <th :title="t('standings.presenceHint')">{{ t('standings.presence') }}</th>
          <th class="pts">{{ t('standings.pts') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.team.id">
          <td class="pin pos">{{ r.position }}</td>
          <td class="pin team left">
            <RouterLink :to="teamPath(r.team)" class="name">
              <TeamBadge :team="r.team" :size="24" /> <span>{{ r.team.name }}</span>
            </RouterLink>
          </td>
          <td>{{ r.played }}</td>
          <td>{{ r.won }}</td>
          <td>{{ r.drawn }}</td>
          <td>{{ r.lost }}</td>
          <td>{{ r.goalsFor }}</td>
          <td>{{ r.goalsAgainst }}</td>
          <td>{{ r.goalDiff > 0 ? `+${r.goalDiff}` : r.goalDiff }}</td>
          <td>{{ r.presencePoints }}</td>
          <td class="pts">{{ r.points }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.empty { padding: 20px; }
.scroll { overflow-x: auto; border-radius: var(--radius); }
table { width: 100%; border-collapse: collapse; font-size: 14px; background: #fff; }
th {
  background: var(--navy); color: #fff; font-size: 12px; font-weight: 700; text-transform: uppercase;
  padding: 11px 8px; text-align: center; white-space: nowrap;
}
td { padding: 12px 8px; text-align: center; border-bottom: 1px solid var(--border); white-space: nowrap; }
tbody tr:last-child td { border-bottom: 0; }
tbody tr:hover td { background: var(--row-alt); }
.left { text-align: left; }
.pos { width: 44px; color: var(--muted); }
th.pos { color: #fff; }
.team { min-width: 180px; }
.name { display: inline-flex; align-items: center; gap: 10px; font-weight: 500; }
.name:hover span { text-decoration: underline; }
.pts { font-weight: 700; font-size: 15px; }
th.pts { font-size: 12px; }
/* Phones: keep position and team visible while the numbers scroll */
@media (max-width: 720px) {
  .pin { position: sticky; z-index: 1; box-sizing: border-box; }
  td.pin { background: #fff; }
  th.pin { background: var(--navy); }
  .pin.pos { left: 0; width: 34px; min-width: 34px; max-width: 34px; padding-left: 4px; padding-right: 4px; }
  .pin.team { left: 34px; min-width: 150px; max-width: 150px; box-shadow: 1px 0 0 var(--border); }
  .name span { white-space: normal; }
}
</style>
