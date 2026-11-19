<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Agent inboxes

One file per agent: `codex.md`, `antigravity.md`, `claude.md`. The Lead (or the owner) writes a block here when
an agent needs to be told something that is not in an issue brief: improve a PR, continue unfinished work,
change direction. Every agent reads its own file right after `STATE.md` (see `AGENTS.md` step 2a).

## Block format

```
## 2026-11-19 PR 480: improve
What is wrong: <one or two lines, concrete: file, test, behaviour>
What to do: <the change, and what done looks like>
Then: <continue with your queue | stop>
```

## Rules

- Short and concrete. The block is a to-do, not a discussion; discussion goes on the PR or issue.
- The reader deletes a block when it is done, in the same PR that does it, and says so in the PR body.
- An empty file means nothing is waiting. Do not invent work.
- Anything that needs the owner goes to `needs-owner`, not here.
- No secrets, no vulnerability detail (the repository is public).

## How the Lead reaches each agent

| Agent | Delivery |
|---|---|
| Codex | Writes the block, and can also start Codex directly from the owner's laptop with `scripts/ask_codex.ps1 "<task>"` (works while Codex has usage left). |
| Antigravity | Writes the block. Antigravity only runs when the owner starts it, so it reads the block at its next `start`. |
| Claude | The owner starts a session, or a block is waiting in `claude.md`. |
