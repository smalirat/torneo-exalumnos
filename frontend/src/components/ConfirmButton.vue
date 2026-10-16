<script setup lang="ts">
import { ref } from 'vue'
import BaseModal from './BaseModal.vue'
import AppIcon from './AppIcon.vue'
import { t } from '../i18n'

// Delete-style button that asks for confirmation in an in-app dialog before emitting `confirm`.
// `icon` renders a compact icon button (for lists); otherwise a text button.
const props = defineProps<{ label: string; message: string; icon?: boolean }>()
const emit = defineEmits<{ confirm: [] }>()
const open = ref(false)

function confirm() {
  open.value = false
  emit('confirm')
}
</script>

<template>
  <button v-if="props.icon" type="button" class="icon-btn danger" :title="label" @click.stop.prevent="open = true">
    <AppIcon name="delete" :size="18" />
  </button>
  <button v-else type="button" class="btn danger-outline" @click.stop.prevent="open = true">
    <AppIcon name="delete" :size="18" /> {{ label }}
  </button>

  <Teleport to="body">
    <BaseModal v-if="open" :title="label" width="420px" @close="open = false">
      <p class="message">{{ message }}</p>
      <template #footer>
        <button type="button" class="btn secondary" @click="open = false">{{ t('standings.cancel') }}</button>
        <button type="button" class="btn danger-fill" @click="confirm">{{ t('common.delete') }}</button>
      </template>
    </BaseModal>
  </Teleport>
</template>

<style scoped>
.message { margin: 0; line-height: 1.5; }
</style>
