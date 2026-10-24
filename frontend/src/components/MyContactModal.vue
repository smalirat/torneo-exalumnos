<script setup lang="ts">
import { ref } from 'vue'
import BaseModal from './BaseModal.vue'
import { ApiError } from '../api/client'
import { currentUser, updateContact } from '../auth'
import { t } from '../i18n'

// "Mis datos": the user's own email and phone (private: only they and the admin see them)
const emit = defineEmits<{ close: [] }>()

const email = ref(currentUser.value?.email ?? '')
const phone = ref(currentUser.value?.phone ?? '')
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const busy = ref(false)

async function save() {
  busy.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    await updateContact({ email: email.value.trim(), phone: phone.value.trim() })
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
    if (e instanceof ApiError) fieldErrors.value = e.fieldErrors
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseModal :title="t('contact.title')" width="420px" @close="emit('close')">
    <form id="contact-form" @submit.prevent="save">
      <p class="muted small">{{ t('contact.help') }}</p>
      <div class="field">
        <label for="contact-email">{{ t('contact.email') }}</label>
        <input id="contact-email" v-model="email" class="input" type="email" autocomplete="email" maxlength="120" required />
        <span v-if="fieldErrors.email" class="error">{{ fieldErrors.email }}</span>
      </div>
      <div class="field">
        <label for="contact-phone">{{ t('contact.phone') }}</label>
        <input id="contact-phone" v-model="phone" class="input" type="tel" autocomplete="tel" maxlength="30" required :placeholder="t('contact.phonePlaceholder')" />
        <span v-if="fieldErrors.phone" class="error">{{ fieldErrors.phone }}</span>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn secondary" @click="emit('close')">{{ t('tournaments.cancel') }}</button>
      <button class="btn" form="contact-form" :disabled="busy">{{ t('contact.save') }}</button>
    </template>
  </BaseModal>
</template>

<style scoped>
.small { font-size: 13px; margin: 0 0 10px; }
</style>
