<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { currentUser, logout } from '../auth'
import { t } from '../i18n'

const router = useRouter()

function doLogout() {
  logout()
  router.push('/')
}
</script>

<template>
  <div class="footer">
    <template v-if="currentUser">
      <div class="who">
        <span class="avatar">{{ currentUser.username[0]?.toUpperCase() }}</span>
        {{ currentUser.username }}
      </div>
      <button class="link" @click="doLogout"><AppIcon name="logout" :size="18" /> {{ t('nav.logout') }}</button>
    </template>
    <RouterLink v-else to="/login" class="link"><AppIcon name="login" :size="18" /> {{ t('nav.login') }}</RouterLink>
  </div>
</template>

<style scoped>
.footer { margin-top: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.who { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.avatar {
  width: 30px; height: 30px; border-radius: 50%;
  background: var(--violet); display: inline-flex; align-items: center; justify-content: center;
}
.link {
  display: inline-flex; align-items: center; gap: 8px;
  background: none; border: 0; color: #fff; font: inherit; font-weight: 700; cursor: pointer; padding: 0;
}
</style>
