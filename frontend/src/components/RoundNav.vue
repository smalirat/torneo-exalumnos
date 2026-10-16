<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { dayLabel, type Round } from '../rounds'
import { t } from '../i18n'

// ‹ Fecha N › navigation over grouped rounds; v-model is the index of the shown round
const props = withDefaults(defineProps<{ rounds: Round[]; tone?: 'navy' | 'public' }>(), { tone: 'navy' })
const index = defineModel<number>({ required: true })
const round = computed(() => props.rounds[index.value] ?? null)
</script>

<template>
  <nav :class="['rounds', tone]" :aria-label="t('fixture.rounds')">
    <button class="nav-btn" :disabled="index === 0" :title="t('fixture.previous')" @click="index--">
      <AppIcon name="chevron" class="flip" />
    </button>
    <div class="round-title">
      <strong>{{ t('fixture.round', { n: round?.number ?? index + 1 }) }}<template v-if="round?.label"> · {{ round.label }}</template></strong>
      <span class="muted">{{ dayLabel(round?.day ?? null) }}</span>
    </div>
    <button class="nav-btn" :disabled="index >= rounds.length - 1" :title="t('fixture.next')" @click="index++">
      <AppIcon name="chevron" />
    </button>
  </nav>
</template>

<style scoped>
.rounds { --accent: var(--navy); display: flex; align-items: center; justify-content: center; gap: 18px; margin: 20px 0 8px; }
.rounds.public { --accent: var(--public); }
.round-title { display: flex; flex-direction: column; align-items: center; min-width: 0; flex: 0 1 260px; text-align: center; }
.round-title strong { font-size: 20px; color: var(--accent); }
.public .round-title strong { font-family: var(--condensed); text-transform: uppercase; font-size: 24px; }
.round-title span { font-size: 13px; }
.nav-btn {
  width: 38px; height: 38px; flex-shrink: 0; border-radius: 50%; border: 1px solid var(--accent); background: #fff;
  color: var(--accent); display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.nav-btn:disabled { opacity: 0.3; cursor: default; }
.flip { transform: rotate(180deg); }
</style>
