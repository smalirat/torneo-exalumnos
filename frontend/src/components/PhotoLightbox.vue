<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import AppIcon from './AppIcon.vue'
import type { CollagePhoto } from '../api/types'
import { t } from '../i18n'

// Full-screen photo with previous / next (buttons, arrow keys, swipe). Escape or the backdrop closes it.
const props = defineProps<{ photos: CollagePhoto[] }>()
const index = defineModel<number | null>('index', { required: true })

const go = (step: number) => {
  if (index.value === null || props.photos.length === 0) return
  index.value = (index.value + step + props.photos.length) % props.photos.length
}
const close = () => { index.value = null }

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft') go(-1)
  else if (e.key === 'ArrowRight') go(1)
}

let startX: number | null = null
const onDown = (e: PointerEvent) => { startX = e.clientX }
function onUp(e: PointerEvent) {
  if (startX === null) return
  const dx = e.clientX - startX
  startX = null
  if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1)
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  document.body.style.overflow = 'hidden'
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="lightbox" role="dialog" aria-modal="true" @click.self="close" @pointerdown="onDown" @pointerup="onUp">
    <button type="button" class="close" :aria-label="t('collage.close')" @click="close"><AppIcon name="close" :size="26" /></button>
    <button v-if="photos.length > 1" type="button" class="nav prev" :aria-label="t('collage.prev')" @click="go(-1)">
      <AppIcon name="chevron" :size="34" />
    </button>
    <img
      v-if="index !== null && photos[index]" :key="photos[index].id" :src="photos[index].url"
      :alt="t('collage.photo', { n: index + 1 })" draggable="false"
    >
    <button v-if="photos.length > 1" type="button" class="nav next" :aria-label="t('collage.next')" @click="go(1)">
      <AppIcon name="chevron" :size="34" />
    </button>
    <p v-if="index !== null" class="counter">{{ index + 1 }} / {{ photos.length }}</p>
  </div>
</template>

<style scoped>
.lightbox {
  position: fixed; inset: 0; z-index: 1000; background: rgba(8, 14, 32, 0.92);
  display: flex; align-items: center; justify-content: center; touch-action: pan-y;
}
img {
  max-width: 92vw; max-height: 84vh; border-radius: 6px; box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  user-select: none; animation: pop 0.18s ease-out;
}
@keyframes pop { from { opacity: 0; transform: scale(0.97); } }
button { position: absolute; background: none; border: 0; color: #fff; cursor: pointer; padding: 10px; opacity: 0.85; }
button:hover { opacity: 1; }
.close { top: 12px; right: 12px; }
.nav { top: 50%; transform: translateY(-50%); }
.prev { left: 8px; }
.prev :deep(svg) { transform: rotate(180deg); }
.next { right: 8px; }
.counter { position: absolute; bottom: 14px; left: 0; right: 0; text-align: center; color: rgba(255, 255, 255, 0.8); font-size: 14px; margin: 0; }
@media (prefers-reduced-motion: reduce) { img { animation: none; } }
</style>
