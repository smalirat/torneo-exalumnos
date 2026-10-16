<script setup lang="ts">
import { computed, ref } from 'vue'
import { t } from '../i18n'

// Titled list that shows the first `limit` items and a "see all" toggle. Slot #item renders one entry.
const props = withDefaults(defineProps<{ title: string; items: unknown[]; limit?: number; tone?: 'default' | 'warning' }>(), {
  limit: 8,
  tone: 'default',
})
const expanded = ref(false)
const visible = computed(() => (expanded.value ? props.items : props.items.slice(0, props.limit)))
</script>

<template>
  <section v-if="items.length" :class="['list', tone]">
    <h3>{{ title }}</h3>
    <slot name="help" />
    <ul>
      <li v-for="(item, i) in visible" :key="i"><slot name="item" :item="item">{{ item }}</slot></li>
    </ul>
    <button v-if="items.length > limit" type="button" class="toggle" @click="expanded = !expanded">
      {{ expanded ? t('excel.showLess') : `${t('excel.showAll')} (${items.length})` }}
    </button>
  </section>
</template>

<style scoped>
.list { margin-top: 18px; }
h3 { font-size: 15px; margin-bottom: 6px; }
.warning h3 { color: #9a6200; }
ul { margin: 0; padding-left: 18px; font-size: 14px; line-height: 1.6; }
.warning li { color: #6b4b00; }
.toggle { background: none; border: 0; color: var(--navy); cursor: pointer; padding: 4px 0; font: inherit; font-weight: 500; }
</style>
