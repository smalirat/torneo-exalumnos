<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TournamentLayout from '../layouts/TournamentLayout.vue'
import AppIcon from '../components/AppIcon.vue'
import BaseModal from '../components/BaseModal.vue'
import ConfirmButton from '../components/ConfirmButton.vue'
import EditionStatus from '../components/EditionStatus.vue'
import InlineEdit from '../components/InlineEdit.vue'
import TournamentForm from '../components/TournamentForm.vue'
import { editionImage, tournamentApi, type TournamentInput } from '../api/endpoints'
import type { Category, Tournament, Zone } from '../api/types'
import { isAdmin } from '../auth'
import { registrationLabel } from '../editions'
import { editionBySlug, loadEditions } from '../editions'
import { shrinkImage } from '../images'
import { PANEL, panelEditionPath } from '../paths'
import { groupSegments } from '../slugs'
import { t } from '../i18n'

const route = useRoute()
const router = useRouter()
const tournamentId = computed(() => editionBySlug(route.params.edicion)?.id ?? 0)
const categories = ref<Category[]>([])
const error = ref('')

watch(tournamentId, async (id) => {
  categories.value = await tournamentApi.categories(id)
}, { immediate: true })

const reloadCategories = async () => { categories.value = await tournamentApi.categories(tournamentId.value) }

async function run(action: () => Promise<unknown>) {
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  }
}

// --- admin: edit / delete the edition ---
const editing = ref(false)
const layoutKey = ref(0) // remounts the layout so sidebar/breadcrumb pick up a new name, logo or status

const saveTournament = (input: TournamentInput) => run(async () => {
  const saved = await tournamentApi.update(tournamentId.value, input)
  editing.value = false
  // Year / Apertura-Clausura are part of the URL: go to the new one if they changed
  await loadEditions(true)
  router.replace(panelEditionPath(saved))
  layoutKey.value++
})

const deleteTournament = () => run(async () => {
  await tournamentApi.remove(tournamentId.value)
  await loadEditions(true)
  router.push(PANEL.editions)
})

// --- admin: banner (cover photo) and logo ---
const bannerInput = ref<HTMLInputElement | null>(null)
const logoInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

async function onImage(kind: 'banner' | 'logo', e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = ''
  if (!file) return
  uploading.value = true
  await run(async () => {
    const blob = kind === 'banner' ? await shrinkImage(file, 1800, 'image/jpeg') : await shrinkImage(file, 400, 'image/png')
    await tournamentApi.setImage(tournamentId.value, kind, blob)
    await loadEditions(true)
    layoutKey.value++
  })
  uploading.value = false
}

const removeImage = (kind: 'banner' | 'logo') => run(async () => {
  await tournamentApi.removeImage(tournamentId.value, kind)
  await loadEditions(true)
  layoutKey.value++
})

const asTournament = (x: unknown) => x as Tournament
const bannerUrl = (x: unknown) => editionImage(asTournament(x), 'banner')
const logoUrl = (x: unknown) => editionImage(asTournament(x), 'logo')

// --- admin: categories and their zones, in one place ---
const adding = ref(false)
const newName = ref('')
const newZones = ref(1)

function openAdd() {
  adding.value = true
  newName.value = ''
  newZones.value = 1
}

/** The category comes with "Zona 1"; the extra zones are created right after */
const addCategory = () => run(async () => {
  const created = await tournamentApi.createCategory(tournamentId.value, newName.value)
  for (let n = 2; n <= newZones.value; n++) await tournamentApi.createZone(created.id, `Zona ${n}`)
  adding.value = false
  await reloadCategories()
})

const renameCategory = async (c: Category, name: string) => {
  Object.assign(c, await tournamentApi.renameCategory(c.id, name))
}

const deleteCategory = (c: Category) => run(async () => {
  await tournamentApi.removeCategory(c.id)
  categories.value = categories.value.filter((x) => x.id !== c.id)
})

const addZone = (c: Category) => run(async () => {
  await tournamentApi.createZone(c.id, `Zona ${c.zoneCount + 1}`)
  await reloadCategories()
})

const renameZone = async (z: Zone, name: string) => {
  await tournamentApi.renameZone(z.id, name)
  await reloadCategories()
}

const deleteZone = (z: Zone) => run(async () => {
  await tournamentApi.removeZone(z.id)
  await reloadCategories()
})

/** Standings of a zone in the panel: /panel/ediciones/2026-clausura/clasificacion?grupo=categoria-a/zona-2 */
function standingsOf(zoneId: number | null) {
  const edition = editionBySlug(route.params.edicion)
  const grupo = groupSegments(categories.value, zoneId).join('/')
  return edition ? { path: panelEditionPath(edition, 'clasificacion'), query: grupo ? { grupo } : {} } : PANEL.editions
}
const buildPath = computed(() => {
  const edition = editionBySlug(route.params.edicion)
  return edition ? panelEditionPath(edition, 'armado') : PANEL.editions
})
</script>

