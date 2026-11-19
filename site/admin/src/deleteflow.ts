// The pure parts of deleting an account: reading what the server returns, splitting photo removal into batches, and
// the words around each step. The database does the deleting; these keep the page honest about what happened.

export interface FoundAccount {
  user_id: string
  email: string | null
  display_name: string | null
  created_at: string
  last_sign_in_at: string | null
  deleted: boolean
  groups: number
  groups_created: number
  heirs: { group: string; heir: string | null }[]
  photos: number
}

/** admin_delete_account returns one row whose only column is a list of file paths. */
export function parseLeftover(rows: unknown): string[] {
  if (!Array.isArray(rows)) return []
  const out: string[] = []
  for (const row of rows) {
    const paths = (row as { leftover_paths?: unknown } | null)?.leftover_paths
    if (Array.isArray(paths)) for (const p of paths) if (typeof p === 'string' && p.length > 0) out.push(p)
  }
  return out
}

/** Storage removes up to this many files per call. */
export const REMOVE_BATCH = 100

export function chunk<T>(items: T[], size: number): T[][] {
  const out: T[][] = []
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size))
  return out
}

/** The typed confirmation must match the person's email, ignoring case and surrounding spaces. */
export function emailMatches(typed: string, email: string | null): boolean {
  return email !== null && typed.trim().toLowerCase() === email.trim().toLowerCase()
}

/** Who inherits each group the person made, in a plain sentence. Empty when they made none. */
export function describeHeirs(heirs: FoundAccount['heirs']): string {
  if (heirs.length === 0) return ''
  const parts = heirs.map((h) => (h.heir ? `“${h.group}” goes to ${h.heir}` : `“${h.group}” is deleted with its records, because nobody else is in it`))
  return parts.join('; ') + '.'
}

export type CleanupOutcome =
  | { kind: 'none' }
  | { kind: 'done'; removed: number }
  | { kind: 'partial'; removed: number; failed: string[] }

/** Folds the batch results into one outcome the screen can describe. */
export function summariseCleanup(paths: string[], failedBatches: string[][]): CleanupOutcome {
  if (paths.length === 0) return { kind: 'none' }
  const failed = failedBatches.flat()
  if (failed.length === 0) return { kind: 'done', removed: paths.length }
  return { kind: 'partial', removed: paths.length - failed.length, failed }
}
