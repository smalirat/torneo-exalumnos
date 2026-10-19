<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import AppIcon from './AppIcon.vue'
import PhotoLightbox from './PhotoLightbox.vue'
import { collageApi } from '../api/endpoints'
import type { CollageItem, CollagePhoto } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'

// Champions' collage: photos spread by hand over a board that scales with the screen (like the Excel sheet).
// Everyone: featured photo + collage, tap to open big. Editable (admin panel only): every change (add, move,
// resize, front/back, featured, delete) goes to a draft and only takes effect with "Guardar cambios";
// "Descartar" goes back to what is saved and "Ordenar automáticamente" lays everything out on the default grid.
const photos = defineModel<CollagePhoto[]>('photos', { required: true })
/** Only the admin panel's History page edits; the public tab is view-only for everyone (admins included) */
const props = defineProps<{ editable?: boolean }>()
const canEdit = computed(() => !!props.editable && isAdmin.value)

/** A photo of the draft; new ones have a negative id and their image in memory until saved */
type DraftPhoto = CollagePhoto & { file?: Blob }

const board = ref<HTMLElement | null>(null)
const selected = ref<number | null>(null)
const viewing = ref<number | null>(null)
const busy = ref<string | null>(null)
const error = ref('')

// ---------------------------------------------------------------- draft

const draft = ref<DraftPhoto[]>([])
let nextTempId = -1

function resetDraft() {
  draft.value.forEach((d) => { if (d.file) URL.revokeObjectURL(d.url) })
  draft.value = photos.value.map((p) => ({ ...p }))
  selected.value = null
}

const near = (a: number, b: number) => Math.abs(a - b) < 1e-6
/** Whether the draft differs from `saved`: added, removed, moved, resized, layered or featured */
function differs(saved: CollagePhoto[]) {
  if (draft.value.length !== saved.length || draft.value.some((d) => d.file)) return true
  const byId = new Map(saved.map((p) => [p.id, p]))
  return draft.value.some((d) => {
    const s = byId.get(d.id)
    return !s || !near(s.x, d.x) || !near(s.y, d.y) || !near(s.w, d.w) || s.z !== d.z || s.featured !== d.featured
  })
}
const dirty = computed(() => canEdit.value && differs(photos.value))

// Photos loaded by the page replace the draft, unless it had changes over the previous saved version
watch(photos, (_, before) => { if (!canEdit.value || !differs(before ?? [])) resetDraft() }, { immediate: true })

/** What is shown: the draft while editing, otherwise what is saved */
const items = computed<DraftPhoto[]>(() => (canEdit.value ? draft.value : photos.value))

/**
 * Default spot of the n-th photo: a loose 4-column grid, slightly overlapped, with small fixed offsets.
 * Same rule the backend uses for a new upload (CollageService.upload).
 */
function defaultSlot(n: number) {
  return { x: 0.005 + (n % 4) * 0.24 + ((n * 7) % 3) / 200, y: Math.floor(n / 4) * 0.19 + ((n * 3) % 4) / 100, w: 0.25 }
}

/** Upload order: saved photos by id, then the new ones in the order they were added */
function uploadOrder(list: DraftPhoto[]) {
  return [...list].sort((a, b) => (a.id < 0) === (b.id < 0) ? Math.abs(a.id) - Math.abs(b.id) : a.id < 0 ? 1 : -1)
}

function autoArrange() {
  uploadOrder(draft.value).forEach((p, i) => Object.assign(p, defaultSlot(i), { z: i + 1 }))
  selected.value = null
}

// ---------------------------------------------------------------- layout

const heightOf = (p: CollagePhoto) => p.w * (p.height / p.width)
/** Board height in board widths: down to the lowest photo (plus room to drag further when editing) */
const boardHeight = computed(() => {
  const bottom = Math.max(0.2, ...items.value.map((p) => p.y + heightOf(p)))
  return bottom + (canEdit.value ? 0.12 : 0.01)
})
const featured = computed(() => items.value.find((p) => p.featured) ?? null)
/** Reading order (top to bottom, left to right) for the big view and the entrance animation */
const ordered = computed(() => [...items.value].sort((a, b) => a.y - b.y || a.x - b.x))
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

