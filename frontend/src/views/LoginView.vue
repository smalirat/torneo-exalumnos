<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../auth'
import { ApiError } from '../api/client'
import { teamApi } from '../api/endpoints'
import type { TeamRef } from '../api/types'
import { t } from '../i18n'

const props = defineProps<{ mode: 'login' | 'register' }>()
const router = useRouter()
const route = useRoute()
/** Only same-app paths, never an external URL; a new captain goes to their team */
const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect
  : props.mode === 'register' ? '/equipos' : '/'

const username = ref('')
const password = ref('')
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const busy = ref(false)

// Sign-up goes with a team: a new one, or one already in the league that has no captain (both need approval)
const teamMode = ref<'new' | 'existing'>('new')
const teamName = ref('')
const claimTeamId = ref<number | null>(null)
const unclaimed = ref<TeamRef[]>([])
onMounted(async () => {
  if (props.mode === 'register') unclaimed.value = await teamApi.unclaimed().catch(() => [])
})

async function submit() {
  busy.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    if (props.mode === 'login') await login(username.value, password.value)
    else await register(username.value, password.value,
      teamMode.value === 'new' ? { teamName: teamName.value.trim() } : { claimTeamId: claimTeamId.value! })
    router.push(redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    if (e instanceof ApiError) fieldErrors.value = e.fieldErrors
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page">
    <form class="card" @submit.prevent="submit">
      <h1>{{ mode === 'login' ? t('auth.loginTitle') : t('auth.registerTitle') }}</h1>
      <div class="field">
        <label for="username">{{ t('auth.username') }}</label>
        <input id="username" v-model="username" class="input" autocomplete="username" required />
        <span v-if="fieldErrors.username" class="error">{{ fieldErrors.username }}</span>
      </div>
      <div class="field">
        <label for="password">{{ t('auth.password') }}</label>
        <input
          id="password" v-model="password" class="input" type="password" required
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
        />
        <span v-if="fieldErrors.password" class="error">{{ fieldErrors.password }}</span>
      </div>
      <fieldset v-if="mode === 'register'" class="team">
        <legend>{{ t('auth.yourTeam') }}</legend>
        <label class="choice"><input v-model="teamMode" type="radio" value="new"> {{ t('auth.newTeam') }}</label>
        <input v-if="teamMode === 'new'" v-model="teamName" class="input" :placeholder="t('teams.namePlaceholder')" maxlength="100" required />
        <label class="choice">
          <input v-model="teamMode" type="radio" value="existing" :disabled="unclaimed.length === 0"> {{ t('auth.existingTeam') }}
        </label>
        <select v-if="teamMode === 'existing'" v-model="claimTeamId" class="select" required>
          <option :value="null" disabled>{{ t('auth.pickTeam') }}</option>
          <option v-for="tm in unclaimed" :key="tm.id" :value="tm.id">{{ tm.name }}</option>
        </select>
        <p class="muted hint">{{ teamMode === 'new' ? t('auth.approvalHint') : t('auth.claimHint') }}</p>
      </fieldset>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn" :disabled="busy">
        {{ mode === 'login' ? t('auth.submitLogin') : t('auth.submitRegister') }}
      </button>
      <RouterLink class="switch" :to="mode === 'login' ? '/registro' : '/login'">
        {{ mode === 'login' ? t('auth.noAccount') : t('auth.haveAccount') }}
      </RouterLink>
    </form>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
form { width: 360px; display: flex; flex-direction: column; }
h1 { font-size: 22px; margin-bottom: 20px; color: var(--navy); }
.btn { justify-content: center; margin-top: 6px; }
.switch { margin-top: 16px; font-size: 14px; color: var(--navy); text-align: center; }
.team { border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px 4px; margin: 0 0 14px; display: flex; flex-direction: column; gap: 8px; }
.team legend { font-size: 13px; font-weight: 500; color: var(--muted); padding: 0 4px; }
.choice { display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; }
.hint { font-size: 12px; margin: 0 0 6px; }
</style>
