<script setup lang="ts">
import { nextTick, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { t } from '../i18n'

// Text that becomes an input when clicking the pencil. The parent persists it in `save`
// (it may throw: the error is shown and the field stays in edit mode).
const props = withDefaults(
  defineProps<{ value: string; save: (value: string) => Promise<unknown>; editable?: boolean }>(),
  { editable: true }, // absent boolean props are cast to false by Vue
)

const editing = ref(false)
const draft = ref('')
const error = ref('')
const input = ref<HTMLInputElement | null>(null)

async function start() {
  draft.value = props.value
  error.value = ''
  editing.value = true
  await nextTick()
  input.value?.select()
}

async function commit() {
  const value = draft.value.trim()
  if (!value || value === props.value) { editing.value = false; return }
  try {
    await props.save(value)
    editing.value = false
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}
</script>

<template>
  <span v-if="!editing" class="view">
    <slot>{{ value }}</slot>
    <button v-if="editable" type="button" class="icon-btn" :title="t('common.rename')" @click.stop.prevent="start">
      <AppIcon name="edit" :size="16" />
    </button>
  </span>
  <form v-else class="edit" @submit.prevent="commit" @click.stop>
    <input ref="input" v-model="draft" class="input" required @keydown.esc="editing = false" />
    <button class="btn">{{ t('standings.save') }}</button>
    <button type="button" class="btn secondary" @click="editing = false">{{ t('standings.cancel') }}</button>
    <span v-if="error" class="error">{{ error }}</span>
  </form>
</template>

<style scoped>
.view { display: inline-flex; align-items: center; gap: 6px; }
.view .icon-btn { opacity: 0.55; }
.view:hover .icon-btn { opacity: 1; }
.edit { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.edit .input { width: auto; min-width: 200px; padding: 6px 10px; }
.edit .btn { padding: 5px 12px; }
</style>
