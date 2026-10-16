<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import { tournamentApi } from '../api/endpoints'
import type { Category } from '../api/types'
import { isAdmin } from '../auth'
import { t } from '../i18n'

const route = useRoute()
const tournamentId = computed(() => Number(route.params.id))
const categories = ref<Category[]>([])
const newName = ref('')
const adding = ref(false)
const error = ref('')

watch(tournamentId, async (id) => {
  categories.value = await tournamentApi.categories(id)
}, { immediate: true })

async function addCategory() {
  error.value = ''
  try {
    categories.value.push(await tournamentApi.createCategory(tournamentId.value, newName.value))
    newName.value = ''
    adding.value = false
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

const formatDate = (d: string | null) => (d ? new Date(`${d}T00:00`).toLocaleDateString('es-AR') : '—')
</script>

<template>
  <TournamentLayout v-slot="{ tournament }">
    <section class="banner">
      <span class="avatar"><AppIcon name="trophy" :size="30" /></span>
    </section>
    <h1 class="name">{{ tournament?.name }}</h1>

    <div v-if="tournament" class="facts">
      <div class="card fact">
        <span class="dot" />
        <div>
          <p class="label">{{ t('tournaments.status') }}</p>
          <p class="value">{{ t(`tournaments.statuses.${tournament.status}`) }}</p>
        </div>
      </div>
      <div class="card fact">
        <div>
          <p class="label">{{ t('tournaments.registration') }}</p>
          <p class="value plain">{{ formatDate(tournament.registrationStart) }} – {{ formatDate(tournament.registrationEnd) }}</p>
        </div>
      </div>
    </div>

    <h2 class="section">{{ t('categories.title') }}</h2>
    <div class="card">
      <div v-if="isAdmin" class="toolbar">
        <form v-if="adding" class="add" @submit.prevent="addCategory">
          <input v-model="newName" class="input" :placeholder="t('categories.namePlaceholder')" required autofocus />
          <button class="btn">{{ t('tournaments.create') }}</button>
          <button type="button" class="btn secondary" @click="adding = false">{{ t('tournaments.cancel') }}</button>
        </form>
        <button v-else class="btn" @click="adding = true"><AppIcon name="plus" :size="18" /> {{ t('categories.new') }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="categories.length === 0" class="muted">{{ t('categories.empty') }}</p>
      <RouterLink
        v-for="c in categories" :key="c.id" class="cat"
        :to="{ path: `/campeonatos/${tournamentId}/clasificacion`, query: { categoria: c.id } }"
      >
        <span class="cat-icon"><AppIcon name="trophy" /></span>
        <span class="cat-name">{{ c.name }}</span>
        <!-- Zones only shown when there is more than one -->
        <span v-if="c.zoneCount > 1" class="muted">{{ t('categories.zones', { n: c.zoneCount }) }}</span>
        <AppIcon name="chevron" />
      </RouterLink>
    </div>
  </TournamentLayout>
</template>

<style scoped>
.banner {
  position: relative; height: 220px; border-radius: var(--radius);
  background: linear-gradient(135deg, var(--navy) 0%, #3b5a9e 60%, #7e93c4 100%);
}
.avatar {
  position: absolute; left: 30px; bottom: -50px; width: 110px; height: 110px; border-radius: 50%;
  background: var(--bg); color: var(--navy); border: 4px solid #fff;
  display: inline-flex; align-items: center; justify-content: center; box-shadow: var(--shadow);
}
.name { font-size: 32px; color: var(--navy); margin: 14px 0 20px 160px; }
.facts { display: flex; gap: 14px; margin: 34px 0 30px; }
.fact { display: flex; align-items: center; gap: 12px; padding: 14px 20px; min-width: 180px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #3d7be0; }
.label { font-size: 11px; text-transform: uppercase; color: var(--muted); margin: 0; }
.value { margin: 2px 0 0; color: #3d7be0; font-weight: 500; }
.value.plain { color: var(--text); }
.section { font-size: 22px; margin-bottom: 14px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.add { display: flex; gap: 8px; width: 100%; }
.cat { display: flex; align-items: center; gap: 14px; padding: 10px 6px; border-bottom: 1px solid var(--border); }
.cat:last-child { border-bottom: 0; }
.cat:hover { background: var(--row-alt); }
.cat-name { flex: 1; font-size: 16px; }
.cat-icon {
  width: 44px; height: 44px; border-radius: 50%; background: var(--bg); color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center;
}
</style>
