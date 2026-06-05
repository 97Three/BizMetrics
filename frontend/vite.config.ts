import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:38080',
        changeOrigin: true,
        configure(proxy) {
          // The browser calls Vite on the same origin. Do not forward its
          // development-only Origin to the backend's production CORS check.
          proxy.on('proxyReq', request => request.removeHeader('origin'))
        },
      },
    },
  },
})
