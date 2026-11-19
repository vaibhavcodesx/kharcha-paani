import { useCallback, useEffect, useState } from 'react'
import { ConfirmSheet, EditableNumber, ErrorPanel } from '../controls'
import { featureLabel, formatInt, toneFor } from '../format'
import { Icon } from '../icons'
import { AdminError, callRpc, type Limits as LimitsData } from '../rpc'
import { supabase } from '../supabase'
import { Meter, Skeleton, State, useToast } from '../ui'

type Load = { kind: 'loading' } | { kind: 'ready'; data: LimitsData } | { kind: 'failed'; error: AdminError }

export function Limits() {
  const [load, setLoad] = useState<Load>({ kind: 'loading' })
  const [pausing, setPausing] = useState(false)
  const [resuming, setResuming] = useState(false)
  const toast = useToast()

  const fetchLimits = useCallback(async () => {
    try {
      setLoad({ kind: 'ready', data: await callRpc<LimitsData>(supabase, 'admin_get_limits') })
    } catch (e) {
      setLoad({ kind: 'failed', error: e instanceof AdminError ? e : new AdminError('other', 'Something went wrong. Try again.') })
    }
  }, [])
  useEffect(() => {
    void fetchLimits()
  }, [fetchLimits])

  if (load.kind === 'failed') return <ErrorPanel title="Limits could not load" error={load.error} onRetry={() => { setLoad({ kind: 'loading' }); void fetchLimits() }} />
  if (load.kind === 'loading') {
    return (
      <>
        <header className="pagehead"><h1>Limits</h1></header>
        <Skeleton rows={5} />
      </>
    )
  }
  const d = load.data

  const setCap = (plan: 'free' | 'friends', feature: string) => async (n: number) => {
    await callRpc<void>(supabase, 'admin_set_cap', { p_plan: plan, p_feature: feature, p_per_day: n })
    await fetchLimits()
  }
  const setCeiling = (feature: string) => async (n: number) => {
    await callRpc<void>(supabase, 'admin_set_ceiling', { p_feature: feature, p_per_day: n })
    await fetchLimits()
  }

  return (
    <>
      <header className="pagehead">
        <h1>Limits</h1>
        <p>How many AI reads a person can use a day, and how many the whole project can. Changes apply at once and are written to the Log.</p>
      </header>

      <section className="master" data-paused={d.paused} aria-labelledby="h-master">
        <div>
          <h2 id="h-master">{d.paused ? 'AI is paused for everyone' : 'AI is on'}</h2>
          <p>{d.paused ? 'Phones show the daily-limit message. Your ceilings are saved and come back exactly when you resume.' : 'Pausing stops every AI read for everyone until you resume. Use it if the free quota is about to run out.'}</p>
        </div>
        {d.paused
          ? <button className="btn btn-primary" onClick={() => setResuming(true)}><Icon name="check" />Resume AI</button>
          : <button className="btn btn-danger" onClick={() => setPausing(true)}><Icon name="pause" />Pause AI</button>}
      </section>

      <div className="columns">
        <section className="section" aria-labelledby="h-caps">
          <h2 id="h-caps">Per person, per day</h2>
          {d.caps.map((c) => (
            <div className="row capsrow" key={c.feature}>
              <span className="label">{featureLabel(c.feature)}</span>
              <span className="pair"><span className="pairlabel">Free</span><EditableNumber label={`${featureLabel(c.feature)}, free plan`} value={c.free} max={10000} onSave={setCap('free', c.feature)} /></span>
              <span className="pair"><span className="pairlabel">Friends</span><EditableNumber label={`${featureLabel(c.feature)}, friends plan`} value={c.friends} max={10000} onSave={setCap('friends', c.feature)} /></span>
            </div>
          ))}
        </section>

        <section className="section" aria-labelledby="h-ceil">
          <h2 id="h-ceil">Whole project, per day</h2>
          {d.paused && <p className="muted">Ceilings are held at zero while AI is paused. Resume to change them.</p>}
          {d.ceilings.map((c) => {
            const tone = toneFor(c.used_today, c.per_day, d.paused)
            return (
              <div className="row" key={c.feature}>
                <span className="label">{featureLabel(c.feature)}</span>
                <span className="value">
                  <span className="of">used </span>{formatInt(c.used_today)}<span className="of"> of </span>
                  <EditableNumber label={`${featureLabel(c.feature)} ceiling`} value={c.per_day} max={100000} nonePhrase="no ceiling" disabled={d.paused} onSave={setCeiling(c.feature)} />
                </span>
                {c.per_day !== null && <Meter used={c.used_today} limit={c.per_day} tone={tone} />}
                <State tone={tone} />
              </div>
            )
          })}
        </section>
      </div>

      <ConfirmSheet
        open={pausing}
        onClose={() => setPausing(false)}
        title="Pause AI for everyone?"
        expected="PAUSE"
        confirmLabel="Pause AI"
        danger
        onConfirm={async () => {
          await callRpc(supabase, 'admin_pause_ai', { p_reason: null })
          await fetchLimits()
          toast.say('AI is paused. It is in the Log.')
        }}
      >
        <p>Every AI read for every person stops until you resume. Typed entries, bill photos, statements and Insights all show the daily-limit message. Your current ceilings are saved.</p>
      </ConfirmSheet>

      <ConfirmSheet
        open={resuming}
        onClose={() => setResuming(false)}
        title="Resume AI?"
        confirmLabel="Resume AI"
        onConfirm={async () => {
          await callRpc(supabase, 'admin_resume_ai')
          await fetchLimits()
          toast.say('AI is back on, with your saved ceilings. It is in the Log.')
        }}
      >
        <p>AI reads work again for everyone, with the ceilings you had before you paused.</p>
      </ConfirmSheet>
    </>
  )
}
