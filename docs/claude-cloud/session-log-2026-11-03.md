# Claude cloud session, 2026-11-03: what was done and what's next

Written at the end of the session for the next Claude Code cloud session. Read
[HANDOFF.md](HANDOFF.md) first (standing permissions and hard stops), then this page.

## The situation when this session ended

- **1.6.0 is released** (signed build ran; the owner is going through it on the phone). The product
  freeze that held product PRs is over. Codex leads until 2026-11-06 and **does all merging**;
  Claude opens PRs, answers reviews and keeps them green.
- Antigravity (Gemini Flash) works a routine queue from `docs/antigravity/HANDBOOK.md`; Codex merges it.
- Progress log for every agent: [issue #188](https://github.com/vaibhavcodesx/kharcha-paani/issues/188).

## PRs from this session

| PR | What | State at hand-off |
|---|---|---|
| #195 | T3.5 sheet-first quick add: the sheet opens at once on the phone's guess, "Improving with AI…", the AI fills only untouched fields, "✓ Updated by AI" for a moment when it changed something | Codex reviewed and asked for changes; **all fixed at 61dacfe** (draft held at the first Save tap; a friend added counts as a split change; red proof on pre-fix main; ViewModel teardown). Waiting on Codex's re-review and exact-head CI |
| #210 | Play store listing: 6 screenshots (1081×1921) and the 1024×500 feature graphic, rendered by test-only `ui/store/` | Codex reviewed; **all fixed at 15390c2** (capture lifecycle, month-start dates, provenance, phone checks). Owner approves and uploads |
| #212 | UI audit of every screen at four device sizes, 28 findings, report `docs/production-readiness/audits/ui-audit-2026-11.md` | Green, not yet reviewed. **Needs the same two fixes #210 got** (below) before Codex will pass it |
| #214 | Audit batch A: money never cut or misread ("1.27L" in the day strip, "₹300" not "₹0" for a pending payment, "<1%", "You're owed" wraps) | Green, stacked on #212 |
| #215 | Audit batch B: consistency (no empty Insights tiles, disabled chips look disabled, "5 Nov 2026" and ₹ in Subscriptions, "What Rohan will see", sort aligned, "Spent most on") | Green, stacked on #214 |
| #220 | CI fixture: `BackToHomeTest` stops its ViewModels before closing Room (the random "connection pool has been closed" failure) | Test-only, just opened |

Merge order: #220, #210, #212, #195, #214, #215. Every PR adds a `CHANGELOG.md` line under
Unreleased, so each merge conflicts the next there: keep both sides.

### Open item on #212 (do this first if Codex hasn't)

The audit harness has the two problems Codex found in #210's captures:
1. `ui/audit/AuditFixture.kt` `stamp()` caps "before now" and can land in the previous month just
   after midnight on the 1st. Copy #210's fix (`StoreFixture.stamp`: injectable clock, never before
   the month starts) and its boundary test.
2. `ui/audit/UiAuditCapture.kt` closes Room without clearing its `MainViewModel` /
   `SharedGroupsViewModel`. Copy `StoreFixture.stopViewModels` (ViewModelStore, idle the main looper
   until the scope job finishes, then close).
