import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// One built CSS file and one script, no source maps: the page's CSP allows only its own files.
export default defineConfig({
  plugins: [react()],
  build: { sourcemap: false, cssCodeSplit: false, assetsInlineLimit: 0 },
  test: {
    environment: 'node',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
