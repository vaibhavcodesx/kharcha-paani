// Pure helpers for how numbers and states are written. No DOM, so they are tested in Node.

const indian = new Intl.NumberFormat('en-IN')

/** 1234567 becomes "12,34,567" (the grouping people here read). */
export function formatInt(n: number): string {
  return indian.format(Math.round(n))
}

/** Bytes as a short size: 182 MB, 1.2 GB. Binary units, written the way the Supabase dashboard writes them. */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 MB'
  const mb = bytes / 1024 ** 2
  if (mb < 1) return '<1 MB'
  if (mb < 1024) return `${mb < 10 ? mb.toFixed(1) : Math.round(mb)} MB`
  const gb = mb / 1024
  return `${gb < 10 ? gb.toFixed(1) : Math.round(gb)} GB`
}

export type Tone = 'ok' | 'near' | 'full' | 'paused'

/** How full a meter is: 0 to 100, never outside. A limit of 0 reads as full. */
export function percent(used: number, limit: number): number {
  if (limit <= 0) return used > 0 ? 100 : 0
  return Math.max(0, Math.min(100, (used / limit) * 100))
}

/** The state of a meter. Near from 80%, full from 100%. Colour is never the only signal: see [toneWord]. */
export function toneFor(used: number, limit: number | null, paused = false): Tone {
  if (paused) return 'paused'
  if (limit === null) return 'ok'
  if (limit <= 0) return 'full'
  const ratio = used / limit
  if (ratio >= 1) return 'full'
  if (ratio >= 0.8) return 'near'
  return 'ok'
}

export function toneWord(tone: Tone): string {
  switch (tone) {
    case 'ok': return 'Plenty left'
    case 'near': return 'Getting close'
    case 'full': return 'At the limit'
    case 'paused': return 'Paused for everyone'
  }
}

const FEATURE_LABELS: Record<string, string> = {
  quick_add: 'Typed entries',
  bill: 'Bill photos',
  statement: 'Statements',
  insights: 'Insights questions',
}

/** The server's kind names, in words. An unknown kind is shown as it is, so nothing is hidden. */
export function featureLabel(feature: string): string {
  return FEATURE_LABELS[feature] ?? feature
}

export interface People { total: number; active_7d: number; deleted: number }

/** The sentence under the Today title, as parts so the numbers can carry their own face. */
export function peopleSentence(p: People): { text: string; num: boolean }[] {
  const parts: { text: string; num: boolean }[] = [
    { text: formatInt(p.total), num: true },
    { text: p.total === 1 ? ' person has an account, ' : ' people have accounts, ', num: false },
    { text: formatInt(p.active_7d), num: true },
    { text: ' of them active this week.', num: false },
  ]
  if (p.deleted > 0) {
    parts.push({ text: ' ', num: false }, { text: formatInt(p.deleted), num: true },
      { text: p.deleted === 1 ? ' account has been deleted.' : ' accounts have been deleted.', num: false })
  }
  return parts
}
