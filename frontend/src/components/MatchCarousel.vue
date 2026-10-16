<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'
import ScoreCard from './ScoreCard.vue'
import type { Match } from '../api/types'
import { t } from '../i18n'

// Horizontal strip of ScoreCards with ‹ › buttons (swipe on phones)
defineProps<{ matches: Match[] }>()
const track = ref<HTMLElement | null>(null)
const scroll = (dir: number) => track.value?.scrollBy({ left: dir * track.value.clientWidth * 0.8, behavior: 'smooth' })
</script>

<template>
  <div class="carousel">
    <button v-if="matches.length > 4" type="button" class="arrow" :title="t('fixture.previous')" @click="scroll(-1)">
      <AppIcon name="chevron" class="flip" />
    </button>
    <div ref="track" class="track">
      <ScoreCard v-for="m in matches" :key="m.id" :match="m" class="item" />
    </div>
    <button v-if="matches.length > 4" type="button" class="arrow" :title="t('fixture.next')" @click="scroll(1)">
      <AppIcon name="chevron" />
    </button>
  </div>
</template>

<style scoped>
.carousel { display: flex; align-items: center; gap: 6px; }
.track {
  flex: 1; display: grid; grid-auto-flow: column; grid-auto-columns: minmax(210px, calc((100% - 36px) / 4));
  gap: 12px; overflow-x: auto; scroll-snap-type: x mandatory; scrollbar-width: none; padding: 2px;
}
.track::-webkit-scrollbar { display: none; }
.item { scroll-snap-align: start; }
.arrow {
  width: 30px; height: 30px; flex-shrink: 0; border: 0; border-radius: 50%; background: transparent; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.arrow:hover { background: #fff; }
.flip { transform: rotate(180deg); }
@media (max-width: 720px) {
  .arrow { display: none; }
  .track { grid-auto-columns: 78%; }
}
</style>
