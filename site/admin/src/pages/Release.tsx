import { useState } from 'react'
import { ConfirmSheet, EditableNumber, ErrorPanel } from '../controls'
import { Icon } from '../icons'
import { useOverview } from '../overview'
import { checkFacts, planVersionChange, proposeVersions } from '../release'
import { AdminError, callRpc } from '../rpc'
import { supabase } from '../supabase'
import { Skeleton, useToast } from '../ui'
import { trackMarks } from '../viz'

const MAX = 1_000_000

export function Release() {
  const { state, refresh } = useOverview()
  const [raise, setRaise] = useState<{ min: number; recommended: number; sentence: string } | null>(null)
  const toast = useToast()

  if (state.kind === 'failed') return <ErrorPanel title="Release could not load" error={state.error} onRetry={() => void refresh()} />
  if (state.kind === 'loading') {
    return (
      <>
        <header className="pagehead"><h1>Release</h1></header>
        <Skeleton rows={4} />
      </>
    )
  }
  const f = state.data.versions
  const { marks } = trackMarks(f)

  const saveVersions = async (min: number, recommended: number) => {
    await callRpc(supabase, 'admin_set_app_versions', { p_min: min, p_recommended: recommended })
    await refresh()
  }

  // A lowering is saved at once. A raise states who is affected and asks for the number to be typed.
  const change = (which: 'min' | 'recommended') => async (n: number): Promise<void | 'deferred'> => {
    const { min, recommended } = proposeVersions(f, which, n)
    const plan = planVersionChange(f, min, recommended)
    if (!plan.ok) throw new AdminError('invalid', plan.reason)
    if (plan.raises) {
      setRaise({ min, recommended, sentence: plan.sentence })
      return 'deferred' as const
    }
    await saveVersions(min, recommended)
  }

  const saveFacts = (which: 'update' | 'latest') => async (n: number) => {
    const update = which === 'update' ? n : f.update_screen
    const latest = which === 'latest' ? n : f.latest_published
    const problem = checkFacts({ update_screen: f.update_screen, latest_published: f.latest_published }, update, latest)
    if (problem) throw new AdminError('invalid', problem)
    await callRpc(supabase, 'admin_set_release_facts', { p_update_screen: update, p_latest_published: latest })
    await refresh()
  }

  return (
    <>
      <header className="pagehead">
        <h1>Release</h1>
        <p>
          {f.min === 0
            ? 'No minimum is set, so every phone keeps syncing.'
            : <>Phones below version <span className="num">{f.min}</span> stop syncing until they update. They can still open and export their data.</>}
        </p>
      </header>

      <section className="panel track-panel" aria-labelledby="h-track">
        <h2 id="h-track">Where the versions sit</h2>
        {marks.every((m) => m.value === 0) ? (
          <p className="muted">Nothing is recorded yet. Add the latest version on Play below and the line fills in.</p>
        ) : (
          <div className="track" role="img" aria-label={marks.map((m) => `${m.label} ${m.value}`).join(', ')}>
            <span className="track-line" />
            {f.min > 0 && <span className="track-danger" style={{ ['--w' as string]: `${marks.find((m) => m.id === 'min')?.pct ?? 0}%` }} />}
            {marks.map((m, i) => (
              <span key={m.id} className="mark" data-mark={m.id} data-row={i % 2} style={{ ['--x' as string]: `${m.pct}%` }}>
                <i />
                <span className="mark-label"><span>{m.label}</span><b className="num">{m.value}</b></span>
              </span>
            ))}
          </div>
        )}
      </section>

      <div className="columns">
        <section className="section" aria-labelledby="h-ver">
          <h2 id="h-ver">What phones are asked to run</h2>
          <div className="row">
            <span className="label">Minimum version</span>
            <span className="value"><EditableNumber label="minimum version" value={f.min} max={MAX} onSave={change('min')} /></span>
            <span className="note">Below this, sync stops and the update screen shows.</span>
          </div>
          <div className="row">
            <span className="label">Recommended version</span>
            <span className="value"><EditableNumber label="recommended version" value={f.recommended} max={MAX} onSave={change('recommended')} /></span>
            <span className="note">Below this, people see a gentle prompt they can dismiss for the day.</span>
          </div>
        </section>

        <section className="section" aria-labelledby="h-facts">
          <h2 id="h-facts">What you have shipped</h2>
          <div className="row">
            <span className="label">First version with the update screen</span>
            <span className="value"><EditableNumber label="update screen version" value={f.update_screen} max={MAX} nonePhrase="not set" onSave={saveFacts('update')} /></span>
            <span className="note">The minimum can only be raised once this is set, so people below it are told why sync stopped.</span>
          </div>
          <div className="row">
            <span className="label">Latest version on Play</span>
            <span className="value"><EditableNumber label="latest version on Play" value={f.latest_published} max={MAX} nonePhrase="not set" onSave={saveFacts('latest')} /></span>
            <span className="note">Neither number above can be higher than this. These two only go up.</span>
          </div>
        </section>
      </div>

      <ConfirmSheet
        open={raise !== null}
        onClose={() => setRaise(null)}
        title="Raise the minimum version?"
        expected={raise ? String(raise.min) : undefined}
        confirmLabel="Raise it"
        danger
        onConfirm={async () => {
          if (!raise) return
          await saveVersions(raise.min, raise.recommended)
          toast.say(`The minimum is now ${raise.min}. It is in the Log.`)
        }}
      >
        <p className="warn"><Icon name="warn" size={20} /><span>{raise?.sentence}</span></p>
        <p className="muted">Lowering it again is always allowed if something goes wrong.</p>
      </ConfirmSheet>
    </>
  )
}
