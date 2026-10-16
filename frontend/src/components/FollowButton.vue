<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { meApi, tournamentApi } from '../api/endpoints'
import { isLoggedIn } from '../auth'
import { t } from '../i18n'

// "Seguir" / "Siguiendo · N" toggle. Without a session it sends the user to login and back.
const props = withDefaults(defineProps<{ tournamentId: number; tone?: 'navy' | 'light' }>(), { tone: 'navy' })
const route = useRoute()
const router = useRouter()
const followers = ref(0)
const following = ref(false)
const busy = ref(false)

watch([() => props.tournamentId, isLoggedIn], async ([id]) => {
  const status = await tournamentApi.followers(id)
  followers.value = status.followers
  following.value = status.following
}, { immediate: true })

async function toggle() {
  if (!isLoggedIn.value) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  busy.value = true
  try {
    const status = following.value ? await meApi.unfollow(props.tournamentId) : await meApi.follow(props.tournamentId)
    followers.value = status.followers
    following.value = status.following
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button
    type="button" :class="['follow', tone, { on: following }]" :disabled="busy" :aria-pressed="following" @click="toggle"
  >
    <AppIcon :name="following ? 'check' : 'bell'" :size="18" />
    {{ following ? t('follow.following') : t('follow.follow') }}
    <span class="count">· {{ followers === 1 ? t('follow.followerOne') : t('follow.followers', { n: followers }) }}</span>
  </button>
</template>

<style scoped>
.follow {
  display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 7px 14px;
  font: inherit; font-size: 14px; font-weight: 500; cursor: pointer; white-space: nowrap;
}
.follow.navy { border: 1px solid var(--navy); background: #fff; color: var(--navy); }
.follow.navy.on { background: var(--navy); color: #fff; }
.follow.light { border: 1px solid rgba(255, 255, 255, 0.7); background: transparent; color: #fff; }
.follow.light.on { background: #fff; color: var(--public-accent, var(--navy)); }
.count { font-weight: 400; opacity: 0.85; }
</style>
