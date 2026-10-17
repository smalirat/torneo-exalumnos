<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import { tournamentApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { Tournament, TournamentStatus } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'

// Status of an edition, moved by hand by the admin: En preparación → Inscripción abierta → En juego → Finalizado
// (and back). Only one edition can be active (inscripción abierta / en juego): if another one is, the admin
// confirms finishing it in the same step.
const props = defineProps<{ tournament: Tournament }>()
const emit = defineEmits<{ changed: [] }>()

const ACTIONS: Record<TournamentStatus, TournamentStatus[]> = {
  DRAFT: ['OPEN', 'LIVE'],
  OPEN: ['LIVE', 'DRAFT'],
  LIVE: ['COMPLETE', 'OPEN'],
  COMPLETE: ['LIVE'],
}
const actions = computed(() => ACTIONS[props.tournament.status] ?? [])
const label = (to: TournamentStatus) => t(`editionStatus.to.${props.tournament.status}.${to}`)

const busy = ref(false)
const error = ref('')
/** Another edition is active: ask before finishing it */
const conflict = ref<{ to: TournamentStatus; message: string } | null>(null)

async function move(to: TournamentStatus, finishActive = false) {
  busy.value = true
  error.value = ''
  try {
    await tournamentApi.changeStatus(props.tournament.id, to, finishActive)
    conflict.value = null
    emit('changed')
  } catch (e) {
    if (e instanceof ApiError && e.status === 409 && !finishActive) conflict.value = { to, message: e.message }
    else error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="status-box">
    <p class="label">{{ t('tournaments.status') }}</p>
    <p :class="['value', tournament.status]">{{ t(`tournaments.statuses.${tournament.status}`) }}</p>
    <p class="muted hint">{{ t(`editionStatus.hint.${tournament.status}`) }}</p>
    <div v-if="isAdmin" class="actions">
      <button
        v-for="(to, i) in actions" :key="to" type="button" :class="['btn', { secondary: i > 0 }]" :disabled="busy"
        @click="move(to)"
      >{{ label(to) }}</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <BaseModal v-if="conflict" :title="t('editionStatus.conflictTitle')" width="440px" @close="conflict = null">
      <p>{{ conflict.message }}</p>
      <p class="muted">{{ t('editionStatus.conflictText', { name: tournament.name }) }}</p>
      <template #footer>
        <button type="button" class="btn secondary" @click="conflict = null">{{ t('tournaments.cancel') }}</button>
        <button type="button" class="btn" :disabled="busy" @click="move(conflict.to, true)">{{ t('editionStatus.finishAndContinue') }}</button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.label { margin: 0; font-size: 13px; color: var(--muted); }
.value { margin: 2px 0 0; font-size: 18px; font-weight: 700; color: var(--navy); }
.value.OPEN { color: #1e7d45; }
.value.LIVE { color: #c0392b; }
.value.COMPLETE { color: var(--muted); }
.hint { margin: 4px 0 0; font-size: 13px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
</style>
