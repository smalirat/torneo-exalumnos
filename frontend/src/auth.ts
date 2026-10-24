import { computed, ref } from 'vue'
import { getToken, setToken, setUnauthorizedHandler } from './api/client'
import { authApi, type Contact, type SignUpTeam } from './api/endpoints'
import type { Me } from './api/types'

const me = ref<Me | null>(null)

export const currentUser = computed(() => me.value)
export const isLoggedIn = computed(() => me.value !== null)
export const isAdmin = computed(() => me.value?.role === 'ADMIN')

/** Restores the session from the stored token (call once at startup). */
export async function loadSession() {
  if (!getToken()) return
  try {
    me.value = await authApi.me()
  } catch {
    logout()
  }
}

export async function login(username: string, password: string) {
  setToken((await authApi.login(username, password)).token)
  me.value = await authApi.me()
}

export async function register(username: string, password: string, team: SignUpTeam, contact: Contact) {
  setToken((await authApi.register(username, password, team, contact)).token)
  me.value = await authApi.me()
}

/** Claims a pending captain account from an invitation link and logs in with it. */
export async function claimInvite(token: string, username: string, password: string, contact: Contact) {
  setToken((await authApi.claim(token, username, password, contact)).token)
  me.value = await authApi.me()
}

/** "Mis datos" */
export async function updateContact(contact: Contact) {
  me.value = await authApi.updateMe(contact)
}

/** A captain who has not given email and phone yet (users from before they were asked) */
export const missingContact = computed(() => me.value?.role === 'CAPTAIN' && (!me.value.email || !me.value.phone))

/** A logged-in captain without a team takes the invited one. */
export async function acceptInvite(token: string) {
  me.value = await authApi.acceptInvite(token)
}

export function logout() {
  setToken(null)
  me.value = null
}

setUnauthorizedHandler(logout)