Then merge `ui-audit` up into `ui-fix-money` (#214) and `ui-fix-consistency` (#215).

## Owner decisions this session (also on #188)

- **Largest system font (200%) is deferred** until after launch ("focus on the normal user"). The
  audit keeps those findings (U3, U4, U5, U10, U11, U12) and the harness still renders `font-2x`.
- **Small phones (360 dp) stay in scope**: common in India.
- **Home hero shows whole rupees** (no paise), only the hero; rows and sheets keep paise. **Approved, not built** (U27).
- **On short phones Home's top section shrinks** so a few entries show on the first screen. **Approved, not built** (U17).
- "Good UX" over silence for AI changes: "✓ Updated by AI" (built in #195).
- Still unanswered: the AI notice's "While Kharcha Paani is free…" clause (D5 pricing hint): keep or drop?

## How to see the app without a phone (no emulator: the container has no KVM)

The render harness is the closest thing: real composables, real `MainViewModel`, real Room.

- **Every screen:** `./gradlew :app:testDebugUnitTest --tests "*.ui.audit.*"` writes
  `app/build/test-results/testDebugUnitTest/ui-audit/{pixel,small,font-2x,new-user}/*.png`
  (27 screens each). Sheets and dialogs are opened the way a tap opens them and every window is
  drawn in order, so a sheet appears over the app. Add a screen: one `@Test` in `UiAuditCapture`.
- **Store art:** `--tests "*.ui.store.*"` writes `store-listing/*.png`.
- Look at the PNGs with the Read tool; downscale with PIL before committing evidence.

### Local build in this container (CI is still the authority)

Maven Central rate-limits this container (HTTP 429). What worked:
- Android SDK: download `commandlinetools-linux-*.zip` from dl.google.com into `/tmp/claude-0/sdk`,
  `sdkmanager "platforms;android-36.1" "platforms;android-36" "build-tools;36.0.0" "platform-tools"`,
  then `export ANDROID_HOME=/tmp/claude-0/sdk`.
- `~/.gradle/init.d/central-mirror.gradle`: put
  `https://maven-central.storage-download.googleapis.com/maven2/` **first** in
  `pluginManagement` and `dependencyResolutionManagement` repositories (`addFirst`).
- Robolectric fetches `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7` at test
  time: curl the jar and pom (with .sha1) from the same mirror into `~/.m2/repository/...`.
- Run with `--no-daemon --no-configuration-cache -q`; a targeted `--tests` run takes 2–5 minutes.
- A command chain that greps test results must stop on failure; one push here went out with a
  failing local run because `grep` succeeded.

## Rules that bit this session (Codex enforces them in review)

- **ViewModel teardown in tests:** never close Room while a real `MainViewModel` is alive. Put it
  (and `SharedGroupsViewModel`) in a `ViewModelStore`; in `@After` release any held fake network,
  `store.clear()`, wait for `viewModelScope`'s job, then `db.close()` and `Dispatchers.resetMain()`.
  With a compose rule the job finishes on Robolectric's real main looper: loop
  `ShadowLooper.idleMainLooper()` until done; `runBlocking { join() }` deadlocks.
- **Red proof means a run:** for a bug, show the new test failing on pre-fix code. Here: a
  `git worktree` of `origin/main`, copy the test in, run it, record head and failure.
- **No fixed sleeps:** wait for an explicit completion (e.g. `client.dispatcher.runningCallsCount()`)
  then poll briefly for the wrong state.
- Each PR: solo-log row in `docs/agents/solo-log.md`, `CHANGELOG.md` line, "Check on the phone",
  latest `main` merged, all three CI jobs green at the exact head. Claim on #188 first; list shared
  files (`MainActivity`, `MainViewModel`, shared sheets/components) there before editing them.
- Never write `@claude` on GitHub; the automated review skips `claude/` branches on purpose.

## The creative stream: what's next

From [HANDOFF.md](HANDOFF.md) Stream C, updated:

1. ~~Quick add opens instantly~~: #195.
2. **The three screenshot moments**: confirm sheet, balance with rolling digits, widget. Audit them
   against `docs/history/prd-2026-09-cloud-migration.md` Part B and raise them. Codex's parked #139
   (hero at large fonts) is under the 200% deferral; check with Codex before touching the hero.
   Start with the two approved Home changes (whole-rupee hero, compact top on short phones).
3. ~~Store assets~~: #210.
4. **20+ friends** (TODOS 6): compact rows and frequent people first, building on Antigravity's Y15.
5. **First-run walkthrough** (TODOS 10): short, skippable, shown once; teach swipe gestures and quick add.
6. **Where the money went, then budgets** (work order §9): a clear monthly picture, then per-month and
   per-category budgets with friendly nudges. Design doc first; no paid-plan hints (D5).
7. Evidence-gated: the widget remembering a split, "Rohan paid" by voice.
8. Owner's yes needed: light theme, Hindi/Hinglish, saved cards, App Links.

Remaining audit findings (report "Fixes" table): U8 live amount grouping (needs a phone), U13 colour
picker "taken" look, U19 ₹ fallback in the answer title, the polish set U20–U23, U25, U26, U28, and the
deferred 200% set. The same harness renders before/after for each.

## What wasn't verified

Nothing from this session has run on a phone. Every check is a Robolectric render or unit test.
Each PR lists its "Check on the phone".
