// A stand-in for src/supabase.ts, used only by `npm run dev:mock` (vite.preview.config.ts). It signs in a fake owner
// and answers the admin functions with sample data that changes as you use it, so every screen and every control
// can be tried without a backend. Variants through the address: ?paused, ?leftover, ?empty, ?slow, ?error.
import type { SupabaseClient } from '@supabase/supabase-js'

const q = new URLSearchParams(window.location.search)

const used: Record<string, number> = { bill: 38, insights: 97, quick_add: 1670, statement: 80 }

const overview = () => ({
  as_of: new Date().toISOString(),
  people: q.has('empty') ? { total: 1, active_7d: 1, deleted: 0 } : { total: 37, active_7d: 21, deleted: 2 },
  plans: { free: 33, friends: 4, paid: 0 },
  ai_today: Object.keys(used).sort().map((feature) => ({ feature, used: q.has('empty') ? 0 : used[feature], ceiling: ceilings[feature] ?? null })),
  ai_paused: paused,
  storage: { objects: 311, bytes: 182 * 1024 ** 2, leftover_objects: q.has('leftover') ? 12 : 0, leftover_bytes: 2 * 1024 ** 2 },
  db: { bytes: 41 * 1024 ** 2 },
  versions: { ...facts },
})

const people = [
  { user_id: '00000000-0000-4000-8000-000000000011', email: 'asha.rao@example.com', display_name: 'Asha Rao', plan: 'friends', created_at: '2026-11-03T05:00:00Z', last_sign_in_at: '2026-11-19T04:00:00Z', deleted: false, ai_today: 12, photos: 4, photo_bytes: 640000, groups: 3 },
  { user_id: '00000000-0000-4000-8000-000000000012', email: 'ravi.k@example.com', display_name: 'Ravi K', plan: 'free', created_at: '2026-11-05T05:00:00Z', last_sign_in_at: '2026-11-18T10:00:00Z', deleted: false, ai_today: 3, photos: 0, photo_bytes: 0, groups: 1 },
  { user_id: '00000000-0000-4000-8000-000000000013', email: 'meera@example.com', display_name: 'Meera Nair', plan: 'free', created_at: '2026-11-07T05:00:00Z', last_sign_in_at: null, deleted: false, ai_today: 0, photos: 1, photo_bytes: 90000, groups: 0 },
  { user_id: '00000000-0000-4000-8000-000000000014', email: null, display_name: 'Former member', plan: 'free', created_at: '2026-11-01T05:00:00Z', last_sign_in_at: null, deleted: true, ai_today: 0, photos: 3, photo_bytes: 300000, groups: 0 },
]
const caps = [
  { feature: 'bill', free: 10, friends: 30 },
  { feature: 'insights', free: 20, friends: 60 },
  { feature: 'quick_add', free: 50, friends: 150 },
  { feature: 'statement', free: 3, friends: 8 },
]
const ceilings: Record<string, number | null> = { bill: 400, insights: 800, quick_add: 2000, statement: 80 }
let paused = q.has('paused')

interface Entry { id: number; at: string; admin_email: string; action: string; target_user: string | null; target: string | null; before: unknown; after: unknown }
const audit: Entry[] = [
  { id: 3, at: '2026-11-19T16:44:00Z', admin_email: 'owner@example.com', action: 'set_cap', target_user: null, target: 'free:bill', before: { per_day: 10 }, after: { per_day: 12 } },
  { id: 2, at: '2026-11-19T15:02:00Z', admin_email: 'owner@example.com', action: 'set_plan', target_user: '00000000-0000-4000-8000-000000000011', target: null, before: { plan: 'free' }, after: { plan: 'friends' } },
  { id: 1, at: '2026-11-18T09:30:00Z', admin_email: 'owner@example.com', action: 'delete_account', target_user: '00000000-0000-4000-8000-000000000014', target: null, before: { groups: 2, photos: 3 }, after: { keep_name: false } },
]
let nextId = 4
function log(action: string, target: string | null, before: unknown, after: unknown, user: string | null = null) {
  audit.unshift({ id: nextId++, at: new Date().toISOString(), admin_email: 'owner@example.com', action, target_user: user, target, before, after })
}

