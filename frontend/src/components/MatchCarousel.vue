<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import ScoreCard from './ScoreCard.vue'
import type { Match } from '../api/types'
import { t } from '../i18n'

// Strip of ScoreCards that only shows whole cards: how many fit (4, 3, 2 or 1) depends on its own width and each
// card takes exactly its share. When not all fit it loops forever and moves one match every few seconds
// (paused on hover, in a hidden tab or with reduced motion). ‹ › and swipe move one match too.
const props = defineProps<{ matches: Match[] }>()

const GAP = 12
const INTERVAL_MS = 4000
/** ~210 px minimum per card: 4 need 876 px, 3 need 654 px, 2 need 432 px */
const perFor = (w: number) => (w >= 876 ? 4 : w >= 654 ? 3 : w >= 432 ? 2 : 1)

const viewport = ref<HTMLElement | null>(null)
const width = ref(0)
const per = computed(() => perFor(width.value))
const loops = computed(() => props.matches.length > per.value)
/** Twice the list when looping: after the last match comes the first again */
const items = computed(() => (loops.value ? [...props.matches, ...props.matches] : props.matches))
const cardWidth = computed(() => Math.max(0, (width.value - (per.value - 1) * GAP) / per.value))

const reducedMotion = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const index = ref(0)
const animate = ref(true)
const offset = computed(() => -index.value * (cardWidth.value + GAP))

/** Runs `then` two frames later, once the browser has painted the position set without animation */
const afterPaint = (then: () => void) => requestAnimationFrame(() => requestAnimationFrame(then))

function go(step: number) {
  if (!loops.value) return
  const n = props.matches.length
  if (reducedMotion) {
    // No slide: just show the next/previous match
    animate.value = false
    index.value = (index.value + step + n) % n
    return
  }
  if (index.value >= n) {
    // Still in the second copy (clicked during a slide): move to the same cards of the first copy first
    animate.value = false
    index.value -= n
    afterPaint(() => go(step))
    return
  }
  if (step < 0 && index.value === 0) {
    // Jump (invisibly) to the same cards in the second copy, then slide back one
    animate.value = false
    index.value = n
    afterPaint(() => { animate.value = true; index.value = n - 1 })
    return
  }
  animate.value = true
  index.value += step
}

/** Reaching the second copy: snap back to the same cards of the first one, without animation */
function onTransitionEnd() {
  if (index.value >= props.matches.length) {
    animate.value = false
    index.value -= props.matches.length
  }
}

// ---- autoplay
const hovering = ref(false)
let timer: number | undefined
function restart() {
  window.clearInterval(timer)
  if (reducedMotion) return
  timer = window.setInterval(() => {
    if (!hovering.value && document.visibilityState === 'visible') go(1)
  }, INTERVAL_MS)
}
/** Manual moves restart the countdown, so the next automatic one waits a full interval */
function manual(step: number) {
  go(step)
  restart()
}

// ---- swipe
let startX: number | null = null
const onDown = (e: PointerEvent) => { startX = e.clientX }
function onUp(e: PointerEvent) {
  if (startX === null) return
  const dx = e.clientX - startX
  startX = null
  if (Math.abs(dx) > 40) manual(dx < 0 ? 1 : -1)
}

let observer: ResizeObserver | null = null
onMounted(() => {
  observer = new ResizeObserver(([entry]) => { width.value = entry.contentRect.width })
  if (viewport.value) observer.observe(viewport.value)
  restart()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  window.clearInterval(timer)
})
watch(() => props.matches, () => {
  animate.value = false
  index.value = 0
  restart()
})
watch(per, () => {
  animate.value = false
  index.value = 0
})
</script>

<template>
  <div class="carousel" @mouseenter="hovering = true" @mouseleave="hovering = false">
    <button v-if="loops" type="button" class="arrow" :title="t('fixture.previous')" @click="manual(-1)">
      <AppIcon name="chevron" class="flip" />
    </button>
    <div ref="viewport" class="viewport" @pointerdown="onDown" @pointerup="onUp" @pointercancel="startX = null">
      <div
        class="track" :class="{ animate }"
        :style="{ transform: `translateX(${offset}px)`, gridAutoColumns: `${cardWidth}px` }"
        @transitionend="onTransitionEnd"
      >
        <ScoreCard v-for="(m, i) in items" :key="`${m.id}-${i}`" :match="m" class="item" />
      </div>
    </div>
    <button v-if="loops" type="button" class="arrow" :title="t('fixture.next')" @click="manual(1)">
      <AppIcon name="chevron" />
    </button>
  </div>
</template>

<style scoped>
.carousel { display: flex; align-items: center; gap: 6px; }
.viewport { flex: 1; min-width: 0; overflow: hidden; padding: 2px 0; touch-action: pan-y; }
.track { display: grid; grid-auto-flow: column; gap: 12px; }
.track.animate { transition: transform 0.5s ease; }
.item { min-width: 0; user-select: none; }
.arrow {
  width: 30px; height: 30px; flex-shrink: 0; border: 0; border-radius: 50%; background: transparent; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.arrow:hover { background: #fff; }
.flip { transform: rotate(180deg); }
/* Phones: swipe instead of arrows, more room for the card */
@media (max-width: 720px) { .arrow { display: none; } }
@media (prefers-reduced-motion: reduce) { .track.animate { transition: none; } }
</style>
