<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import PhotoLightbox from './PhotoLightbox.vue'
import { collageApi } from '../api/endpoints'
import type { CollageItem, CollagePhoto } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'

// Champions' collage: photos spread by hand over a board that scales with the screen (like the Excel sheet).
// Everyone: featured photo + collage, tap to open big. Admin: floating "+" to add, drag to move, corner to resize,
// tap a photo for its options (front / back / featured / delete). Photos are not tied to a champion.
const photos = defineModel<CollagePhoto[]>('photos', { required: true })

const board = ref<HTMLElement | null>(null)
const selected = ref<number | null>(null)
const viewing = ref<number | null>(null)
const uploading = ref<{ n: number; total: number } | null>(null)
const error = ref('')

const heightOf = (p: CollagePhoto) => p.w * (p.height / p.width)
/** Board height in board widths: down to the lowest photo (plus room to drag further when editing) */
const boardHeight = computed(() => {
  const bottom = Math.max(0.2, ...photos.value.map((p) => p.y + heightOf(p)))
  return bottom + (isAdmin.value ? 0.12 : 0.01)
})
const featured = computed(() => photos.value.find((p) => p.featured) ?? null)
/** Reading order (top to bottom, left to right) for the big view and the entrance animation */
const ordered = computed(() => [...photos.value].sort((a, b) => a.y - b.y || a.x - b.x))
const orderOf = computed(() => new Map(ordered.value.map((p, i) => [p.id, i])))

function style(p: CollagePhoto) {
  return {
    left: `${p.x * 100}%`,
    top: `${(p.y / boardHeight.value) * 100}%`,
    width: `${p.w * 100}%`,
    // The selected photo (admin) comes forward so its corner and options are never covered
    zIndex: selected.value === p.id ? 100000 : p.z,
    animationDelay: `${Math.min(orderOf.value.get(p.id) ?? 0, 30) * 40}ms`,
  }
}

const open = (p: CollagePhoto) => { viewing.value = orderOf.value.get(p.id) ?? 0 }

// ---------------------------------------------------------------- admin: move / resize

let drag: { id: number; mode: 'move' | 'resize'; startX: number; startY: number; orig: CollagePhoto; moved: boolean } | null = null

function onDown(e: PointerEvent, p: CollagePhoto, mode: 'move' | 'resize') {
  if (!isAdmin.value || e.button !== 0) return
  e.preventDefault()
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  drag = { id: p.id, mode, startX: e.clientX, startY: e.clientY, orig: { ...p }, moved: false }
}

function onMove(e: PointerEvent) {
  if (!drag || !board.value) return
  const px = board.value.clientWidth
  const dx = (e.clientX - drag.startX) / px
  const dy = (e.clientY - drag.startY) / px
  if (!drag.moved && Math.hypot(e.clientX - drag.startX, e.clientY - drag.startY) < 4) return
  drag.moved = true
  const p = photos.value.find((x) => x.id === drag!.id)
  if (!p) return
  if (drag.mode === 'move') {
    p.x = clamp(drag.orig.x + dx, 0, 1 - drag.orig.w)
    p.y = Math.max(0, drag.orig.y + dy)
  } else {
    p.w = clamp(drag.orig.w + dx, 0.06, 1 - drag.orig.x)
  }
}

async function onUp(p: CollagePhoto) {
  if (!drag) return
  const wasMoved = drag.moved
  drag = null
  if (wasMoved) await save([p])
  else selected.value = selected.value === p.id ? null : p.id
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))
const item = (p: CollagePhoto): CollageItem => ({ id: p.id, x: p.x, y: p.y, w: p.w, z: p.z })

async function save(changed: CollagePhoto[]) {
  error.value = ''
  try {
    photos.value = await collageApi.layout(changed.map(item))
  } catch (e) {
    error.value = (e as Error).message
    photos.value = await collageApi.list()
  }
}

function toFront(p: CollagePhoto) {
  p.z = Math.max(...photos.value.map((x) => x.z)) + 1
  save([p])
}

function toBack(p: CollagePhoto) {
  const min = Math.min(...photos.value.map((x) => x.z))
  if (min > 0) {
    p.z = min - 1
    save([p])
  } else {
    // Layer 0 is taken: push everyone else up one
    const others = photos.value.filter((x) => x.id !== p.id)
    others.forEach((x) => { x.z += 1 })
    p.z = 0
    save([p, ...others])
  }
}

async function toggleFeatured(p: CollagePhoto) {
  photos.value = await collageApi.featured(p.id, !p.featured)
}

async function remove(p: CollagePhoto) {
  if (!confirm(t('collage.confirmRemove'))) return
  await collageApi.remove(p.id)
  photos.value = photos.value.filter((x) => x.id !== p.id)
  selected.value = null
}

// ---------------------------------------------------------------- admin: add photos

const fileInput = ref<HTMLInputElement | null>(null)

async function onFiles(e: Event) {
  const files = [...((e.target as HTMLInputElement).files ?? [])]
  ;(e.target as HTMLInputElement).value = ''
  error.value = ''
  for (let i = 0; i < files.length; i++) {
    uploading.value = { n: i + 1, total: files.length }
    try {
      const jpg = await toJpeg(files[i])
      photos.value = [...photos.value, await collageApi.upload(jpg, files[i].name.replace(/\.\w+$/, '') + '.jpg')]
    } catch (err) {
      error.value = t('collage.uploadError', { name: files[i].name, error: (err as Error).message })
    }
  }
  uploading.value = null
}

/** Shrinks the photo in the browser (max 1600 px, JPG) so uploads stay light. */
async function toJpeg(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('JPG'))), 'image/jpeg', 0.85))
}
</script>

