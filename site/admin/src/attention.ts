import { FREE_DATABASE_BYTES, FREE_STORAGE_BYTES } from './config'
import { featureLabel, formatBytes, formatInt } from './format'
import type { Overview } from './rpc'
import type { TabId } from './router'

// What needs the owner's eyes, worked out from the overview. Each item says what is wrong, why it matters and where to go.

export type Severity = 'stop' | 'watch' | 'todo'

export interface Attention {
  id: string
  severity: Severity
  title: string
  detail: string
  go: TabId
}

export function attentionItems(o: Overview): Attention[] {
  const items: Attention[] = []

  if (o.ai_paused) {
    items.push({ id: 'paused', severity: 'stop', title: 'AI is paused for everyone', detail: 'Phones show the daily-limit message until you resume it.', go: 'limits' })
  }

  for (const a of o.ai_today) {
    if (a.ceiling === null) continue
    if (a.ceiling > 0 && a.used >= a.ceiling) {
      items.push({ id: `ceiling-${a.feature}`, severity: 'stop', title: `${featureLabel(a.feature)} hit today's ceiling`, detail: `${formatInt(a.used)} of ${formatInt(a.ceiling)} used. Everyone is refused until tomorrow, India time.`, go: 'limits' })
    } else if (a.ceiling > 0 && a.used / a.ceiling >= 0.8) {
      items.push({ id: `near-${a.feature}`, severity: 'watch', title: `${featureLabel(a.feature)} is close to today's ceiling`, detail: `${formatInt(a.used)} of ${formatInt(a.ceiling)} used.`, go: 'limits' })
    }
  }

  if (o.storage.bytes / FREE_STORAGE_BYTES >= 0.8) {
    items.push({ id: 'storage', severity: 'watch', title: 'Photo storage is nearly full', detail: `${formatBytes(o.storage.bytes)} of ${formatBytes(FREE_STORAGE_BYTES)} on the free plan.`, go: 'people' })
  }
  if (o.db.bytes / FREE_DATABASE_BYTES >= 0.8) {
    items.push({ id: 'db', severity: 'watch', title: 'The database is nearly full', detail: `${formatBytes(o.db.bytes)} of ${formatBytes(FREE_DATABASE_BYTES)} on the free plan.`, go: 'today' })
  }

  const leftover = o.storage.leftover_objects ?? 0
  if (leftover > 0) {
    items.push({ id: 'leftover', severity: 'todo', title: `${formatInt(leftover)} ${leftover === 1 ? 'photo' : 'photos'} left behind by deleted accounts`, detail: `${formatBytes(o.storage.leftover_bytes ?? 0)} you can reclaim from People.`, go: 'people' })
  }

  if (o.versions.update_screen === null) {
    items.push({ id: 'update-screen', severity: 'todo', title: 'The update screen version is not recorded', detail: 'You cannot raise the minimum app version until it is.', go: 'release' })
  }

  const order: Record<Severity, number> = { stop: 0, watch: 1, todo: 2 }
  return items.sort((a, b) => order[a.severity] - order[b.severity])
}
