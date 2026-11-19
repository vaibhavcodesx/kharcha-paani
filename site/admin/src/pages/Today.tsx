import { attentionItems, type Severity } from '../attention'
import { FREE_DATABASE_BYTES, FREE_STORAGE_BYTES } from '../config'
import { ErrorPanel } from '../controls'
import { featureLabel, formatBytes, formatInt, percent, toneFor, type Tone } from '../format'
import { Icon, type IconName } from '../icons'
import { useOverview } from '../overview'
import type { TabId } from '../router'
import { Skeleton, State } from '../ui'
import { planSegments } from '../viz'

const SEVERITY_ICON: Record<Severity, IconName> = { stop: 'warn', watch: 'warn', todo: 'arrow' }

/** A capacity bar: what is used, with an optional second segment (the part that could be reclaimed). */
function Capacity({ label, used, limit, reclaim = 0, tone }: { label: string; used: number; limit: number; reclaim?: number; tone: Tone }) {
  const live = Math.max(used - reclaim, 0)
  return (
    <div className="capacity" data-tone={tone}>
      <div className="capacity-head">
        <span>{label}</span>
        <span className="num">{formatBytes(used)}<span className="of"> of {formatBytes(limit)}</span></span>
      </div>
      <div className="capacity-bar" role="meter" aria-label={`${label}: ${formatBytes(used)} of ${formatBytes(limit)}`} aria-valuemin={0} aria-valuemax={limit} aria-valuenow={Math.min(used, limit)}>
        <span className="live" style={{ ['--w' as string]: `${percent(live, limit)}%` }} />
        {reclaim > 0 && <span className="reclaim" style={{ ['--w' as string]: `${percent(reclaim, limit)}%` }} />}
      </div>
      <State tone={tone} />
    </div>
  )
}

export function Today({ go }: { go: (tab: TabId) => void }) {
  const { state, refresh } = useOverview()

  if (state.kind === 'failed') {
    return <ErrorPanel title="Today could not load" error={state.error} onRetry={() => void refresh()} />
  }
  if (state.kind === 'loading') {
    return (
      <>
        <header className="pagehead"><h1>Today</h1></header>
        <div className="bento">
          <section className="panel p-people"><Skeleton rows={3} /></section>
          <section className="panel p-ai"><Skeleton rows={4} /></section>
          <section className="panel p-space"><Skeleton rows={2} /></section>
        </div>
      </>
    )
  }

  const o = state.data
  const segments = planSegments(o.plans)
  const attention = attentionItems(o)
  const storageTone = toneFor(o.storage.bytes, FREE_STORAGE_BYTES)
  const dbTone = toneFor(o.db.bytes, FREE_DATABASE_BYTES)
  const reclaim = o.storage.leftover_bytes ?? 0

  return (
    <>
      <header className="pagehead">
        <h1>Today</h1>
        <p>{attention.length === 0 ? 'Nothing needs you right now.' : `${attention.length} ${attention.length === 1 ? 'thing needs' : 'things need'} a look.`}</p>
      </header>

      <div className="bento">
        <section className="panel p-people" aria-labelledby="h-people">
          <h2 id="h-people">People</h2>
          <div className="hero">
            <span className="hero-num num">{formatInt(o.people.total)}</span>
            <span className="hero-note">{o.people.total === 1 ? 'account' : 'accounts'}<br />
              <span className="num">{formatInt(o.people.active_7d)}</span> active this week
              {o.people.deleted > 0 && <>, <span className="num">{formatInt(o.people.deleted)}</span> deleted</>}
            </span>
          </div>
          {segments.length > 0 && (
            <>
              <div className="split" role="img" aria-label={segments.map((s) => `${s.count} ${s.label}`).join(', ')}>
                {segments.map((s) => <span key={s.id} data-plan={s.id} style={{ ['--w' as string]: `${s.pct}%` }} />)}
              </div>
              <ul className="legend">
                {segments.map((s) => <li key={s.id} data-plan={s.id}><span className="num">{formatInt(s.count)}</span> {s.label}</li>)}
              </ul>
            </>
          )}
        </section>

        <section className="panel p-ai" aria-labelledby="h-ai">
          <div className="panel-head">
            <h2 id="h-ai">AI reads today</h2>
            <button className="linkbtn" onClick={() => go('limits')}>Limits</button>
          </div>
          {o.ai_today.length === 0 ? (
            <p className="muted">No AI reads recorded today.</p>
          ) : (
            o.ai_today.map((a) => {
              const tone = toneFor(a.used, a.ceiling, o.ai_paused)
              return (
                <div className="gauge" key={a.feature} data-tone={tone}>
                  <div className="gauge-head">
                    <span className="label">{featureLabel(a.feature)}</span>
                    <span className="num">{formatInt(a.used)}{a.ceiling !== null && <span className="of"> of {formatInt(a.ceiling)}</span>}</span>
                  </div>
                  {a.ceiling !== null && (
                    <div className="gauge-bar" role="meter" aria-label={`${featureLabel(a.feature)} today`} aria-valuemin={0} aria-valuemax={a.ceiling} aria-valuenow={Math.min(a.used, a.ceiling)}>
                      <span style={{ ['--w' as string]: `${o.ai_paused ? 100 : percent(a.used, a.ceiling)}%` }} />
                    </div>
                  )}
                  <State tone={tone} />
                </div>
              )
            })
          )}
        </section>

        <section className="panel p-space" aria-labelledby="h-space">
          <div className="panel-head">
            <h2 id="h-space">Space on the free plan</h2>
          </div>
          <Capacity label="Bill photos" used={o.storage.bytes} limit={FREE_STORAGE_BYTES} reclaim={reclaim} tone={storageTone} />
          {reclaim > 0 && <p className="key"><span className="swatch" data-kind="reclaim" /> {formatBytes(reclaim)} is left behind by deleted accounts and can be cleaned up.</p>}
          <Capacity label="Database" used={o.db.bytes} limit={FREE_DATABASE_BYTES} tone={dbTone} />
        </section>

        <section className="panel p-attention" aria-labelledby="h-att">
          <h2 id="h-att">Needs a look</h2>
          {attention.length === 0 ? (
            <p className="allclear"><Icon name="check" size={22} /> Everything is within its limits, and nothing is waiting on you.</p>
          ) : (
            <ul className="attention">
              {attention.map((a) => (
                <li key={a.id} data-severity={a.severity}>
                  <button onClick={() => go(a.go)}>
                    <span className="a-icon"><Icon name={SEVERITY_ICON[a.severity]} size={20} /></span>
                    <span className="a-text"><strong>{a.title}</strong><small>{a.detail}</small></span>
                    <Icon name="arrow" size={18} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
