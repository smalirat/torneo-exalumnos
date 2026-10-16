import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // In dev, API calls go through Vite to the Spring backend (no CORS needed)
    proxy: { '/api': 'http://localhost:8081' },
  },
})
