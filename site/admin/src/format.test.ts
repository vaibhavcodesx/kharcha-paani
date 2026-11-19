import { describe, expect, it } from 'vitest'
import { featureLabel, formatBytes, formatInt, peopleSentence, percent, toneFor, toneWord } from './format'

describe('formatInt', () => {
  it('groups the Indian way', () => {
    expect(formatInt(1234567)).toBe('12,34,567')
    expect(formatInt(412)).toBe('412')
  })
})

describe('formatBytes', () => {
  it('writes small, medium and large sizes', () => {
    expect(formatBytes(0)).toBe('0 MB')
    expect(formatBytes(200 * 1024)).toBe('<1 MB')
    expect(formatBytes(5.5 * 1024 ** 2)).toBe('5.5 MB')
    expect(formatBytes(182 * 1024 ** 2)).toBe('182 MB')
    expect(formatBytes(1.5 * 1024 ** 3)).toBe('1.5 GB')
  })
  it('survives nonsense', () => {
    expect(formatBytes(Number.NaN)).toBe('0 MB')
    expect(formatBytes(-5)).toBe('0 MB')
  })
})

describe('meters', () => {
  it('clamps the fill', () => {
    expect(percent(50, 100)).toBe(50)
    expect(percent(150, 100)).toBe(100)
    expect(percent(-3, 100)).toBe(0)
    expect(percent(0, 0)).toBe(0)
    expect(percent(4, 0)).toBe(100)
  })
  it('turns near at 80 percent and full at the limit', () => {
    expect(toneFor(79, 100)).toBe('ok')
    expect(toneFor(80, 100)).toBe('near')
    expect(toneFor(100, 100)).toBe('full')
    expect(toneFor(0, 0)).toBe('full')
    expect(toneFor(5, null)).toBe('ok')
  })
  it('paused wins over everything', () => {
    expect(toneFor(1, 100, true)).toBe('paused')
    expect(toneWord('paused')).toBe('Paused for everyone')
  })
  it('never relies on colour alone: every tone has words', () => {
    for (const t of ['ok', 'near', 'full', 'paused'] as const) expect(toneWord(t).length).toBeGreaterThan(3)
  })
})

describe('words', () => {
  it('names the kinds and keeps unknown ones visible', () => {
    expect(featureLabel('quick_add')).toBe('Typed entries')
    expect(featureLabel('dance')).toBe('dance')
  })
  it('writes the people sentence', () => {
    const text = peopleSentence({ total: 37, active_7d: 21, deleted: 2 }).map((p) => p.text).join('')
    expect(text).toBe('37 people have accounts, 21 of them active this week. 2 accounts have been deleted.')
    expect(peopleSentence({ total: 1, active_7d: 1, deleted: 0 }).map((p) => p.text).join(''))
      .toBe('1 person has an account, 1 of them active this week.')
  })
})
