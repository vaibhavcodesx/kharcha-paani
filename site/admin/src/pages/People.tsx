import { useCallback, useEffect, useRef, useState } from 'react'
import { Segmented, ErrorPanel } from '../controls'
import { formatBytes, formatDay, formatInt } from '../format'
import { AdminError, callRpc, type UserRow } from '../rpc'
import { supabase } from '../supabase'
import { Sheet, Skeleton, useToast } from '../ui'
import { DeleteAccountSheet } from './DeleteAccount'

const PAGE = 50

type Load = { kind: 'loading' } | { kind: 'ready' } | { kind: 'failed'; error: AdminError }

function nameOf(u: UserRow): string {
  return u.deleted ? 'Former member' : u.display_name?.trim() || u.email || 'Unnamed'
}

export function People() {
  const [query, setQuery] = useState('')
  const [rows, setRows] = useState<UserRow[]>([])
  const [total, setTotal] = useState(0)
  const [load, setLoad] = useState<Load>({ kind: 'loading' })
  const [more, setMore] = useState(false)
  const [picked, setPicked] = useState<UserRow | null>(null)
  const [deleteFor, setDeleteFor] = useState<{ email: string | null } | null>(null)
  const generation = useRef(0)
  const toast = useToast()

  const fetchPage = useCallback(async (q: string, offset: number) => {
    const mine = ++generation.current
    if (offset === 0) setLoad({ kind: 'loading' })
    else setMore(true)
    try {
      const page = await callRpc<UserRow[]>(supabase, 'admin_list_users', { p_query: q.trim() || null, p_limit: PAGE, p_offset: offset })
      if (mine !== generation.current) return
      setRows((prev) => (offset === 0 ? page : [...prev, ...page]))
      setTotal(page[0]?.total_count ?? 0)
      setLoad({ kind: 'ready' })
    } catch (e) {
      if (mine !== generation.current) return
      setLoad({ kind: 'failed', error: e instanceof AdminError ? e : new AdminError('other', 'Something went wrong. Try again.') })
    } finally {
      setMore(false)
    }
  }, [])

  // Search runs on the server, a moment after typing stops.
  useEffect(() => {
    const t = window.setTimeout(() => void fetchPage(query, 0), query === '' ? 0 : 300)
    return () => window.clearTimeout(t)
  }, [query, fetchPage])

  const changePlan = async (u: UserRow, plan: 'free' | 'friends') => {
    try {
      await callRpc<string>(supabase, 'admin_set_plan', { p_user: u.user_id, p_plan: plan })
      const next = { ...u, plan }
      setRows((prev) => prev.map((r) => (r.user_id === u.user_id ? next : r)))
      setPicked(next)
      toast.say(`${nameOf(u)} is now on the ${plan === 'friends' ? 'Friends' : 'Free'} plan. It is in the Log.`)
    } catch (e) {
      toast.say((e as AdminError).message)
    }
  }

  return (
    <>
      <header className="pagehead">
        <h1>People</h1>
        <p>Everyone with an account. Search by name or email.</p>
      </header>

      <div className="searchbar">
        <label className="sr" htmlFor="q">Search people</label>
        <input id="q" className="field search" type="search" placeholder="Name or email" value={query} autoComplete="off" onChange={(e) => setQuery(e.target.value)} />
        {load.kind === 'ready' && <span className="count num" aria-live="polite">{formatInt(total)} {total === 1 ? 'person' : 'people'}</span>}
      </div>
      <p className="searchnote">Someone asked for their account to be deleted? <button className="linkbtn" onClick={() => setDeleteFor({ email: null })}>Delete by email</button></p>

      {load.kind === 'failed' && <ErrorPanel title="People could not load" error={load.error} onRetry={() => void fetchPage(query, 0)} />}
      {load.kind === 'loading' && <Skeleton rows={6} />}
      {load.kind === 'ready' && rows.length === 0 && (
        <section className="panel">
          <h2>{query ? `Nobody matches “${query}”` : 'No accounts yet'}</h2>
          <p>{query ? 'Try part of a name, or the start of an email.' : 'People appear here once they sign in to the app.'}</p>
        </section>
      )}
      {load.kind === 'ready' && rows.length > 0 && (
        <ul className="people" aria-label="People">
          {rows.map((u) => (
            <li key={u.user_id}>
              <button className="person" data-gone={u.deleted} onClick={() => setPicked(u)}>
                <span className="person-main">
                  <strong>{nameOf(u)}</strong>
                  <span className="person-sub">{u.deleted ? 'Account deleted' : u.email}</span>
                  <span className="person-meta">
                    {u.deleted
                      ? `${u.photos} ${u.photos === 1 ? 'photo' : 'photos'} may remain`
                      : <><span className="num">{formatInt(u.ai_today)}</span> AI today · <span className="num">{u.photos}</span> {u.photos === 1 ? 'photo' : 'photos'} · <span className="num">{u.groups}</span> {u.groups === 1 ? 'group' : 'groups'}</>}
                  </span>
                </span>
                <span className="chip" data-plan={u.deleted ? 'gone' : u.plan}>{u.deleted ? 'Deleted' : u.plan === 'friends' ? 'Friends' : 'Free'}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {load.kind === 'ready' && rows.length < total && (
        <div className="morewrap">
          <button className="btn" disabled={more} onClick={() => void fetchPage(query, rows.length)}>{more ? 'Loading' : `Show more (${formatInt(total - rows.length)} left)`}</button>
        </div>
      )}

      <DeleteAccountSheet
        open={deleteFor !== null}
        initialEmail={deleteFor?.email ?? null}
        onClose={() => setDeleteFor(null)}
        onDeleted={() => void fetchPage(query, 0)}
      />

      <Sheet open={picked !== null} onClose={() => setPicked(null)} title={picked ? nameOf(picked) : ''}>
        {picked && (
          <>
            <dl className="facts">
              {!picked.deleted && <><dt>Email</dt><dd>{picked.email}</dd></>}
              <dt>Joined</dt><dd className="num">{formatDay(picked.created_at)}</dd>
              {!picked.deleted && <><dt>Last sign-in</dt><dd className="num">{formatDay(picked.last_sign_in_at)}</dd></>}
              {!picked.deleted && <><dt>AI reads today</dt><dd className="num">{formatInt(picked.ai_today)}</dd></>}
              <dt>Bill photos</dt><dd><span className="num">{picked.photos}</span> ({formatBytes(picked.photo_bytes)})</dd>
              {!picked.deleted && <><dt>Groups</dt><dd className="num">{picked.groups}</dd></>}
            </dl>
            {picked.deleted ? (
              <p className="muted">This account was deleted. Only the name on shared history remains.</p>
            ) : (
              <div className="fieldrow">
                <span id="plan-label">Plan</span>
                <Segmented
                  label="Plan"
                  value={picked.plan === 'friends' ? 'friends' : 'free'}
                  options={[{ id: 'free', label: 'Free' }, { id: 'friends', label: 'Friends' }]}
                  onChange={(p) => void changePlan(picked, p)}
                />
                <p className="muted">Friends get higher daily AI limits. Change takes effect at once.</p>
              </div>
            )}
            <div className="actions">
              {!picked.deleted && picked.email && (
                <button className="btn btn-danger" onClick={() => { setDeleteFor({ email: picked.email }); setPicked(null) }}>Delete account…</button>
              )}
              <button className="btn" onClick={() => setPicked(null)}>Done</button>
            </div>
          </>
        )}
      </Sheet>
    </>
  )
}
