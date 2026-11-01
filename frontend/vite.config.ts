import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// The Content-Security-Policy is sent by nginx as a header (frontend/nginx.conf), not as a <meta> tag here: a header
// also supports frame-ancestors, and a single policy is easier to reason about than two that add up.

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // In dev, API calls go through Vite to the Spring backend (no CORS needed).
    // API_TARGET points a second dev server to another backend (e.g. a throwaway H2 one for browser tests).
    proxy: { '/api': process.env.API_TARGET ?? 'http://localhost:8081' },
  },
})
