import { ref } from 'vue'
import { meApi } from './api/endpoints'
import { isLoggedIn } from './auth'

/** Unread notifications badge shared by the layouts. */
export const unreadNotifications = ref(0)

export async function refreshUnread() {
  if (!isLoggedIn.value) {
    unreadNotifications.value = 0
    return
  }
  try {
    unreadNotifications.value = (await meApi.notifications()).unread
  } catch {
    unreadNotifications.value = 0
  }
}
