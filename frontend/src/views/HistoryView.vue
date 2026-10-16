<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import ChampionsCollage from '../components/ChampionsCollage.vue'
import ChampionsHistory from '../components/ChampionsHistory.vue'
import { championApi, collageApi } from '../api/endpoints'
import type { CategoryHistory, CollagePhoto } from '../api/types'
import { t } from '../i18n'

const history = ref<CategoryHistory[] | null>(null)
const photos = ref<CollagePhoto[]>([])
onMounted(async () => { [history.value, photos.value] = await Promise.all([championApi.history(), collageApi.list()]) })
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('historyPage.title') }}</h1>
    <ChampionsCollage v-model:photos="photos" />
    <ChampionsHistory v-if="history" :history="history" />
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
</style>
