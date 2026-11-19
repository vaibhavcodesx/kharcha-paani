import { describe, expect, it } from 'vitest'
import { parseTab } from './router'

describe('parseTab', () => {
  it('reads a tab from the hash', () => {
    expect(parseTab('#people')).toBe('people')
    expect(parseTab('#/limits')).toBe('limits')
    expect(parseTab('#log?x=1')).toBe('log')
  })
  it('falls back to Today for anything unknown or empty', () => {
    expect(parseTab('')).toBe('today')
    expect(parseTab('#nope')).toBe('today')
    expect(parseTab('#__proto__')).toBe('today')
  })
})
