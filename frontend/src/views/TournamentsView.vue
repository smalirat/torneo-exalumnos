<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import TournamentForm from '../components/TournamentForm.vue'
import { tournamentApi, type TournamentInput } from '../api/endpoints'
import type { Tournament } from '../api/types'
import { isAdmin, isLoggedIn } from '../auth'
import { t } from '../i18n'

const tournaments = ref<Tournament[] | null>(null)
const showForm = ref(false)
const error = ref('')

onMounted(async () => {
  tournaments.value = await tournamentApi.list()
})

async function create(input: TournamentInput) {
  error.value = ''
  try {
    const created = await tournamentApi.create(input)
    tournaments.value = [...(tournaments.value ?? []), created]
    showForm.value = false
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

    <div v-if="showForm" class="card new">
      <h2>{{ t('tournaments.new') }}</h2>
      <TournamentForm :submit-label="t('tournaments.create')" @submit="create" @cancel="showForm = false">
        <template #error><p v-if="error" class="error">{{ error }}</p></template>
      </TournamentForm>
    </div>

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
.fab {
  position: fixed; right: 32px; bottom: 32px; width: 56px; height: 56px; border-radius: 14px;
  background: var(--navy); color: #fff; border: 0; cursor: pointer; box-shadow: 0 4px 12px rgba(20, 39, 90, 0.35);
  display: inline-flex; align-items: center; justify-content: center;
}
</style>
