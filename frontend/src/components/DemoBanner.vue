<script setup lang="ts">
import { onMounted } from 'vue'
import { league, loadLeague } from '../league'
import { isLoggedIn } from '../auth'
import { AUTH } from '../paths'
import { t } from '../i18n'

// Public demo only: says the data is fictional (so nobody takes it for the league's official site) and gives the
// login anyone may use to try the panel. Nothing is shown outside the demo.
onMounted(loadLeague)
</script>

<template>
  <div v-if="league?.demo" class="demo" role="note">
    <strong>{{ t('demo.badge') }}</strong>
    <span>{{ t('demo.fictional') }}</span>
    <span v-if="!isLoggedIn" class="access">
      {{ t('demo.tryPanel') }}
      <code>{{ league.demo.username }}</code> / <code>{{ league.demo.password }}</code>
      <RouterLink :to="AUTH.login" class="go">{{ t('demo.enter') }}</RouterLink>
    </span>
  </div>
</template>

<style scoped>
.demo {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 4px 12px;
  padding: 6px 16px; background: #fff4d6; color: #5c4400; font-size: 13px; text-align: center;
  border-bottom: 1px solid #f0dca0;
}
.demo strong { text-transform: uppercase; letter-spacing: 0.04em; font-size: 12px; }
.access { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 4px; }
code { background: #fff; border: 1px solid #f0dca0; border-radius: 4px; padding: 0 5px; font-size: 13px; }
.go { font-weight: 700; text-decoration: underline; margin-left: 4px; }
</style>
