<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import RoundNav from '../components/RoundNav.vue'
import MatchCard from '../components/MatchCard.vue'
import MatchResultEditor from '../components/MatchResultEditor.vue'
import { tournamentApi } from '../api/endpoints'
import type { Category, Match } from '../api/types'
import { editionBySlug } from '../editions'
import { zoneOptions } from '../groups'
import { groupRounds, isDone } from '../rounds'
import { t } from '../i18n'

// Admin: load what was played, one match day ("Fecha N") at a time for the whole edition (every category and
// zone, interzonal matches and definitions). Each match opens the editor: score, scorers, cards, walkover.
const route = useRoute()
const tournamentId = computed(() => editionBySlug(route.params.edicion)?.id ?? 0)
const categories = ref<Category[]>([])
const matches = ref<Match[] | null>(null)
const index = ref(0)
const onlyPending = ref(false)
const openNext = ref(true)
const editing = ref<Match | null>(null)

const rounds = computed(() => groupRounds(matches.value ?? []))
const round = computed(() => rounds.value[index.value] ?? null)

async function load(keepIndex: boolean) {
  const id = tournamentId.value
  if (!id) return
  ;[categories.value, matches.value] = await Promise.all([tournamentApi.categories(id), tournamentApi.fixtures(id)])
  if (keepIndex) return
  // Open on the first match day already due with something to load; else the first one still to play
  const now = new Date().toISOString().slice(0, 10)
  const due = rounds.value.findIndex((r) => r.matches.some((m) => !isDone(m)) && (r.day ?? '') <= now && r.day !== null)
  const pending = rounds.value.findIndex((r) => r.matches.some((m) => !isDone(m)))
  index.value = due >= 0 ? due : pending >= 0 ? pending : Math.max(rounds.value.length - 1, 0)
}
watch(tournamentId, () => load(false), { immediate: true })

const labels = computed(() => new Map(zoneOptions(categories.value).map((o) => [o.id, o.label])))

/** Matches of the round grouped by zone (interzonal and definitions apart), in the order of the edition */
const groups = computed(() => {
  const out = new Map<string, Match[]>()
  for (const m of round.value?.matches ?? []) {
    if (onlyPending.value && isDone(m)) continue
    const key = m.type !== 'REGULAR' ? t(`results.matchTypes.${m.type}`)
      : m.zoneId !== null ? labels.value.get(m.zoneId) ?? '' : m.roundLabel ?? t('results.interzonal')
    if (!out.has(key)) out.set(key, [])
    out.get(key)!.push(m)
  }
  return [...out.entries()].map(([label, list]) => ({ label, matches: list }))
})

const loaded = computed(() => round.value?.matches.filter(isDone).length ?? 0)
const total = computed(() => round.value?.matches.length ?? 0)

async function onSaved() {
  const saved = editing.value
  editing.value = null
  await load(true)
  if (!openNext.value || !saved) return
  // Next match of the same day still to load (in the shown order)
  const list = groups.value.flatMap((g) => g.matches)
  const from = list.findIndex((m) => m.id === saved.id)
  editing.value = [...list.slice(from + 1), ...list.slice(0, Math.max(from, 0))].find((m) => !isDone(m)) ?? null
}
</script>

<template>
  <TournamentLayout>
    <p v-if="matches === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="rounds.length === 0" class="card muted">{{ t('results.noMatches') }}</p>

    <section v-else class="card entry">
      <RoundNav v-model="index" :rounds="rounds" />
      <div class="bar">
        <span :class="['progress', { complete: loaded === total }]">{{ t('results.progress', { loaded, total }) }}</span>
        <label class="check"><input v-model="onlyPending" type="checkbox" /> {{ t('results.onlyPending') }}</label>
        <label class="check"><input v-model="openNext" type="checkbox" /> {{ t('results.openNext') }}</label>
      </div>
      <p v-if="groups.length === 0" class="muted empty">{{ t('results.allLoaded') }}</p>
      <div v-for="g in groups" :key="g.label" class="group">
        <h3>{{ g.label }}</h3>
        <MatchCard v-for="m in g.matches" :key="m.id" :match="m" date-format="time" clickable @click="editing = m" />
      </div>
    </section>

    <MatchResultEditor v-if="editing" :key="editing.id" :match="editing" @saved="onSaved" @close="editing = null" />
  </TournamentLayout>
</template>

<style scoped>
.entry { max-width: 820px; }
.bar { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; margin: 4px 0 10px; font-size: 14px; }
.progress { font-weight: 500; color: var(--navy); }
.progress.complete { color: #1e7d45; }
.check { display: flex; align-items: center; gap: 6px; cursor: pointer; color: var(--muted); }
.group { margin-top: 12px; }
.group h3 { font-size: 13px; text-transform: uppercase; color: var(--muted); margin: 0 0 4px; letter-spacing: .02em; }
.empty { margin-top: 12px; }
</style>
