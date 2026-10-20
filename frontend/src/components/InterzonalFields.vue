<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { t } from '../i18n'

// Which zones play each other, how many match days and how positions are crossed: shared by the Interzonales
// button of the fixture (positions = the tables now) and the automatic build (positions = the seeding).
const props = defineProps<{ zones: { id: number; label: string }[] }>()
const pairs = defineModel<{ zoneA: number; zoneB: number }[]>('pairs', { required: true })
const rounds = defineModel<number>('rounds', { required: true })
const crossing = defineModel<'SAME' | 'INVERSE'>('crossing', { required: true })
const emit = defineEmits<{ change: [] }>()

function addPair() {
  const [a, b] = props.zones
  if (a && b) pairs.value.push({ zoneA: a.id, zoneB: b.id })
  emit('change')
}
function removePair(i: number) {
  pairs.value.splice(i, 1)
  emit('change')
}
</script>

<template>
  <div class="fields">
    <h3>{{ t('interzonal.pairs') }}</h3>
    <div v-for="(p, i) in pairs" :key="i" class="pair">
      <select v-model="p.zoneA" class="select" :aria-label="t('interzonal.zone')" @change="emit('change')">
        <option v-for="o in zones" :key="o.id" :value="o.id">{{ o.label }}</option>
      </select>
      <span class="muted">vs</span>
      <select v-model="p.zoneB" class="select" :aria-label="t('interzonal.zone')" @change="emit('change')">
        <option v-for="o in zones" :key="o.id" :value="o.id">{{ o.label }}</option>
      </select>
      <button type="button" class="icon-btn" :title="t('interzonal.removePair')" :aria-label="t('interzonal.removePair')" @click="removePair(i)">
        <AppIcon name="close" :size="18" />
      </button>
    </div>
    <button type="button" class="add" @click="addPair"><AppIcon name="plus" :size="16" /> {{ t('interzonal.addPair') }}</button>

    <label class="rounds">
      {{ t('interzonal.rounds') }}
      <input v-model.number="rounds" class="input" type="number" min="1" max="30" required @input="emit('change')" />
    </label>
    <div class="crossing" role="radiogroup" :aria-label="t('interzonal.crossing')">
      <label><input v-model="crossing" type="radio" value="SAME" @change="emit('change')" /> {{ t('interzonal.same') }}</label>
      <label><input v-model="crossing" type="radio" value="INVERSE" @change="emit('change')" /> {{ t('interzonal.inverse') }}</label>
    </div>
  </div>
</template>

<style scoped>
.fields { display: flex; flex-direction: column; }
h3 { font-size: 15px; margin: 0 0 6px; color: var(--navy); }
.pair { display: grid; grid-template-columns: 1fr auto 1fr 36px; align-items: center; gap: 8px; margin-bottom: 6px; }
.add {
  align-self: flex-start; display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px; border-radius: 999px; cursor: pointer;
  margin: 2px 0 12px; border: 1px dashed var(--border); background: none; color: var(--navy); font: inherit; font-size: 13px; font-weight: 500;
}
.rounds { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); max-width: 180px; margin-bottom: 10px; }
.crossing { display: flex; flex-direction: column; gap: 4px; font-size: 14px; }
.crossing label { display: flex; align-items: center; gap: 8px; cursor: pointer; }
</style>
