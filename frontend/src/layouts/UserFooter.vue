<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import MyContactModal from '../components/MyContactModal.vue'
import { currentUser, logout } from '../auth'
import { AUTH } from '../paths'
import { t } from '../i18n'

const router = useRouter()
const editing = ref(false)

function doLogout() {
  logout()
  router.push('/')
}
</script>

<template>
  <div class="footer">
    <template v-if="currentUser">
      <button type="button" class="who" :title="t('contact.title')" @click="editing = true">
        <span class="avatar">{{ currentUser.username[0]?.toUpperCase() }}</span>
        {{ currentUser.username }}
      </button>
      <MyContactModal v-if="editing" @close="editing = false" />
      <button class="link" @click="doLogout"><AppIcon name="logout" :size="18" /> {{ t('nav.logout') }}</button>
    </template>
    <RouterLink v-else :to="AUTH.login" class="link"><AppIcon name="login" :size="18" /> {{ t('nav.login') }}</RouterLink>
  </div>
</template>

<style scoped>
.footer { margin-top: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.who {
  display: flex; align-items: center; gap: 10px; font: inherit; font-size: 14px; color: inherit;
  background: none; border: 0; padding: 4px 6px 4px 0; border-radius: 8px; cursor: pointer; text-align: left;
}
.who:hover { background: rgba(255, 255, 255, 0.08); }
.avatar {
  width: 30px; height: 30px; border-radius: 50%;
  background: var(--violet); display: inline-flex; align-items: center; justify-content: center;
}
.link {
  display: inline-flex; align-items: center; gap: 8px;
  background: none; border: 0; color: #fff; font: inherit; font-weight: 700; cursor: pointer; padding: 0;
}
</style>
