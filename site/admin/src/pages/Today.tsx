import { useCallback, useEffect, useState } from 'react'
import { FREE_DATABASE_BYTES, FREE_STORAGE_BYTES } from '../config'
import { featureLabel, formatBytes, formatInt, peopleSentence, toneFor } from '../format'
import { Icon } from '../icons'
import { AdminError, callRpc, type Overview } from '../rpc'
import type { TabId } from '../router'
import { supabase } from '../supabase'
import { Meter, Skeleton, State } from '../ui'

type Load = { kind: 'loading' } | { kind: 'ready'; data: Overview } | { kind: 'failed'; error: AdminError }

export function Today({ go }: { go: (tab: TabId) => void }) {
  const [load, setLoad] = useState<Load>({ kind: 'loading' })

  const fetchOverview = useCallback(async () => {
    setLoad({ kind: 'loading' })
    try {
      setLoad({ kind: 'ready', data: await callRpc<Overview>(supabase, 'admin_overview') })
    } catch (e) {
      setLoad({ kind: 'failed', error: e instanceof AdminError ? e : new AdminError('other', 'Something went wrong. Try again.') })
    }
  }, [])

  useEffect(() => {
    void fetchOverview()
  }, [fetchOverview])

  if (load.kind === 'failed') {
    return (
      <section className="panel" role="alert">
        <h2>Today could not load</h2>
        <p>{load.error.message}</p>
        <button className="btn btn-primary" onClick={() => void fetchOverview()}>Try again</button>
      </section>
    )
  }

  if (load.kind === 'loading') {
    return (
      <>
        <header className="pagehead">
          <h1>Today</h1>
        </header>
        <div className="columns">
          <div className="section"><h2>AI reads today</h2><Skeleton rows={4} /></div>
          <div className="section"><h2>Space</h2><Skeleton rows={3} /></div>
        </div>
      </>
    )
  }

  const o = load.data
  const leftover = o.storage.leftover_objects ?? 0
  const storageTone = toneFor(o.storage.bytes, FREE_STORAGE_BYTES)
  const dbTone = toneFor(o.db.bytes, FREE_DATABASE_BYTES)

  return (
    <>
      <header className="pagehead">
        <h1>Today</h1>
        <p>
          {peopleSentence(o.people).map((part, i) =>
            part.num ? <span className="num" key={i}>{part.text}</span> : <span key={i}>{part.text}</span>,
          )}
        </p>
      </header>

      {o.ai_paused && (
        <div className="banner" role="status">
          <Icon name="pause" />
          <div>
            <strong>AI is paused for everyone.</strong>
            Phones show the daily-limit message until you resume it in Limits.
          </div>
        </div>
      )}

      <div className="columns">
        <section className="section" aria-labelledby="h-ai">
          <h2 id="h-ai">AI reads today</h2>
          {o.ai_today.map((a) => {
            const tone = toneFor(a.used, a.ceiling, o.ai_paused)
            return (
              <div className="row" key={a.feature}>
                <span className="label">{featureLabel(a.feature)}</span>
                <span className="value">
                  {formatInt(a.used)}
                  {a.ceiling !== null && <span className="of"> of {formatInt(a.ceiling)}</span>}
                </span>
                {a.ceiling !== null && <Meter used={a.used} limit={a.ceiling} tone={tone} />}
                <State tone={tone} />
              </div>
            )
          })}
        </section>

        <div className="section-stack" style={{ display: 'grid', gap: 'var(--s-8)' }}>
          <section className="section" aria-labelledby="h-space">
            <h2 id="h-space">Space on the free plan</h2>
            <div className="row">
              <span className="label">Bill photos</span>
              <span className="value">
                {formatBytes(o.storage.bytes)}<span className="of"> of {formatBytes(FREE_STORAGE_BYTES)}</span>
              </span>
              <Meter used={o.storage.bytes} limit={FREE_STORAGE_BYTES} tone={storageTone} />
              <State tone={storageTone} />
              <span className="note"><span className="num">{formatInt(o.storage.objects)}</span> files stored.</span>
            </div>
            <div className="row">
              <span className="label">Database</span>
              <span className="value">
                {formatBytes(o.db.bytes)}<span className="of"> of {formatBytes(FREE_DATABASE_BYTES)}</span>
              </span>
              <Meter used={o.db.bytes} limit={FREE_DATABASE_BYTES} tone={dbTone} />
              <State tone={dbTone} />
            </div>
          </section>

          <section className="section" aria-labelledby="h-next">
            <h2 id="h-next">Needs a look</h2>
            {leftover > 0 && (
              <button className="linkrow" onClick={() => go('people')}>
                <span>
                  <strong><span className="num">{formatInt(leftover)}</span> {leftover === 1 ? 'photo' : 'photos'} left behind by deleted accounts</strong>
                  <small>Clean them up from People.</small>
                </span>
                <Icon name="arrow" />
              </button>
            )}
            <button className="linkrow" onClick={() => go('release')}>
              <span>
                <strong>App version: minimum <span className="num">{o.versions.min}</span>, recommended <span className="num">{o.versions.recommended}</span></strong>
                <small>
                  {o.versions.update_screen === null
                    ? 'Record which version has the update screen before raising the minimum.'
                    : 'Phones below the minimum stop syncing until they update.'}
                </small>
              </span>
              <Icon name="arrow" />
            </button>
          </section>
        </div>
      </div>
    </>
  )
}
