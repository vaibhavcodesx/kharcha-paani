import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ErrorPanel, Segmented } from '../controls'
import { formatBytes, formatDay, formatInt } from '../format'
import { useMediaQuery } from '../hooks'
import { Icon } from '../icons'
import { useOverview } from '../overview'
import { AdminError, callRpc, type UserRow } from '../rpc'
import { supabase } from '../supabase'
import { Sheet, Skeleton, useToast } from '../ui'
import { avatarHue, initials } from '../viz'
import { DeleteAccountSheet } from './DeleteAccount'

const PAGE = 50

type Load = { kind: 'loading' } | { kind: 'ready' } | { kind: 'failed'; error: AdminError }
type Filter = 'all' | 'friends' | 'free' | 'deleted'

function nameOf(u: UserRow): string {
  return u.deleted ? 'Former member' : u.display_name?.trim() || u.email || 'Unnamed'
}

function Avatar({ u }: { u: UserRow }) {
  return (
    <span className="avatar" data-gone={u.deleted} style={{ ['--hue' as string]: avatarHue(u.user_id) }} aria-hidden="true">
      {u.deleted ? '' : initials(u.display_name, u.email)}
    </span>
  )
}

function Detail({ u, onPlan, onDelete }: { u: UserRow; onPlan: (p: 'free' | 'friends') => void; onDelete: () => void }) {
  return (
    <div className="detail">
      <div className="detail-head">
        <Avatar u={u} />
        <div>
          <h2>{nameOf(u)}</h2>
          <p className="muted">{u.deleted ? 'Account deleted' : u.email}</p>
        </div>
      </div>

      {!u.deleted && (
        <div className="fieldrow">
          <span className="fieldlabel">Plan</span>
          <Segmented
            label="Plan"
            value={u.plan === 'friends' ? 'friends' : 'free'}
            options={[{ id: 'free', label: 'Free' }, { id: 'friends', label: 'Friends' }]}
            onChange={onPlan}
          />
          <p className="muted">Friends get higher daily AI limits. It takes effect at once.</p>
        </div>
      )}

      <dl className="facts">
        <dt>Joined</dt><dd className="num">{formatDay(u.created_at)}</dd>
        {!u.deleted && <><dt>Last sign-in</dt><dd className="num">{formatDay(u.last_sign_in_at)}</dd></>}
        {!u.deleted && <><dt>AI reads today</dt><dd className="num">{formatInt(u.ai_today)}</dd></>}
        {!u.deleted && <><dt>Groups</dt><dd className="num">{u.groups}</dd></>}
        <dt>Bill photos</dt><dd><span className="num">{u.photos}</span> <span className="of">({formatBytes(u.photo_bytes)})</span></dd>
      </dl>

      {u.deleted ? (
        <p className="muted">Only the name on shared history remains.</p>
      ) : u.email ? (
        <button className="btn btn-danger" onClick={onDelete}><Icon name="trash" size={18} />Delete account…</button>
      ) : null}
    </div>
  )
}

