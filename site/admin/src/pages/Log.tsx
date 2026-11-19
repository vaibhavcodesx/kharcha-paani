import { useCallback, useEffect, useMemo, useState } from 'react'
import { actionLabel, changeSummary, targetLabel } from '../audit'
import { ErrorPanel } from '../controls'
import { formatWhen } from '../format'
import { Icon, type IconName } from '../icons'
import { AdminError, callRpc, type AuditRow } from '../rpc'
import { supabase } from '../supabase'
import { Skeleton } from '../ui'
import { groupByDay } from '../viz'

const PAGE = 50

type Load = { kind: 'loading' } | { kind: 'ready' } | { kind: 'failed'; error: AdminError }

const ACTION_ICON: Record<string, IconName> = {
  set_plan: 'people', delete_account: 'trash', photo_cleanup: 'photo', set_cap: 'limits', set_ceiling: 'limits',
  pause_ai: 'pause', resume_ai: 'check', set_app_versions: 'release', set_release_facts: 'release',
}

const time = new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Kolkata' })

export function Log() {
  const [rows, setRows] = useState<AuditRow[]>([])
  const [load, setLoad] = useState<Load>({ kind: 'loading' })
  const [more, setMore] = useState(false)
  const [end, setEnd] = useState(false)

  const fetchPage = useCallback(async (before: number | null) => {
    if (before !== null) setMore(true)
    try {
      const page = await callRpc<AuditRow[]>(supabase, 'admin_list_audit', { p_limit: PAGE, p_before: before })
      setRows((prev) => (before === null ? page : [...prev, ...page]))
      setEnd(page.length < PAGE)
      setLoad({ kind: 'ready' })
    } catch (e) {
      setLoad({ kind: 'failed', error: e instanceof AdminError ? e : new AdminError('other', 'Something went wrong. Try again.') })
    } finally {
      setMore(false)
    }
  }, [])
  useEffect(() => {
    void fetchPage(null)
  }, [fetchPage])

  const groups = useMemo(() => groupByDay(rows), [rows])

  return (
    <>
      <header className="pagehead">
        <h1>Log</h1>
        <p>Every change made on this page: when, what it was before, and what it became. Nothing here can be edited.</p>
      </header>

      {load.kind === 'failed' && <ErrorPanel title="The log could not load" error={load.error} onRetry={() => { setLoad({ kind: 'loading' }); void fetchPage(null) }} />}
      {load.kind === 'loading' && <Skeleton rows={5} />}
      {load.kind === 'ready' && rows.length === 0 && (
        <section className="panel empty">
          <h2>No changes yet</h2>
          <p>Changes you make here are listed with their before and after.</p>
        </section>
      )}
      {load.kind === 'ready' && groups.map((g) => (
        <section className="day" key={g.label} aria-label={g.label}>
          <h2>{g.label}</h2>
          <ol className="timeline">
            {g.rows.map((r) => {
              const who = targetLabel(r)
              const what = changeSummary(r)
              const [from, to] = what.split(' → ')
              const simple = what.includes(' → ') && !what.includes(', ')
              return (
                <li key={r.id} data-action={r.action}>
                  <span className="t-icon"><Icon name={ACTION_ICON[r.action] ?? 'log'} size={18} /></span>
                  <div className="t-body">
                    <strong>{actionLabel(r.action)}</strong>
                    {who && <span className="t-target">{who}</span>}
                    {what && (simple
                      ? <span className="t-change num"><span className="sr">changed from </span><s>{from}</s><Icon name="arrow" size={14} /><span className="sr"> to </span><b>{to}</b></span>
                      : <span className="t-change num">{what}</span>)}
                  </div>
                  <time className="num" dateTime={r.at} title={formatWhen(r.at)}>{time.format(new Date(r.at))}</time>
                </li>
              )
            })}
          </ol>
        </section>
      ))}
      {load.kind === 'ready' && rows.length > 0 && !end && (
        <div className="morewrap">
          <button className="btn" disabled={more} onClick={() => void fetchPage(rows[rows.length - 1].id)}>{more ? 'Loading' : 'Show older'}</button>
        </div>
      )}
    </>
  )
}
