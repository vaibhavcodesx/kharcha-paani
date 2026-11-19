import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { percent, toneWord, type Tone } from './format'
import { Icon, type IconName } from './icons'

// Small pieces every screen shares. Text goes through React, never as markup.

/** A thin bar with its state in words and an icon: colour is never the only signal. */
export function Meter({ used, limit, tone }: { used: number; limit: number; tone: Tone }) {
  const pct = tone === 'paused' ? 100 : percent(used, limit)
  return (
    <div className="meter" data-tone={tone} role="meter" aria-valuemin={0} aria-valuemax={limit} aria-valuenow={Math.min(used, limit)} aria-label={toneWord(tone)}>
      <span style={{ ['--fill' as string]: `${pct}%` }} />
    </div>
  )
}

const TONE_ICON: Record<Tone, IconName> = { ok: 'check', near: 'warn', full: 'warn', paused: 'pause' }

export function State({ tone }: { tone: Tone }) {
  return (
    <span className="state" data-tone={tone}>
      <Icon name={TONE_ICON[tone]} />
      {toneWord(tone)}
    </span>
  )
}

/** Rows shaped like the real ones, so the page does not jump when data arrives. */
export function Skeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => (
        <div className="skel" key={i}>
          <i style={{ ['--w' as string]: `${42 + ((i * 17) % 34)}%` }} />
          <i />
        </div>
      ))}
    </div>
  )
}

/** A native dialog: it traps focus, closes on Escape and hands focus back. A bottom sheet on phones. */
export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog ref={ref} className="sheet" aria-label={title} onClose={onClose} onClick={(e) => { if (e.target === ref.current) onClose() }}>
      <div className="sheet-body">
        <h2>{title}</h2>
        {children}
      </div>
    </dialog>
  )
}

interface ToastApi { say: (message: string) => void }
const ToastCtx = createContext<ToastApi>({ say: () => {} })
export const useToast = () => useContext(ToastCtx)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<{ id: number; message: string }[]>([])
  const next = useRef(1)
  const say = useCallback((message: string) => {
    const id = next.current++
    setItems((xs) => [...xs, { id, message }])
    window.setTimeout(() => setItems((xs) => xs.filter((x) => x.id !== id)), 4500)
  }, [])
  const api = useMemo(() => ({ say }), [say])
  return (
    <ToastCtx.Provider value={api}>
      {children}
      <div className="toasts" aria-live="polite" role="status">
        {items.map((t) => (
          <div className="toast" key={t.id}>{t.message}</div>
        ))}
      </div>
    </ToastCtx.Provider>
  )
}
