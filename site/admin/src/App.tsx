import { useEffect, useState } from 'react'
import { AuthProvider, useAuth } from './auth'
import { Icon, type IconName } from './icons'
import { Soon } from './pages/Soon'
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
      <section className="panel">
        <p className="brandmark">Kharcha Paani admin</p>
        <h2>{title}</h2>
        <p>{body}</p>
        {action && <button className="btn btn-primary" onClick={onAction}>{action}</button>}
      </section>
    </main>
  )
}

const TAB_ICON: Record<TabId, IconName> = { today: 'today', people: 'people', limits: 'limits', release: 'release', log: 'log' }

function Shell() {
  const { state, signIn, signOut } = useAuth()
  const [tab, go] = useTab()
  const [dark, toggleTheme] = useTheme()

  if (state.kind === 'booting' || state.kind === 'checking') {
    return <main className="center" aria-busy="true"><p className="brandmark">Kharcha Paani admin</p></main>
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
    <div className="app">
      <header className="topbar">
        <div className="brand">Kharcha Paani <small>admin</small></div>
        <button className="iconbtn" onClick={toggleTheme} aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}>
          <Icon name={dark ? 'sun' : 'moon'} />
        </button>
        <button className="iconbtn" onClick={() => void signOut()} aria-label="Sign out">
          <Icon name="logout" />
        </button>
      </header>
        <nav className="dock" role="tablist" aria-label="Sections">
          {TABS.map((t) => (
            <button
              key={t.id}
              role="tab"
              id={`tab-${t.id}`}
              aria-selected={tab === t.id}
              aria-controls="view"
              className="tab"
              onClick={() => go(t.id)}
              onKeyDown={(e) => {
                const i = TABS.findIndex((x) => x.id === tab)
                if (e.key === 'ArrowRight') go(TABS[(i + 1) % TABS.length].id)
                if (e.key === 'ArrowLeft') go(TABS[(i + TABS.length - 1) % TABS.length].id)
              }}
            >
              <Icon name={TAB_ICON[t.id]} />
              {t.label}
            </button>
          ))}
        </nav>
      <main className="main" id="view" role="tabpanel" aria-labelledby={`tab-${tab}`}>
        <div className="view" key={tab}>
          {tab === 'today' ? <Today go={go} /> : <Soon tab={tab} />}
        </div>
      </main>
    </div>
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
