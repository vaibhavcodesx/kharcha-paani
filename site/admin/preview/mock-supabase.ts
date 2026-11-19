// A stand-in for src/supabase.ts, used only by `npm run dev:mock` (vite.preview.config.ts). It signs in a fake owner
// and answers the admin functions with sample data, so every screen can be looked at without a backend.
// Variants through the address: ?paused, ?leftover, ?empty, ?slow, ?error.
import type { SupabaseClient } from '@supabase/supabase-js'

const q = new URLSearchParams(window.location.search)

const overview = () => ({
  as_of: new Date().toISOString(),
  people: q.has('empty') ? { total: 1, active_7d: 1, deleted: 0 } : { total: 37, active_7d: 21, deleted: 2 },
  plans: { free: 33, friends: 4, paid: 0 },
  ai_today: [
    { feature: 'bill', used: q.has('empty') ? 0 : 38, ceiling: 400 },
    { feature: 'insights', used: q.has('empty') ? 0 : 97, ceiling: 800 },
    { feature: 'quick_add', used: q.has('empty') ? 0 : 1670, ceiling: 2000 },
    { feature: 'statement', used: q.has('empty') ? 0 : 80, ceiling: 80 },
  ],
  ai_paused: q.has('paused'),
  storage: { objects: 311, bytes: 182 * 1024 ** 2, leftover_objects: q.has('leftover') ? 12 : 0, leftover_bytes: 2 * 1024 ** 2 },
  db: { bytes: 41 * 1024 ** 2 },
  versions: { min: 0, recommended: 0, update_screen: null, latest_published: null },
})

const session = { user: { id: '00000000-0000-4000-8000-000000000001' } }

export const supabase = {
  auth: {
    getSession: async () => ({ data: { session } }),
    onAuthStateChange: () => ({ data: { subscription: { unsubscribe() {} } } }),
    signOut: async () => ({ error: null }),
    signInWithOAuth: async () => ({ error: null }),
  },
  rpc: async (fn: string) => {
    if (q.has('slow')) await new Promise((r) => setTimeout(r, 4000))
    if (q.has('error') && fn === 'admin_overview') return { data: null, error: { code: 'XX000', message: 'boom' }, status: 500 }
    if (fn === 'admin_whoami') return { data: true, error: null, status: 200 }
    if (fn === 'admin_overview') return { data: overview(), error: null, status: 200 }
    return { data: null, error: { code: 'PGRST202', message: 'not found' }, status: 404 }
  },
} as unknown as SupabaseClient
