<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import TeamBadge from './TeamBadge.vue'
import type { CategoryHistory } from '../api/types'
import { t } from '../i18n'

// Champions by category and year, with the most-titles ranking next to each list
defineProps<{ history: CategoryHistory[] }>()
</script>

<template>
  <p v-if="history.length === 0" class="card muted">{{ t('historyPage.empty') }}</p>
  <section v-for="c in history" :key="c.categoryName" class="card category">
    <h2><AppIcon name="trophy" /> {{ t('historyPage.category', { name: c.categoryName }) }}</h2>
    <div class="grid">
      <table>
        <thead><tr><th>{{ t('historyPage.year') }}</th><th class="left">{{ t('historyPage.champion') }}</th></tr></thead>
        <tbody>
          <tr v-for="ch in c.champions" :key="ch.id">
            <td class="year">{{ ch.year }}</td>
            <td class="left"><span class="team"><TeamBadge :name="ch.team.name" :size="22" /> {{ ch.team.name }}</span></td>
          </tr>
        </tbody>
      </table>
      <div>
        <h3>{{ t('historyPage.top') }}</h3>
        <table>
          <thead><tr><th class="left">{{ t('rankings.team') }}</th><th>{{ t('historyPage.titles') }}</th></tr></thead>
          <tbody>
            <tr v-for="top in c.topChampions" :key="top.team.id">
              <td class="left"><span class="team"><TeamBadge :name="top.team.name" :size="22" /> {{ top.team.name }}</span></td>
              <td class="strong">{{ top.titles }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<style scoped>
.category { margin-bottom: 20px; }
h2 { display: flex; align-items: center; gap: 8px; font-size: 20px; color: var(--navy); margin-bottom: 12px; }
h3 { font-size: 14px; text-transform: uppercase; color: var(--muted); margin-bottom: 6px; }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; align-items: start; }
table { width: 100%; border-collapse: separate; border-spacing: 0 3px; font-size: 14px; }
th { font-weight: 500; padding: 4px 6px; text-align: center; }
td { padding: 5px 6px; text-align: center; }
.left { text-align: left; }
tbody tr:nth-child(even) td { background: var(--row-alt); }
.year { font-weight: 700; color: var(--navy); width: 70px; }
.team { display: inline-flex; align-items: center; gap: 8px; }
.strong { font-weight: 700; }
</style>
