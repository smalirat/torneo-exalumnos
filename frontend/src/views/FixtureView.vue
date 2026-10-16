<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import MatchCard from '../components/MatchCard.vue'
import MatchResultEditor from '../components/MatchResultEditor.vue'
import ZonePicker from '../components/ZonePicker.vue'
import { tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, Match } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'

// Zone fixture by "fecha" (match day): ‹ Fecha N › navigation, results and upcoming matches
const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const categories = ref<Category[] | null>(null)
const zoneId = ref<number | null>(null)
const matches = ref<Match[]>([])
const index = ref(0)
const editing = ref<Match | null>(null)

interface Round { key: string; number: number | null; label: string | null; day: string | null; matches: Match[] }

/**
 * Matches grouped by "Fecha N" when they are numbered (league data), otherwise by calendar day.
 * Matches arrive sorted by date (undated last).
 */
const rounds = computed<Round[]>(() => {
  const out: Round[] = []
  const byKey = new Map<string, Round>()
  for (const m of matches.value) {
    const day = m.scheduledAt?.slice(0, 10) ?? null
    const key = m.round != null ? `r${m.round}` : `d${day}`
    let round = byKey.get(key)
    if (!round) {
      round = { key, number: m.round, label: m.roundLabel, day, matches: [] }
      byKey.set(key, round)
      out.push(round)
    }
    round.matches.push(m)
  }
  return out
})
const round = computed(() => rounds.value[index.value] ?? null)

watch(tournamentId, async (id) => { categories.value = await tournamentApi.categories(id) }, { immediate: true })

async function load(keepIndex = false) {
  matches.value = zoneId.value ? await zoneApi.fixtures(zoneId.value) : []
  if (keepIndex) return
  // Open on the first round that still has matches to play; otherwise the last one
  const pending = rounds.value.findIndex((r) => r.matches.some((m) => m.status !== 'FINISHED'))
  index.value = pending >= 0 ? pending : Math.max(rounds.value.length - 1, 0)
}
watch(zoneId, () => load())

function dayLabel(day: string | null) {
  if (!day) return t('fixture.noDate')
  const text = new Date(`${day}T00:00`).toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' })
  return text.charAt(0).toUpperCase() + text.slice(1) // "Domingo, 1 de noviembre"
}

async function onSaved() {
  editing.value = null
  await load(true)
}
</script>

<template>
  <TournamentLayout>
    <p v-if="categories === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="categories.length === 0" class="muted">{{ t('standings.noCategories') }}</p>

    <section v-else class="card fixture">
      <ZonePicker v-model="zoneId" :categories="categories" />

      <p v-if="rounds.length === 0" class="muted empty">{{ t('standings.noGames') }}</p>
      <template v-else>
        <nav class="rounds" :aria-label="t('fixture.rounds')">
          <button class="nav-btn" :disabled="index === 0" :title="t('fixture.previous')" @click="index--">
            <AppIcon name="chevron" class="flip" />
          </button>
          <div class="round-title">
            <strong>{{ t('fixture.round', { n: round?.number ?? index + 1 }) }}<template v-if="round?.label"> · {{ round.label }}</template></strong>
            <span class="muted">{{ dayLabel(round?.day ?? null) }}</span>
          </div>
          <button class="nav-btn" :disabled="index >= rounds.length - 1" :title="t('fixture.next')" @click="index++">
            <AppIcon name="chevron" />
          </button>
        </nav>

        <div class="list">
          <MatchCard
            v-for="m in round?.matches ?? []" :key="m.id" :match="m" date-format="time"
            :clickable="isAdmin" @click="isAdmin && (editing = m)"
          />
        </div>
        <p class="muted small">{{ t('fixture.progress', { played: round?.matches.filter((m) => m.status === 'FINISHED').length ?? 0, total: round?.matches.length ?? 0 }) }}</p>
      </template>
    </section>

    <MatchResultEditor v-if="editing" :match="editing" @saved="onSaved" @close="editing = null" />
  </TournamentLayout>
</template>

<style scoped>
.fixture { max-width: 760px; }
.empty { margin-top: 16px; }
.rounds { display: flex; align-items: center; justify-content: center; gap: 18px; margin: 20px 0 8px; }
.round-title { display: flex; flex-direction: column; align-items: center; min-width: 220px; }
.round-title strong { font-size: 20px; color: var(--navy); }
.round-title span { font-size: 13px; }
.nav-btn {
  width: 38px; height: 38px; border-radius: 50%; border: 1px solid var(--navy); background: #fff; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.nav-btn:disabled { opacity: 0.3; cursor: default; }
.flip { transform: rotate(180deg); }
.list { margin-top: 8px; }
.small { font-size: 13px; text-align: center; margin-top: 12px; }
</style>
