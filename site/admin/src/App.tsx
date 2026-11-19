import { useCallback, useEffect, useState } from 'react'
import { AuthProvider, useAuth } from './auth'
import { CommandPalette } from './CommandPalette'
import type { CommandId } from './commands'
import { FREE_STORAGE_BYTES } from './config'
import { formatBytes } from './format'
import { Icon, type IconName } from './icons'
import { OverviewProvider, useOverview } from './overview'
import { Limits } from './pages/Limits'
import { Log } from './pages/Log'
import { People } from './pages/People'
import { Release } from './pages/Release'
import { Today } from './pages/Today'
import { TABS, useTab, type TabId } from './router'
import { ToastProvider } from './ui'

type Theme = 'system' | 'dark' | 'light'

function readTheme(): Theme {
  try {
    const t = window.localStorage.getItem('kp-admin-theme')
    return t === 'dark' || t === 'light' ? t : 'system'
  } catch {
    return 'system'
  }
}

function useTheme(): [boolean, () => void] {
  const [theme, setTheme] = useState<Theme>(readTheme)
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches)
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const on = () => setSystemDark(mq.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', theme)
  }, [theme])
  const dark = theme === 'dark' || (theme === 'system' && systemDark)
  const toggle = () => {
    const next: Theme = dark ? 'light' : 'dark'
    setTheme(next)
    try {
      window.localStorage.setItem('kp-admin-theme', next)
    } catch {
      /* private window: the choice just lasts for this visit */
    }
  }
  return [dark, toggle]
}

function Gate({ title, body, action, onAction }: { title: string; body: string; action?: string; onAction?: () => void }) {
  return (
    <main className="center">
      <section className="panel gate">
        <p className="wordmark">Kharcha Paani</p>
        <h2>{title}</h2>
        <p>{body}</p>
        {action && <button className="btn btn-primary" onClick={onAction}>{action}</button>}
      </section>
    </main>
  )
}

const TAB_ICON: Record<TabId, IconName> = { today: 'today', people: 'people', limits: 'limits', release: 'release', log: 'log' }

function Tabs({ tab, go, variant }: { tab: TabId; go: (t: TabId) => void; variant: 'rail' | 'dock' }) {
  return (
    <nav className={variant === 'rail' ? 'tabs-rail' : 'dock'} role="tablist" aria-label="Sections" aria-orientation={variant === 'rail' ? 'vertical' : 'horizontal'}>
      {TABS.map((t) => (
        <button
          key={t.id}
          role="tab"
          id={variant === 'rail' ? `tab-${t.id}` : undefined}
          aria-selected={tab === t.id}
          aria-controls="view"
          className="tab"
          onClick={() => go(t.id)}
          onKeyDown={(e) => {
            const i = TABS.findIndex((x) => x.id === tab)
            const keys = variant === 'rail' ? ['ArrowDown', 'ArrowUp'] : ['ArrowRight', 'ArrowLeft']
            if (e.key === keys[0]) go(TABS[(i + 1) % TABS.length].id)
            if (e.key === keys[1]) go(TABS[(i + TABS.length - 1) % TABS.length].id)
          }}
        >
          <Icon name={TAB_ICON[t.id]} filled={tab === t.id} />
          <span>{t.label}</span>
        </button>
      ))}
    </nav>
  )
}

/** The three things worth knowing at a glance, always on screen on a wide display. */
function StatusBlock({ go }: { go: (t: TabId) => void }) {
  const { state } = useOverview()
  if (state.kind !== 'ready') {
    return (
      <div className="status" aria-hidden="true">
        <div className="skel"><i /></div>
        <div className="skel"><i /></div>
      </div>
    )
  }
  const o = state.data
  const storagePct = Math.round((o.storage.bytes / FREE_STORAGE_BYTES) * 100)
  return (
    <dl className="status" aria-label="Right now">
      <button onClick={() => go('limits')} data-tone={o.ai_paused ? 'stop' : 'ok'}>
        <dt>AI</dt>
        <dd><Icon name={o.ai_paused ? 'pause' : 'check'} size={16} />{o.ai_paused ? 'Paused' : 'On'}</dd>
      </button>
      <button onClick={() => go('release')}>
        <dt>Minimum app</dt>
        <dd className="num">{o.versions.min === 0 ? 'none' : `v${o.versions.min}`}</dd>
      </button>
      <button onClick={() => go('today')} data-tone={storagePct >= 80 ? 'watch' : 'ok'}>
        <dt>Photo space</dt>
        <dd className="num">{storagePct}% <span className="of">of {formatBytes(FREE_STORAGE_BYTES)}</span></dd>
      </button>
    </dl>
  )
}

