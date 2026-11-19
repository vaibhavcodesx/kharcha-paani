import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

// Development only: swaps the real Supabase client for sample data (preview/mock-supabase.ts) and drops the CSP
// meta tag, because the dev server injects inline styles and scripts that the production CSP rightly forbids.
// Never used by `build`.
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'drop-csp-in-dev',
      transformIndexHtml: (html) => html.replace(/<meta http-equiv="Content-Security-Policy"[^>]*>/, ''),
    },
  ],
  resolve: {
    alias: [{ find: /^\.\.?\/supabase$/, replacement: fileURLToPath(new URL('./preview/mock-supabase.ts', import.meta.url)) }],
  },
})
