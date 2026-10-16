<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import TeamBadge from '../components/TeamBadge.vue'
import YellowCardsTable from '../components/YellowCardsTable.vue'
import { tournamentApi } from '../api/endpoints'
import type { CardRow, ScorerRow, YellowTally } from '../api/types'
import { t } from '../i18n'

const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const scorers = ref<ScorerRow[] | null>(null)
const cards = ref<CardRow[] | null>(null)
const yellows = ref<YellowTally[]>([])

watch(tournamentId, async (id) => {
  ;[scorers.value, cards.value, yellows.value] = await Promise.all([
    tournamentApi.scorers(id), tournamentApi.cards(id), tournamentApi.yellowCards(id)])
}, { immediate: true })
</script>

<template>
  <TournamentLayout>
    <div class="grid">
      <section class="card">
        <h2>{{ t('rankings.scorers') }}</h2>
        <p v-if="scorers && scorers.length === 0" class="muted">{{ t('rankings.noGoals') }}</p>
        <table v-else-if="scorers">
          <thead>
            <tr>
              <th>{{ t('standings.pos') }}</th>
              <th class="left">{{ t('rankings.player') }}</th>
              <th class="left">{{ t('rankings.team') }}</th>
              <th>{{ t('rankings.goals') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in scorers" :key="r.player.id">
              <td><span class="pos">{{ i + 1 }}</span></td>
              <td class="left">{{ r.player.fullName }}</td>
              <td class="left"><span class="team"><TeamBadge :team="r.team" :size="22" /> {{ r.team.name }}</span></td>
              <td class="strong">{{ r.goals }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card">
        <h2>{{ t('rankings.cards') }}</h2>
        <p v-if="cards && cards.length === 0" class="muted">{{ t('rankings.noCards') }}</p>
        <table v-else-if="cards">
          <thead>
            <tr>
              <th class="left">{{ t('rankings.player') }}</th>
              <th class="left">{{ t('rankings.team') }}</th>
              <th><span class="card-icon yellow" :title="t('results.types.YELLOW_CARD')" /></th>
              <th><span class="card-icon red" :title="t('results.types.RED_CARD')" /></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in cards" :key="r.player.id">
              <td class="left">{{ r.player.fullName }}</td>
              <td class="left"><span class="team"><TeamBadge :team="r.team" :size="22" /> {{ r.team.name }}</span></td>
              <td>{{ r.yellowCards }}</td>
              <td>{{ r.redCards }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="yellows.length" class="card">
        <h2>{{ t('rankings.yellows') }}</h2>
        <YellowCardsTable :rows="yellows" />
      </section>
    </div>
  </TournamentLayout>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr)); gap: 20px; align-items: start; }
h2 { font-size: 18px; margin-bottom: 12px; color: var(--navy); }
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
.card-icon { display: inline-block; width: 12px; height: 16px; border-radius: 2px; vertical-align: middle; }
.card-icon.yellow { background: #f6d55c; }
.card-icon.red { background: #e05a4f; }
</style>
