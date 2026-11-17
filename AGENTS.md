# Kharcha Paani: instructions for coding agents

> **Start here (2026-11-17).** The launch audit in `docs/audits/2026-11-17/` replaced the older
> plans. **Claude is Lead; the "Codex leads until 2026-11-06" line below is out of date.** Read
> `docs/audits/2026-11-17/NEXT-STEPS.md` and `OWNER-ANSWERS.md` section 6, then take work only from
> GitHub issues in milestone `1.8.0`. Hard stops below still apply. The rest of this page is
> rewritten by issue #316.

A native Android app: Kotlin, Jetpack Compose, Room, Supabase, Gemini. Package
`com.vaibhav.kharchapaani`, everything under `app/`.

**Several agents work on this repo: Claude, Codex and Antigravity.** Before any task, read
[`docs/agents/work-split.md`](docs/agents/work-split.md). It says which items are yours, which files
you may not touch, how to name branches and PRs, and which guides to read next. If anything else
disagrees with it, that page wins. Antigravity: stop and read this first, your limits, queue and state are in
[`docs/antigravity/HANDBOOK.md`](docs/antigravity/HANDBOOK.md).

Quick reference (details in the page above):

- **Codex: you lead until 2026-11-06, with merge rights. Read `docs/agents/codex-lead-2026-11-03.md` first.**
- One item, one branch (`codex/<item>-<short-name>`), one PR titled `[codex] <item id>: …`. Never push to `main`.
- No Room schema changes, nothing under `supabase/`, `data/db/`, `data/sync/`, `.github/`.
- Tests with every change. For a bug, the failing test comes first. CI on the PR is the authority.
- Add one entry to `CHANGELOG.md` under `## [Unreleased]`.
- Don't run an emulator. End every PR with a short "Check on the phone" list.
