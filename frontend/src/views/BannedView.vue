<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import ConfirmButton from '../components/ConfirmButton.vue'
import { bannedApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { BannedPerson } from '../api/types'
import { t } from '../i18n'

// Admin: the Asociación's list of players not allowed in the tournament (by DNI). A DNI on it cannot be added to a
// roster; someone already in one shows where, so the organizer can act.
const list = ref<BannedPerson[] | null>(null)
const dni = ref('')
const fullName = ref('')
const reason = ref('')
const search = ref('')
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const busy = ref(false)

const load = async () => { list.value = await bannedApi.list() }
onMounted(load)

const shown = computed(() => {
  const q = search.value.trim().toLocaleLowerCase('es').replace(/[.\s]/g, '')
  return (list.value ?? []).filter((b) => !q || b.dni.includes(q) || (b.fullName ?? '').toLocaleLowerCase('es').replace(/\s/g, '').includes(q))
})

async function add() {
  busy.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    await bannedApi.add({ dni: dni.value.trim(), fullName: fullName.value.trim() || null, reason: reason.value.trim() || null })
    dni.value = ''
    fullName.value = ''
    reason.value = ''
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    if (e instanceof ApiError) fieldErrors.value = e.fieldErrors
  } finally {
    busy.value = false
  }
}

async function remove(b: BannedPerson) {
  error.value = ''
  try {
    await bannedApi.remove(b.id)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

const date = (d: string) => new Date(d).toLocaleDateString('es-AR')
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('banned.title') }}</h1>
    <section class="card">
      <p class="muted small">{{ t('banned.help') }}</p>
      <form class="add" @submit.prevent="add">
        <input v-model="dni" class="input dni" inputmode="numeric" maxlength="12" required :placeholder="t('banned.dni')" :aria-label="t('banned.dni')" />
        <input v-model="fullName" class="input" maxlength="255" :placeholder="t('banned.name')" :aria-label="t('banned.name')" />
        <input v-model="reason" class="input" maxlength="500" :placeholder="t('banned.reason')" :aria-label="t('banned.reason')" />
        <button class="btn" :disabled="busy">{{ t('banned.add') }}</button>
      </form>
      <p v-if="fieldErrors.dni" class="error">{{ fieldErrors.dni }}</p>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card">
      <input v-model="search" class="input search" type="search" :placeholder="t('banned.search')" :aria-label="t('banned.search')" />
      <p v-if="list === null" class="muted">{{ t('common.loading') }}</p>
      <p v-else-if="shown.length === 0" class="muted">{{ t('banned.empty') }}</p>
      <ul v-else class="list">
        <li v-for="b in shown" :key="b.id">
          <div class="who">
            <strong>{{ b.fullName ?? '—' }}</strong>
            <span class="muted">{{ t('banned.dni') }} {{ b.dni }} · {{ t('banned.since', { date: date(b.createdAt) }) }}</span>
            <span v-if="b.reason" class="reason">{{ b.reason }}</span>
            <span v-if="b.currentTeam" class="in-roster">{{ t('banned.inRoster', { team: b.currentTeam }) }}</span>
          </div>
          <ConfirmButton icon :label="t('banned.remove')" :message="t('banned.removeConfirm', { dni: b.dni })" @confirm="remove(b)" />
        </li>
      </ul>
    </section>
  </GlobalLayout>
</template>

<style scoped>
.card { margin-bottom: 16px; }
.small { font-size: 13px; margin: 0 0 10px; }
.add { display: flex; gap: 8px; flex-wrap: wrap; }
.add .input { flex: 1; min-width: 160px; }
.add .dni { flex: 0 0 140px; min-width: 120px; }
.search { margin-bottom: 10px; }
.list { list-style: none; margin: 0; padding: 0; }
.list li { display: flex; align-items: center; gap: 10px; padding: 8px 2px; border-bottom: 1px solid var(--border); }
.who { flex: 1; display: flex; flex-direction: column; gap: 2px; font-size: 14px; }
.reason { font-size: 13px; }
.in-roster { font-size: 12px; font-weight: 700; color: var(--danger); }
</style>
