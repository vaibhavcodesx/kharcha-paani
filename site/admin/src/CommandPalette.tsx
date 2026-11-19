import { useEffect, useId, useRef, useState } from 'react'
import { filterCommands, type Command, type CommandId } from './commands'
import { Icon } from './icons'

// Ctrl or Cmd + K: jump anywhere or run a quick action without reaching for the pointer. A native dialog, so focus is
// trapped, Escape closes it and focus returns to where it was.

export function CommandPalette({ open, onClose, onRun }: { open: boolean; onClose: () => void; onRun: (id: CommandId) => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listId = useId()
  const results = filterCommands(query)

  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) {
      setQuery('')
      setActive(0)
      d.showModal()
    }
    if (!open && d.open) d.close()
  }, [open])

  const run = (c: Command | undefined) => {
    if (!c) return
    onClose()
    onRun(c.id)
  }

  return (
    <dialog ref={ref} className="palette" aria-label="Command palette" onClose={onClose} onClick={(e) => { if (e.target === ref.current) onClose() }}>
      <div className="palette-body">
        <div className="palette-input">
          <Icon name="search" size={20} />
          <input
            autoFocus
            role="combobox"
            aria-expanded="true"
            aria-controls={listId}
            aria-activedescendant={results[active] ? `${listId}-${results[active].id}` : undefined}
            placeholder="Type a command or a section"
            value={query}
            onChange={(e) => { setQuery(e.target.value); setActive(0) }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, Math.max(results.length - 1, 0))) }
              if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
              if (e.key === 'Enter') { e.preventDefault(); run(results[active]) }
            }}
          />
          <kbd>Esc</kbd>
        </div>
        <ul id={listId} role="listbox" className="palette-list">
          {results.length === 0 && <li className="palette-empty">Nothing matches “{query}”.</li>}
          {results.map((c, i) => (
            <li key={c.id} id={`${listId}-${c.id}`} role="option" aria-selected={i === active} data-active={i === active} onMouseEnter={() => setActive(i)} onClick={() => run(c)}>
              <span>{c.label}</span>
              <small>{c.hint}</small>
            </li>
          ))}
        </ul>
      </div>
    </dialog>
  )
}
