# Codex: you lead, 2026-11-03 to 2026-11-06

Claude is away for 3 days and the owner has given you the lead, with merge rights and a large quota. You know this repo:
56 of your PRs landed in the last solo period, and Claude's review found them clean. This page is what's changed and what
"make it count" means now. **Read it fully, then `PRD.md`, `docs/antigravity/HANDBOOK.md` sections 1–5, `docs/plan-1.6-to-launch.md`,
and work order §1 and §10.**

## What counts this time

Last time you merged 41 string-move PRs. They were clean, but no user felt them. **Nothing since 1.5.0 has been on a phone, and the
closed test can't start until it has.** This period is judged by, in order:

1. **1.6.0 is on the owner's phone**, and everything they report is fixed.
2. **Real user-facing bugs are gone** (your four parked PRs, the review findings).
3. **Sync failures are visible** before testers hit them.
4. **Nothing broke:** no wrong balance, no crash, `main` always green.

Fewer, deeper PRs beat a high count. Don't open mechanical work to look busy. Antigravity has that queue.

## Your rights, and where they stop

**You may:** merge your PRs and Antigravity's into `main`; edit `data/db`, `data/sync`, `AccountSheet`, `AppSettings`, `GeminiRepository`,
AI prompts, `.github/workflows` (only to keep CI working), `PRD.md`, and the work order; add **additive** Room migrations; run the release
workflow for `v1.6.0` once (read `release.yml` first: if it would publish rather than draft, leave it to the owner and say so on #188).

**Merge gate, every time:** the branch has the latest `main`, all three CI jobs are green on that exact commit, you've read the whole diff,
the body has "Check on the phone", and a line is in `docs/agents/solo-log.md`. Squash. Confirm `merged=true`. For anything touching money, a
real-Room test must fail without the change.

**Still hard stops, no exceptions:**
- **RLS, auth, privileges, SECURITY DEFINER, anything under `supabase/`, or a non-additive migration:** open as `[owner review]`, leave unmerged.
- **Never apply anything to Supabase** or deploy Edge Functions, even if you have access. Write the migration plus a paste-ready test ending in a raised error carrying its results, so it rolls back. The owner runs it.
- **Don't publish** a release, change repo settings or secrets, force-push, or rewrite history. No emulator (the owner tests on the phone). No pricing or paywall work (D5). Don't re-architect the ledger, debt simplification, AI pipeline or groups model.
- **Never write `@claude`** on GitHub. It spends the owner's last Claude quota. `claude-code-review.yml` now skips `codex/`, `antigravity/` and `claude/` branches for the same reason. Don't undo that.
- **#71 R8:** keep it current, but merge only after the owner's signed-release smoke test. **Dependabot:** diagnose now, merge after the owner confirms 1.6.

**If `main` breaks:** revert first with a PR, diagnose second.

**One correction to the two-failure rule.** #132 and #139 parked with a fix never written, because the expected red run of a failing test counted as a failure. A red run you **predicted** doesn't count. Two *unpredicted* failures: stop, write down your diagnosis in the PR, move on, and come back with a new hypothesis if you have one.

## The queue

### 1. Get 1.6.0 out (today)
Details are in `HANDBOOK.md` section 7, items R1–R3:
- **R1:** `ui/format/AskMessage.kt:37` tells the friend "you owe". Change to `Asking ₹X of the ₹Y for now.` with a test.
- **R2:** `docs/testing/1.6.0-device-checks.md` from every PR's phone list since `v1.5.0`. Most important first: install over 1.5.0, sign-in on the release build, **quick add through the proxy (never tested end to end from a phone)**, Say the split from the widget, Delete my account on a throwaway account only.
- **R3:** `versionCode = 7`, `versionName = "1.6.0"`, Unreleased renamed. Read the whole section through.
- Run the build; run `scripts/release-smoke.sh` steps only if you have a device (you don't: list them for the owner). Then comment on #188 with exactly what the owner taps. **After R3, no product merges until the build has run.**
- If F2's flaky `InsightsAccessibilityTest` blocks you, fix it first (`:67`, run 41270005355).

### 2. Owner's phone results: top priority whenever they arrive
Watch #188. Fix what they report before anything else, each with a test that reproduces it.

### 3. Finish what you parked, plus Claude's findings
- **#130:** reserve every exact-date match before near-date. A wrong answer means duplicate money or a lost row, so this goes first.
- **#132:** nullable Home snapshot from direct Room flows; brand mark until feed, people, balance and analytics have emitted.
- **#139:** one auto-sized whole figure when the animated digits can't fit.
- **#143:** after those two. Confirm the fixture cause rather than assuming it.
- **F1, review prompt:** answering a payment shows a ~4 s snackbar; `ReviewAfterSettlement` checks at 750 ms, sees `canPrompt` false and drops the moment. Wait for `canPrompt` with a ~6 s timeout via `snapshotFlow`. Nothing uses up the cooldown unless the requester ran.
- **Unreviewed by Claude, for you to re-read critically:** #133, #135, #136, #138, #140, #141.

### 4. Bugs in the capture path (`HANDBOOK.md` Y5–Y7)
- **Y5:** track `parseJob`; a late AI result must never overwrite what someone typed, or turn an edit into a new entry.
- **Y6:** a half-filled entry survives process death (someone switches to PhonePe to copy a note). **Y7:** pending swipe-deletes survive it too.

### 5. T2.4 sync problems visible (REL M3, M9)
Room **v11**: nullable error column, Migration, schema JSON, migration test. `lastSyncError`; stop retrying on 401/403 with "Sign in again"; "Couldn't sync since…" in the account sheet; "N couldn't sync" opens the list; "Not synced" on FAILED rows. Additive, so you may merge it. If it changes auth code, it becomes `[owner review]`. Gradle only with 4 GB+ free RAM; stop leftover `java.exe`.

### 6. Prepared for owner review, in order
Open, tested, unmerged: **T2.6 part 3** (sign-out data removal), **T2.2** (limits; propose names 64, notes 500; the app's fields must match), **"Edited by X"** (server column, trigger, Room migration; never a guessed name), then **T2.7 phase 2** (activity table, Updates inbox). Stack migrations carefully and say which version each takes.

### 7. If you still have quota
`HANDBOOK.md` Y1–Y4 and T3.7 performance, unless Antigravity has taken them. **Design-heavy work** (sheet-first quick add, walkthrough, budgets, light theme, friend rows) is Stream C of `docs/claude-cloud/HANDOFF.md`, for Claude. If nobody has claimed it by day 3, you may take sheet-first quick add only, using existing components, with Roborazzi captures attached. Don't invent visual design: see `HANDBOOK.md` section 4.

## The other agents

| Who | Does what | Your job |
|---|---|---|
| **Antigravity** (Gemini 3.8 Flash), in the owner's folder | Tests, mechanical polish, reports (`HANDBOOK.md` T, M, D, Y8 onward). It can't merge. | **Review and merge its PRs.** It's untested here, so reject weakened tests (removed assertions, `@Ignore`, longer sleeps), scope creep, raw colours and sizes, reworded copy. Don't rubber-stamp green CI. |
| **Claude cloud**, if the owner starts it | Stream C (creative), per its handoff | Avoid overlap. Claim big items with a comment on #188 before starting. |
| **You** | Everything above, from your own worktree | Lead. Keep #188 current. |

Don't work in `Z:\V\.The_Future\Vibeeee\Kharcha Paani`: Antigravity switches branches there, and it holds the owner's untracked files and secrets. Your answers from last time: #130 first; Claude owned "Edited by X" but it's yours to prepare now; the Ask wording is decided (R1).

## Questions

Follow `CLAUDE.md`: don't stop to ask. Take the safe, reversible choice, note it, and collect questions on #188. Still unanswered from the owner: did they watch a friend add from the widget, and what were the #103 SQL-editor results?

## Before the period ends: what Claude checks on return

Write `docs/agents/codex-lead-report-2026-11-06.md`, kept short:
- What merged, by what it changed for a user. What's open and why. What the owner must do, with exact clicks.
- **What wasn't verified**, plainly. Merged and green isn't verified on a phone.
- 1.6.0 status: built? on the phone? results? Has the old Gemini key been deleted by the owner?
- `main` green, `CHANGELOG.md` read through, work order marked "done in #NN", no half-finished branch presented as done.

When Claude returns it will read the diffs of whatever touched money, sync or migrations, and check that no test was weakened. **On 2026-11-06 your merge rights end** unless the owner extends them on #188. Keep working, but leave PRs open after that.