export function People() {
  const { refresh } = useOverview()
  const wide = useMediaQuery('(min-width: 1024px)')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [rows, setRows] = useState<UserRow[]>([])
  const [total, setTotal] = useState(0)
  const [load, setLoad] = useState<Load>({ kind: 'loading' })
  const [more, setMore] = useState(false)
  const [pickedId, setPickedId] = useState<string | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)
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

  const shown = useMemo(() => rows.filter((u) => {
    if (filter === 'all') return true
    if (filter === 'deleted') return u.deleted
    if (u.deleted) return false
    return filter === 'friends' ? u.plan === 'friends' : u.plan !== 'friends'
  }), [rows, filter])

  const picked = rows.find((u) => u.user_id === pickedId) ?? null

  // On a wide screen the first person is shown beside the list without a click.
  useEffect(() => {
    if (wide && !picked && shown.length > 0) setPickedId(shown[0].user_id)
  }, [wide, picked, shown])

  const choose = (u: UserRow) => {
    setPickedId(u.user_id)
    if (!wide) setSheetOpen(true)
  }

  const changePlan = async (u: UserRow, plan: 'free' | 'friends') => {
    try {
      await callRpc<string>(supabase, 'admin_set_plan', { p_user: u.user_id, p_plan: plan })
      setRows((prev) => prev.map((r) => (r.user_id === u.user_id ? { ...r, plan } : r)))
      toast.say(`${nameOf(u)} is now on the ${plan === 'friends' ? 'Friends' : 'Free'} plan. It is in the Log.`)
      void refresh()
    } catch (e) {
      toast.say((e as AdminError).message)
    }
  }

  const detail = picked && (
    <Detail
      u={picked}
      onPlan={(p) => void changePlan(picked, p)}
      onDelete={() => { setSheetOpen(false); setDeleteFor({ email: picked.email }) }}
    />
  )

  return (
    <>
      <header className="pagehead">
        <h1>People</h1>
        <p>Everyone with an account. Search by name or email, change a plan, or delete an account.</p>
      </header>

      <div className="toolbar">
        <label className="sr" htmlFor="q">Search people</label>
        <div className="searchfield">
          <Icon name="search" size={20} />
          <input id="q" className="field search" type="search" placeholder="Name or email" value={query} autoComplete="off" onChange={(e) => setQuery(e.target.value)} />
        </div>
        <Segmented<Filter>
          label="Show"
          value={filter}
          options={[{ id: 'all', label: 'All' }, { id: 'friends', label: 'Friends' }, { id: 'free', label: 'Free' }, { id: 'deleted', label: 'Deleted' }]}
          onChange={setFilter}
        />
        <button className="linkbtn" onClick={() => setDeleteFor({ email: null })}>Delete by email</button>
      </div>

      {load.kind === 'failed' && <ErrorPanel title="People could not load" error={load.error} onRetry={() => void fetchPage(query, 0)} />}
      {load.kind === 'loading' && <Skeleton rows={6} />}
      {load.kind === 'ready' && shown.length === 0 && (
        <section className="panel empty">
          <h2>{query ? `Nobody matches “${query}”` : filter === 'all' ? 'No accounts yet' : 'Nobody in this view'}</h2>
          <p>{query ? 'Try part of a name, or the start of an email.' : filter === 'all' ? 'People appear here once they sign in to the app.' : 'Switch the view above to see everyone.'}</p>
        </section>
      )}

      {load.kind === 'ready' && shown.length > 0 && (
        <div className="masterdetail">
          <div className="master">
            <p className="count num" aria-live="polite">{formatInt(total)} {total === 1 ? 'person' : 'people'}</p>
            <ul className="people" aria-label="People">
              {shown.map((u) => (
                <li key={u.user_id}>
                  <button className="person" data-gone={u.deleted} aria-current={u.user_id === pickedId ? 'true' : undefined} onClick={() => choose(u)}>
                    <Avatar u={u} />
                    <span className="person-main">
                      <strong>{nameOf(u)}</strong>
                      <span className="person-sub">{u.deleted ? 'Account deleted' : u.email}</span>
                    </span>
                    <span className="person-stats num">
                      {u.deleted ? `${u.photos} photos` : <>{formatInt(u.ai_today)} AI · {u.photos} photos</>}
                    </span>
                    <span className="chip" data-plan={u.deleted ? 'gone' : u.plan}>{u.deleted ? 'Deleted' : u.plan === 'friends' ? 'Friends' : 'Free'}</span>
                  </button>
                </li>
              ))}
            </ul>
            {rows.length < total && (
              <div className="morewrap">
                <button className="btn" disabled={more} onClick={() => void fetchPage(query, rows.length)}>{more ? 'Loading' : `Show more (${formatInt(total - rows.length)} left)`}</button>
              </div>
            )}
          </div>
          {wide && <aside className="pane" aria-label="Selected person">{detail}</aside>}
        </div>
      )}

      {!wide && (
        <Sheet open={sheetOpen && picked !== null} onClose={() => setSheetOpen(false)} title={picked ? nameOf(picked) : ''}>
          {detail}
          <div className="actions"><button className="btn" onClick={() => setSheetOpen(false)}>Done</button></div>
        </Sheet>
      )}

      <DeleteAccountSheet
        open={deleteFor !== null}
        initialEmail={deleteFor?.email ?? null}
        onClose={() => setDeleteFor(null)}
        onDeleted={() => { void fetchPage(query, 0); void refresh() }}
      />
    </>
  )
}
