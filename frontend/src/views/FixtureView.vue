<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import RoundNav from '../components/RoundNav.vue'
import MatchCard from '../components/MatchCard.vue'
import MatchResultEditor from '../components/MatchResultEditor.vue'
import ZonePicker from '../components/ZonePicker.vue'
import { tournamentApi, zoneApi } from '../api/endpoints'
import type { Category, Match } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'
import { groupRounds } from '../rounds'

// Zone fixture by "fecha" (match day): ‹ Fecha N › navigation, results and upcoming matches
const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const categories = ref<Category[] | null>(null)
const zoneId = ref<number | null>(null)
const matches = ref<Match[]>([])
const index = ref(0)
const editing = ref<Match | null>(null)

const rounds = computed(() => groupRounds(matches.value))
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
        <RoundNav v-model="index" :rounds="rounds" />

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
.list { margin-top: 8px; }
.small { font-size: 13px; text-align: center; margin-top: 12px; }
</style>
