<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../auth'
import { ApiError } from '../api/client'
import { teamApi } from '../api/endpoints'
import type { TeamRef } from '../api/types'
import { AUTH, PANEL, safeRedirect } from '../paths'
import { t } from '../i18n'
import { league, loadLeague } from '../league'

const props = defineProps<{ mode: 'login' | 'register' }>()
loadLeague()
/** Public demo: fill the form with the published demo login */
function useDemo() {
  if (!league.value?.demo) return
  username.value = league.value.demo.username
  password.value = league.value.demo.password
}
const router = useRouter()
const route = useRoute()
/** Only same-app paths, never an external URL; a new captain goes to their team */
const redirect = computed(() => safeRedirect(route.query.redirect, props.mode === 'register' ? PANEL.teams : '/'))

const username = ref('')
const password = ref('')
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const busy = ref(false)
const email = ref('')
const phone = ref('')

// Sign-up goes with a team: a new one, or one already in the league that has no captain (both need approval)
const teamMode = ref<'new' | 'existing'>('new')
const teamName = ref('')
const claimTeamId = ref<number | null>(null)
const unclaimed = ref<TeamRef[]>([])
// Login and sign-up are the same component: going from one to the other only changes the mode, it does not remount
watch(() => props.mode, async (mode) => {
  error.value = ''
  fieldErrors.value = {}
  if (mode === 'register') unclaimed.value = await teamApi.unclaimed().catch(() => [])
}, { immediate: true })

async function submit() {
  busy.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    if (props.mode === 'login') await login(username.value, password.value)
    else await register(username.value, password.value,
      teamMode.value === 'new' ? { teamName: teamName.value.trim() } : { claimTeamId: claimTeamId.value! },
      { email: email.value.trim(), phone: phone.value.trim() })
    router.push(redirect.value)
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
      <div v-if="mode === 'login' && league?.demo" class="demo-hint">
        <p>{{ t('demo.loginHint') }} <code>{{ league.demo.username }}</code> / <code>{{ league.demo.password }}</code></p>
        <button type="button" class="btn secondary" @click="useDemo">{{ t('demo.fill') }}</button>
      </div>
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
      <template v-if="mode === 'register'">
        <div class="field">
          <label for="email">{{ t('contact.email') }}</label>
          <input id="email" v-model="email" class="input" type="email" autocomplete="email" maxlength="120" required />
          <span v-if="fieldErrors.email" class="error">{{ fieldErrors.email }}</span>
        </div>
        <div class="field">
          <label for="phone">{{ t('contact.phone') }}</label>
          <input id="phone" v-model="phone" class="input" type="tel" autocomplete="tel" maxlength="30" required :placeholder="t('contact.phonePlaceholder')" />
          <span v-if="fieldErrors.phone" class="error">{{ fieldErrors.phone }}</span>
        </div>
      </template>
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
      <RouterLink class="switch" :to="mode === 'login' ? AUTH.register : AUTH.login">
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
.demo-hint {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px;
  margin-bottom: 16px; padding: 10px 12px; border-radius: 8px; background: #fff4d6; color: #5c4400; font-size: 14px;
}
.demo-hint p { margin: 0; }
.demo-hint code { background: #fff; border-radius: 4px; padding: 0 5px; }
</style>
