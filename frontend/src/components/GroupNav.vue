<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import type { Category } from '../api/types'
import { t } from '../i18n'

// ‹ Categoría A · Zona 1 › : steps through every zone of every category, in order (wraps around at the ends).
// The selection is kept in the URL (?categoria=&zona=) so the shared link opens the same group.
const props = defineProps<{ categories: Category[] }>()
const zoneId = defineModel<number | null>({ required: true })

const route = useRoute()
const router = useRouter()

/** Every zone in order: categories as listed, zones within each category */
const groups = computed(() => props.categories.flatMap((c) => c.zones.map((z) => ({
  zoneId: z.id,
  categoryId: c.id,
  title: t('league.category', { name: c.name }),
  zone: c.zoneCount > 1 ? z.name : null,
}))))
const index = computed(() => groups.value.findIndex((g) => g.zoneId === zoneId.value))
const current = computed(() => groups.value[index.value] ?? null)

watch(() => props.categories, () => {
  const fromUrl = groups.value.find((g) => g.zoneId === Number(route.query.zona))
    ?? groups.value.find((g) => g.categoryId === Number(route.query.categoria))
  zoneId.value = (fromUrl ?? groups.value[0])?.zoneId ?? null
}, { immediate: true })

function step(delta: number) {
  const n = groups.value.length
  if (n === 0) return
  zoneId.value = groups.value[((index.value < 0 ? 0 : index.value) + delta + n) % n].zoneId
}

watch(zoneId, () => {
  router.replace({ query: { ...route.query, categoria: current.value?.categoryId ?? undefined, zona: zoneId.value ?? undefined } })
})
</script>

<template>
  <nav v-if="current" class="group-nav" :aria-label="t('standings.category')">
    <button v-if="groups.length > 1" type="button" class="nav-btn" :title="t('fixture.previous')" @click="step(-1)">
      <AppIcon name="chevron" class="flip" />
    </button>
    <div class="title">
      <strong>{{ current.title }}</strong>
      <span v-if="current.zone">{{ current.zone }}</span>
    </div>
    <button v-if="groups.length > 1" type="button" class="nav-btn" :title="t('fixture.next')" @click="step(1)">
      <AppIcon name="chevron" />
    </button>
  </nav>
</template>

<style scoped>
.group-nav { display: flex; align-items: center; justify-content: center; gap: 18px; margin: 0 0 18px; }
.title { display: flex; flex-direction: column; align-items: center; min-width: 0; flex: 0 1 280px; text-align: center; }
.title strong { font-family: var(--condensed); text-transform: uppercase; font-size: 22px; color: var(--navy); line-height: 1.1; }
.title span { font-size: 13px; color: var(--muted); font-weight: 500; }
.nav-btn {
  width: 38px; height: 38px; flex-shrink: 0; border-radius: 50%; border: 1px solid var(--navy); background: #fff;
  color: var(--navy); display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.nav-btn:hover { background: var(--navy); color: #fff; }
.flip { transform: rotate(180deg); }
</style>