// ---------------------------------------------------------------- admin: move / resize (draft only)

let drag: { id: number; mode: 'move' | 'resize'; startX: number; startY: number; orig: CollagePhoto; moved: boolean } | null = null

function onDown(e: PointerEvent, p: CollagePhoto, mode: 'move' | 'resize') {
  if (!canEdit.value || e.button !== 0) return
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
  const p = draft.value.find((x) => x.id === drag!.id)
  if (!p) return
  if (drag.mode === 'move') {
    p.x = clamp(drag.orig.x + dx, 0, 1 - drag.orig.w)
    p.y = Math.max(0, drag.orig.y + dy)
  } else {
    p.w = clamp(drag.orig.w + dx, 0.06, 1 - drag.orig.x)
  }
}

function onUp(p: CollagePhoto) {
  if (!drag) return
  const wasMoved = drag.moved
  drag = null
  if (!wasMoved) selected.value = selected.value === p.id ? null : p.id
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))

function toFront(p: DraftPhoto) {
  p.z = Math.max(...draft.value.map((x) => x.z)) + 1
}

function toBack(p: DraftPhoto) {
  const min = Math.min(...draft.value.map((x) => x.z))
  if (min > 0) {
    p.z = min - 1
  } else {
    // Layer 0 is taken: push everyone else up one
    draft.value.filter((x) => x.id !== p.id).forEach((x) => { x.z += 1 })
    p.z = 0
  }
}

function toggleFeatured(p: DraftPhoto) {
  const on = !p.featured
  draft.value.forEach((x) => { x.featured = false })
  p.featured = on
}

function remove(p: DraftPhoto) {
  if (!confirm(t('collage.confirmRemove'))) return
  if (p.file) URL.revokeObjectURL(p.url)
  draft.value = draft.value.filter((x) => x.id !== p.id)
  selected.value = null
}

// ---------------------------------------------------------------- admin: add photos (kept in memory until saved)

const fileInput = ref<HTMLInputElement | null>(null)

async function onFiles(e: Event) {
  const files = [...((e.target as HTMLInputElement).files ?? [])]
  ;(e.target as HTMLInputElement).value = ''
  error.value = ''
  for (const file of files) {
    try {
      const { blob, width, height } = await toJpeg(file)
      draft.value.push({
        id: nextTempId--, url: URL.createObjectURL(blob), width, height, featured: false, file: blob,
        ...defaultSlot(draft.value.length), z: Math.max(0, ...draft.value.map((x) => x.z)) + 1,
      })
    } catch (err) {
      error.value = t('collage.uploadError', { name: file.name, error: (err as Error).message })
    }
  }
}

/** Shrinks the photo in the browser (max 1600 px, JPG) so uploads stay light. */
async function toJpeg(file: File): Promise<{ blob: Blob; width: number; height: number }> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('JPG'))), 'image/jpeg', 0.85))
  return { blob, width: canvas.width, height: canvas.height }
}

// ---------------------------------------------------------------- save / discard

/**
 * Publishes the draft: deletes the removed photos, uploads the new ones, saves every position/size/layer and
 * the featured photo. Each step updates the draft (new photos get their real id), so a retry after an error
 * does not upload twice.
 */
