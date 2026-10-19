<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import type { Category } from '../api/types'
import { zoneOptions } from '../groups'
import { t } from '../i18n'

// Navy strip on top of the standings / fixture card: ‹ Categoría A · Zona 1 › steps through every zone of every
// category, in order (wraps around at the ends); changing it changes everything shown for that group.
// The selection is kept in the URL (?categoria=&zona=) so the shared link opens the same group.
const props = defineProps<{ categories: Category[] }>()
const zoneId = defineModel<number | null>({ required: true })

const route = useRoute()
const router = useRouter()

/** Every zone in order: categories as listed, zones within each category */
const groups = computed(() => zoneOptions(props.categories).map((o) => ({ zoneId: o.id, categoryId: o.categoryId, title: o.label })))
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
    </div>
    <button v-if="groups.length > 1" type="button" class="nav-btn" :title="t('fixture.next')" @click="step(1)">
      <AppIcon name="chevron" />
    </button>
  </nav>
</template>

<style scoped>
.group-nav {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  background: var(--navy-dark); color: #fff; padding: 8px 10px; border-radius: var(--radius) var(--radius) 0 0;
}
.title { display: flex; align-items: baseline; justify-content: center; gap: 8px; min-width: 0; flex: 1; text-align: center; }
.title strong { font-family: var(--condensed); text-transform: uppercase; font-size: 19px; letter-spacing: 0.02em; line-height: 1.2; }
.title span { font-size: 13px; opacity: 0.8; font-weight: 500; }
.nav-btn {
  width: 34px; height: 34px; flex-shrink: 0; border-radius: 50%; border: 1px solid rgba(255, 255, 255, 0.5);
  background: transparent; color: #fff; display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
}
.nav-btn:hover { background: rgba(255, 255, 255, 0.15); }
.flip { transform: rotate(180deg); }
</style>
