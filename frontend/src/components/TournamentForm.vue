<script setup lang="ts">
import { ref } from 'vue'
import type { Tournament } from '../api/types'
import type { TournamentInput } from '../api/endpoints'
import { t } from '../i18n'

// Create/edit form for a tournament. The status is changed apart (EditionStatus); dates are informative.
const props = defineProps<{ initial?: Tournament; submitLabel: string }>()
const emit = defineEmits<{ submit: [value: TournamentInput]; cancel: [] }>()

const form = ref<TournamentInput>({
  name: props.initial?.name ?? '',
  location: props.initial?.location ?? '',
  registrationStart: props.initial?.registrationStart ?? '',
  registrationEnd: props.initial?.registrationEnd ?? '',
  matchStart: props.initial?.matchStart ?? '',
  matchEnd: props.initial?.matchEnd ?? '',
  seasonYear: props.initial?.seasonYear ?? null,
  stage: props.initial?.stage ?? null,
})

function submit() {
  const v = form.value
  emit('submit', {
    ...v,
    location: v.location || null,
    registrationStart: v.registrationStart || null,
    registrationEnd: v.registrationEnd || null,
    matchStart: v.matchStart || null,
    matchEnd: v.matchEnd || null,
    seasonYear: v.seasonYear || null,
    stage: v.seasonYear ? v.stage : null,
  })
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
        <label for="t-season">{{ t('tournaments.seasonYear') }}</label>
        <input id="t-season" v-model.number="form.seasonYear" class="input" type="number" min="1900" max="2100" />
      </div>
      <div class="field">
        <label for="t-stage">{{ t('tournaments.stage') }}</label>
        <select id="t-stage" v-model="form.stage" class="select" :disabled="!form.seasonYear">
          <option :value="null">{{ t('tournaments.noStage') }}</option>
          <option value="APERTURA">{{ t('tournaments.stages.APERTURA') }}</option>
          <option value="CLAUSURA">{{ t('tournaments.stages.CLAUSURA') }}</option>
        </select>
      </div>
    </div>
    <div class="row">
      <div class="field">
        <label for="t-start">{{ t('tournaments.registrationStart') }}</label>
        <input id="t-start" v-model="form.registrationStart" class="input" type="date" />
      </div>
      <div class="field">
        <label for="t-end">{{ t('tournaments.registrationEnd') }}</label>
        <input id="t-end" v-model="form.registrationEnd" class="input" type="date" />
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
    <p class="muted small">{{ t('tournaments.datesHelp') }}</p>
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
.small { font-size: 13px; margin: 0 0 12px; }
</style>