const facts = { min: 0, recommended: 0, update_screen: null as number | null, latest_published: null as number | null }
const ok = (data: unknown) => ({ data, error: null, status: 200 })
const bad = (code: string, message: string) => ({ data: null, error: { code, message }, status: 400 })

function handle(fn: string, a: Record<string, unknown> = {}) {
  switch (fn) {
    case 'admin_whoami': return ok(true)
    case 'admin_overview': return ok(overview())
    case 'admin_list_users': {
      const term = String(a.p_query ?? '').toLowerCase()
      const hit = people.filter((p) => !term || (p.email ?? '').toLowerCase().includes(term) || (p.display_name ?? '').toLowerCase().includes(term))
      const from = Number(a.p_offset ?? 0)
      const page = hit.slice(from, from + Number(a.p_limit ?? 50))
      return ok(q.has('empty') ? [] : page.map((p) => ({ ...p, total_count: hit.length })))
    }
    case 'admin_set_plan': {
      const p = people.find((x) => x.user_id === a.p_user)
      if (!p) return bad('P0002', 'no such person')
      log('set_plan', null, { plan: p.plan }, { plan: a.p_plan }, p.user_id)
      p.plan = a.p_plan as string
      return ok(a.p_plan)
    }
    case 'admin_get_limits':
      return ok({
        caps,
        ceilings: caps.map((c) => ({ feature: c.feature, per_day: ceilings[c.feature] ?? null, used_today: used[c.feature] })),
        paused,
      })
    case 'admin_set_cap': {
      const c = caps.find((x) => x.feature === a.p_feature)
      if (!c) return bad('22023', 'unknown feature')
      const key = a.p_plan as 'free' | 'friends'
      log('set_cap', `${key}:${a.p_feature}`, { per_day: c[key] }, { per_day: a.p_per_day })
      c[key] = Number(a.p_per_day)
      return ok(null)
    }
    case 'admin_set_ceiling':
      if (paused) return bad('22023', 'AI is paused; resume it first')
      log('set_ceiling', String(a.p_feature), { per_day: ceilings[String(a.p_feature)] ?? null }, { per_day: a.p_per_day })
      ceilings[String(a.p_feature)] = Number(a.p_per_day)
      return ok(null)
    case 'admin_pause_ai':
      paused = true
      log('pause_ai', null, { ...ceilings }, {})
      return ok({})
    case 'admin_resume_ai':
      paused = false
      log('resume_ai', null, {}, { ...ceilings })
      return ok({})
    case 'admin_set_release_facts':
      log('set_release_facts', null, { update_screen: facts.update_screen, latest_published: facts.latest_published }, { update_screen: a.p_update_screen, latest_published: a.p_latest_published })
      facts.update_screen = a.p_update_screen as number | null
      facts.latest_published = a.p_latest_published as number | null
      return ok(null)
    case 'admin_set_app_versions':
      log('set_app_versions', null, { min: facts.min, recommended: facts.recommended }, { min: a.p_min, recommended: a.p_recommended })
      facts.min = Number(a.p_min)
      facts.recommended = Number(a.p_recommended)
      return ok(null)
    case 'admin_list_audit': {
      const before = a.p_before as number | null
      return ok(audit.filter((r) => before === null || r.id < before).slice(0, Number(a.p_limit ?? 50)))
    }
    default: return bad('PGRST202', 'not found')
  }
}

const session = { user: { id: '00000000-0000-4000-8000-000000000001' } }

export const supabase = {
  auth: {
    getSession: async () => ({ data: { session } }),
    onAuthStateChange: () => ({ data: { subscription: { unsubscribe() {} } } }),
    signOut: async () => ({ error: null }),
    signInWithOAuth: async () => ({ error: null }),
  },
  rpc: async (fn: string, args?: Record<string, unknown>) => {
    if (q.has('slow')) await new Promise((r) => setTimeout(r, 4000))
    if (q.has('error') && fn !== 'admin_whoami') return { data: null, error: { code: 'XX000', message: 'boom' }, status: 500 }
    return handle(fn, args)
  },
} as unknown as SupabaseClient
