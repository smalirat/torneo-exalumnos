<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, register } from '../auth'
import { ApiError } from '../api/client'
import { t } from '../i18n'

const props = defineProps<{ mode: 'login' | 'register' }>()
const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const busy = ref(false)

async function submit() {
  busy.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    await (props.mode === 'login' ? login : register)(username.value, password.value)
    router.push('/')
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
</style>
