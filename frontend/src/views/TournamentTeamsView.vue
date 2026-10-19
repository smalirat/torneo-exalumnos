<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import TeamBadge from '../components/TeamBadge.vue'
import ConfirmButton from '../components/ConfirmButton.vue'
import InlineEdit from '../components/InlineEdit.vue'
import BaseModal from '../components/BaseModal.vue'
import { tournamentApi } from '../api/endpoints'
import type { Category, Registration, Zone } from '../api/types'
import { zoneOptions } from '../groups'
import { t } from '../i18n'

// Admin: place registered teams into category zones and manage zones
const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const categories = ref<Category[]>([])
const registrations = ref<Registration[]>([])
const error = ref('')
const zoneFormFor = ref<number | null>(null)
const zoneName = ref('')

/** Label of every zone: only the zone with a single category, else "A · Zona 1" / "A" (see groups.ts) */
const options = computed(() => zoneOptions(categories.value, true))

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

const reloadCategories = async () => { categories.value = await tournamentApi.categories(tournamentId.value) }

const renameCategory = async (c: Category, name: string) => {
  await tournamentApi.renameCategory(c.id, name)
  await reloadCategories()
}

const renameZone = async (z: Zone, name: string) => {
  await tournamentApi.renameZone(z.id, name)
  await reloadCategories()
}

const deleteZone = (z: Zone) => run(async () => {
  await tournamentApi.removeZone(z.id)
  await reloadCategories()
})

// Team withdrawal ("se dio de baja"): its remaining matches become 3-0 walkovers; it can be undone
const withdrawFor = ref<Registration | null>(null)
const withdrawDate = ref('')
function openWithdraw(reg: Registration) {
  withdrawFor.value = reg
  withdrawDate.value = new Date().toISOString().slice(0, 10)
}
const confirmWithdraw = () => run(async () => {
  const reg = withdrawFor.value
  if (!reg) return
  Object.assign(reg, reg.withdrawn
    ? await tournamentApi.undoWithdraw(reg.id)
    : await tournamentApi.withdraw(reg.id, withdrawDate.value || null))
  withdrawFor.value = null
})

// Random draw of unassigned teams into one category's zones
const drawFor = ref<Category | null>(null)
const drawIds = ref<number[]>([])
const notice = ref('')

function openDraw(c: Category) {
  drawFor.value = c
  // With a single category every unassigned team goes there; otherwise the admin picks
  drawIds.value = categories.value.length === 1 ? unassigned.value.map((r) => r.id) : []
}

