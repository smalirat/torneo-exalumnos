<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { protestApi } from '../api/endpoints'
import type { Protest } from '../api/types'
import { refreshPendingRequests } from '../requests'
import { t } from '../i18n'

// Admin: protests of matches filed by captains. Answer each one (final); the captain sees the answer.
const protests = ref<Protest[]>([])
const answers = ref<Record<number, string>>({})
const showResolved = ref(false)
const error = ref('')
const busy = ref(false)

const load = async () => { protests.value = await protestApi.all() }
onMounted(load)

const pending = computed(() => protests.value.filter((p) => p.status === 'OPEN'))
const resolved = computed(() => protests.value.filter((p) => p.status === 'RESOLVED'))
const date = (d: string | null) => (d ? new Date(d).toLocaleDateString('es-AR') : '')

async function resolve(p: Protest) {
  busy.value = true
  error.value = ''
  try {
    await protestApi.resolve(p.id, (answers.value[p.id] ?? '').trim())
    await Promise.all([load(), refreshPendingRequests()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section v-if="protests.length" class="card protests">
    <h2>{{ t('protests.adminTitle') }} <span v-if="pending.length" class="count">{{ pending.length }}</span></h2>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!pending.length" class="muted small">{{ t('protests.noneOpen') }}</p>
    <article v-for="p in pending" :key="p.id" class="protest">
      <div class="head">
        <strong>{{ p.match }}</strong>
        <span class="muted small">{{ p.round ? t('fixture.round', { n: p.round }) + ' · ' : '' }}{{ date(p.matchAt) }}</span>
      </div>
      <p class="muted small">{{ t('protests.by', { team: p.team.name, captain: p.captain, date: date(p.createdAt) }) }}</p>
      <p><span class="label">{{ t('protests.facts') }}:</span> {{ p.facts }}</p>
      <p><span class="label">{{ t('protests.request') }}:</span> {{ p.request }}</p>
      <form class="answer" @submit.prevent="resolve(p)">
        <textarea v-model="answers[p.id]" class="input" rows="2" maxlength="2000" required :placeholder="t('protests.answerPlaceholder')" />
        <button class="btn" :disabled="busy || !(answers[p.id] ?? '').trim()">{{ t('protests.resolve') }}</button>
      </form>
    </article>
    <button v-if="resolved.length" type="button" class="link" @click="showResolved = !showResolved">
      {{ showResolved ? t('protests.hideResolved') : t('protests.showResolved', { n: resolved.length }) }}
    </button>
    <template v-if="showResolved">
      <article v-for="p in resolved" :key="p.id" class="protest done">
        <div class="head"><strong>{{ p.match }}</strong><span class="muted small">{{ p.team.name }}</span></div>
        <p class="muted small">{{ p.request }}</p>
        <p>{{ t('protests.answer') }}: {{ p.resolution }}</p>
      </article>
    </template>
  </section>
</template>

<style scoped>
.protests { margin-bottom: 16px; }
h2 { font-size: 18px; margin: 0 0 8px; }
.count { font-size: 12px; background: var(--danger); color: #fff; border-radius: 999px; padding: 1px 8px; margin-left: 4px; vertical-align: middle; }
.protest { padding: 10px 0; border-bottom: 1px solid var(--border); font-size: 14px; }
.protest p { margin: 4px 0; }
.protest.done { opacity: .8; }
.head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.label { font-weight: 500; color: var(--muted); }
.small { font-size: 13px; }
.answer { display: flex; gap: 8px; align-items: flex-end; margin-top: 6px; }
.answer textarea { flex: 1; resize: vertical; font: inherit; }
.link { background: none; border: 0; padding: 0; margin-top: 8px; color: var(--navy); font: inherit; font-size: 13px; text-decoration: underline; cursor: pointer; }
</style>
