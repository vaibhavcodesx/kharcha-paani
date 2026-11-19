import type { TabId } from '../router'

// The tabs that are built next. Each says what it will do, in the page's own voice, so the frame is honest.

const COPY: Record<Exclude<TabId, 'today'>, { title: string; body: string }> = {
  people: {
    title: 'People',
    body: 'Search everyone who has an account, move a friend onto the friends plan, and delete an account with the photos it left behind.',
  },
  limits: {
    title: 'Limits',
    body: 'Set how many AI reads each plan gets a day, the ceiling for the whole project, and pause AI for everyone in one tap.',
  },
  release: {
    title: 'Release',
    body: 'Choose the minimum and recommended app version, with a plain sentence on who stops syncing before you confirm.',
  },
  log: {
    title: 'Log',
    body: 'Every change made here, with who made it and what it was before. Nothing in it can be edited.',
  },
}

export function Soon({ tab }: { tab: Exclude<TabId, 'today'> }) {
  const c = COPY[tab]
  return (
    <section className="panel">
      <h2>{c.title} is coming next</h2>
      <p>{c.body}</p>
    </section>
  )
}
