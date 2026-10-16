<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import TeamBadge from '../components/TeamBadge.vue'
import { tournamentApi } from '../api/endpoints'
import type { Category, Registration } from '../api/types'
import { t } from '../i18n'

// Admin: place registered teams into category zones and manage zones
const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const categories = ref<Category[]>([])
const registrations = ref<Registration[]>([])
const error = ref('')
const zoneFormFor = ref<number | null>(null)
const zoneName = ref('')

/** Label of a zone; the category alone when it has a single zone. */
const zoneOptions = computed(() => categories.value.flatMap((c) =>
  c.zones.map((z) => ({ id: z.id, label: c.zoneCount > 1 ? `${c.name} · ${z.name}` : c.name }))))

const unassigned = computed(() => registrations.value.filter((r) => r.zoneId === null))
const inZone = (zoneId: number) => registrations.value.filter((r) => r.zoneId === zoneId)

async function load(id: number) {
  ;[categories.value, registrations.value] = await Promise.all([
    tournamentApi.categories(id), tournamentApi.registrations(id)])
}
watch(tournamentId, load, { immediate: true })

async function run(action: () => Promise<void>) {
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    await load(tournamentId.value) // put selects back to the real state
  }
}

const assign = (reg: Registration, event: Event) => run(async () => {
  const zoneId = Number((event.target as HTMLSelectElement).value)
  const updated = await tournamentApi.assignZone(reg.id, zoneId)
  Object.assign(reg, updated)
})

const addZone = (categoryId: number) => run(async () => {
  await tournamentApi.createZone(categoryId, zoneName.value)
  zoneName.value = ''
  zoneFormFor.value = null
  categories.value = await tournamentApi.categories(tournamentId.value)
})
</script>

<template>
  <TournamentLayout>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="categories.length === 0" class="card muted">{{ t('tournamentTeams.needCategory') }}</p>

    <section class="card">
      <h2>{{ t('tournamentTeams.unassigned', { n: unassigned.length }) }}</h2>
      <p v-if="registrations.length === 0" class="muted">{{ t('tournamentTeams.noRegistrations') }}</p>
      <p v-else-if="unassigned.length === 0" class="muted">{{ t('tournamentTeams.noneUnassigned') }}</p>
      <div v-for="r in unassigned" :key="r.id" class="team">
        <TeamBadge :name="r.team.name" :size="34" />
        <span class="name">{{ r.team.name }}</span>
        <select v-if="zoneOptions.length" class="select" :aria-label="t('tournamentTeams.assignTo')" @change="assign(r, $event)">
          <option value="" selected disabled>{{ t('tournamentTeams.assignTo') }}</option>
          <option v-for="z in zoneOptions" :key="z.id" :value="z.id">{{ z.label }}</option>
        </select>
      </div>
    </section>

    <section v-for="c in categories" :key="c.id" class="card">
      <header class="cat-header">
        <h2>{{ c.name }}</h2>
        <form v-if="zoneFormFor === c.id" class="zone-form" @submit.prevent="addZone(c.id)">
          <input v-model="zoneName" class="input" :placeholder="t('tournamentTeams.zoneName')" required autofocus />
          <button class="btn">{{ t('tournaments.create') }}</button>
          <button type="button" class="btn secondary" @click="zoneFormFor = null">{{ t('tournaments.cancel') }}</button>
        </form>
        <button v-else class="btn secondary" @click="zoneFormFor = c.id; zoneName = `Zona ${c.zoneCount + 1}`">
          <AppIcon name="plus" :size="18" /> {{ t('tournamentTeams.addZone') }}
        </button>
      </header>
      <div v-for="z in c.zones" :key="z.id" class="zone">
        <h3 v-if="c.zoneCount > 1">{{ z.name }}</h3>
        <p v-if="inZone(z.id).length === 0" class="muted">{{ t('tournamentTeams.noTeamsInZone') }}</p>
        <div v-for="r in inZone(z.id)" :key="r.id" class="team">
          <TeamBadge :name="r.team.name" :size="34" />
          <span class="name">{{ r.team.name }}</span>
          <select class="select" :value="r.zoneId" :aria-label="t('tournamentTeams.assignTo')" @change="assign(r, $event)">
            <option v-for="o in zoneOptions" :key="o.id" :value="o.id">{{ o.label }}</option>
          </select>
        </div>
      </div>
    </section>
  </TournamentLayout>
</template>

<style scoped>
.card { margin-bottom: 18px; }
h2 { font-size: 18px; margin-bottom: 10px; }
h3 { font-size: 14px; color: var(--muted); margin: 12px 0 4px; text-transform: uppercase; }
.cat-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 4px; }
.cat-header h2 { margin: 0; }
.zone-form { display: flex; gap: 8px; }
.team { display: flex; align-items: center; gap: 12px; padding: 8px 4px; border-bottom: 1px solid var(--border); }
.team:last-child { border-bottom: 0; }
.name { flex: 1; }
.select { width: auto; min-width: 200px; }
</style>
