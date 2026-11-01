import { createApp } from 'vue'
// Fonts served by this site instead of Google Fonts: no request to a third party (which would see every visitor's IP)
// and a stricter Content-Security-Policy. Latin subset only (covers Spanish)
import '@fontsource/roboto/latin-400.css'
import '@fontsource/roboto/latin-500.css'
import '@fontsource/roboto/latin-700.css'
import '@fontsource/roboto-condensed/latin-700.css'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { loadSession } from './auth'

loadSession().finally(() => createApp(App).use(router).mount('#app'))
