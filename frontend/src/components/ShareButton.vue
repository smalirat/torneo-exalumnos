<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { t } from '../i18n'

// Shares a link: native share sheet on phones, otherwise copies it to the clipboard
const props = withDefaults(defineProps<{ url: string; title: string; tone?: 'navy' | 'light' }>(), { tone: 'navy' })
const copied = ref(false)

async function share() {
  if (navigator.share) {
    try {
      await navigator.share({ title: props.title, url: props.url })
      return
    } catch {
      // cancelled or not allowed: fall back to copying
    }
  }
  try {
    await navigator.clipboard.writeText(props.url)
  } catch {
    window.prompt(t('share.copyManually'), props.url)
    return
  }
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}
</script>

<template>
  <button type="button" :class="['share', tone]" @click="share">
    <AppIcon :name="copied ? 'check' : 'share'" :size="18" />
    {{ copied ? t('share.copied') : t('share.share') }}
  </button>
</template>

<style scoped>
.share {
  display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 7px 14px;
  font: inherit; font-size: 14px; font-weight: 500; cursor: pointer; white-space: nowrap; background: transparent;
}
.share.navy { border: 1px solid var(--border); color: var(--navy); background: #fff; }
.share.light { border: 1px solid rgba(255, 255, 255, 0.7); color: #fff; }
</style>
