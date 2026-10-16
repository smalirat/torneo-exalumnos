<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'
import TeamBadge from './TeamBadge.vue'
import { teamApi } from '../api/endpoints'
import { t } from '../i18n'

// Crest of a team in management screens (admin, or its captain): click to upload / change it, × to remove it.
// Without a crest it shows a dashed "+" slot, only here; public screens show nothing.
const props = withDefaults(defineProps<{ team: { id: number; name: string; crest?: string | null }; size?: number }>(), { size: 54 })
const emit = defineEmits<{ change: [crest: string | null] }>()

const input = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const error = ref('')

async function onFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = ''
  if (!file) return
  await run(async () => emit('change', (await teamApi.setCrest(props.team.id, await toPng(file))).crest ?? null))
}

const remove = () => run(async () => {
  await teamApi.removeCrest(props.team.id)
  emit('change', null)
})

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (err) {
    error.value = (err as Error).message
  } finally {
    busy.value = false
  }
}

/** Shrinks the image to 256 px (keeping transparency) so crests stay light. */
async function toPng(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 256 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('PNG'))), 'image/png'))
}
</script>

<template>
  <span class="picker" :style="{ width: `${size}px`, height: `${size}px` }">
    <button
      type="button" :class="['slot', { empty: !team.crest }]" :disabled="busy"
      :title="team.crest ? t('crest.change') : t('crest.add')" :aria-label="team.crest ? t('crest.change') : t('crest.add')"
      @click="input?.click()"
    >
      <TeamBadge v-if="team.crest" :team="team" :size="size" />
      <AppIcon v-else name="plus" :size="Math.round(size * 0.4)" />
    </button>
    <button v-if="team.crest" type="button" class="remove" :title="t('crest.remove')" :aria-label="t('crest.remove')" @click="remove">
      <AppIcon name="close" :size="12" />
    </button>
    <input ref="input" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onFile">
    <span v-if="error" class="error tip">{{ error }}</span>
  </span>
</template>

<style scoped>
.picker { position: relative; display: inline-flex; flex-shrink: 0; }
.slot {
  width: 100%; height: 100%; padding: 0; border: 0; background: none; cursor: pointer; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center; color: var(--muted);
}
.slot.empty { border: 2px dashed #c4cbd8; }
.slot.empty:hover { border-color: var(--navy); color: var(--navy); }
.slot:disabled { opacity: 0.5; cursor: wait; }
.remove {
  position: absolute; top: -4px; right: -4px; width: 20px; height: 20px; border-radius: 50%; padding: 0;
  border: 1px solid var(--border); background: #fff; color: var(--muted); cursor: pointer;
  display: none; align-items: center; justify-content: center;
}
.picker:hover .remove, .remove:focus-visible { display: inline-flex; }
.tip { position: absolute; top: 100%; left: 0; width: 220px; font-size: 12px; margin-top: 4px; z-index: 5; }
</style>
