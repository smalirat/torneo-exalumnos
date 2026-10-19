<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import ScoreCard from './ScoreCard.vue'
import type { Match } from '../api/types'
import { t } from '../i18n'

// Horizontal strip of ScoreCards that only ever shows whole cards: how many fit (4, 3, 2 or 1) depends on the
// carousel's own width (container queries) and each card takes exactly its share. ‹ › move one page; swipe too.
const props = defineProps<{ matches: Match[] }>()
const track = ref<HTMLElement | null>(null)
const canPrev = ref(false)
const canNext = ref(false)

const GAP = 12

function update() {
  const el = track.value
  if (!el) return
  canPrev.value = el.scrollLeft > 1
  canNext.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
}

/** One page = the visible width plus the gap after the last visible card, so the next page starts on a card */
const scroll = (dir: number) => track.value?.scrollBy({ left: dir * (track.value.clientWidth + GAP), behavior: 'smooth' })

let observer: ResizeObserver | null = null
onMounted(() => {
  observer = new ResizeObserver(update)
  if (track.value) observer.observe(track.value)
  update()
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => props.matches, () => {
  track.value?.scrollTo({ left: 0 })
  requestAnimationFrame(update)
})
</script>

<template>
  <div class="carousel">
    <button type="button" class="arrow" :disabled="!canPrev" :title="t('fixture.previous')" @click="scroll(-1)">
      <AppIcon name="chevron" class="flip" />
    </button>
    <div class="viewport">
      <div ref="track" class="track" @scroll.passive="update">
        <ScoreCard v-for="m in matches" :key="m.id" :match="m" class="item" />
      </div>
    </div>
    <button type="button" class="arrow" :disabled="!canNext" :title="t('fixture.next')" @click="scroll(1)">
      <AppIcon name="chevron" />
    </button>
  </div>
</template>

<style scoped>
.carousel { display: flex; align-items: center; gap: 6px; }
/* The viewport is the container the card count is measured against */
.viewport { flex: 1; min-width: 0; container-type: inline-size; }
.track {
  --per: 4;
  display: grid; grid-auto-flow: column; gap: 12px;
  grid-auto-columns: calc((100% - (var(--per) - 1) * 12px) / var(--per));
  overflow-x: auto; scroll-snap-type: x mandatory; scrollbar-width: none; padding: 2px 0;
}
/* ~210 px minimum per card: 4 need 876 px, 3 need 654 px, 2 need 432 px */
@container (max-width: 875px) { .track { --per: 3; } }
@container (max-width: 653px) { .track { --per: 2; } }
@container (max-width: 431px) { .track { --per: 1; } }
.track::-webkit-scrollbar { display: none; }
.item { scroll-snap-align: start; min-width: 0; }
.arrow {
  width: 30px; height: 30px; flex-shrink: 0; border: 0; border-radius: 50%; background: transparent; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.arrow:hover:not(:disabled) { background: #fff; }
.arrow:disabled { opacity: 0.25; cursor: default; }
.flip { transform: rotate(180deg); }
/* Phones: swipe instead of arrows, more room for the card */
@media (max-width: 720px) { .arrow { display: none; } }
</style>
