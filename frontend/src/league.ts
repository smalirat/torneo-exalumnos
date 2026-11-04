import { ref } from 'vue'
import { leagueApi, type LeagueInfo } from './api/endpoints'

/** The league's name and, in the public demo, the login anyone may use. Loaded once, shared by every page. */
export const league = ref<LeagueInfo | null>(null)

let loading: Promise<void> | null = null
export function loadLeague(): Promise<void> {
  loading ??= leagueApi.info()
    .then((info) => { league.value = info })
    .catch(() => { loading = null }) // try again on the next page
  return loading
}
