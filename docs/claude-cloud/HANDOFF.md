# Handoff for Claude Code cloud sessions

Written 2026-11-03. The owner, Vaibhav, is funding cloud sessions to merge what's pending and to do the hard and creative
work. You're the senior engineer and designer on this project now. Antigravity (Gemini 3.8 Flash) does the routine queue and
**can't merge**: its PRs collect against `main` for you.

**Update 2026-11-03: Codex leads until 2026-11-06 and owns Stream A and most of Stream B (`docs/agents/codex-lead-2026-11-03.md`). Until then, take Stream C and claim items on #188 first. Check what Codex has opened before starting anything.**

Read these first: `CLAUDE.md`, `PRD.md`, `docs/antigravity/HANDBOOK.md` sections 1–5 (product, code map, past disasters, design
system), `docs/plan-1.6-to-launch.md`, and `docs/production-readiness/README.md` §1 and §10 (§10 overrides everything). Then read
[issue #188](https://github.com/vaibhavcodesx/kharcha-paani/issues/188) and `gh pr list --state open`.

## Standing permission from the owner

`CLAUDE.md` asks agents to confirm each tier and ask questions up front. The owner approved the work below on 2026-11-03, so don't stop
to ask. Take the safe, reversible option, note it, and put questions in a comment on #188.

**Hard stops still hold, with no exceptions:**
- RLS, auth, security or a non-additive migration: open the PR as `[owner review]` and leave it unmerged.
- No pricing, paywall or billing work (D5). No re-architecting the ledger, debt simplification, AI pipeline or groups model.
- No destructive Room migrations. Nothing here is tested on a phone by you: every PR says what the owner should check.
- **Never apply anything to Supabase, even if you have access.** Write the migration and a paste-ready test that ends in a raised error
  carrying its results, so it always rolls back. The owner runs it in the SQL editor.
- Don't run releases or the release workflow, change repo settings, or handle secrets. A clone has no `.env` or signing keys.

**Budget:** it's real money and finite. One workstream per session. Read only what the item needs, use CI rather than long local builds,
and finish things rather than starting many. When a session's workstream is done, comment on #188 and stop.

## Stream A: merge what's pending (do this first, it's the cheapest value)

Review every PR before merging: read the diff, not only the checks. If your environment can't merge, list what's ready on #188 for the owner.

**1. Release 1.6.0.** Merge R1 (Ask wording), R2 (device checklist), then R3 (version bump). If Antigravity hasn't opened them, do them yourself from
`HANDBOOK.md` section 7. Then comment on #188 telling the owner to run Actions → Release APK with tag `v1.6.0`. **After R3, merge only tests and docs to `main`
until #188 says the build ran**, so the build matches its checklist. Keep working on everything else meanwhile.

**2. `[antigravity]` PRs,** in item order once 1.6 is built. Gemini 3.8 Flash wrote these and Claude hasn't tested it hands-on, so check:
- **Weakened tests:** removed assertions, `@Ignore`, longer sleeps, narrower checks. Any of these means reject.
- **Money:** ⚠ items and anything touching balances, shares, stored ids or sync need a real-Room test that fails without the change.
- **Scope creep and design drift:** raw colours or sizes, new layouts, reworded copy, "you owe" toward a friend.
- **Merge order:** one at a time, or batch them: make a temporary branch, merge the batch into it, run CI once, then merge `main` into each PR's tested commit
  with `-X ours` and squash. In `CHANGELOG.md` keep both sides. Fix small problems yourself rather than sending them back.

**3. Codex's parked #130, #132, #139, #143.** Close them once an `[antigravity]` PR says "Supersedes #N" and merges. If Antigravity parked one, finish it yourself.

**4. Dependabot, after the owner confirms 1.6 on the phone.** #186 is actions only, and its one test failure looks flaky (handbook item F2). #97 fails unit tests and the release bundle: find out why and split out what's safe. #86 is a large Compose BOM jump;
check large-text and auto-size tests. **#71 R8:** bring it up to date and check keep rules for serialization and the Play libraries, but merge only after the owner runs a signed-release smoke test. R8 once risked breaking sign-in.

## Stream B: hard engineering, in this order

Before the closed test, testers will hit these:
1. **T2.4 sync problems visible** (REL M3, M9): Room v11 nullable error column with Migration, schema JSON and migration test; `lastSyncError`; stop retrying on 401/403 with "Sign in again"; "Couldn't sync since…" in the account sheet; "N couldn't sync" opens the list. Build on Antigravity's Y13 and Y14 if merged.
2. **T2.2 abuse limits** `[owner review]`: `char_length` checks, invite attempt limits, per-user write limits, member cap (SEC M-5). Propose defaults in the PR (names 64, notes 500). The app's field limits must match; see item Y3.
3. **T2.6 part 3** `[owner review]`: sign-out offers to remove this account's data when nothing is unsynced. It deletes data, so test it hard. Reuse `AccountLeftovers`.
4. **T2.7 notifications phase 2** `[owner review]`, the biggest: server `activity` table with triggers and RLS, Room `inbox_items`, Updates screen, notifications from the pull (48 h window, no flood on first sync). "Everyone in a split hears Priya added an expense." Design and copy are in `audits/notifications.md`; defaults in D3.
5. **"Edited by X · time"** on shared expenses (T3.8), `[owner review]`: server column and trigger, Room migration. `ownerProfileId` is the adder, not the editor: never show a guess. Note the Ask-to-pay wording is handled by R1.

Then, soon after launch:
6. **T3.7 performance** and whatever's left of REL M8: shared flows, `currentAsks` without per-friend queries, root recomposition.
7. **T3.4 process death:** the statement result cached by hash and cleared at sign-out, plus anything Antigravity parked from Y6/Y7.
8. **T3.1 push, N3–N5** `[owner review]`: FCM with **ids-only payloads** (D4). The owner must set Firebase secrets (O11), so build it and list exactly what they do.
9. **T3.2 app lock** `[owner review]`: opt-in, can't lock anyone out. **T2.8 part 2:** server minimum app version.

## Stream C: creative work

**Capture is the product** (handbook section 1). The bar is "hand-crafted, not AI-generated": read `docs/history/prd-2026-09-cloud-migration.md` Part B (the slop tells) and `docs/part-b-ui-audit.md`. Extend the design system; don't replace it. For anything new, write a short `docs/designs/<name>.md` first and include it in the PR with Roborazzi captures. The owner judges on the phone, so keep each one easy to revert.

1. **Quick add opens instantly** (T3.5, REL M5 part 2): the sheet opens with the on-phone guess, "Improving with AI…", and the AI fills in only fields the user hasn't touched. The most important feel in the app.
2. **The three screenshot moments:** confirm sheet, balance with rolling digits, widget. Audit them against Part B and raise them. Then loading, empty and error states everywhere.
3. **Store assets:** screenshots and the 1024×500 graphic from `docs/store-listing/`, via the `ScreenRenderCapture` setup, so the owner only uploads.
4. **20+ friends** (TODOS 6): compact rows and frequent people first, building on Antigravity's Y15.
5. **First-run walkthrough** (TODOS 10): short, always skippable, shown once. Teach swipe gestures and quick add.
6. **Where the money went, then budgets** (work order §9, the owner's top priorities after launch): a clear monthly picture, then per-month and per-category budgets with friendly nudges ("Food's at 80% for October, ₹1,200 left"). Never preachy. Design doc first; don't hint at paid plans.
7. **Only if there's evidence:** the widget remembering a split, "Rohan paid" by voice (plan §5). Check #188 for what the owner has seen before building these.
8. **Needs the owner's yes on #188:** light theme (T3.12; the app is dark by design), Hindi/Hinglish, saved cards (TODOS 3b; affects Play declarations), App Links (needs a domain).

## Working alongside Antigravity

It works in the owner's local folder from the queue in `HANDBOOK.md` section 7. Before starting an item, check `gh pr list` and #188. Don't redo its items unless parked. You both edit `MainViewModel.kt` and `MainActivity.kt`, so keep diffs small and merge often.

Your PRs: branch `claude/<item>-<short-name>`, title `[claude] <item>: <what changed>`, one item each, a `CHANGELOG.md` entry under Unreleased, and a "Check on the phone" list. Test the gatherer: real Room database, real balance. For a bug, the failing test comes first. Mark items "done in #NN" in `docs/production-readiness/README.md`. `claude-code-review.yml` skips `antigravity/` branches to save quota, so your review is the only one those PRs get.

## When you stop

Comment on #188: what merged, what's open and why, what the owner must do (with exact clicks), and your questions. Say plainly what wasn't verified. **Nothing since 1.5.0 has been on a phone yet.**
