import { ref } from 'vue'
import { requestApi } from './api/endpoints'

/** How many captains' requests wait for the admin (sidebar counter) */
export const pendingRequests = ref(0)

export async function refreshPendingRequests() {
  try {
    pendingRequests.value = (await requestApi.count()).count
  } catch {
    pendingRequests.value = 0
  }
}
