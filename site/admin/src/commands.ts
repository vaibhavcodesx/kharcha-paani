import type { TabId } from './router'

// The command palette's commands and how a typed query narrows them. Pure, so it is tested without a browser.

export type CommandId = `go-${TabId}` | 'theme' | 'refresh' | 'sign-out'

export interface Command {
  id: CommandId
  label: string
  hint: string
  words: string
}

export const COMMANDS: Command[] = [
  { id: 'go-today', label: 'Go to Today', hint: 'What needs you', words: 'today home overview status attention' },
  { id: 'go-people', label: 'Go to People', hint: 'Search, plans, delete', words: 'people users accounts search plan delete friends photos' },
  { id: 'go-limits', label: 'Go to Limits', hint: 'AI caps, ceilings, pause', words: 'limits caps ceiling ai pause resume quota' },
  { id: 'go-release', label: 'Go to Release', hint: 'Minimum app version', words: 'release version minimum recommended update play' },
  { id: 'go-log', label: 'Go to Log', hint: 'Every change made here', words: 'log history audit changes' },
  { id: 'theme', label: 'Switch theme', hint: 'Light or dark', words: 'theme dark light mode appearance' },
  { id: 'refresh', label: 'Refresh the numbers', hint: 'Reload the overview', words: 'refresh reload update numbers' },
  { id: 'sign-out', label: 'Sign out', hint: 'End this session', words: 'sign out logout leave' },
]

/** Every word typed must start a word in the label or keywords. An empty query lists everything. */
export function filterCommands(query: string, commands: Command[] = COMMANDS): Command[] {
  const terms = query.toLowerCase().split(/\s+/).filter(Boolean)
  if (terms.length === 0) return commands
  return commands.filter((c) => {
    const haystack = `${c.label} ${c.words}`.toLowerCase().split(/\s+/)
    return terms.every((t) => haystack.some((w) => w.startsWith(t)))
  })
}