const draw = () => run(async () => {
  if (!drawFor.value) return
  const n = (await tournamentApi.drawZones(drawFor.value.id, drawIds.value)).length
  drawFor.value = null
  notice.value = t('tournamentTeams.drawDone', { n })
  await load(tournamentId.value)
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
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="categories.length === 0" class="card muted">{{ t('tournamentTeams.needCategory') }}</p>

    <section class="card">
      <h2>{{ t('tournamentTeams.unassigned', { n: unassigned.length }) }}</h2>
      <p v-if="registrations.length === 0" class="muted">{{ t('tournamentTeams.noRegistrations') }}</p>
      <p v-else-if="unassigned.length === 0" class="muted">{{ t('tournamentTeams.noneUnassigned') }}</p>
      <div v-for="r in unassigned" :key="r.id" class="team">
        <TeamBadge :team="r.team" :size="34" />
        <span class="name">{{ r.team.name }}</span>
        <select v-if="options.length" class="select" :aria-label="t('tournamentTeams.assignTo')" @change="assign(r, $event)">
          <option value="" selected disabled>{{ t('tournamentTeams.assignTo') }}</option>
          <option v-for="z in options" :key="z.id" :value="z.id">{{ z.label }}</option>
        </select>
      </div>
    </section>

    <section v-for="c in categories" :key="c.id" class="card">
      <header class="cat-header">
        <h2><InlineEdit :value="c.name" :save="(name) => renameCategory(c, name)" /></h2>
        <form v-if="zoneFormFor === c.id" class="zone-form" @submit.prevent="addZone(c.id)">
          <input v-model="zoneName" class="input" :placeholder="t('tournamentTeams.zoneName')" required autofocus />
          <button class="btn">{{ t('tournaments.create') }}</button>
          <button type="button" class="btn secondary" @click="zoneFormFor = null">{{ t('tournaments.cancel') }}</button>
        </form>
        <div v-else class="cat-actions">
          <button v-if="unassigned.length" class="btn secondary" @click="openDraw(c)">{{ t('tournamentTeams.draw') }}</button>
          <button class="btn secondary" @click="zoneFormFor = c.id; zoneName = `Zona ${c.zoneCount + 1}`">
            <AppIcon name="plus" :size="18" /> {{ t('tournamentTeams.addZone') }}
          </button>
        </div>
      </header>
      <div v-for="z in c.zones" :key="z.id" class="zone">
        <h3 v-if="c.zoneCount > 1" class="zone-title">
          <InlineEdit :value="z.name" :save="(name) => renameZone(z, name)" />
          <ConfirmButton
            icon :label="t('tournamentTeams.deleteZone')"
            :message="t('tournamentTeams.deleteZoneConfirm', { name: z.name })" @confirm="deleteZone(z)"
          />
        </h3>
        <p v-if="inZone(z.id).length === 0" class="muted">{{ t('tournamentTeams.noTeamsInZone') }}</p>
        <div v-for="r in inZone(z.id)" :key="r.id" class="team">
          <TeamBadge :team="r.team" :size="34" />
          <span class="name">{{ r.team.name }}</span>
          <select class="select" :value="r.zoneId" :aria-label="t('tournamentTeams.assignTo')" @change="assign(r, $event)">
            <option v-for="o in options" :key="o.id" :value="o.id">{{ o.label }}</option>
          </select>
          <button type="button" class="btn secondary small-btn" @click="openWithdraw(r)">
            {{ r.withdrawn ? t('tournamentTeams.undoWithdraw') : t('tournamentTeams.withdraw') }}
          </button>
        </div>
      </div>
    </section>

    <BaseModal
      v-if="withdrawFor" width="460px" @close="withdrawFor = null"
      :title="withdrawFor.withdrawn ? t('tournamentTeams.undoWithdrawTitle', { name: withdrawFor.team.name }) : t('tournamentTeams.withdrawTitle', { name: withdrawFor.team.name })"
    >
      <template v-if="withdrawFor.withdrawn">
        <p>{{ t('tournamentTeams.undoWithdrawText') }}</p>
      </template>
      <template v-else>
        <p>{{ t('tournamentTeams.withdrawText', { name: withdrawFor.team.name }) }}</p>
        <div class="field">
          <label for="withdraw-date">{{ t('tournamentTeams.withdrawDate') }}</label>
          <input id="withdraw-date" v-model="withdrawDate" class="input" type="date" />
        </div>
      </template>
      <template #footer>
        <button type="button" class="btn secondary" @click="withdrawFor = null">{{ t('tournaments.cancel') }}</button>
        <button type="button" class="btn" @click="confirmWithdraw">
          {{ withdrawFor.withdrawn ? t('tournamentTeams.undoWithdraw') : t('tournamentTeams.withdraw') }}
        </button>
      </template>
    </BaseModal>

    <BaseModal v-if="drawFor" :title="t('tournamentTeams.drawTitle', { name: drawFor.name })" @close="drawFor = null">
      <p class="muted small">{{ t('tournamentTeams.drawHelp') }}</p>
      <div class="pick-actions">
        <button type="button" class="link" @click="drawIds = unassigned.map((r) => r.id)">{{ t('tournamentTeams.drawAll') }}</button>
        <button type="button" class="link" @click="drawIds = []">{{ t('tournamentTeams.drawNone') }}</button>
      </div>
      <label v-for="r in unassigned" :key="r.id" class="pick">
        <input v-model="drawIds" type="checkbox" :value="r.id" />
        <TeamBadge :team="r.team" :size="24" /> {{ r.team.name }}
      </label>
      <template #footer>
        <button class="btn secondary" @click="drawFor = null">{{ t('tournaments.cancel') }}</button>
        <button class="btn" :disabled="drawIds.length === 0" @click="draw">
          {{ t('tournamentTeams.drawSubmit', { n: drawIds.length }) }}
        </button>
      </template>
    </BaseModal>
  </TournamentLayout>
</template>

<style scoped>
.card { margin-bottom: 18px; }
h2 { font-size: 18px; margin-bottom: 10px; }
h3 { font-size: 14px; color: var(--muted); margin: 12px 0 4px; text-transform: uppercase; }
.zone-title { display: flex; align-items: center; gap: 6px; }
.cat-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 4px; }
.cat-header h2 { margin: 0; }
.zone-form { display: flex; gap: 8px; }
.cat-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.notice { color: var(--navy); font-weight: 500; margin-bottom: 12px; }
.small { font-size: 13px; }
.pick-actions { display: flex; gap: 12px; margin: 10px 0 6px; }
.link { background: none; border: 0; color: var(--navy); font: inherit; font-weight: 500; cursor: pointer; padding: 0; text-decoration: underline; }
.pick { display: flex; align-items: center; gap: 10px; padding: 6px 2px; border-bottom: 1px solid var(--border); cursor: pointer; }
.team { display: flex; align-items: center; gap: 12px; padding: 8px 4px; border-bottom: 1px solid var(--border); }
.team:last-child { border-bottom: 0; }
.name { flex: 1; }
.select { width: auto; min-width: 200px; }
.small-btn { padding: 6px 12px; font-size: 13px; white-space: nowrap; }
</style>