async function save() {
  error.value = ''
  try {
    const kept = new Set(draft.value.map((d) => d.id))
    const removed = photos.value.filter((p) => !kept.has(p.id))
    for (const p of removed) {
      busy.value = t('collage.saving')
      await collageApi.remove(p.id)
    }
    const added = draft.value.filter((d) => d.file)
    for (let i = 0; i < added.length; i++) {
      busy.value = t('collage.uploading', { n: i + 1, total: added.length })
      const created = await collageApi.upload(added[i].file!, 'foto.jpg')
      URL.revokeObjectURL(added[i].url)
      Object.assign(added[i], { id: created.id, url: created.url, file: undefined })
    }
    busy.value = t('collage.saving')
    const items: CollageItem[] = draft.value.map((d) => ({ id: d.id, x: d.x, y: d.y, w: d.w, z: d.z }))
    let result = items.length ? await collageApi.layout(items) : await collageApi.list()
    const wanted = draft.value.find((d) => d.featured)?.id ?? null
    const current = result.find((p) => p.featured)?.id ?? null
    if (wanted !== current) {
      result = wanted !== null ? await collageApi.featured(wanted, true) : await collageApi.featured(current!, false)
    }
    photos.value = result
    resetDraft()
  } catch (err) {
    error.value = (err as Error).message
    // What is saved now (some steps may have gone through); the draft keeps the pending changes
    const saved = await collageApi.list().catch(() => null)
    if (saved) photos.value = saved
  } finally {
    busy.value = null
  }
}

function discard() {
  if (confirm(t('collage.confirmDiscard'))) resetDraft()
}

// Leaving with unsaved changes asks first (other page of the app, or closing / reloading the tab)
onBeforeRouteLeave(() => !dirty.value || confirm(t('collage.confirmLeave')))
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) e.preventDefault()
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  draft.value.forEach((d) => { if (d.file) URL.revokeObjectURL(d.url) })
})
</script>

<template>
  <section v-if="items.length || canEdit" class="collage">
    <!-- Admin: draft bar (nothing is published until "Guardar cambios") -->
    <div v-if="canEdit" :class="['edit-bar', { dirty }]">
      <span class="state">
        <template v-if="busy">{{ busy }}</template>
        <template v-else-if="dirty">{{ t('collage.unsaved') }}</template>
        <template v-else>{{ t('collage.saved') }}</template>
      </span>
      <button type="button" class="btn secondary" :disabled="!!busy || items.length === 0" @click="autoArrange">{{ t('collage.autoArrange') }}</button>
      <button type="button" class="btn secondary" :disabled="!dirty || !!busy" @click="discard">{{ t('collage.discard') }}</button>
      <button type="button" class="btn" :disabled="!dirty || !!busy" @click="save">{{ t('collage.save') }}</button>
    </div>

    <button v-if="featured" type="button" class="featured" @click="open(featured)">
      <img :src="featured.url" :alt="t('collage.featured')">
    </button>

    <p v-if="canEdit" class="hint muted">{{ items.length ? t('collage.editHint') : t('collage.empty') }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div
      v-if="items.length" ref="board" :class="['board', { editing: canEdit }]"
      :style="{ aspectRatio: `1 / ${boardHeight}` }" @click.self="selected = null"
    >
      <figure
        v-for="p in items" :key="p.id" :class="['photo', { selected: selected === p.id }]" :style="style(p)"
        @pointerdown="onDown($event, p, 'move')" @pointermove="onMove" @pointerup="onUp(p)"
        @click="!canEdit && open(p)" @dblclick="canEdit && open(p)"
      >
        <img :src="p.url" :alt="t('collage.photo', { n: (orderOf.get(p.id) ?? 0) + 1 })" loading="lazy" draggable="false">
        <template v-if="canEdit">
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

    <template v-if="canEdit">
      <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFiles">
      <button type="button" class="fab" :title="t('collage.add')" :aria-label="t('collage.add')" :disabled="!!busy" @click="fileInput?.click()">
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
/* Draft bar: sticks to the top while scrolling the collage */
.edit-bar {
  position: sticky; top: 0; z-index: 950; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  background: #fff; border-radius: var(--radius); box-shadow: var(--shadow); padding: 10px 12px; margin-bottom: 14px;
  border-left: 4px solid #1e7d45;
}
.edit-bar.dirty { border-left-color: #e0a800; }
.edit-bar .state { flex: 1; min-width: 160px; font-size: 14px; font-weight: 500; color: var(--muted); }
.edit-bar.dirty .state { color: #8a6500; }
.edit-bar .btn { padding: 7px 14px; font-size: 14px; }
@media (prefers-reduced-motion: reduce) {
  .photo { animation: none; }
  .photo img { transition: none; }
}
</style>
