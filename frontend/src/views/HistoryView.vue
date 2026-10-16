<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import ChampionsHistory from '../components/ChampionsHistory.vue'
import { championApi } from '../api/endpoints'
import type { CategoryHistory } from '../api/types'
import { t } from '../i18n'

const history = ref<CategoryHistory[] | null>(null)
onMounted(async () => { history.value = await championApi.history() })
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('historyPage.title') }}</h1>
    <ChampionsHistory v-if="history" :history="history" />
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
</style>
