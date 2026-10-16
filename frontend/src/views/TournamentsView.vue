<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import { tournamentApi } from '../api/endpoints'
import type { Tournament } from '../api/types'
import { isAdmin, isLoggedIn } from '../auth'
import { t } from '../i18n'

const tournaments = ref<Tournament[] | null>(null)
const showForm = ref(false)
const form = ref({ name: '', location: '', registrationStart: '', registrationEnd: '' })
const error = ref('')

onMounted(async () => {
  tournaments.value = await tournamentApi.list()
})

async function create() {
  error.value = ''
  try {
    const created = await tournamentApi.create({ ...form.value, location: form.value.location || undefined })
    tournaments.value = [...(tournaments.value ?? []), created]
    showForm.value = false
    form.value = { name: '', location: '', registrationStart: '', registrationEnd: '' }
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

const formatDate = (d: string | null) => (d ? new Date(`${d}T00:00`).toLocaleDateString('es-AR') : '—')
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('nav.myTournaments') }}</h1>

    <p v-if="tournaments === null" class="muted">{{ t('common.loading') }}</p>

    <div v-else-if="tournaments.length === 0" class="empty">
      <span class="empty-icon"><AppIcon name="trophy" :size="34" /></span>
      <h2>{{ t('tournaments.emptyTitle') }}</h2>
      <p class="muted">{{ t('tournaments.emptyText') }}</p>
      <RouterLink v-if="!isLoggedIn" to="/login" class="btn violet">
        <AppIcon name="login" :size="18" /> {{ t('auth.doLogin') }}
      </RouterLink>
    </div>

    <div v-else class="grid">
      <RouterLink v-for="tour in tournaments" :key="tour.id" :to="`/campeonatos/${tour.id}`" class="card tour">
        <span class="tour-icon"><AppIcon name="trophy" /></span>
        <div>
          <h2>{{ tour.name }}</h2>
          <p class="muted">{{ t(`tournaments.statuses.${tour.status}`) }}<template v-if="tour.location"> · {{ tour.location }}</template></p>
          <p class="muted small">
            {{ t('tournaments.registration') }}: {{ formatDate(tour.registrationStart) }} – {{ formatDate(tour.registrationEnd) }}
          </p>
        </div>
      </RouterLink>
    </div>

    <form v-if="showForm" class="card new" @submit.prevent="create">
      <h2>{{ t('tournaments.new') }}</h2>
      <div class="field">
        <label for="t-name">{{ t('tournaments.name') }}</label>
        <input id="t-name" v-model="form.name" class="input" required />
      </div>
      <div class="field">
        <label for="t-location">{{ t('tournaments.location') }}</label>
        <input id="t-location" v-model="form.location" class="input" />
      </div>
      <div class="row">
        <div class="field">
          <label for="t-start">{{ t('tournaments.registrationStart') }}</label>
          <input id="t-start" v-model="form.registrationStart" class="input" type="date" required />
        </div>
        <div class="field">
          <label for="t-end">{{ t('tournaments.registrationEnd') }}</label>
          <input id="t-end" v-model="form.registrationEnd" class="input" type="date" required />
        </div>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <button type="button" class="btn secondary" @click="showForm = false">{{ t('tournaments.cancel') }}</button>
        <button class="btn">{{ t('tournaments.create') }}</button>
      </div>
    </form>

    <button v-if="isAdmin && !showForm" class="fab" :title="t('tournaments.new')" @click="showForm = true">
      <AppIcon name="plus" :size="26" />
    </button>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
.empty { display: flex; flex-direction: column; align-items: center; text-align: center; margin-top: 18vh; gap: 6px; }
.empty h2 { font-size: 18px; font-weight: 500; }
.empty-icon {
  width: 86px; height: 86px; border-radius: 50%; background: #dde2ec; color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center; margin-bottom: 10px;
}
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.tour { display: flex; gap: 14px; align-items: flex-start; }
.tour:hover { box-shadow: 0 2px 10px rgba(20, 39, 90, 0.15); }
.tour h2 { font-size: 18px; margin-bottom: 4px; }
.tour p { margin: 2px 0; }
.small { font-size: 13px; }
.tour-icon {
  width: 42px; height: 42px; border-radius: 50%; background: var(--bg); color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.new { max-width: 520px; margin-top: 24px; }
.new h2 { font-size: 18px; margin-bottom: 16px; }
.row { display: flex; gap: 12px; }
.row .field { flex: 1; }
.actions { display: flex; gap: 10px; justify-content: flex-end; }
.fab {
  position: fixed; right: 32px; bottom: 32px; width: 56px; height: 56px; border-radius: 14px;
  background: var(--navy); color: #fff; border: 0; cursor: pointer; box-shadow: 0 4px 12px rgba(20, 39, 90, 0.35);
  display: inline-flex; align-items: center; justify-content: center;
}
</style>
