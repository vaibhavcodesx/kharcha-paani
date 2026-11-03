# Claude cloud session, 2026-11-03: what was done and what's next

Written at the end of the session for the next Claude Code cloud session. Read
[HANDOFF.md](HANDOFF.md) first (standing permissions and hard stops), then this page.

## The situation when this session ended

- **1.6.0 is a GitHub phone-test prerelease**; the signed build succeeded. This is not a Play
  rollout or passed phone acceptance. The product freeze that held product PRs is over. Codex leads until 2026-11-06 and **does all merging**;
  Claude opens PRs, answers reviews and keeps them green.
- Antigravity (Gemini Flash) works a routine queue from `docs/antigravity/HANDBOOK.md`; Codex merges it.
- Progress log for every agent: [issue #188](https://github.com/vaibhavcodesx/kharcha-paani/issues/188).

## PRs from this session

| PR | What | State at hand-off |
|---|---|---|
| #195 | T3.5 sheet-first quick add: the sheet opens at once on the phone's guess, "Improving with AI…", the AI fills only untouched fields, "✓ Updated by AI" for a moment when it changed something | **Merged** after review fixes (draft held at the first Save tap; a friend added counts as a split change; red proof on pre-fix main). In the next build; phone checks in the PR |
| #210 | Play store listing: 6 screenshots (1081×1921) and the 1024×500 feature graphic, rendered by test-only `ui/store/` | **Merged.** Owner approves and uploads |
| #212 | UI audit of every screen at four device sizes, 28 findings, report `docs/production-readiness/audits/ui-audit-2026-11.md` | Green; **Codex asked for changes** (checklist below) |
| #214 | Audit batch A: money never cut or misread ("1.27L" in the day strip, "₹300" not "₹0" for a pending payment, "<1%", "You're owed" wraps) | Green, stacked on #212; **Codex asked for changes** (below) |
| #215 | Audit batch B: consistency (no empty Insights tiles, disabled chips look disabled, "5 Nov 2026" and ₹ in Subscriptions, "What Rohan will see", sort aligned, "Spent most on") | Green, stacked on #214; no production blocker, **more tests asked** (below) |
| #220 | CI fixture: `BackToHomeTest` stops its ViewModels before closing Room (the random "connection pool has been closed" failure) | **Merged** at `7f3ef32`; test-only |

Verified merge commits: #195 `0337c5c`, #210 `bb954ba`, #220 `7f3ef32`.

This documentation handoff (#222) can land independently. For the remaining UI work, review
#212, then #214, then #215; they remain held for the findings below, not the former release freeze.
Every PR adds a `CHANGELOG.md` line under
Unreleased, so each merge conflicts the next there: keep both sides.

### Codex's open review asks: the next session's first checklist

Reviews arrived at the very end of the session (18:14–18:22 UTC). Read each PR's latest comment first.

**#212 (audit harness and report):**
- [ ] `UiAuditCapture`: keep `MainViewModel` *and* `SharedGroupsViewModel` in a `ViewModelStore`; at
      teardown dispose the composition, clear and await jobs (idle the main looper), then close Room;
      safe after a partial setup. Copy `StoreFixture.stopViewModels` from #210.
- [ ] Each screen waits for its *real* content (people, balances, groups, analytics, the sheet's
      async content) before the shot, not just a non-empty transaction list or Compose idle, so a
      loading frame can't pass as a finding.
- [ ] `AuditFixture.stamp`: the month-start bug #210 fixed. Copy its injectable clock and boundary test;
      record the fixture's date, time and zone in the report.
- [ ] Report accuracy: **102** images, not 105; say which dialogs, error and signed-out states aren't
      covered; add an image link for every finding U7–U28; narrow "every figure was right" (the
      harness only asserts a PNG exists; figures were checked by eye).
- [ ] Put the owner's 200% deferral into the report itself, here as well as in #214. (#214's commit
      already edits it; make sure the #212 baseline says so.)
- [ ] Solo-log row, latest `main`, three green jobs at the exact head. Then merge `ui-audit` into
      `ui-fix-money` (#214) and that into `ui-fix-consistency` (#215).

**#214 (money never cut):**
- [ ] `Money.compactDigits` caps decimals but not width: ₹10,01,00,000 becomes "10.01Cr" (7 chars),
      ₹999.99 crore "999.99Cr" (8), and a day's sum can be larger. Use magnitude-dependent precision
      (e.g. "10Cr", "999Cr") or measured fitting so it always fits the 52 dp cell; keep the exact amount
      in the spoken label and the tap. Add boundary tests at the supported maximum and summed days.
- [ ] Real-Room regression for the pending-payment row: insert a PENDING settlement in Room, read the
      friend history through `getLedgerForPerson` and the real sheet, show it failing on pre-fix code
      ("₹0") and passing after. (The current test builds `PersonLedgerItem`s by hand.)
- [ ] Solo-log row; after #212 lands, latest `main`, three green jobs.

**#215 (consistency):** Codex found no production blocker.
- [ ] Real ViewModel and Room test typing and pasting through the ₹-prefixed subscription amount
      (the saved value is unchanged by the prefix).
- [ ] Tests: a disabled chip doesn't respond to a click, and an income-only month hides the
      spend highlights.
- [ ] Solo-log row, latest `main`, three green jobs.

**#195 and #210 merged.** The listing assets are in `docs/store-listing/assets/` for the owner to approve and upload.
**#220 is merged** at `7f3ef32`.

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
  (27 scenarios: 27 images in each of the three seeded variants and 21 in `new-user`, **102 images**
  total). Sheets and dialogs are opened the way a tap opens them and every window is
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
  Use a bounded completion wait (for example, 10 seconds), driving the fixture's test dispatcher
  and Robolectric main looper as applicable. A blocking `join()` can stall if its child jobs need
  the same blocked dispatcher/looper; advance that work while waiting. Keep Room open until the
  jobs complete, and report a timeout as a test failure.
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
