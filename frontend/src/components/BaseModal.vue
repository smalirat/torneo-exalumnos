<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import AppIcon from './AppIcon.vue'
import { t } from '../i18n'

// Generic modal: title + close button, body in the default slot, actions in #footer
withDefaults(defineProps<{ title: string; width?: string }>(), { width: '520px' })
const emit = defineEmits<{ close: [] }>()

const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') emit('close') }
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <div class="modal card" role="dialog" aria-modal="true" :aria-label="title" :style="{ width: `min(${width}, 100%)` }">
      <header class="head">
        <h2>{{ title }}</h2>
        <button type="button" class="icon-btn" :title="t('common.close')" @click="emit('close')">
          <AppIcon name="close" />
        </button>
      </header>
      <slot />
      <footer v-if="$slots.footer" class="actions"><slot name="footer" /></footer>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0; background: rgba(20, 39, 90, 0.45);
  display: flex; align-items: flex-start; justify-content: center; padding: 6vh 16px; z-index: 10; overflow-y: auto;
}
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.head h2 { font-size: 20px; color: var(--navy); }
.actions { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 18px; }
</style>
