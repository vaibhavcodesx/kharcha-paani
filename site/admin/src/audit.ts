import type { AuditRow } from './rpc'
import { featureLabel } from './format'

// The log in plain words. Before and after come from the server as small objects; nothing here assumes a shape it
// has not checked, because the log must never fail to render.

const ACTIONS: Record<string, string> = {
  set_plan: 'Changed a plan',
  set_cap: 'Changed a daily cap',
  set_ceiling: 'Changed a project ceiling',
  pause_ai: 'Paused AI for everyone',
  resume_ai: 'Resumed AI',
  set_release_facts: 'Recorded release facts',
  set_app_versions: 'Changed the app versions',
  delete_account: 'Deleted an account',
  photo_cleanup: 'Removed leftover photos',
}

export function actionLabel(action: string): string {
  return ACTIONS[action] ?? action.replace(/_/g, ' ')
}

function val(o: Record<string, unknown> | null, key: string): string {
  const v = o?.[key]
  if (v === null || v === undefined) return 'none'
  return typeof v === 'object' ? JSON.stringify(v) : String(v)
}

/** One line saying what changed: "free → friends", "10 → 12", "min 0 → 5". */
export function changeSummary(row: AuditRow): string {
  switch (row.action) {
    case 'set_plan':
      return `${val(row.before, 'plan')} → ${val(row.after, 'plan')}`
    case 'set_cap':
    case 'set_ceiling':
      return `${val(row.before, 'per_day')} → ${val(row.after, 'per_day')}`
    case 'set_app_versions':
      return `minimum ${val(row.before, 'min')} → ${val(row.after, 'min')}, recommended ${val(row.before, 'recommended')} → ${val(row.after, 'recommended')}`
    case 'set_release_facts':
      return `update screen ${val(row.before, 'update_screen')} → ${val(row.after, 'update_screen')}, on Play ${val(row.before, 'latest_published')} → ${val(row.after, 'latest_published')}`
    case 'delete_account':
      return `${val(row.before, 'groups')} groups, ${val(row.before, 'photos')} photos${row.after?.keep_name ? ', name kept' : ', shown as Former member'}`
    case 'photo_cleanup':
      return `${val(row.after, 'removed')} removed, ${val(row.after, 'still_left')} left`
    case 'pause_ai':
      return row.target ? `reason: ${row.target}` : ''
    default:
      return ''
  }
}

/** What the change was made to: a kind and plan for a cap, a person's short id, or nothing. */
export function targetLabel(row: AuditRow): string {
  if ((row.action === 'set_cap') && row.target) {
    const [plan, feature] = row.target.split(':')
    return `${featureLabel(feature ?? '')}, ${plan} plan`
  }
  if (row.action === 'set_ceiling' && row.target) return featureLabel(row.target)
  if (row.target_user) return `person ${row.target_user.slice(0, 8)}`
  return ''
}
