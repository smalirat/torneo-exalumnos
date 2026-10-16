import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { loadSession } from './auth'

loadSession().finally(() => createApp(App).use(router).mount('#app'))
