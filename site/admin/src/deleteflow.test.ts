import { describe, expect, it } from 'vitest'
import { chunk, describeHeirs, emailMatches, parseLeftover, summariseCleanup } from './deleteflow'

describe('parseLeftover', () => {
  it('reads the one row the server returns', () => {
    expect(parseLeftover([{ leftover_paths: ['a/b/c.webp', 'a/d/e.webp'] }])).toEqual(['a/b/c.webp', 'a/d/e.webp'])
  })
  it('survives an empty answer, nulls and nonsense', () => {
    expect(parseLeftover([{ leftover_paths: [] }])).toEqual([])
    expect(parseLeftover([{ leftover_paths: null }])).toEqual([])
    expect(parseLeftover(null)).toEqual([])
    expect(parseLeftover([{ leftover_paths: [1, '', 'x/y/z.jpg'] }])).toEqual(['x/y/z.jpg'])
  })
})

describe('chunk', () => {
  it('splits into batches and keeps every item', () => {
    const items = Array.from({ length: 250 }, (_, i) => i)
    const parts = chunk(items, 100)
    expect(parts.map((p) => p.length)).toEqual([100, 100, 50])
    expect(parts.flat()).toEqual(items)
    expect(chunk([], 100)).toEqual([])
  })
})

describe('emailMatches', () => {
  it('ignores case and spaces but needs the whole address', () => {
    expect(emailMatches('  Dee@Example.com ', 'dee@example.com')).toBe(true)
    expect(emailMatches('dee@example', 'dee@example.com')).toBe(false)
    expect(emailMatches('', 'dee@example.com')).toBe(false)
    expect(emailMatches('x', null)).toBe(false)
  })
})

describe('describeHeirs', () => {
  it('names who inherits and who loses a group', () => {
    expect(describeHeirs([])).toBe('')
    expect(describeHeirs([{ group: 'Goa', heir: 'Hal' }])).toBe('“Goa” goes to Hal.')
    expect(describeHeirs([{ group: 'Goa', heir: 'Hal' }, { group: 'Solo', heir: null }]))
      .toContain('“Solo” is deleted with its records')
  })
})

describe('summariseCleanup', () => {
  it('reports nothing, all removed, or a partial failure with the paths to remove by hand', () => {
    expect(summariseCleanup([], [])).toEqual({ kind: 'none' })
    expect(summariseCleanup(['a', 'b', 'c'], [])).toEqual({ kind: 'done', removed: 3 })
    expect(summariseCleanup(['a', 'b', 'c'], [['b', 'c']])).toEqual({ kind: 'partial', removed: 1, failed: ['b', 'c'] })
  })
})
