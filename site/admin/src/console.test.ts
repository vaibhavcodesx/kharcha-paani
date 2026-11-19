import { describe, expect, it } from 'vitest'
import { attentionItems } from './attention'
import { filterCommands } from './commands'
import type { AuditRow, Overview } from './rpc'
import { avatarHue, groupByDay, initials, planSegments, trackMarks } from './viz'

const overview = (over: Partial<Overview> = {}): Overview => ({
  as_of: '2026-11-19T10:00:00Z',
  people: { total: 37, active_7d: 21, deleted: 2 },
  plans: { free: 33, friends: 4, paid: 0 },
  ai_today: [{ feature: 'bill', used: 38, ceiling: 400 }],
  ai_paused: false,
  storage: { objects: 311, bytes: 182 * 1024 ** 2, leftover_objects: 0, leftover_bytes: 0 },
  db: { bytes: 41 * 1024 ** 2 },
  versions: { min: 0, recommended: 0, update_screen: 10, latest_published: 12 },
  ...over,
})

describe('avatars', () => {
  it('gives a person the same hue every time, from the palette', () => {
    expect(avatarHue('abc')).toBe(avatarHue('abc'))
    expect([18, 38, 74, 152, 190, 232, 276, 332]).toContain(avatarHue('00000000-0000-4000-8000-000000000011'))
  })
  it('makes initials from a name or an email', () => {
    expect(initials('Asha Rao')).toBe('AR')
    expect(initials('Meera')).toBe('ME')
    expect(initials(null, 'ravi.k@example.com')).toBe('RK')
    expect(initials('', null)).toBe('?')
  })
})

describe('planSegments', () => {
  it('adds to 100 and leaves out empty plans', () => {
    const s = planSegments({ free: 33, friends: 4, paid: 0 })
    expect(s.map((x) => x.id)).toEqual(['free', 'friends'])
    expect(Math.round(s.reduce((n, x) => n + x.pct, 0))).toBe(100)
  })
  it('survives nobody', () => {
    expect(planSegments({ free: 0, friends: 0, paid: 0 })).toEqual([])
  })
})

describe('trackMarks', () => {
  it('places each number as a share of the largest', () => {
    const { marks, max } = trackMarks({ min: 5, recommended: 10, update_screen: 8, latest_published: 20 })
    expect(max).toBe(20)
    expect(marks.find((m) => m.id === 'min')?.pct).toBe(25)
    expect(marks.find((m) => m.id === 'latest')?.pct).toBe(100)
  })
  it('leaves unset numbers off and never divides by zero', () => {
    const { marks, max } = trackMarks({ min: 0, recommended: 0, update_screen: null, latest_published: null })
    expect(max).toBe(1)
    expect(marks.map((m) => m.id)).toEqual(['min', 'recommended'])
  })
})

describe('groupByDay', () => {
  const row = (id: number, at: string): AuditRow => ({ id, at, admin_email: 'o', action: 'set_plan', target_user: null, target: null, before: null, after: null })
  it('labels today, yesterday and older days in India time, keeping order', () => {
    const now = new Date('2026-11-19T10:00:00Z')
    const g = groupByDay([row(3, '2026-11-19T09:00:00Z'), row(2, '2026-11-19T02:00:00Z'), row(1, '2026-11-18T09:00:00Z'), row(0, '2026-11-10T09:00:00Z')], now)
    expect(g.map((x) => x.label)).toEqual(['Today', 'Yesterday', '10 Nov'])
    expect(g[0].rows.map((r) => r.id)).toEqual([3, 2])
  })
  it('skips rows with a bad date', () => {
    expect(groupByDay([row(1, 'nope')])).toEqual([])
  })
})

describe('attentionItems', () => {
  it('is empty when nothing needs the owner', () => {
    expect(attentionItems(overview())).toEqual([])
  })
  it('puts stops before watches before todos', () => {
    const items = attentionItems(overview({
      ai_paused: true,
      ai_today: [{ feature: 'statement', used: 80, ceiling: 80 }, { feature: 'quick_add', used: 1700, ceiling: 2000 }],
      storage: { objects: 9, bytes: 1, leftover_objects: 3, leftover_bytes: 3 * 1024 ** 2 },
      versions: { min: 0, recommended: 0, update_screen: null, latest_published: null },
    }))
    expect(items.map((i) => i.severity)).toEqual(['stop', 'stop', 'watch', 'todo', 'todo'])
    expect(items[1].id).toBe('ceiling-statement')
    expect(items.some((i) => i.id === 'update-screen')).toBe(true)
  })
  it('warns when storage passes 80 percent', () => {
    expect(attentionItems(overview({ storage: { objects: 1, bytes: 900 * 1024 ** 2 } })).map((i) => i.id)).toContain('storage')
  })
})

describe('filterCommands', () => {
  it('lists everything for an empty query', () => {
    expect(filterCommands('').length).toBeGreaterThan(6)
  })
  it('matches word starts in the label or keywords', () => {
    expect(filterCommands('lim').map((c) => c.id)).toEqual(['go-limits'])
    expect(filterCommands('pause').map((c) => c.id)).toEqual(['go-limits'])
    expect(filterCommands('go pe').map((c) => c.id)).toEqual(['go-people'])
    expect(filterCommands('zzz')).toEqual([])
  })
})
