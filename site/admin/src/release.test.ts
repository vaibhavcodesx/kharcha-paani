import { describe, expect, it } from 'vitest'
import { checkFacts, planVersionChange, proposeVersions, type Facts } from './release'

const base: Facts = { min: 0, recommended: 0, update_screen: null, latest_published: null }

describe('planVersionChange', () => {
  it('refuses to raise the minimum before the update screen is known', () => {
    const r = planVersionChange({ ...base, latest_published: 12 }, 5, 5)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.reason).toContain('update screen')
  })
  it('refuses a number above the latest published, and when none is recorded', () => {
    expect(planVersionChange({ ...base, update_screen: 10, latest_published: 12 }, 13, 13).ok).toBe(false)
    const none = planVersionChange({ ...base, update_screen: 10 }, 5, 5)
    expect(none.ok).toBe(false)
    if (!none.ok) expect(none.reason).toContain('latest version published')
  })
  it('refuses a recommended version below the minimum and non-whole numbers', () => {
    expect(planVersionChange({ ...base, update_screen: 10, latest_published: 12 }, 5, 3).ok).toBe(false)
    expect(planVersionChange(base, 1.5, 2).ok).toBe(false)
    expect(planVersionChange(base, -1, 2).ok).toBe(false)
  })
  it('says who is affected when the minimum is raised', () => {
    const r = planVersionChange({ ...base, update_screen: 10, latest_published: 12 }, 5, 12)
    expect(r.ok && r.raises).toBe(true)
    if (r.ok) {
      expect(r.sentence).toContain('version 4 or lower')
      expect(r.sentence).toContain('older than 1.8')
    }
  })
  it('lowering is always fine', () => {
    const r = planVersionChange({ min: 5, recommended: 9, update_screen: 10, latest_published: 12 }, 0, 0)
    expect(r.ok && r.raises).toBe(false)
  })
  it('reports no change when nothing changes', () => {
    const r = planVersionChange(base, 0, 0)
    expect(r.ok && r.sentence).toBe('Nothing changes.')
  })
})

describe('checkFacts', () => {
  it('rejects negatives and an update screen newer than Play', () => {
    expect(checkFacts({ update_screen: null, latest_published: null }, -1, 3)).toContain('negative')
    expect(checkFacts({ update_screen: null, latest_published: null }, 9, 5)).toContain('newer')
    expect(checkFacts({ update_screen: null, latest_published: null }, 5, 9)).toBeNull()
  })
})

describe('proposeVersions', () => {
  it('raising the minimum lifts the recommended version with it', () => {
    expect(proposeVersions({ ...base, min: 0, recommended: 3 }, 'min', 5)).toEqual({ min: 5, recommended: 5 })
    expect(proposeVersions({ ...base, min: 0, recommended: 9 }, 'min', 5)).toEqual({ min: 5, recommended: 9 })
  })
  it('editing the recommended version leaves the minimum alone', () => {
    expect(proposeVersions({ ...base, min: 4, recommended: 6 }, 'recommended', 8)).toEqual({ min: 4, recommended: 8 })
  })
})
