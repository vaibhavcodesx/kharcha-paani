import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AdminError, callRpc, type Overview } from './rpc'
import { supabase } from './supabase'

// The overview is read once and shared: the status rail, Today and Release all show parts of it. Anything that changes
// it (a plan, a ceiling, a version) calls refresh so every screen agrees.

type State = { kind: 'loading' } | { kind: 'ready'; data: Overview } | { kind: 'failed'; error: AdminError }

interface Api {
  state: State
  refresh: () => Promise<void>
}

const Ctx = createContext<Api | null>(null)

export function useOverview(): Api {
  const v = useContext(Ctx)
  if (!v) throw new Error('useOverview outside OverviewProvider')
  return v
}

export function OverviewProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ kind: 'loading' })

  const refresh = useCallback(async () => {
    try {
      const data = await callRpc<Overview>(supabase, 'admin_overview')
      setState({ kind: 'ready', data })
    } catch (e) {
      setState({ kind: 'failed', error: e instanceof AdminError ? e : new AdminError('other', 'Something went wrong. Try again.') })
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const api = useMemo(() => ({ state, refresh }), [state, refresh])
  return <Ctx.Provider value={api}>{children}</Ctx.Provider>
}