function Console() {
  const { signOut } = useAuth()
  const { refresh } = useOverview()
  const [tab, go] = useTab()
  const [dark, toggleTheme] = useTheme()
  const [palette, setPalette] = useState(false)

  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPalette((p) => !p)
      }
    }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [])

  const run = useCallback((id: CommandId) => {
    if (id.startsWith('go-')) go(id.slice(3) as TabId)
    else if (id === 'theme') toggleTheme()
    else if (id === 'refresh') void refresh()
    else if (id === 'sign-out') void signOut()
  }, [go, toggleTheme, refresh, signOut])

  const themeLabel = dark ? 'Switch to light theme' : 'Switch to dark theme'
  const title = TABS.find((t) => t.id === tab)?.label ?? 'Today'

  return (
    <div className="console">
      <a href="#view" className="sr-skip">Skip to main content</a>
      <aside className="rail">
        <div className="wordmark">Kharcha Paani<small>Owner console</small></div>
        <Tabs tab={tab} go={go} variant="rail" />
        <StatusBlock go={go} />
        <div className="rail-foot">
          <button className="iconbtn" onClick={() => setPalette(true)} aria-label="Open the command palette"><Icon name="command" size={20} /></button>
          <button className="iconbtn" onClick={toggleTheme} aria-label={themeLabel}><Icon name={dark ? 'sun' : 'moon'} size={20} /></button>
          <button className="iconbtn" onClick={() => void signOut()} aria-label="Sign out"><Icon name="logout" size={20} /></button>
        </div>
      </aside>

      <div className="stage">
        <header className="topbar">
          <div className="wordmark">Kharcha Paani<small>{title}</small></div>
          <button className="iconbtn" onClick={() => setPalette(true)} aria-label="Open the command palette"><Icon name="search" size={20} /></button>
          <button className="iconbtn" onClick={toggleTheme} aria-label={themeLabel}><Icon name={dark ? 'sun' : 'moon'} size={20} /></button>
          <button className="iconbtn" onClick={() => void signOut()} aria-label="Sign out"><Icon name="logout" size={20} /></button>
        </header>
        <main className="main" id="view" role="tabpanel" aria-labelledby={`tab-${tab}`}>
          <div className="view" key={tab}>
            {tab === 'today' && <Today go={go} />}
            {tab === 'people' && <People />}
            {tab === 'limits' && <Limits />}
            {tab === 'release' && <Release />}
            {tab === 'log' && <Log />}
          </div>
        </main>
      </div>

      <Tabs tab={tab} go={go} variant="dock" />
      <CommandPalette open={palette} onClose={() => setPalette(false)} onRun={run} />
    </div>
  )
}

function Shell() {
  const { state, signIn } = useAuth()

  if (state.kind === 'booting' || state.kind === 'checking') {
    return <main className="center" aria-busy="true"><p className="wordmark">Kharcha Paani</p></main>
  }
  if (state.kind === 'signed-out') {
    return <Gate title="Owner only." body="Sign in with the Google account that runs Kharcha Paani." action="Sign in with Google" onAction={() => void signIn()} />
  }
  if (state.kind === 'stranger') {
    return <Gate title="This page is for the owner." body="You have been signed out. If you should have access, sign in with the owner's Google account." action="Try another account" onAction={() => void signIn()} />
  }
  if (state.kind === 'not-installed') {
    return <Gate title="The server part is not installed yet." body="The admin functions have not been added to the database. Follow the admin runbook, then reload this page." action="Reload" onAction={() => window.location.reload()} />
  }
  if (state.kind === 'error') {
    return <Gate title="Something went wrong." body={state.message} action="Try again" onAction={() => window.location.reload()} />
  }
  return (
    <OverviewProvider>
      <Console />
    </OverviewProvider>
  )
}

export default function App() {
  // Framed by another site: show nothing (the CSP also forbids it).
  if (window.top !== window.self) return null
  return (
    <AuthProvider>
      <ToastProvider>
        <Shell />
      </ToastProvider>
    </AuthProvider>
  )
}