<template>
  <TournamentLayout :key="layoutKey" v-slot="{ tournament }">
    <section
      class="banner" :class="{ photo: tournament && bannerUrl(tournament) }"
      :style="tournament && bannerUrl(tournament) ? { backgroundImage: `url(${bannerUrl(tournament)})` } : undefined"
    >
      <span class="avatar">
        <img v-if="tournament && logoUrl(tournament)" :src="logoUrl(tournament)!" alt="" />
        <AppIcon v-else name="trophy" :size="30" />
        <button
          v-if="isAdmin && tournament" type="button" class="avatar-edit" :disabled="uploading"
          :title="t('editionImages.changeLogo')" :aria-label="t('editionImages.changeLogo')" @click="logoInput?.click()"
        ><AppIcon name="edit" :size="16" /></button>
        <button
          v-if="isAdmin && tournament && logoUrl(tournament)" type="button" class="avatar-remove"
          :title="t('editionImages.removeLogo')" :aria-label="t('editionImages.removeLogo')" @click="removeImage('logo')"
        ><AppIcon name="close" :size="14" /></button>
      </span>
      <div v-if="isAdmin && tournament" class="admin-actions">
        <button class="btn secondary" :disabled="uploading" @click="bannerInput?.click()">
          <AppIcon name="image" :size="18" /> {{ bannerUrl(tournament) ? t('editionImages.changeBanner') : t('editionImages.addBanner') }}
        </button>
        <button v-if="bannerUrl(tournament)" class="btn secondary" @click="removeImage('banner')">{{ t('editionImages.removeBanner') }}</button>
        <button class="btn secondary" @click="editing = true"><AppIcon name="edit" :size="18" /> {{ t('common.edit') }}</button>
        <ConfirmButton :label="t('tournaments.delete')" :message="t('tournaments.deleteConfirm')" @confirm="deleteTournament" />
      </div>
      <input ref="bannerInput" type="file" accept="image/png,image/jpeg" hidden @change="onImage('banner', $event)" />
      <input ref="logoInput" type="file" accept="image/png,image/jpeg" hidden @change="onImage('logo', $event)" />
    </section>
    <h1 class="name">{{ tournament?.name }}</h1>

    <BaseModal v-if="editing && tournament" :title="t('tournaments.edit')" @close="editing = false">
      <TournamentForm
        :initial="asTournament(tournament)" :submit-label="t('tournaments.save')"
        @submit="saveTournament" @cancel="editing = false"
      >
        <template #error><p v-if="error" class="error">{{ error }}</p></template>
      </TournamentForm>
    </BaseModal>

    <div v-if="tournament" class="facts">
      <div class="card fact">
        <EditionStatus :tournament="asTournament(tournament)" @changed="layoutKey++" />
      </div>
      <div v-if="tournament.registrationStart || tournament.registrationEnd" class="card fact">
        <div>
          <p class="label">{{ t('tournaments.registration') }}</p>
          <p class="value plain">{{ registrationLabel(tournament.registrationStart, tournament.registrationEnd) }}</p>
        </div>
      </div>
    </div>

    <div class="section-head">
      <h2 class="section">{{ t('categories.title') }}</h2>
      <RouterLink v-if="isAdmin && categories.length" :to="buildPath" class="btn secondary">
        <AppIcon name="auto" :size="18" /> {{ t('build.open') }}
      </RouterLink>
    </div>
    <div class="card">
      <div v-if="isAdmin" class="toolbar">
        <form v-if="adding" class="add" @submit.prevent="addCategory">
          <input v-model="newName" class="input" :placeholder="t('categories.namePlaceholder')" required autofocus />
          <label class="zones-input">
            {{ t('categories.zoneCount') }}
            <input v-model.number="newZones" class="input" type="number" min="1" max="8" required />
          </label>
          <button class="btn">{{ t('tournaments.create') }}</button>
          <button type="button" class="btn secondary" @click="adding = false">{{ t('tournaments.cancel') }}</button>
        </form>
        <button v-else class="btn" @click="openAdd"><AppIcon name="plus" :size="18" /> {{ t('categories.new') }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="categories.length === 0" class="muted">{{ t('categories.empty') }}</p>
      <div v-for="c in categories" :key="c.id" class="cat">
        <div class="cat-row">
          <span class="cat-icon"><AppIcon name="trophy" /></span>
          <span class="cat-name">
            <InlineEdit :value="c.name" :editable="isAdmin" :save="(name) => renameCategory(c, name)">
              <RouterLink :to="standingsOf(c.zones[0]?.id ?? null)">{{ c.name }}</RouterLink>
            </InlineEdit>
          </span>
          <ConfirmButton
            v-if="isAdmin" icon :label="t('categories.delete')"
            :message="t('categories.deleteConfirm', { name: c.name })" @confirm="deleteCategory(c)"
          />
          <RouterLink :to="standingsOf(c.zones[0]?.id ?? null)" class="icon-btn">
            <AppIcon name="chevron" />
          </RouterLink>
        </div>
        <!-- Zones: with a single one it is not named anywhere, but the admin can add more here -->
        <div v-if="c.zoneCount > 1 || isAdmin" class="zones">
          <template v-if="c.zoneCount > 1">
            <span v-for="z in c.zones" :key="z.id" class="zone-chip">
              <InlineEdit :value="z.name" :editable="isAdmin" :save="(name) => renameZone(z, name)">
                <RouterLink :to="standingsOf(z.id)">{{ z.name }}</RouterLink>
              </InlineEdit>
              <ConfirmButton
                v-if="isAdmin" icon :label="t('tournamentTeams.deleteZone')"
                :message="t('tournamentTeams.deleteZoneConfirm', { name: z.name })" @confirm="deleteZone(z)"
              />
            </span>
          </template>
          <span v-else class="muted single">{{ t('categories.singleZone') }}</span>
          <button v-if="isAdmin" type="button" class="add-zone" @click="addZone(c)">
            <AppIcon name="plus" :size="16" /> {{ t('tournamentTeams.addZone') }}
          </button>
        </div>
      </div>
    </div>
  </TournamentLayout>
</template>

<style scoped>
.banner {
  position: relative; height: 220px; border-radius: var(--radius);
  background: linear-gradient(135deg, var(--navy) 0%, #3b5a9e 60%, #7e93c4 100%);
}
.banner.photo { background-size: cover; background-position: center; }
.admin-actions { position: absolute; right: 16px; bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; max-width: calc(100% - 180px); }
.avatar {
  position: absolute; left: 30px; bottom: -50px; width: 110px; height: 110px; border-radius: 50%;
  background: var(--bg); color: var(--navy); border: 4px solid #fff;
  display: inline-flex; align-items: center; justify-content: center; box-shadow: var(--shadow);
}
.avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.avatar-edit, .avatar-remove {
  position: absolute; border: 0; border-radius: 50%; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; box-shadow: var(--shadow);
}
.avatar-edit { right: -2px; bottom: 4px; width: 32px; height: 32px; background: var(--navy); color: #fff; }
.avatar-remove { right: 2px; top: 2px; width: 24px; height: 24px; background: #fff; color: var(--danger); }
.name { font-size: 32px; color: var(--navy); margin: 14px 0 20px 160px; }
.facts { display: flex; gap: 14px; margin: 34px 0 30px; flex-wrap: wrap; }
.fact { display: flex; align-items: center; gap: 12px; padding: 14px 20px; min-width: 180px; }
.label { font-size: 11px; text-transform: uppercase; color: var(--muted); margin: 0; }
.value { margin: 2px 0 0; color: #3d7be0; font-weight: 500; }
.value.plain { color: var(--text); }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.section { font-size: 22px; margin: 0; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.add { display: flex; gap: 8px; width: 100%; align-items: center; flex-wrap: wrap; }
.add > .input { flex: 1; min-width: 200px; }
.zones-input { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); white-space: nowrap; }
.zones-input .input { width: 70px; }
.cat { padding: 10px 6px; border-bottom: 1px solid var(--border); }
.cat:last-child { border-bottom: 0; }
.cat-row { display: flex; align-items: center; gap: 14px; }
.cat-name { flex: 1; font-size: 16px; }
.cat-icon {
  width: 44px; height: 44px; border-radius: 50%; background: var(--bg); color: var(--navy);
  display: inline-flex; align-items: center; justify-content: center;
}
.zones { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 8px 0 2px 58px; }
.zone-chip {
  display: inline-flex; align-items: center; gap: 2px; padding: 2px 4px 2px 12px; border-radius: 999px;
  background: var(--bg); font-size: 14px;
}
.single { font-size: 13px; }
.add-zone {
  display: inline-flex; align-items: center; gap: 4px; padding: 4px 12px; border-radius: 999px; cursor: pointer;
  border: 1px dashed var(--border); background: none; color: var(--navy); font: inherit; font-size: 13px; font-weight: 500;
}
.add-zone:hover { background: var(--row-alt); }
</style>
