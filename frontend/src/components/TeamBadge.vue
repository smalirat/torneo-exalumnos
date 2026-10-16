<script setup lang="ts">
// Team crest: the uploaded image, or nothing at all when the team has none
withDefaults(defineProps<{ team: { name: string; crest?: string | null }; size?: number }>(), { size: 28 })
</script>

<template>
  <img
    v-if="team.crest" class="crest" :src="team.crest" :alt="team.name" loading="lazy"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
</template>

<style scoped>
.crest { object-fit: contain; flex-shrink: 0; display: inline-block; vertical-align: middle; }
</style>
