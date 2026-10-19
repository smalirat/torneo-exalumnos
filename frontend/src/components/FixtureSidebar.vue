<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { tournamentApi } from '../api/endpoints'
import type { Category, Tournament } from '../api/types'
import { zoneOptions } from '../groups'
import { t } from '../i18n'

// Left list of the fixture: every edition of the year with its categories (and zones, when a category has
// several), always open — there are never many. Picking one emits the edition and the zone.
const props = defineProps<{ editions: Tournament[]; tournamentId: number; zoneId: number | null }>()
const emit = defineEmits<{ select: [tournamentId: number, categoryId: number, zoneId: number] }>()

const categories = ref<Record<number, Category[]>>({})
watch(() => props.editions.map((e) => e.id).join(','), async () => {
  const entries = await Promise.all(props.editions.map(async (e) => [e.id, await tournamentApi.categories(e.id)] as const))
  categories.value = Object.fromEntries(entries)
  // Phones show the list as a horizontal strip: bring the chosen one into view
  await nextTick()
  nav.value?.querySelector('.item.active')?.scrollIntoView({ inline: 'center', block: 'nearest' })
}, { immediate: true })
const nav = ref<HTMLElement | null>(null)

const isCurrent = (e: Tournament, zoneId: number) => e.id === props.tournamentId && zoneId === props.zoneId
</script>

<template>
  <nav ref="nav" class="sidebar" :aria-label="t('fixture.categories')">
    <section v-for="e in editions" :key="e.id" class="edition">
      <h3>{{ e.name }}</h3>
      <ul>
        <li v-for="o in zoneOptions(categories[e.id] ?? [])" :key="o.id">
          <button
            type="button" :class="['item', { active: isCurrent(e, o.id) }]" :aria-current="isCurrent(e, o.id) ? 'true' : undefined"
            @click="emit('select', e.id, o.categoryId, o.id)"
          >{{ o.label }}</button>
        </li>
      </ul>
    </section>
  </nav>
</template>

<style scoped>
.sidebar { display: flex; flex-direction: column; gap: 18px; }
h3 {
  font-family: var(--condensed); font-size: 14px; text-transform: uppercase; letter-spacing: 0.04em; color: var(--navy);
  margin-bottom: 6px;
}
ul { list-style: none; margin: 0; padding: 0 0 0 8px; border-left: 1px solid var(--border); display: flex; flex-direction: column; gap: 2px; }
.item {
  width: 100%; text-align: left; background: none; border: 0; border-left: 2px solid transparent; margin-left: -9px;
  font: inherit; font-size: 14px; color: var(--text); padding: 6px 10px; border-radius: 0 8px 8px 0; cursor: pointer;
}
.item:hover { color: var(--navy); background: #fff; }
.item.active { color: var(--navy); font-weight: 700; background: #fff; border-left-color: var(--navy); }
/* Phones: one horizontal strip of choices above the content */
@media (max-width: 760px) {
  .sidebar { flex-direction: row; gap: 14px; overflow-x: auto; scrollbar-width: none; padding-bottom: 4px; }
  .edition { flex-shrink: 0; }
  ul { flex-direction: row; border-left: 0; padding: 0; gap: 6px; }
  .item {
    margin-left: 0; border: 1px solid var(--border); border-radius: 999px; white-space: nowrap; background: #fff; padding: 6px 12px;
  }
  .item.active { border-color: var(--navy); background: var(--navy); color: #fff; }
}
</style>
