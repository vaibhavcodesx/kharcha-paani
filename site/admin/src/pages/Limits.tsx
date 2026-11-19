import { useCallback, useEffect, useState } from 'react'
import { ConfirmSheet, EditableNumber, ErrorPanel } from '../controls'
import { featureLabel, formatInt, percent, toneFor } from '../format'
import { Icon } from '../icons'
import { useOverview } from '../overview'
import { AdminError, callRpc, type Limits as LimitsData } from '../rpc'
import { supabase } from '../supabase'
import { Skeleton, State, useToast } from '../ui'

type Load = { kind: 'loading' } | { kind: 'ready'; data: LimitsData } | { kind: 'failed'; error: AdminError }

export function Limits() {
  const { refresh } = useOverview()
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

  const changed = async () => {
    await fetchLimits()
    void refresh()
  }
  const setCap = (plan: 'free' | 'friends', feature: string) => async (n: number) => {
    await callRpc<void>(supabase, 'admin_set_cap', { p_plan: plan, p_feature: feature, p_per_day: n })
    await changed()
  }
  const setCeiling = (feature: string) => async (n: number) => {
    await callRpc<void>(supabase, 'admin_set_ceiling', { p_feature: feature, p_per_day: n })
    await changed()
  }
  const ceilingOf = (feature: string) => d.ceilings.find((c) => c.feature === feature)

  return (
    <>
      <header className="pagehead">
        <h1>Limits</h1>
        <p>How many AI reads each person can use a day, and how many the whole project can. Every change applies at once and is written to the Log.</p>
      </header>

      <section className="master-switch" data-paused={d.paused} aria-labelledby="h-master">
        <span className="switch-icon"><Icon name={d.paused ? 'pause' : 'check'} size={28} filled /></span>
        <div className="switch-text">
          <h2 id="h-master">{d.paused ? 'AI is paused for everyone' : 'AI is on'}</h2>
          <p>{d.paused ? 'Phones show the daily-limit message. Your ceilings are saved and come back exactly when you resume.' : 'Pausing stops every AI read for every person until you resume. Use it if the free quota is about to run out.'}</p>
        </div>
        {d.paused
          ? <button className="btn btn-primary" onClick={() => setResuming(true)}><Icon name="check" size={18} />Resume AI</button>
          : <button className="btn btn-danger" onClick={() => setPausing(true)}><Icon name="pause" size={18} />Pause AI</button>}
      </section>

      <section className="panel table-panel" aria-labelledby="h-table">
        <h2 id="h-table">Daily limits by kind</h2>
        {d.caps.length === 0 ? (
          <p className="muted">No daily limit rules configured yet.</p>
        ) : (
          <div className="limits" role="table" aria-label="Daily limits by kind">
            <div className="limits-head" role="row">
              <span role="columnheader">Kind</span>
              <span role="columnheader">Free, per person</span>
              <span role="columnheader">Friends, per person</span>
              <span role="columnheader">Whole project</span>
            </div>
            {d.caps.map((c) => {
              const ceil = ceilingOf(c.feature)
              const tone = toneFor(ceil?.used_today ?? 0, ceil?.per_day ?? null, d.paused)
              return (
                <div className="limits-row" role="row" key={c.feature}>
                  <span role="cell" className="kind">{featureLabel(c.feature)}</span>
                  <span role="cell" data-label="Free, per person">
                    <EditableNumber label={`${featureLabel(c.feature)}, free plan`} value={c.free} max={10000} onSave={setCap('free', c.feature)} />
                  </span>
                  <span role="cell" data-label="Friends, per person">
                    <EditableNumber label={`${featureLabel(c.feature)}, friends plan`} value={c.friends} max={10000} onSave={setCap('friends', c.feature)} />
                  </span>
                  <span role="cell" data-label="Whole project" className="ceil">
                    <EditableNumber label={`${featureLabel(c.feature)} ceiling`} value={ceil?.per_day ?? null} max={100000} nonePhrase="no ceiling" disabled={d.paused} onSave={setCeiling(c.feature)} />
                    {ceil?.per_day != null && (
                      <>
                        <span className="gauge-bar" data-tone={tone} role="meter" aria-label={`${featureLabel(c.feature)} used today`} aria-valuemin={0} aria-valuemax={ceil.per_day} aria-valuenow={Math.min(ceil.used_today, ceil.per_day)}>
                          <span style={{ ['--w' as string]: `${d.paused ? 100 : percent(ceil.used_today, ceil.per_day)}%` }} />
                        </span>
                        <span className="ceil-note"><span className="num">{formatInt(ceil.used_today)}</span> used today <State tone={tone} /></span>
                      </>
                    )}
                  </span>
                </div>
              )
            })}
          </div>
        )}
        {d.paused && <p className="muted">Ceilings are held at zero while AI is paused. Resume to change them.</p>}
      </section>

      <ConfirmSheet
        open={pausing}
        onClose={() => setPausing(false)}
        title="Pause AI for everyone?"
        expected="PAUSE"
        confirmLabel="Pause AI"
        danger
        onConfirm={async () => {
          await callRpc(supabase, 'admin_pause_ai', { p_reason: null })
          await changed()
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
          await changed()
          toast.say('AI is back on, with your saved ceilings. It is in the Log.')
        }}
      >
        <p>AI reads work again for everyone, with the ceilings you had before you paused.</p>
      </ConfirmSheet>
    </>
  )
}