<template>
  <section v-if="photos.length || isAdmin" class="collage">
    <button v-if="featured" type="button" class="featured" @click="open(featured)">
      <img :src="featured.url" :alt="t('collage.featured')">
    </button>

    <p v-if="isAdmin" class="hint muted">{{ photos.length ? t('collage.editHint') : t('collage.empty') }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div
      v-if="photos.length" ref="board" :class="['board', { editing: isAdmin }]"
      :style="{ aspectRatio: `1 / ${boardHeight}` }" @click.self="selected = null"
    >
      <figure
        v-for="p in photos" :key="p.id" :class="['photo', { selected: selected === p.id }]" :style="style(p)"
        @pointerdown="onDown($event, p, 'move')" @pointermove="onMove" @pointerup="onUp(p)"
        @click="!isAdmin && open(p)" @dblclick="isAdmin && open(p)"
      >
        <img :src="p.url" :alt="t('collage.photo', { n: (orderOf.get(p.id) ?? 0) + 1 })" loading="lazy" draggable="false">
        <template v-if="isAdmin">
          <span
            class="resize" :title="t('collage.resize')"
            @pointerdown.stop="onDown($event, p, 'resize')" @pointermove="onMove" @pointerup.stop="onUp(p)"
          />
          <div v-if="selected === p.id" class="tools" @pointerdown.stop>
            <button type="button" @click.stop="toFront(p)">{{ t('collage.toFront') }}</button>
            <button type="button" @click.stop="toBack(p)">{{ t('collage.toBack') }}</button>
            <button type="button" :class="{ on: p.featured }" @click.stop="toggleFeatured(p)">
              <AppIcon name="star" :size="14" /> {{ p.featured ? t('collage.unfeature') : t('collage.feature') }}
            </button>
            <button type="button" class="danger" @click.stop="remove(p)"><AppIcon name="delete" :size="14" /> {{ t('collage.remove') }}</button>
          </div>
        </template>
      </figure>
    </div>

    <template v-if="isAdmin">
      <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFiles">
      <p v-if="uploading" class="uploading">{{ t('collage.uploading', uploading) }}</p>
      <button type="button" class="fab" :title="t('collage.add')" :aria-label="t('collage.add')" :disabled="!!uploading" @click="fileInput?.click()">
        <AppIcon name="plus" :size="28" />
      </button>
    </template>

    <PhotoLightbox v-if="viewing !== null" v-model:index="viewing" :photos="ordered" />
  </section>
</template>

<style scoped>
.collage { margin-bottom: 24px; }
.featured {
  display: block; width: 100%; padding: 0; border: 0; background: none; cursor: zoom-in; margin-bottom: 18px;
}
.featured img {
  display: block; width: 100%; max-height: 460px; object-fit: cover; border-radius: var(--radius);
  box-shadow: 0 6px 24px rgba(20, 39, 90, 0.25);
}
.hint { font-size: 13px; margin: 0 0 10px; }
.board { position: relative; width: 100%; }
.photo {
  position: absolute; margin: 0; cursor: zoom-in; user-select: none;
  animation: drop-in 0.45s ease-out both;
}
.photo img {
  display: block; width: 100%; height: auto; border: 3px solid #fff; border-radius: 2px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.28); transition: transform 0.15s, box-shadow 0.15s;
}
.board:not(.editing) .photo:hover img { transform: scale(1.03); box-shadow: 0 8px 22px rgba(0, 0, 0, 0.35); }
.editing .photo { cursor: grab; touch-action: none; }
.editing .photo:active { cursor: grabbing; }
.photo.selected img { outline: 3px solid var(--navy); outline-offset: 1px; }
@keyframes drop-in { from { opacity: 0; transform: translateY(-14px) scale(0.97); } }

.resize {
  position: absolute; right: -7px; bottom: -7px; width: 18px; height: 18px; border-radius: 50%;
  background: var(--navy); border: 2px solid #fff; cursor: nwse-resize; touch-action: none;
  opacity: 0; transition: opacity 0.15s;
}
.photo:hover .resize, .photo.selected .resize { opacity: 1; }
.tools {
  position: absolute; left: 0; top: calc(100% + 6px); z-index: 2; display: flex; flex-wrap: wrap; gap: 4px;
  background: #fff; border-radius: 10px; padding: 6px; box-shadow: 0 4px 16px rgba(20, 39, 90, 0.25); width: max-content;
}
.tools button {
  display: inline-flex; align-items: center; gap: 4px; font: inherit; font-size: 12px; font-weight: 500;
  border: 1px solid var(--border); background: #fff; color: var(--navy); border-radius: 999px; padding: 4px 10px; cursor: pointer;
}
.tools button.on { background: #f6c400; border-color: #f6c400; color: #3d3000; }
.tools button.danger { color: var(--danger); }

.fab {
  position: fixed; right: 24px; bottom: 24px; z-index: 900; width: 58px; height: 58px; border-radius: 50%;
  background: var(--navy); color: #fff; border: 0; cursor: pointer; box-shadow: 0 6px 16px rgba(20, 39, 90, 0.4);
  display: inline-flex; align-items: center; justify-content: center;
}
.fab:disabled { opacity: 0.6; cursor: wait; }
.uploading {
  position: fixed; right: 92px; bottom: 36px; z-index: 900; margin: 0; background: var(--navy); color: #fff;
  font-size: 13px; padding: 6px 12px; border-radius: 999px;
}
@media (prefers-reduced-motion: reduce) {
  .photo { animation: none; }
  .photo img { transition: none; }
}
</style>
