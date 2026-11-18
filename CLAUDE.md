<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Kharcha Paani (Claude Code)

Read [AGENTS.md](AGENTS.md) first. It is the start page for every agent; Claude is the Lead (`docs/agents/STATE.md`).
Then `docs/agents/PLAYBOOK.md` and `docs/agents/roles/lead.md`.

Only for Claude Code:

- **Ask up front, then autopilot.** At the start of a session, collect every question, decision and permission the
  planned work needs and ask them together, once. After that, no stopping. Later questions: take the safe, reversible
  choice and file a `needs-owner` issue; do not interrupt. Hard stops are in `AGENTS.md`.
- Use at most two subagents, on a cheaper model, and say so in one line first. Codex and Antigravity are prompted by
  the owner, not launched from here.
- Private memory is only for the owner's machine and preferences. Project rules go in `docs/agents/PLAYBOOK.md`,
  `docs/engineering/testing.md` or `docs/product/decisions.md`.
- Record what changed in `changes/<issue>.md`. Update `PRD.md` only when what the product is, or a rule, changes.
- A cloud or fresh-clone session reads `AGENTS.md`; there are no standing cloud permissions any more.
