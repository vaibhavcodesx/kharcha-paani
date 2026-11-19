import type { AuditRow } from './rpc'
import type { Facts } from './release'

// Small pure helpers for the visual parts of the console. No DOM, so they are tested in Node.

/** The hues the app gives people (its chart colours), as CSS custom property values. */
const AVATAR_HUES = [18, 38, 74, 152, 190, 232, 276, 332]

/** A stable hue for a person, from their id, so a person keeps the same colour on every screen. */
export function avatarHue(id: string): number {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return AVATAR_HUES[h % AVATAR_HUES.length]
}

/** Up to two initials from a display name or an email. */
export function initials(name: string | null | undefined, email?: string | null): string {
  const source = (name ?? '').trim() || (email ?? '').split('@')[0] || ''
  const words = source.split(/[\s._-]+/).filter(Boolean)
  if (words.length === 0) return '?'
  const letters = words.length === 1 ? words[0].slice(0, 2) : words[0][0] + words[words.length - 1][0]
  return letters.toUpperCase()
}

export interface Segment { id: string; label: string; count: number; pct: number }

/** Free, Friends and Paid shares of the people, as bar segments that add to 100. Zero-count plans are left out. */
export function planSegments(plans: { free: number; friends: number; paid: number }): Segment[] {
  const total = plans.free + plans.friends + plans.paid
  const raw: [string, string, number][] = [
    ['free', 'Free', plans.free],
    ['friends', 'Friends', plans.friends],
    ['paid', 'Paid', plans.paid],
  ]
  return raw
    .filter(([, , n]) => n > 0)
    .map(([id, label, count]) => ({ id, label, count, pct: total === 0 ? 0 : (count / total) * 100 }))
}

export interface Mark { id: 'min' | 'recommended' | 'update_screen' | 'latest'; label: string; value: number; pct: number }

/** Where the four version numbers sit on a 0 to (largest number) track. Unset numbers are left off. */
export function trackMarks(f: Facts): { marks: Mark[]; max: number } {
  const entries: [Mark['id'], string, number | null][] = [
    ['min', 'Minimum', f.min],
    ['recommended', 'Recommended', f.recommended],
    ['update_screen', 'Update screen', f.update_screen],
    ['latest', 'On Play', f.latest_published],
  ]
  const present = entries.filter((e): e is [Mark['id'], string, number] => e[2] !== null && (e[2] > 0 || e[0] === 'min' || e[0] === 'recommended'))
  const max = Math.max(1, ...present.map((e) => e[2]))
  return { max, marks: present.map(([id, label, value]) => ({ id, label, value, pct: (value / max) * 100 })) }
}

const dayKey = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' })
const dayLabel = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short' })

/** Groups log rows (newest first) under "Today", "Yesterday" or "19 Nov", by India day. Row order is kept. */
export function groupByDay(rows: AuditRow[], now: Date = new Date()): { label: string; rows: AuditRow[] }[] {
  const today = dayKey.format(now)
  const yesterday = dayKey.format(new Date(now.getTime() - 86_400_000))
  const groups: { key: string; label: string; rows: AuditRow[] }[] = []
  for (const row of rows) {
    const d = new Date(row.at)
    if (Number.isNaN(d.getTime())) continue
    const key = dayKey.format(d)
    let g = groups[groups.length - 1]
    if (!g || g.key !== key) {
      g = { key, label: key === today ? 'Today' : key === yesterday ? 'Yesterday' : dayLabel.format(d), rows: [] }
      groups.push(g)
    }
    g.rows.push(row)
  }
  return groups.map(({ label, rows: r }) => ({ label, rows: r }))
}
