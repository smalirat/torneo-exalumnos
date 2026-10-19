<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { teamApi, tournamentApi } from '../api/endpoints'
import type { Category, SlotReq, TeamSummary, Tournament } from '../api/types'
import { zoneOptions } from '../groups'
import { t } from '../i18n'

// One side of a promotion/final: a fixed team, or "position N of a zone" from any tournament
const props = defineProps<{ label: string; defaultTournamentId: number }>()
const model = defineModel<SlotReq | null>({ required: true })

const mode = ref<'position' | 'team'>('position')
const teams = ref<TeamSummary[]>([])
const tournaments = ref<Tournament[]>([])
const categories = ref<Category[]>([])
const teamId = ref<number | null>(null)
const tournamentId = ref<number>(props.defaultTournamentId)
const zoneId = ref<number | null>(null)
const position = ref(1)

const options = computed(() => zoneOptions(categories.value, true))

onMounted(async () => {
  ;[teams.value, tournaments.value] = await Promise.all([teamApi.list(), tournamentApi.list()])
  teamId.value = teams.value[0]?.id ?? null
})

watch(tournamentId, async (id) => {
  categories.value = await tournamentApi.categories(id)
  zoneId.value = options.value[0]?.id ?? null
}, { immediate: true })

watch([mode, teamId, zoneId, position], () => {
  model.value = mode.value === 'team'
    ? (teamId.value ? { teamId: teamId.value } : null)
    : (zoneId.value ? { zoneId: zoneId.value, position: position.value } : null)
}, { immediate: true })
</script>

<template>
  <fieldset class="slot">
    <legend>{{ label }}</legend>
    <div class="modes">
      <label><input v-model="mode" type="radio" value="position" /> {{ t('playoffs.byPosition') }}</label>
      <label><input v-model="mode" type="radio" value="team" /> {{ t('playoffs.byTeam') }}</label>
    </div>

    <template v-if="mode === 'position'">
      <label class="field-inline">{{ t('playoffs.position') }}
        <input v-model.number="position" class="input num" type="number" min="1" required />
      </label>
      <label class="field-inline">{{ t('playoffs.tournament') }}
        <select v-model="tournamentId" class="select">
          <option v-for="x in tournaments" :key="x.id" :value="x.id">{{ x.name }}</option>
        </select>
      </label>
      <label class="field-inline">{{ t('standings.category') }}
        <select v-model="zoneId" class="select" required>
          <option v-for="z in options" :key="z.id" :value="z.id">{{ z.label }}</option>
        </select>
      </label>
    </template>
    <label v-else class="field-inline">{{ t('playoffs.team') }}
      <select v-model="teamId" class="select" required>
        <option v-for="x in teams" :key="x.id" :value="x.id">{{ x.name }}</option>
      </select>
    </label>
  </fieldset>
</template>

<style scoped>
.slot { border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; margin: 0; flex: 1; min-width: 260px; }
legend { font-weight: 700; color: var(--navy); padding: 0 6px; }
.modes { display: flex; gap: 16px; margin-bottom: 10px; font-size: 14px; }
.modes label { display: inline-flex; align-items: center; gap: 6px; }
.field-inline { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); margin-bottom: 8px; }
.num { width: 90px; }
</style>
