<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalLayout from '../layouts/GlobalLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import CollapsibleList from '../components/CollapsibleList.vue'
import { importApi, seasonApi } from '../api/endpoints'
import type { ImportHistory, ImportPreview, Season } from '../api/types'
import { t } from '../i18n'

// Admin: upload the league's workbook -> preview (counters, conflicts, warnings) -> apply. Also history and export.
const preview = ref<ImportPreview | null>(null)
const overwrite = ref<string[]>([])
const busy = ref(false)
const error = ref('')
const history = ref<ImportHistory[]>([])
const seasons = ref<Season[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

async function loadSide() {
  ;[history.value, seasons.value] = await Promise.all([importApi.history(), seasonApi.list()])
}
onMounted(loadSide)

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}

const onFile = (event: Event) => run(async () => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  preview.value = await importApi.upload(file)
  overwrite.value = []
  if (fileInput.value) fileInput.value.value = ''
  await loadSide()
})

const apply = () => run(async () => {
  if (!preview.value) return
  preview.value = await importApi.apply(preview.value.importId, overwrite.value)
  await loadSide()
})

const exportSeason = (s: Season) => run(() => importApi.export(s.id, s.year))

const hasChanges = (p: ImportPreview) => p.counters.some((c) => c.created + c.updated + c.conflicts > 0)
const formatDate = (d: string | null) => (d ? new Date(d).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' }) : '')
</script>

<template>
  <GlobalLayout>
    <h1 class="title">{{ t('excel.title') }}</h1>
    <div class="grid">
      <section class="card main">
        <p class="muted">{{ t('excel.intro') }}</p>
        <label class="btn upload">
          <AppIcon name="plus" :size="18" /> {{ t('excel.choose') }}
          <input ref="fileInput" type="file" accept=".xlsx" hidden :disabled="busy" @change="onFile" />
        </label>
        <p v-if="busy" class="muted">{{ preview ? t('excel.applying') : t('excel.uploading') }}</p>
        <p v-if="error" class="error">{{ error }}</p>

        <template v-if="preview">
          <h2>{{ preview.status === 'APPLIED' ? t('excel.applied', { file: preview.fileName }) : t('excel.preview', { file: preview.fileName }) }}</h2>
          <div class="scroll">
            <table class="counters">
              <thead>
                <tr>
                  <th class="left">{{ t('excel.entity') }}</th>
                  <th>{{ t('excel.created') }}</th>
                  <th>{{ t('excel.updated') }}</th>
                  <th>{{ t('excel.unchanged') }}</th>
                  <th>{{ t('excel.conflicts') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in preview.counters" :key="c.entity">
                  <td class="left">{{ c.entity }}</td>
                  <td :class="{ hot: c.created }">{{ c.created }}</td>
                  <td :class="{ hot: c.updated }">{{ c.updated }}</td>
                  <td>{{ c.unchanged }}</td>
                  <td :class="{ warn: c.conflicts }">{{ c.conflicts }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="!hasChanges(preview)" class="muted">{{ t('excel.nothing') }}</p>

          <section v-if="preview.conflicts.length" class="conflicts">
            <h3>{{ t('excel.conflictsTitle', { n: preview.conflicts.length }) }}</h3>
            <p class="muted small">{{ t('excel.conflictsHelp') }}</p>
            <table>
              <thead>
                <tr>
                  <th class="left">{{ t('excel.entity') }}</th>
                  <th>{{ t('excel.excelValue') }}</th>
                  <th>{{ t('excel.webValue') }}</th>
                  <th v-if="preview.status === 'PREVIEW'">{{ t('excel.overwrite') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in preview.conflicts" :key="c.key">
                  <td class="left">{{ c.description }}</td>
                  <td>{{ c.excelValue }}</td>
                  <td>{{ c.webValue }}</td>
                  <td v-if="preview.status === 'PREVIEW'">
                    <input v-model="overwrite" type="checkbox" :value="c.key" :aria-label="t('excel.overwrite')" />
                  </td>
                </tr>
              </tbody>
            </table>
          </section>

          <CollapsibleList :title="t('excel.warningsTitle', { n: preview.warnings.length })" :items="preview.warnings" tone="warning">
            <template #help><p class="muted small">{{ t('excel.warningsHelp') }}</p></template>
          </CollapsibleList>
          <CollapsibleList :title="t('excel.changesTitle', { n: preview.changes.length })" :items="preview.changes">
            <template #item="{ item }">
              <span class="tag" :class="(item as ImportPreview['changes'][number]).action">{{ (item as ImportPreview['changes'][number]).entity }}</span>
              {{ (item as ImportPreview['changes'][number]).description }}
            </template>
          </CollapsibleList>
          <CollapsibleList :title="t('excel.noticesTitle', { n: preview.notices.length })" :items="preview.notices" :limit="5" />

          <div v-if="preview.status === 'PREVIEW'" class="actions">
            <button class="btn secondary" :disabled="busy" @click="preview = null">{{ t('excel.discard') }}</button>
            <button class="btn" :disabled="busy || !hasChanges(preview)" @click="apply">{{ t('excel.apply') }}</button>
          </div>
        </template>
      </section>

      <aside class="side">
        <section class="card">
          <h2>{{ t('excel.export') }}</h2>
          <p class="muted small">{{ t('excel.exportHelp') }}</p>
          <button v-for="s in seasons" :key="s.id" class="btn secondary season" :disabled="busy" @click="exportSeason(s)">
            {{ t('season.title', { year: s.year }) }}
          </button>
        </section>
        <section class="card">
          <h2>{{ t('excel.historyTitle') }}</h2>
          <ul class="history">
            <li v-for="h in history" :key="h.id">
              <strong>{{ h.fileName }}</strong>
              <span class="muted small">{{ formatDate(h.createdAt) }} · {{ t(`excel.statuses.${h.status}`) }}</span>
              <span class="small">{{ h.summary }}</span>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </GlobalLayout>
</template>

<style scoped>
.title { font-size: 28px; color: var(--navy); margin-bottom: 24px; }
.grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 20px; align-items: start; }
.main h2 { font-size: 18px; margin: 20px 0 10px; color: var(--navy); }
.side { display: flex; flex-direction: column; gap: 20px; }
.side h2 { font-size: 16px; margin-bottom: 6px; }
.upload { margin: 12px 0; }
.scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { font-weight: 500; padding: 6px; text-align: center; border-bottom: 1px solid var(--border); }
td { padding: 6px; text-align: center; border-bottom: 1px solid var(--border); }
.left { text-align: left; }
.hot { font-weight: 700; color: var(--navy); }
.warn { font-weight: 700; color: var(--danger); }
.conflicts { margin-top: 18px; }
.conflicts h3 { font-size: 15px; color: var(--danger); }
.small { font-size: 13px; }
.tag { font-size: 11px; font-weight: 700; padding: 1px 8px; border-radius: 999px; background: var(--bg); color: var(--navy); margin-right: 6px; }
.tag.UPDATE { background: #f6d55c; color: #5a4500; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.season { margin: 6px 6px 0 0; }
.history { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.history li { display: flex; flex-direction: column; gap: 2px; border-bottom: 1px solid var(--border); padding-bottom: 8px; font-size: 14px; }
</style>
