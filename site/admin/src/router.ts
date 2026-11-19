import { useCallback, useEffect, useState } from 'react'

// Five tabs, kept in the URL hash so a reload keeps the place. No router library.

export const TABS = [
  { id: 'today', label: 'Today' },
  { id: 'people', label: 'People' },
  { id: 'limits', label: 'Limits' },
  { id: 'release', label: 'Release' },
  { id: 'log', label: 'Log' },
] as const

export type TabId = (typeof TABS)[number]['id']

export function parseTab(hash: string): TabId {
  const id = hash.replace(/^#\/?/, '').split(/[/?]/)[0]
  return TABS.find((t) => t.id === id)?.id ?? 'today'
}

export function useTab(): [TabId, (id: TabId) => void] {
  const [tab, setTab] = useState<TabId>(() => parseTab(window.location.hash))
  useEffect(() => {
    const on = () => setTab(parseTab(window.location.hash))
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  const go = useCallback((id: TabId) => {
    window.location.hash = id
  }, [])
  return [tab, go]
}
