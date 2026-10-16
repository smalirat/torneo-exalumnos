<script setup lang="ts">
import { ref } from 'vue'
import type { Tournament, TournamentStatus } from '../api/types'
import type { TournamentInput } from '../api/endpoints'
import { t } from '../i18n'

// Create/edit form for a tournament. The status selector only shows when editing.
const props = defineProps<{ initial?: Tournament; submitLabel: string }>()
const emit = defineEmits<{ submit: [value: TournamentInput]; cancel: [] }>()

const STATUSES: TournamentStatus[] = ['DRAFT', 'OPEN', 'CLOSED', 'LIVE', 'COMPLETE']
const form = ref<TournamentInput>({
  name: props.initial?.name ?? '',
  location: props.initial?.location ?? '',
  registrationStart: props.initial?.registrationStart ?? '',
  registrationEnd: props.initial?.registrationEnd ?? '',
  matchStart: props.initial?.matchStart ?? '',
  matchEnd: props.initial?.matchEnd ?? '',
  status: props.initial?.status ?? 'OPEN',
})

function submit() {
  const v = form.value
  emit('submit', { ...v, location: v.location || null, matchStart: v.matchStart || null, matchEnd: v.matchEnd || null })
}
</script>

<template>
  <form @submit.prevent="submit">
    <div class="field">
      <label for="t-name">{{ t('tournaments.name') }}</label>
      <input id="t-name" v-model="form.name" class="input" required />
    </div>
    <div class="field">
      <label for="t-location">{{ t('tournaments.location') }}</label>
      <input id="t-location" v-model="form.location" class="input" />
    </div>
    <div class="row">
      <div class="field">
        <label for="t-start">{{ t('tournaments.registrationStart') }}</label>
        <input id="t-start" v-model="form.registrationStart" class="input" type="date" required />
      </div>
      <div class="field">
        <label for="t-end">{{ t('tournaments.registrationEnd') }}</label>
        <input id="t-end" v-model="form.registrationEnd" class="input" type="date" required />
      </div>
    </div>
    <div class="row">
      <div class="field">
        <label for="t-mstart">{{ t('tournaments.matchStart') }}</label>
        <input id="t-mstart" v-model="form.matchStart" class="input" type="date" />
      </div>
      <div class="field">
        <label for="t-mend">{{ t('tournaments.matchEnd') }}</label>
        <input id="t-mend" v-model="form.matchEnd" class="input" type="date" />
      </div>
    </div>
    <div v-if="initial" class="field">
      <label for="t-status">{{ t('tournaments.status') }}</label>
      <select id="t-status" v-model="form.status" class="select">
        <option v-for="s in STATUSES" :key="s" :value="s">{{ t(`tournaments.statuses.${s}`) }}</option>
      </select>
    </div>
    <slot name="error" />
    <div class="actions">
      <button type="button" class="btn secondary" @click="emit('cancel')">{{ t('tournaments.cancel') }}</button>
      <button class="btn">{{ submitLabel }}</button>
    </div>
  </form>
</template>

<style scoped>
.row { display: flex; gap: 12px; }
.row .field { flex: 1; }
.actions { display: flex; gap: 10px; justify-content: flex-end; }
</style>
