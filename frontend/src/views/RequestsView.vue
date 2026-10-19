<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import BaseModal from '../components/BaseModal.vue'
import { requestApi } from '../api/endpoints'
import type { RequestItem } from '../api/types'
import { refreshPendingRequests } from '../requests'
import { t } from '../i18n'

// Captains' changes waiting for the admin, grouped by team. Approving applies the change; rejecting drops it
// (with an optional reason the captain sees). "Aprobar todo" approves everything of a team except deleting it.
const items = ref<RequestItem[] | null>(null)
const error = ref('')
const busy = ref(false)

async function load() {
  ;[items.value] = await Promise.all([requestApi.list(), refreshPendingRequests()])
}
onMounted(load)

const groups = computed(() => {
  const byTeam = new Map<number, { teamId: number; teamName: string; captain: string | null; items: RequestItem[] }>()
  for (const r of items.value ?? []) {
    const g = byTeam.get(r.teamId) ?? { teamId: r.teamId, teamName: r.teamName, captain: r.captain, items: [] }
    g.items.push(r)
    byTeam.set(r.teamId, g)
  }
  return [...byTeam.values()]
})
/** "Aprobar todo" makes sense with two or more requests it covers (deleting the team is never included) */
const bulkCount = (g: { items: RequestItem[] }) => g.items.filter((r) => r.type !== 'DELETE_TEAM').length

const when = (at: string | null) => (at
  ? new Date(at).toLocaleString('es-AR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  : '')

async function run(action: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = (e as Error).message
    await load()
  } finally {
    busy.value = false
  }
}

const approve = (r: RequestItem) => run(() => requestApi.approve(r.type, r.id))
const approveAll = (teamId: number) => run(() => requestApi.approveAll(teamId))

const rejecting = ref<RequestItem | null>(null)
const reason = ref('')
function openReject(r: RequestItem) {
  rejecting.value = r
  reason.value = ''
}
const confirmReject = () => run(async () => {
  const r = rejecting.value
  if (!r) return
  await requestApi.reject(r.type, r.id, reason.value.trim() || null)
  rejecting.value = null
})
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('requests.title') }}</h1>
    <p class="muted intro">{{ t('requests.intro') }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <p v-if="items === null" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="items.length === 0" class="card muted">{{ t('requests.empty') }}</p>

    <section v-for="g in groups" :key="g.teamId" class="card group">
      <header class="group-head">
        <div>
          <h2>{{ g.teamName }}</h2>
          <p v-if="g.captain" class="muted small">{{ t('requests.captain', { name: g.captain }) }}</p>
        </div>
        <button v-if="bulkCount(g) > 1" type="button" class="btn" :disabled="busy" @click="approveAll(g.teamId)">
          {{ t('requests.approveAll', { n: bulkCount(g) }) }}
        </button>
      </header>
      <ul>
        <li v-for="r in g.items" :key="`${r.type}-${r.id}`" :class="['item', r.type]">
          <div class="what">
            <strong>{{ t(`requests.types.${r.type}`, { subject: r.subject }) }}</strong>
            <span class="muted small">{{ when(r.requestedAt) }}</span>
          </div>
          <div class="actions">
            <button type="button" class="btn small-btn" :disabled="busy" @click="approve(r)">{{ t('requests.approve') }}</button>
            <button type="button" class="btn secondary small-btn" :disabled="busy" @click="openReject(r)">{{ t('requests.reject') }}</button>
          </div>
        </li>
      </ul>
    </section>

    <BaseModal v-if="rejecting" :title="t('requests.rejectTitle')" width="460px" @close="rejecting = null">
      <p>{{ t(`requests.types.${rejecting.type}`, { subject: rejecting.subject }) }} · {{ rejecting.teamName }}</p>
      <div class="field">
        <label for="reject-reason">{{ t('requests.reason') }}</label>
        <textarea id="reject-reason" v-model="reason" class="input" rows="3" maxlength="500" :placeholder="t('requests.reasonPlaceholder')" />
      </div>
      <template #footer>
        <button type="button" class="btn secondary" @click="rejecting = null">{{ t('tournaments.cancel') }}</button>
        <button type="button" class="btn danger-fill" :disabled="busy" @click="confirmReject">{{ t('requests.reject') }}</button>
      </template>
    </BaseModal>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 6px; }
.intro { margin: 0 0 20px; font-size: 14px; }
.group { margin-bottom: 16px; }
.group-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.group-head h2 { font-size: 18px; color: var(--navy); }
.small { font-size: 13px; margin: 2px 0 0; }
ul { list-style: none; margin: 0; padding: 0; }
.item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border); flex-wrap: wrap; }
.item.DELETE_TEAM strong { color: var(--danger); }
.what { display: flex; flex-direction: column; gap: 2px; }
.actions { display: flex; gap: 8px; }
.small-btn { padding: 6px 14px; font-size: 13px; }
textarea.input { resize: vertical; font: inherit; }
</style>
