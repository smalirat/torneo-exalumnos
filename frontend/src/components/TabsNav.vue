<script setup lang="ts">
// Pill tabs bound with v-model to the active key
defineProps<{ tabs: { key: string; label: string }[] }>()
const active = defineModel<string>({ required: true })
</script>

<template>
  <div class="tabs" role="tablist">
    <button
      v-for="tab in tabs" :key="tab.key" type="button" role="tab" :aria-selected="active === tab.key"
      :class="['tab', { active: active === tab.key }]" @click="active = tab.key"
    >{{ tab.label }}</button>
  </div>
</template>

<style scoped>
.tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.tab {
  border: 1px solid var(--navy); background: #fff; color: var(--navy); border-radius: 999px;
  padding: 7px 16px; font: inherit; font-weight: 500; cursor: pointer;
}
.tab.active { background: var(--navy); color: #fff; }
</style>
