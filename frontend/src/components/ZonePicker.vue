<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Category } from '../api/types'
import { t } from '../i18n'

// Category + zone selectors (the zone one only when the category has more than one zone).
// The selection is kept in the URL (?categoria=&zona=) so pages are shareable and remember it.
const props = defineProps<{ categories: Category[] }>()
const zoneId = defineModel<number | null>({ required: true })

const route = useRoute()
const router = useRouter()
const categoryId = ref<number | null>(null)
const category = computed(() => props.categories.find((c) => c.id === categoryId.value) ?? null)

watch(() => props.categories, (cats) => {
  const cat = cats.find((c) => c.id === Number(route.query.categoria)) ?? cats[0]
  categoryId.value = cat?.id ?? null
  zoneId.value = cat?.zones.find((z) => z.id === Number(route.query.zona))?.id ?? cat?.zones[0]?.id ?? null
}, { immediate: true })

function onCategoryChange() {
  zoneId.value = category.value?.zones[0]?.id ?? null
}

watch(zoneId, (z) => {
  router.replace({ query: { ...route.query, categoria: categoryId.value ?? undefined, zona: z ?? undefined } })
})
</script>

<template>
  <div class="picker">
    <select v-if="categories.length > 1" v-model="categoryId" class="select" :aria-label="t('standings.category')" @change="onCategoryChange">
      <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
    </select>
    <select v-if="category && category.zoneCount > 1" v-model="zoneId" class="select" :aria-label="t('standings.zone')">
      <option v-for="z in category.zones" :key="z.id" :value="z.id">{{ z.name }}</option>
    </select>
  </div>
</template>

<style scoped>
.picker { display: flex; gap: 10px; flex-wrap: wrap; }
.select { width: auto; min-width: 180px; border-radius: 999px; }
</style>
