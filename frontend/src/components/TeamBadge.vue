<script setup lang="ts">
import { computed } from 'vue'

// Circular crest with the team's initials; the color is derived from the name so it is stable
const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 28 })

const initials = computed(() => props.name.replace(/[^\p{L}\p{N}]/gu, '').slice(0, 3).toUpperCase())

const color = computed(() => {
  let hash = 0
  for (const ch of props.name) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return `hsl(${Math.abs(hash) % 360} 60% 45%)`
})
</script>

<template>
  <span
    class="badge"
    :style="{ background: color, width: `${size}px`, height: `${size}px`, fontSize: `${size * 0.32}px` }"
  >{{ initials }}</span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}
</style>
