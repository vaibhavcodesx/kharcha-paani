# Kharcha Paani: instructions for coding agents

A native Android app: Kotlin, Jetpack Compose, Room, Supabase, Gemini. Package
`com.vaibhav.kharchapaani`, everything under `app/`.

**Two agents work on this repo, Claude and Codex.** Before any task, read
[`docs/agents/work-split.md`](docs/agents/work-split.md). It says which items are yours, which files
you may not touch, how to name branches and PRs, and which guides to read next. If anything else
disagrees with it, that page wins.

Quick reference (details in the page above):

- One item, one branch (`codex/<item>-<short-name>`), one PR titled `[codex] <item id>: …`. Never
  merge, never push to `main`.
- No Room schema changes, nothing under `supabase/`, `data/db/`, `data/sync/`, `.github/`.
- Tests with every change. For a bug, the failing test comes first. CI on the PR is the authority.
- Add one entry to `CHANGELOG.md` under `## [Unreleased]`.
- Don't run an emulator. End every PR with a short "Check on the phone" list.
