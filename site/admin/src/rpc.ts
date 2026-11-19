import type { SupabaseClient } from '@supabase/supabase-js'

// Calls to the admin functions, and what their errors mean in plain words. The functions are the only
// way in: each one checks the caller in the database (see docs/designs/admin-page.md, section 2).

export type ErrorKind = 'signed-out' | 'not-installed' | 'rate' | 'network' | 'invalid' | 'other'

export class AdminError extends Error {
  readonly kind: ErrorKind
  constructor(kind: ErrorKind, message: string) {
    super(message)
    this.kind = kind
  }
}

interface RawError { code?: string; message?: string; status?: number }

/** Maps the database's error codes to one plain message each. Pure, so it is tested without a server. */
export function describeError(raw: RawError): AdminError {
  const code = raw.code ?? ''
  const status = raw.status ?? 0
  if (code === '42501' || status === 401 || status === 403) return new AdminError('signed-out', 'Signed out, or this account is not the owner.')
  if (code === 'PGRST202' || code === '42883' || status === 404) return new AdminError('not-installed', 'The server functions are not installed yet.')
  if (code === '54000') return new AdminError('rate', 'Too many changes in a few minutes. Try again shortly.')
  if (code === '22023' || code === 'P0002') return new AdminError('invalid', raw.message || 'That change was not accepted.')
  if (status === 0 && /fetch|network|failed to|load failed/i.test(raw.message ?? '')) return new AdminError('network', 'Cannot reach the server. Check the connection and try again.')
  return new AdminError('other', 'Something went wrong. Try again.')
}

/** Calls an admin function and returns its data, or throws an [AdminError]. */
export async function callRpc<T>(client: SupabaseClient, fn: string, args?: Record<string, unknown>): Promise<T> {
  try {
    const { data, error, status } = await client.rpc(fn, args)
    if (error) throw describeError({ code: error.code, message: error.message, status })
    return data as T
  } catch (e) {
    if (e instanceof AdminError) throw e
    throw describeError({ message: e instanceof Error ? e.message : String(e) })
  }
}

export interface AiToday { feature: string; used: number; ceiling: number | null }

export interface Overview {
  as_of: string
  people: { total: number; active_7d: number; deleted: number }
  plans: { free: number; friends: number; paid: number }
  ai_today: AiToday[]
  ai_paused: boolean
  storage: { objects: number; bytes: number; leftover_objects?: number; leftover_bytes?: number }
  db: { bytes: number }
  versions: { min: number; recommended: number; update_screen: number | null; latest_published: number | null }
}
