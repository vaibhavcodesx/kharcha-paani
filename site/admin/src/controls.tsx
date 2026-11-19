import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { formatInt } from './format'
import type { AdminError } from './rpc'
import { Sheet } from './ui'

// Controls that change something. Nothing saves on blur or while typing: a change is always Save or Cancel, and the
// shown value only changes after the server confirms (never optimistic).

export function Segmented<T extends string>({ value, options, onChange, label, disabled }: {
  value: T
  options: { id: T; label: string }[]
  onChange: (v: T) => void
  label: string
  disabled?: boolean
}) {
  const onKeyDown = (e: React.KeyboardEvent) => {
    const i = options.findIndex((o) => o.id === value)
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault()
      const next = options[(i + 1) % options.length]
      if (next && !disabled) onChange(next.id)
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault()
      const prev = options[(i + options.length - 1) % options.length]
      if (prev && !disabled) onChange(prev.id)
    }
  }

  return (
    <div className="seg" role="radiogroup" aria-label={label} onKeyDown={onKeyDown}>
      {options.map((o) => (
        <button
          key={o.id}
          role="radio"
          aria-checked={value === o.id}
          tabIndex={value === o.id ? 0 : -1}
          disabled={disabled}
          className="seg-btn"
          onClick={() => value !== o.id && onChange(o.id)}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

/** A number shown as text with a Change button; Change turns it into a field with Save and Cancel. */
export function EditableNumber({ label, value, max, nonePhrase, disabled, onSave }: {
  label: string
  value: number | null
  max: number
  nonePhrase?: string
  disabled?: boolean
  /** Returns when the server has confirmed, or 'deferred' when a confirmation sheet takes over. Throw to show a message here. */
  onSave: (next: number) => Promise<void | 'deferred'>
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [was, setWas] = useState<number | null | undefined>(undefined)
  const input = useRef<HTMLInputElement>(null)
  const id = useId()

  useEffect(() => {
    if (editing) input.current?.focus()
  }, [editing])
  useEffect(() => {
    if (was === undefined) return
    const t = window.setTimeout(() => setWas(undefined), 5000)
    return () => window.clearTimeout(t)
  }, [was])

  const open = () => {
    setDraft(value === null ? '' : String(value))
    setError(null)
    setEditing(true)
  }

  const save = async () => {
    const n = Number(draft)
    if (draft.trim() === '' || !Number.isInteger(n) || n < 0 || n > max) {
      setError(`Enter a whole number from 0 to ${formatInt(max)}.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const before = value
      const result = await onSave(n)
      // 'deferred': a confirmation sheet took over, so just close the field.
      if (result !== 'deferred') setWas(before)
      setEditing(false)
    } catch (e) {
      setError((e as AdminError).message)
    } finally {
      setBusy(false)
    }
  }

  if (!editing) {
    return (
      <span className="editnum">
        <span className="num editnum-value">{value === null ? (nonePhrase ?? 'None') : formatInt(value)}</span>
        {was !== undefined && <span className="was" role="status">was {was === null ? 'none' : formatInt(was)}</span>}
        <button className="linkbtn" onClick={open} disabled={disabled} aria-label={`Change ${label}`}>Change</button>
      </span>
    )
  }
  return (
    <form className="editnum editing" onSubmit={(e) => { e.preventDefault(); void save() }}>
      <label className="sr" htmlFor={id}>{label}</label>
      <input id={id} ref={input} className="field num" inputMode="numeric" value={draft} onChange={(e) => setDraft(e.target.value)} aria-invalid={error !== null} aria-describedby={error ? `${id}-e` : undefined} />
      <button type="submit" className="btn btn-primary btn-small" disabled={busy}>{busy ? 'Saving' : 'Save'}</button>
      <button type="button" className="btn btn-small" onClick={() => setEditing(false)} disabled={busy}>Cancel</button>
      {error && <span className="fielderr" id={`${id}-e`} role="alert">{error}</span>}
    </form>
  )
}

/** A confirmation that states the effect in one sentence and, for the heavy ones, asks for a word or number to be typed. */
export function ConfirmSheet({ open, onClose, title, children, expected, confirmLabel, danger, onConfirm }: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  /** What must be typed to enable the button. Omit for a plain confirm. */
  expected?: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => Promise<void>
}) {
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  useEffect(() => {
    if (open) {
      setTyped('')
      setError(null)
    }
  }, [open])
  const ready = expected === undefined || typed.trim() === expected
  return (
    <Sheet open={open} onClose={onClose} title={title}>
      <div className="confirm-body">{children}</div>
      {expected !== undefined && (
        <div className="fieldrow">
          <label htmlFor={id}>Type <strong className="num">{expected}</strong> to confirm</label>
          <input id={id} className="field num" value={typed} autoComplete="off" autoCapitalize="off" spellCheck={false} required aria-required="true" onChange={(e) => setTyped(e.target.value)} />
        </div>
      )}
      {error && <p className="fielderr" role="alert">{error}</p>}
      <div className="actions">
        <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
        <button
          className={danger ? 'btn btn-danger' : 'btn btn-primary'}
          disabled={!ready || busy}
          onClick={async () => {
            setBusy(true)
            setError(null)
            try {
              await onConfirm()
              onClose()
            } catch (e) {
              setError((e as AdminError).message)
            } finally {
              setBusy(false)
            }
          }}
        >
          {busy ? 'Working' : confirmLabel}
        </button>
      </div>
    </Sheet>
  )
}

export function ErrorPanel({ title, error, onRetry }: { title: string; error: AdminError; onRetry: () => void }) {
  return (
    <section className="panel" role="alert">
      <h2>{title}</h2>
      <p>{error.message}</p>
      <button className="btn btn-primary" onClick={onRetry}>Try again</button>
    </section>
  )
}
