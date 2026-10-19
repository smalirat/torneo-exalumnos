import vue from '@vitejs/plugin-vue'
import { defineConfig, type Plugin } from 'vite'

// Content Security Policy of the built site: scripts and API only from our own origin (the API is under /api),
// fonts from Google Fonts, images also as blob:/data: (collage and crest previews). Only in the build:
// the dev server injects inline scripts for hot reload.
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' https://fonts.googleapis.com",
  "font-src https://fonts.gstatic.com",
  "img-src 'self' blob: data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ')

const contentSecurityPolicy = (): Plugin => ({
  name: 'content-security-policy',
  apply: 'build',
  transformIndexHtml: () => [{ tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: CSP }, injectTo: 'head-prepend' }],
})

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), contentSecurityPolicy()],
  server: {
    port: 5173,
    // In dev, API calls go through Vite to the Spring backend (no CORS needed).
    // API_TARGET points a second dev server to another backend (e.g. a throwaway H2 one for browser tests).
    proxy: { '/api': process.env.API_TARGET ?? 'http://localhost:8081' },
  },
})
