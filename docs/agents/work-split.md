# Two agents, one repo: who does what

From 2026-10-26 the remaining work is split between **Claude** (Claude Code, on the owner's laptop,
with access to Supabase and releases) and **Codex** (OpenAI Codex, working from GitHub). This page is
the contract between them. Both read it before every task, and whatever it says wins over any
habit. The owner is Vaibhav Chauhan.

## Read first (in this order)

1. [`PRD.md`](../../PRD.md): what the product is and which rules hold.
2. [`docs/production-readiness/README.md`](../production-readiness/README.md): the work order.
   Read **§1 "Rules for the implementing agent"** and **§10 "Owner decisions"** (§10 overrides the
   rest). The item ids below (T2.5, T2.9…) are defined in its §5.
3. [`TODOS.md`](../../TODOS.md): the owner's own feature notes (items 2–10 referenced below).
4. [`CLAUDE.md`](../../CLAUDE.md): how the owner wants to be worked with. It's written for Claude,
   but its rules apply to Codex too.
5. As needed: the audits in [`docs/production-readiness/audits/`](../production-readiness/audits/)
   (each item cites its source, such as "REL M4" or "SEC L-3"), the design notes in
   [`docs/designs/`](../designs/), and the device checklist format in
   [`docs/testing/1.5.0-device-checks.md`](../testing/1.5.0-device-checks.md).

## The lanes

An item belongs to one agent. Never start an item from the other lane, even if it looks idle.
If your lane is empty, stop and say so; don't borrow.

### Codex lane, in this order

| # | Item | What | Notes |
|---|---|---|---|
| X1 | **T2.9** store listing text | `docs/store-listing/`: 80-character short description, full description, a screenshot plan (which screens, what state, captions), a brief for the 1024×500 feature graphic | Docs only. Follow PLAY §4/§9 in the audits: no "best/#1/free", no payment-app logos, no "loan/lend/borrow/credit" |
| X2 | **TODOS 7** Groups → categories toggle | It's a bug: failing test first (§1 rule 8), then the fix | P1 |
| X3 | **T2.6**, parts 1 and 2 only | `setRecentsScreenshotEnabled(false)` on API 33+; notifications that show amounts get `VISIBILITY_PRIVATE` plus a public version; shared files accept only `content://` URIs that aren't the app's own FileProvider | The third part (sign-out offer to remove data) is **Claude's** |
| X4 | **T2.5** confirm or undo | Retract settlement, delete IOU, delete group: the `PendingDeletes` undo pattern or a dialog that states the consequence | Copy tone rule, §1 rule 13 |
| X5 | **TODOS 4, 9, 8, 5, 2** | One PR each, in that order: IOU card shows the whole note; preselect a group when splitting with someone; settlements view in a group; sorting groups; member colour dots | |
| X6 | **T2.8** in-app updates | Play Core `AppUpdateManager`, **flexible updates only** | The server-side minimum version (for immediate updates) is Claude's, later |
| X7 | **T2.10** accessibility | One PR per screen or area, not one big PR | Last, because it touches many UI files |

### Claude lane

- Merging the PRs that were open when this split began (#71, #82, #86, #97, #101, #103, #106,
  #107) and future Dependabot updates; releases.
- **T1.3** Gemini proxy (#101), **T1.1** account deletion (#103), **T2.3** (#107): the Supabase side.
- **T2.2** abuse limits, **T2.4** sync visibility, **T2.6 part 3** (sign-out data removal),
  **T2.7** notifications phase 2, the crash-report off switch.
- **TODOS 3a, 3b, 6, 10** and the Ledger / Ask-to-pay items in TODOS.
- All of Tier 3 unless this page is updated to hand an item over.

## Files: who may touch what

**Codex does not edit these.** If an item seems to need one, stop, explain it in the PR, and leave
the change to Claude:

- `supabase/**`: migrations, Edge Functions and SQL tests. Never apply anything to a database.
- `app/src/main/java/com/vaibhav/kharchapaani/data/db/**` and `app/schemas/**`, so **no Room schema
  change, no new Room migration.** Version numbers would collide.
- `app/src/main/java/com/vaibhav/kharchapaani/data/sync/**` (including `SyncWorker.kt`).
- `data/repository/GeminiRepository.kt` and the AI prompts (#101 rewrites them).
- `ui/auth/AccountSheet.kt` and `data/settings/AppSettings.kt`.
- `.github/**`, `PRD.md`, `docs/production-readiness/README.md` (Claude marks items "done in #NN"
  when it merges them), release headings in `CHANGELOG.md`.

**Shared hot files.** Both agents edit these, so keep diffs to them as small as possible. No
reformatting, no reordering, no moving code around:
`MainViewModel.kt`, `MainActivity.kt`, `ui/components/Sheets.kt`, `util/NotificationHelper.kt`,
`gradle/libs.versions.toml` and `app/build.gradle.kts` (dependency lines only).

**`CHANGELOG.md`:** every PR adds one entry under `## [Unreleased]` in the right `###` section and
touches nothing else. Conflicts there are expected; the agent merging resolves them.

## How Codex works

- **One item, one branch, one PR.** Branch `codex/<item>-<short-name>` (e.g. `codex/x2-category-toggle`)
  from the latest `main`. PR title `[codex] <item id>: <what changed>`.
- **Don't merge.** Claude reviews Codex PRs and merges them once CI is green. While preparing a merge,
  Claude may push a merge-from-main commit onto a Codex branch; build on top of it, don't force-push
  it away.
- **Don't push to `main`** and don't push to any branch you didn't create.
- **Tests:** follow §1 rules 7 and 8. Unit tests use Robolectric. Compose tests use
  `StandardTestDispatcher` unless a test says otherwise. UI and ViewModel changes need a test that
  builds the real `MainViewModel` or the app shell (see `AppShellLaunchTest`), because a crash on
  launch once shipped with every unit test green. Kotlin initialisers run in declaration order, so
  mind where new properties go.
- **CI is the authority.** If your environment can run `./gradlew :app:testDebugUnitTest :app:lintDebug`,
  run it. If it can't (no Android SDK), say so in the PR, and CI on the PR decides. Never run an
  emulator: the owner tests on the phone.
- **Every PR body has:** what changed and why (cite the item and its audit id), the tests added,
  a short "Check on the phone" list in the style of `docs/testing/1.5.0-device-checks.md`, and any
  questions for the owner. Don't stop to ask: make the safe, reversible choice, note it, and move
  on.
- **Owner-review hard stop:** anything touching RLS, auth, privileges, SECURITY DEFINER code or a
  non-additive migration is out of Codex's lane anyway. If one sneaks in, title the PR
  `[codex][owner review] …`.
- **Product rules that bite:** the app is free for the first 3+ months (§10 D5), so no pricing,
  paywall or billing work. Text one person sends or shows another must sound casual and friendly:
  never "you owe", "overdue" or "pay now" (§1 rule 13). Amounts are rupees with Indian grouping
  (`ui/format/Money.kt`); never let a money value ellipsize.
- **Commits:** plain English subject lines describing the change for a user where possible (see
  `git log`). Don't add a Claude co-author line.

## How Claude works with this

- Before starting anything, check this page's lanes and the open `[codex]` PRs (`gh pr list`), so it
  never picks up a Codex item.
- Merge Codex PRs in the order they become green, resolving `CHANGELOG.md` conflicts. Update the
  work order's "done in #NN" on merge.
- Moving an item between lanes means editing this page on `main` first, in its own small PR.
