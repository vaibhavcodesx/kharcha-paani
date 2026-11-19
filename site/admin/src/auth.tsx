import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { IDLE_SIGN_OUT_MS } from './config'
import { AdminError, callRpc } from './rpc'
import { supabase } from './supabase'

// Who is looking at the page. The page decides nothing about trust: `admin_whoami` asks the database, which
// checks the uid, the email and the Google identity together. Everyone else is signed out at once.

export type AuthState =
  | { kind: 'booting' }
  | { kind: 'signed-out' }
  | { kind: 'checking' }
  | { kind: 'stranger' }
  | { kind: 'not-installed' }
  | { kind: 'owner'; session: Session }
  | { kind: 'error'; message: string }

interface AuthApi {
  state: AuthState
  signIn: () => Promise<void>
  signOut: () => Promise<void>
}

const Ctx = createContext<AuthApi | null>(null)

export function useAuth(): AuthApi {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ kind: 'booting' })
  const owner = useRef<string | null>(null)

  const check = useCallback(async (session: Session | null) => {
    if (!session) {
      owner.current = null
      setState({ kind: 'signed-out' })
      return
    }
    // The owner is checked once per sign-in; a tab coming back to the front does not flicker the screen.
    if (owner.current === session.user.id) return
    setState({ kind: 'checking' })
    try {
      const isOwner = await callRpc<boolean>(supabase, 'admin_whoami')
      if (isOwner) {
        owner.current = session.user.id
        setState({ kind: 'owner', session })
      } else {
        // Anyone else who signed in gets nothing, and is signed out straight away.
        setState({ kind: 'stranger' })
        void supabase.auth.signOut()
      }
    } catch (e) {
      if (e instanceof AdminError && e.kind === 'not-installed') setState({ kind: 'not-installed' })
      else if (e instanceof AdminError && e.kind === 'signed-out') setState({ kind: 'stranger' })
      else setState({ kind: 'error', message: e instanceof Error ? e.message : 'Something went wrong.' })
    }
  }, [])

  useEffect(() => {
    void supabase.auth.getSession().then(({ data }) => check(data.session))
    const { data: sub } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_OUT') {
        owner.current = null
        setState((s) => (s.kind === 'stranger' || s.kind === 'not-installed' ? s : { kind: 'signed-out' }))
        return
      }
      if (event === 'TOKEN_REFRESHED') return
      void check(session)
    })
    return () => sub.subscription.unsubscribe()
  }, [check])

  // Idle sign-out: any touch, key or scroll resets the clock.
  useEffect(() => {
    if (state.kind !== 'owner') return
    let timer = window.setTimeout(() => void supabase.auth.signOut(), IDLE_SIGN_OUT_MS)
    const reset = () => {
      window.clearTimeout(timer)
      timer = window.setTimeout(() => void supabase.auth.signOut(), IDLE_SIGN_OUT_MS)
    }
    const events = ['pointerdown', 'keydown', 'scroll', 'touchstart'] as const
    events.forEach((e) => window.addEventListener(e, reset, { passive: true }))
    return () => {
      window.clearTimeout(timer)
      events.forEach((e) => window.removeEventListener(e, reset))
    }
  }, [state.kind])

  const signIn = useCallback(async () => {
    setState({ kind: 'checking' })
    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: `${window.location.origin}${window.location.pathname}`, queryParams: { prompt: 'select_account' } },
    })
    if (error) setState({ kind: 'error', message: 'Could not start the Google sign-in. Try again.' })
  }, [])

  const signOut = useCallback(async () => {
    await supabase.auth.signOut()
    setState({ kind: 'signed-out' })
  }, [])

  const api = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut])
  return <Ctx.Provider value={api}>{children}</Ctx.Provider>
}
