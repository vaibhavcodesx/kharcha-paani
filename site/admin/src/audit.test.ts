import { describe, expect, it } from 'vitest'
import { actionLabel, changeSummary, targetLabel } from './audit'
import type { AuditRow } from './rpc'
import { formatDay, formatWhen } from './format'

const row = (over: Partial<AuditRow>): AuditRow => ({
  id: 1, at: '2026-11-19T16:44:00Z', admin_email: 'o@x', action: 'set_plan', target_user: null, target: null, before: null, after: null, ...over,
})

describe('audit words', () => {
  it('names actions and keeps unknown ones readable', () => {
    expect(actionLabel('set_cap')).toBe('Changed a daily cap')
    expect(actionLabel('brand_new_thing')).toBe('brand new thing')
  })
  it('summarises a plan, a cap and the version switch', () => {
    expect(changeSummary(row({ before: { plan: 'free' }, after: { plan: 'friends' } }))).toBe('free → friends')
    expect(changeSummary(row({ action: 'set_cap', before: { per_day: 10 }, after: { per_day: 12 } }))).toBe('10 → 12')
    expect(changeSummary(row({ action: 'set_app_versions', before: { min: 0, recommended: 0 }, after: { min: 5, recommended: 12 } })))
      .toBe('minimum 0 → 5, recommended 0 → 12')
  })
  it('survives missing before and after', () => {
    expect(changeSummary(row({ action: 'set_plan' }))).toBe('none → none')
    expect(changeSummary(row({ action: 'something_else' }))).toBe('')
  })
  it('describes a deletion without any email', () => {
    const s = changeSummary(row({ action: 'delete_account', before: { groups: 2, photos: 3 }, after: { keep_name: false } }))
    expect(s).toBe('2 groups, 3 photos, shown as Former member')
    expect(s).not.toContain('@')
  })
  it('labels the target', () => {
    expect(targetLabel(row({ action: 'set_cap', target: 'free:bill' }))).toBe('Bill photos, free plan')
    expect(targetLabel(row({ action: 'set_ceiling', target: 'quick_add' }))).toBe('Typed entries')
    expect(targetLabel(row({ target_user: 'abcdef12-0000-4000-8000-000000000000' }))).toBe('person abcdef12')
  })
})

describe('dates', () => {
  it('writes India time', () => {
    expect(formatWhen('2026-11-19T16:44:00Z')).toBe('19 Nov, 22:14')
  })
  it('survives bad input', () => {
    expect(formatWhen('nope')).toBe('')
    expect(formatDay(null)).toBe('never')
    expect(formatDay('2026-11-03T05:00:00Z')).toBe('3 Nov')
  })
})
