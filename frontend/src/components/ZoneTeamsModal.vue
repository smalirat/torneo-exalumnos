<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import ConfirmButton from './ConfirmButton.vue'
import TeamBadge from './TeamBadge.vue'
import { teamApi, tournamentApi, zoneApi } from '../api/endpoints'
import type { Registration, TeamSummary } from '../api/types'
import { t } from '../i18n'

// Admin: teams of a zone. Put a team straight in (one of the league, or a new one by name) or take it out
// while it has no matches (afterwards it is a withdrawal, in Equipos).
const props = defineProps<{ tournamentId: number; zoneId: number; title: string }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const inZone = ref<Registration[]>([])
const placedIds = ref<Set<number>>(new Set())
const allTeams = ref<TeamSummary[]>([])
const mode = ref<'existing' | 'new'>('existing')
const search = ref('')
const teamId = ref<number | null>(null)
const newName = ref('')
const error = ref('')
const busy = ref(false)

async function load() {
  const [zone, regs, teams] = await Promise.all([
    zoneApi.registrations(props.zoneId), tournamentApi.registrations(props.tournamentId), teamApi.list()])
  inZone.value = zone
  placedIds.value = new Set(regs.filter((r) => r.zoneId !== null).map((r) => r.team.id))
  allTeams.value = teams.filter((x) => x.approved)
}
onMounted(load)

/** Teams of the league that are not playing in a zone of this edition yet, filtered by the search */
const candidates = computed(() => {
  const q = search.value.trim().toLocaleLowerCase('es')
  return allTeams.value
    .filter((x) => !placedIds.value.has(x.id))
    .filter((x) => !q || x.name.toLocaleLowerCase('es').includes(q))
})

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}

const add = () => run(async () => {
  await tournamentApi.addZoneTeam(props.zoneId, mode.value === 'existing' ? { teamId: teamId.value! } : { teamName: newName.value.trim() })
  teamId.value = null
  newName.value = ''
  search.value = ''
})

const remove = (r: Registration) => run(() => tournamentApi.removeRegistration(r.id))
</script>

<template>
  <BaseModal :title="title" width="560px" @close="emit('close')">
    <p v-if="inZone.length === 0" class="muted">{{ t('zoneTeams.empty') }}</p>
    <ul class="teams">
      <li v-for="r in inZone" :key="r.id">
        <TeamBadge :team="r.team" :size="28" />
        <span class="name">{{ r.team.name }}</span>
        <ConfirmButton
          icon :label="t('zoneTeams.remove')" :message="t('zoneTeams.removeConfirm', { name: r.team.name })"
          @confirm="remove(r)"
        />
      </li>
    </ul>

    <form class="add" @submit.prevent="add">
      <h3>{{ t('zoneTeams.addTitle') }}</h3>
      <div class="modes" role="radiogroup">
        <label><input v-model="mode" type="radio" value="existing" /> {{ t('zoneTeams.existing') }}</label>
        <label><input v-model="mode" type="radio" value="new" /> {{ t('zoneTeams.new') }}</label>
      </div>
      <template v-if="mode === 'existing'">
        <input v-model="search" class="input" type="search" :placeholder="t('zoneTeams.search')" :aria-label="t('zoneTeams.search')" />
        <select v-model="teamId" class="select" required size="6" :aria-label="t('zoneTeams.existing')">
          <option v-for="x in candidates" :key="x.id" :value="x.id">{{ x.name }}</option>
        </select>
        <p v-if="candidates.length === 0" class="muted small">{{ t('zoneTeams.noCandidates') }}</p>
      </template>
      <template v-else>
        <input v-model="newName" class="input" maxlength="100" required :placeholder="t('teams.namePlaceholder')" />
        <p class="muted small">{{ t('zoneTeams.newHint') }}</p>
      </template>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn" :disabled="busy || (mode === 'existing' ? teamId === null : !newName.trim())">{{ t('zoneTeams.add') }}</button>
    </form>
  </BaseModal>
</template>

<style scoped>
.teams { list-style: none; margin: 0 0 14px; padding: 0; }
.teams li { display: flex; align-items: center; gap: 10px; padding: 6px 2px; border-bottom: 1px solid var(--border); }
.name { flex: 1; }
.add { display: flex; flex-direction: column; gap: 8px; border-top: 1px solid var(--border); padding-top: 12px; }
.add h3 { font-size: 15px; margin: 0; color: var(--navy); }
.modes { display: flex; gap: 16px; font-size: 14px; }
.modes label { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.select[size] { height: auto; padding: 4px; }
.small { font-size: 12px; margin: 0; }
.btn { align-self: flex-end; }
</style>
