import { useCallback, useEffect, useState } from 'react'
import { ConfirmSheet, EditableNumber, ErrorPanel } from '../controls'
import { Icon } from '../icons'
import { AdminError, callRpc, type Overview } from '../rpc'
import { checkFacts, planVersionChange, proposeVersions, type Facts } from '../release'
import { supabase } from '../supabase'
import { Skeleton, useToast } from '../ui'

type Load = { kind: 'loading' } | { kind: 'ready'; facts: Facts } | { kind: 'failed'; error: AdminError }

const MAX = 1_000_000

export function Release() {
  const [load, setLoad] = useState<Load>({ kind: 'loading' })
  const [raise, setRaise] = useState<{ min: number; recommended: number; sentence: string } | null>(null)
  const toast = useToast()

  const fetchFacts = useCallback(async () => {
    try {
      const o = await callRpc<Overview>(supabase, 'admin_overview')
      setLoad({ kind: 'ready', facts: o.versions })
    } catch (e) {
      setLoad({ kind: 'failed', error: e instanceof AdminError ? e : new AdminError('other', 'Something went wrong. Try again.') })
    }
  }, [])
  useEffect(() => {
    void fetchFacts()
  }, [fetchFacts])

  if (load.kind === 'failed') return <ErrorPanel title="Release could not load" error={load.error} onRetry={() => { setLoad({ kind: 'loading' }); void fetchFacts() }} />
  if (load.kind === 'loading') {
    return (
      <>
        <header className="pagehead"><h1>Release</h1></header>
        <Skeleton rows={4} />
      </>
    )
  }
  const f = load.facts

  const saveVersions = async (min: number, recommended: number) => {
    await callRpc(supabase, 'admin_set_app_versions', { p_min: min, p_recommended: recommended })
    await fetchFacts()
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
    await fetchFacts()
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
        <p className="warn"><Icon name="warn" />{raise?.sentence}</p>
        <p className="muted">Lowering it again is always allowed if something goes wrong.</p>
      </ConfirmSheet>
    </>
  )
}
