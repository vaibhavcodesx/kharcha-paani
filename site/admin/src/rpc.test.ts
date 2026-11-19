import { describe, expect, it } from 'vitest'
import { describeError } from './rpc'

describe('describeError', () => {
  it('a permission error means signed out or not the owner', () => {
    expect(describeError({ code: '42501' }).kind).toBe('signed-out')
    expect(describeError({ status: 401 }).kind).toBe('signed-out')
  })
  it('a missing function means the server part is not installed', () => {
    expect(describeError({ code: 'PGRST202' }).kind).toBe('not-installed')
    expect(describeError({ status: 404 }).kind).toBe('not-installed')
  })
  it('the rate limit has its own message', () => {
    const e = describeError({ code: '54000' })
    expect(e.kind).toBe('rate')
    expect(e.message).toContain('Try again shortly')
  })
  it('a refused change shows the server reason', () => {
    expect(describeError({ code: '22023', message: 'these numbers only go up' }).message).toBe('these numbers only go up')
  })
  it('a dropped connection is a network error', () => {
    expect(describeError({ message: 'TypeError: Failed to fetch' }).kind).toBe('network')
  })
  it('anything else is a short generic message that leaks nothing', () => {
    const e = describeError({ code: 'XX000', message: 'internal detail: relation private.admins' })
    expect(e.kind).toBe('other')
    expect(e.message).not.toContain('private')
  })
})
