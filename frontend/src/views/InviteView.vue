<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { acceptInvite, claimInvite, currentUser, logout } from '../auth'
import { authApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { AUTH, PANEL } from '../paths'
import { t } from '../i18n'

// Invitation link of a pending captain (/invitacion/:token): the real captain picks username and password,
// or, if already signed up and without a team, takes the team with that account.
const route = useRoute()
const router = useRouter()
const token = String(route.params.token)

const teamName = ref<string | null>(null)
const invalid = ref(false)
const username = ref('')
const password = ref('')
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const busy = ref(false)

/** Logged-in user who can take the team with their own account (a captain without a team) */
const canAccept = computed(() => currentUser.value?.role === 'CAPTAIN' && currentUser.value.teamIds.length === 0)

onMounted(async () => {
  try {
    teamName.value = (await authApi.inviteInfo(token)).teamName
  } catch {
    invalid.value = true
  }
})

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    await action()
    router.push(PANEL.teams)
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    if (e instanceof ApiError) fieldErrors.value = e.fieldErrors
  } finally {
    busy.value = false
  }
}

const claim = () => run(() => claimInvite(token, username.value, password.value))
const accept = () => run(() => acceptInvite(token))
</script>

<template>
  <div class="page">
    <div class="card box">
      <p v-if="invalid" class="muted">{{ t('invite.invalid') }}</p>
      <template v-else-if="teamName">
        <h1>{{ t('invite.title', { name: teamName }) }}</h1>

        <!-- Already logged in -->
        <template v-if="currentUser">
          <template v-if="canAccept">
            <p>{{ t('invite.acceptAs', { name: currentUser.username, team: teamName }) }}</p>
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn" :disabled="busy" @click="accept">{{ t('invite.accept') }}</button>
          </template>
          <p v-else class="muted">{{ t('invite.alreadyCaptain', { name: currentUser.username }) }}</p>
          <button type="button" class="link" @click="logout">{{ t('invite.otherAccount') }}</button>
        </template>

        <!-- New user for the team -->
        <form v-else @submit.prevent="claim">
          <p class="muted">{{ t('invite.text', { name: teamName }) }}</p>
          <div class="field">
            <label for="username">{{ t('auth.username') }}</label>
            <input id="username" v-model="username" class="input" autocomplete="username" required />
            <span v-if="fieldErrors.username" class="error">{{ fieldErrors.username }}</span>
          </div>
          <div class="field">
            <label for="password">{{ t('auth.password') }}</label>
            <input id="password" v-model="password" class="input" type="password" autocomplete="new-password" required />
            <span v-if="fieldErrors.password" class="error">{{ fieldErrors.password }}</span>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <button class="btn" :disabled="busy">{{ t('invite.submit') }}</button>
          <RouterLink class="link" :to="{ path: AUTH.login, query: { redirect: route.fullPath } }">{{ t('invite.haveAccount') }}</RouterLink>
        </form>
      </template>
      <p v-else class="muted">{{ t('common.loading') }}</p>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 16px; }
.box { width: 400px; max-width: 100%; display: flex; flex-direction: column; gap: 8px; }
form { display: flex; flex-direction: column; }
h1 { font-size: 22px; color: var(--navy); margin-bottom: 4px; }
.btn { justify-content: center; margin-top: 6px; }
.link {
  margin-top: 12px; font: inherit; font-size: 14px; color: var(--navy); text-align: center;
  background: none; border: 0; cursor: pointer;
}
</style>
