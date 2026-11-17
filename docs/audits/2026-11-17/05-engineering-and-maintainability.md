# Engineering and maintainability, 2026-11-17

Architecture, data layer, tests, build and CI, and the state of the repository itself, on
`origin/main` at `8839b3f`. Security and SQL are in the security report (kept out of the public repo, see the README); UX is in
[04-ux-and-feature-gaps.md](04-ux-and-feature-gaps.md).

**How it was done.** A reviewer read `MainViewModel.kt`, `MainActivity.kt`, `LedgerRepository.kt`,
the sync engines, the domain maths, the build files, workflows and a sample of UI and test files,
and measured the rest with grep and line counts. The headline numbers were then re-run by a second
pass (marked ✔). No Gradle build was run (the owner's machine can't spare the memory), so anything
that needs a build to confirm is in "Unverified" at the end. Fix sizes: S = hours, M = a day or two,
L = a week or more.

Counts: **0 Blocker, 7 High, 18 Medium, 10 Low.**

## The honest overall picture

For an app written almost entirely by agents in two months, the engineering underneath is
unusually disciplined. The money maths is done in paise with shared test vectors, Room has a
migration and a test for every schema step, the sync engines are careful and commented with their
reasons, and there are no TODOs, no `GlobalScope`, eight `!!` in 38,000 lines.

The cost of how it was built shows in three places:

1. **Two files hold the app together and everything collides in them.** `MainViewModel.kt` (3,161
   lines) and the root composable in `MainActivity.kt` (892 lines). Every feature adds to both, so
   every pull request conflicts with every other.
2. **The tests are many and partly hollow.** 1,413 tests, but about a tenth are copy-pasted string
   checks or screenshots that assert nothing, 600 of them block on real time, and the code most
   likely to hurt users (SQL and RLS, the Edge Function, the minified release build, sign-in and
   account deletion) is not exercised by CI at all.
3. **The instructions for agents contradict each other.** Eight "start here" pages, three of which
   name a different lead, and one megabyte of docs. The next agent will read stale orders.

None of this stops a launch. All of it makes each week after launch slower and riskier than the last.

## High

### ENG-01 ✔ · `MainViewModel` is a 3,161-line class that does eighteen jobs

`app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt`: 194 functions, 110
`StateFlow` declarations, 83 coroutine launches, 62 writes to the status message, imports running
from line 3 to 174. It builds at least nine collaborators inside itself, so a test has to construct
the whole thing. The check that blocks actions during a full backup is a line pasted at 53 call
sites (`if (fullBackupBlocksActions()) return`); a new action that forgets it writes during a
restore.

The map, so the split can be planned:

| # | Concern | Lines |
|---|---|---|
| 1 | Full-backup lock, recovery journal, maintenance | 195–207, 2897–3125 |
| 2 | Action runner, error net, status messages | 219–242, 1025–1041 |
| 3 | Sync status and retry (calls DAOs directly) | 244–285 |
| 4 | Settings: AI choice, discard prompt, bill photos, category prefs, reminder, quiet hours | 278–404, 2686–2727 |
| 5 | Notification moments and offers | 300–345 |
| 6 | Subscriptions | 380–446 |
| 7 | Categories, UPI payee | 448–519 |
| 8 | Tab, search, filter, Home and Insights flows, feed, balances | 519–610, 695–700 |
| 9 | Settlements (retract, answer), colour fitting | 611–694 |
| 10 | Capture: text, voice, receipt and PDF parsing, the on-phone guess | 701–823, 1397–1730 |
| 11 | The entry draft's fields and share recalculation | 824–870, 1728–1965 |
| 12 | Bill photo | 915–1024 |
| 13 | Split by items (already extracted into a controller) | 1046–1192 |
| 14 | Entry open, edit, save, delete, undo | 1211–1400, 2000–2184 |
| 15 | Statement import | 1032–1040, 1554–1672 |
| 16 | Groups, friends, settle-up, IOU, simplify | 2185–2420 |
| 17 | AI notice, AI-off nudge, Insights questions, trip review | 2421–2685 |
| 18 | CSV export, local backup, "everything" backup | 2729–3125 |

**A split that does not re-architect what works** (one PR per step; `MainViewModel` stays as a thin
facade at first so `MainActivity` compiles unchanged):

1. **Mechanical extractions, S each.** Plain controllers that take the scope and the repository, the
   way `ItemSplitController` and `SharedGroupsController` already do: `BackupController` (1, 18,
   owning the lock), `AiPolicyController` (17 and the AI parts of 4), `SettingsFacade` (4, 5, 7),
   `SubscriptionsController` (6), `StatementImportController` (15).
2. **The entry flow, M.** An `EntryController` holding one `StateFlow<EntryDraft>` plus capture,
   bill photo, item split and save/delete/undo. This is the area that changes most, so it pays most.
3. **Three view models by screen, M.** Home (8, 9), Splits (16), Account (1, 3, 4, 17, 18).
   `AccountSessionHolder` already keeps a per-account `ViewModelStore`, so this is supported.
4. **One gate.** Move the backup check into `launchAction` (line 229) and delete the 53 copies.
5. **Leave the balance maths where it is** (the owner's rule).

### ENG-02 ✔ · The root composable is 892 lines and collects about a hundred flows

`MainActivity.kt:455-1349`, 13 parameters. In lines 496–581 alone there are 58 `collectAsState`
calls, 14 of them lifecycle-aware. `ConfirmBottomSheet` takes about 48 parameters and passes them
all on; `HomeScreen` takes 37. 34 functions are longer than 100 lines. Every new sheet adds a
collection line, a parameter and a branch here.

Fix (M to L): one host composable per feature that collects its own state; replace the 48 parameters
with an `EntryDraftState` and an `EntryActions` interface; switch the plain `collectAsState` calls to
the lifecycle version (S, mechanical, and it stops background work when the app is not visible).

### ENG-16 ✔ · 39 copy-pasted `*StringResourcesTest` files

2,947 lines for about 40 assertions. Each repeats some 55 lines of setup (Robolectric, a Compose
rule, WorkManager, an in-memory database, a complete `MainViewModel`, a polling helper) to check one
or two English sentences. They break on any wording change, they will all be wrong for a Hindi or
international build, and `NoHardcodedTextLiteralsTest` plus `StringsXmlFormattingTest` already guard
what they were written to guard. The same polling helper is pasted into 50 files.

Fix (M): one shared test rule and one parameterised test per screen. About 2,000 lines and several
minutes of CI go away.

### ENG-17 ✔ · Tests wait on real time

600 `runBlocking` against 147 `runTest`, 84 `delay(` calls, 45 reads of the wall clock in tests.
Examples: `ActivitySearchTest.kt:60,75,151` (a 10-second timeout around a real sleep);
`BackToHomeTest.kt:87-88`. The cause is in the main code: 80 hard-coded `Dispatchers.*` (27 in
`LedgerRepository.kt` around Room calls that are already main-safe) and no injected clock. The git
history shows the price ("Fix flaky test timing", the thread-dump watchdog in `android-ci.yml`).

Fix (M): inject a dispatcher and a `Clock` into the repositories, delete the redundant
`withContext(Dispatchers.IO)` wrappers, convert the ten most-polled tests to `runTest` as the pattern.

### ENG-21 ✔ · CI only tests Kotlin

`android-ci.yml` runs unit tests, lint and a release bundle, and one Node check of the split page.
It never mentions Supabase or Deno. So the 16 SQL and RLS test scripts, the Edge Function's Deno
tests, and the store-listing and legal-site checks gate nothing. The sync tests run against
hand-written fakes that re-implement the server (`GroupSyncFakes.kt`); the real
`SupabaseTransactionRemote` with its string column names is executed by no test. A renamed column or
a changed policy passes CI and fails on a phone.

Fix (M): a CI job that starts a local Postgres (the repo already has `supabase/tests/local/run.mjs`),
runs `supabase/tests/*.sql` and `deno test`, and is required for pull requests touching `supabase/`.
This is also the prerequisite for security S-05 (stop testing on production).

### ENG-22 · The release process still leans on memory

`release.yml` is good where it exists: tag equals version name, version code strictly higher than
the last tag, signature verification, the R8 mapping attached. Missing: a check that the APK's
certificate fingerprint is the one registered for Google sign-in (it is public and sits only in the
manual `scripts/release-smoke.sh:17`; this exact mistake broke sign-in for three releases); lint on
the release variant; any automated run of the minified app; Gradle dependency verification. The
Play upload will be manual.

Fix (M): `apksigner verify --print-certs` compared against the known fingerprint, `lintRelease`, and
make the release smoke test a checklist item that the workflow refuses to skip.

### ENG-28 ✔ · The agent instructions contradict each other

- `AGENTS.md:14`: "Codex: you lead until 2026-11-06, with merge rights." Today is 2026-11-17.
- `docs/agents/work-split.md:24` repeats that window. `docs/antigravity/HANDBOOK.md` says in one
  place that Claude leads again and in another that Codex leads and merges.
- `CLAUDE.md` says "v1.0–v1.4 have shipped". The changelog is at 1.7.0.
- The reading chain is `CLAUDE.md` → `PRD.md` → the work order's §0 → `plan-1.6-to-launch.md` →
  `work-split.md` → `codex-lead` → `HANDBOOK.md`. Eight pages say "read this first".
- The work order cites `docs/handoffs/2026-10-24-owner-decisions.md`, which is untracked and exists
  only on the owner's laptop.
- `docs/architecture.md` describes Gemini only; typed quick adds go to Groq.
- 64 Markdown files, about 1 MB. The coordination pages alone are over 200 KB.

Why it matters: agents obey what they read. Contradictory authority is how one of them ends up
merging what it should not, or redoing finished work.

Fix (M): one `docs/START-HERE.md`, at most a page: what phase the project is in, who may merge what,
the hard stops, and links. `CLAUDE.md` and `AGENTS.md` shrink to pointers to it. Move to
`docs/history/`: the Codex and Antigravity handbooks and logs, `claude-cloud/`, `plan-1.6-to-launch`,
the UI audit and investigation notes, the October audits, the 1.5 and 1.6 device checklists. Keep
live: `architecture.md` (refreshed), `release-checklist.md`, `incident-plan.md`, `distribution.md`,
`legal/`, `store-listing/`, `designs/`, and this audit folder.

## Medium

**ENG-03 ✔ · The entry being typed is 14 separate flows of strings, and is lost if Android kills
the app.** `MainViewModel.kt:824-869`. `SavedStateHandle` is used nowhere. `"expense"` and `"income"`
are string literals in 76 places; the split type is a string too. Fix (M): one `EntryDraft` state,
enums with a Room converter that keeps the stored strings (no migration), persisted draft. Codex's
draft PR #252 attempts the last part and is stuck in conflicts.

**ENG-04 · Layers leak.** The view model calls DAOs directly at 11 sites, including its own database
transaction. Five `domain/` files import Android (`ActivityFeed.kt`, `GroupFeed.kt`,
`GroupExpenseCredit.kt` take a `Context`). `Money` lives in `ui.format` but the repository and the
domain import it. `LedgerRepository.getLedgerForPerson(personId, context)` builds localised text
inside the repository. Fix (M): move `Money` to `domain`, pass a small string-provider interface
instead of `Context`. Do not split into Gradle modules before launch.

**ENG-05 · No dependency wiring, five kinds of settings storage.** No DI library (fine), but
`SupabaseClientProvider.get(` appears 12 times, `LedgerMode(context)` is constructed 14 times, and
preferences are spread over DataStore and four separate SharedPreferences wrappers. Fix (M): one
hand-written `AppContainer` created in `KharchaPaaniApp`.

**ENG-06 ✔ · 80 hard-coded dispatchers.** See ENG-17.

**ENG-07 ✔ · "Me" is the number -1 in 105 places, and "settled" is 0.005 in about 40.**
`const val ME = -1L` is declared separately in 19 files. The same -1 is stored in `group_members`,
`splits` and `settlements`, where no foreign key can protect it. The half-paisa threshold is
repeated across `FriendRow`, `PersonLedgerSheet`, `GroupDetailSheet`, `SettleUpSheet`, `TripCard`,
`HisaabItemiser`, `AskMessage` and more. Fix (S, no behaviour change): one `Ledger` object with
`ME`, `isSettled()`, `owesMe()`.

**ENG-08 · Money is a `Double`, with sound rounding at the edges.** Room stores REAL; rounding goes
through `roundPaisa`, `SplitRounding` works in whole paise, formatting uses `BigDecimal`. The weak
spots are `personBalances` and the local-group branch of `groupsWithDetails`
(`LedgerRepository.kt:104-190`), which add up Doubles and compare against a threshold. Not a launch
problem. It becomes one with a second currency (see the international report): do the `Paise` value
type as part of that work.

**ENG-09 · Three versions of "who owes what", and a stale TODO.** `TODOS.md` says the per-friend
balance is computed twice; at this commit it is not (both go through `balanceEntries`, pinned by
`PersonBalanceOneSourceTest`), so that entry can be closed. What remains: the local-group branch
credits differently from `GroupBalances.netPositions`, and `DailySpend`, `MonthAnalytics`,
`CsvExport`, `GroupInsights` and `AiContext` each carry their own payer logic. Fix (M): route local
groups through `GroupBalances.netPositions`.

**ENG-10 · Every write recomputes everything.** `LedgerRepository.kt:81-87` exposes `SELECT *` flows
with no limit; the feed, balances, groups, analytics and each friend's history recompute from them
on every change. `personBalances` is people × (splits + settlements). There is no test with
thousands of rows. Fix (M): measure first with 20,000 entries and 50 friends, then one pass for all
friends and a shared upstream flow.

**ENG-12 · The sync state machine is written inline.** `LedgerRepository.updateTransactionWithSplits`
(`:438-507`) is 70 lines of nested decisions about pending deletes, retractions and re-keying. Fix
(M): a pure function from (existing row, edit) to transition, with a table-driven test.

**ENG-13 ✔ · A refused edit or delete does nothing, says nothing, and still asks for a sync.**
`LedgerRepository.kt:447-453`: `return@withTransaction` when the row can't be edited. The UI hides
what isn't allowed, so this is a safety net, but if the two ever disagree the user sees "saved" and
nothing was. Fix (S): return a result and show it through `launchAction`.

**ENG-18 · Sync is tested against a fake server only.** See ENG-21.

**ENG-19 ✔ · The lint baseline hides 413 issues; 311 are one kind.** `app/lint-baseline.xml`: 311
`NewApi` (the sampled ones are `java.time`, which desugaring covers), 16 `HardcodedText`. If lint is
not seeing the desugaring setting, a real API-26-only call in those files is hidden too, and minSdk
is 24. Fix (S): regenerate the baseline and read what is left.

**ENG-20 · About 50 "tests" assert nothing.** `ui/ScreenRenderCapture.kt` (23 tests),
`ui/audit/UiAuditCapture.kt` (27) and `GreetingScreenshotTest.kt` render screens with no golden
comparison. They are useful as tools; they should not count as tests. Fix (S): move them out of the
unit suite, or add real golden images for five key screens.

**ENG-23 ✔ · A fresh clone can't make a debug build, and the README says something else.**
`app/build.gradle.kts` signs debug builds with `${rootDir}/debug.keystore`, which is ignored and not
in the repo. Fix (S): drop the custom debug signing config.

**ENG-24 · Dependabot is steered by comments that are no longer true.** `libs.versions.toml` has
Kotlin 2.4.21, while its comments and `.github/dependabot.yml:18-22` say "Kotlin 2.2.10 can't
consume 3.7.0+" and ignore those updates. The Compose BOM is 2025.08 and its Dependabot PR (#287) is
failing; the Firebase BOM major (#288) and a failing minor group (#290) are open. Fix (S to M):
remove the stale ignore rule, then take the Compose jump as its own PR with a release smoke test on
a phone.

**ENG-25 · The R8 rules are right and nothing runs them.** 28 lines, each with a reason. No
automated run of the minified app exists, and that is where release-only failures come from. Two
Play libraries are hard-coded in the build file instead of the version catalog.

**ENG-26 · The AI workflows are mostly safe.** `pull_request` (not `pull_request_target`), read-only
permissions, actions pinned by SHA. Gaps: the plugin marketplace is fetched unpinned into a job
that holds `id-token: write`; the review job also runs on Dependabot PRs where it has no secrets;
`release.yml` interpolates `${{ inputs.tag }}` straight into shell (pass it through `env`).

**ENG-29 · The changelog is a work log.** 836 lines, 285 bullets, one of them 1,188 characters long,
five PR numbers cited across about 290 PRs, and test-only items mixed in with user-facing ones. Fix
(S to M): user-facing lines only, at most two sentences, with the PR number.

**ENG-30 · 170 remote branches, four author identities, no commit convention.** 113 commits landed
on one day and 108 on another. Fix (S): delete merged branches, turn on automatic deletion,
squash-merge only.

**ENG-33 · Compose: good tokens, weak boundaries.** Good: theme tokens are used, 840
`stringResource` calls against 3 literals, test tags throughout. Weak: 401 raw `.dp` values, screens
take Room entities and strings as parameters (19 of 103 UI files import `data.db.entity`),
`today: LocalDate = LocalDate.now()` as a default parameter hides a clock, 6 previews for 238
composables.

## Low

- **ENG-11 ·** `GeminiRepository.requireAiAllowed()` throws `CancellationException("AI is off")` for
  a user setting, which callers can confuse with real cancellation. `LocalBackupRepository.kt:268,
  300` use `!!` on values from a user-supplied backup file: check a malformed file ends in a
  message, not a crash. `AskSentReceiver.kt:22` launches on an unscoped `CoroutineScope`.
- **ENG-14 ·** Room is sound. No snapshot for schema v1; confirm v1 never shipped.
- **ENG-15 ·** Sync corners with no test: a row deleted on the server while edited locally comes
  back on the next push (documented, untested); `markSyncedIfUnchanged` compares a millisecond
  timestamp; `SyncWorker` has no catch-all (security S-06).
- **ENG-27 ·** No baseline profile, so cold start on cheap phones is slower than it needs to be.
- **ENG-31 ·** `tools/ai-bench/` commits 1.8 MB of sample bills and raw result dumps.
- **ENG-32 ✔ ·** `README.md` is still the AI Studio template ("Run and deploy your AI Studio app")
  on a public repository, with build steps that are wrong. Rewrite it in 40 lines.
- **ENG-34 ·** The account-switching machinery (`LedgerDatabases`, `AccountSessionHolder`,
  `CloudAuthOwner`, `SessionClosingWork`, `PendingDeletes`, the sync pass lock) is correct-looking,
  well tested and understood by nobody but the agent that wrote it. Write one sequence diagram.
- **ENG-35 ·** `GeminiRepository` (926 lines) mixes transport, prompts, JSON parsing with `org.json`
  and bitmap encoding, and its name is out of date now that Groq is involved. `DebtSimplifier` is
  cubic in the number of friends. `LocalStatementParser` creates 13 date formatters per row.

## The ten riskiest paths with no real test

1. **The minified release build is never run.** CI builds the bundle and stops.
2. **The real contract with Supabase.** Fakes in Kotlin; SQL and Deno suites outside CI.
3. **Sign-in, sign-out with data removal, account deletion UI.** `AccountSheet.kt` (1,008 lines),
   `DeleteAccountSheet.kt` and `BackgroundAuth.kt` are referenced by no test.
4. **Android killing the app mid-entry.**
5. **A pull meeting a queued local edit of a row deleted elsewhere.**
6. **A refused edit or delete at the repository (ENG-13).**
7. **Scale:** nothing with thousands of rows.
8. **`SyncScheduler`** (uniqueness, constraints, backoff): no test. `SyncWorker`: two.
9. **Restoring a corrupt or hostile backup file.** Plenty of round-trip tests, no malformed input.
10. **The Edge Function's `index.ts`** (the cap, the Groq fallback, the size limit): no Deno test.
    Also `VoiceRecorder`, `FlexibleUpdates`, `ReviewRequester`.

## What is good and must not be touched

- **Split rounding** (`domain/SplitRounding.kt`): whole paise, the leftover goes to the payer,
  deterministic; the same test vectors check the JavaScript in `site/split.html` in CI.
- **Money formatting** with guards that came from a real crash.
- **Room discipline:** a schema snapshot and a migration test per version, and a test that a missing
  migration fails instead of wiping.
- **The sync engines:** idempotent pushes by phone-made id, an "if unchanged" check, bad rows
  isolated and resent one by one, a cursor overlap with its reasoning written down, one pass at a time.
- **Debt simplification and `GroupBalances.netPositions`.**
- **The release workflow's guards** and SHA-pinned actions.
- **The small R8 rule file.**
- **The controller pattern** in `ItemSplitController` and `SharedGroupsController`: it is the
  template for taking `MainViewModel` apart.
- **The test spine:** roughly 400 domain and 330 data tests are real behaviour tests
  (`PersonBalanceOneSourceTest`, `LocalBackupRoundTripTest`, `SyncEntityMappingSafetyTest`).

## Unverified (needs a build, a profile or a console)

1. Whether the 311 `NewApi` baseline entries are all false positives (ENG-19).
2. Which Compose and AndroidX versions actually resolve (ENG-24).
3. Whether local-group balances can disagree with `GroupBalances` on an old unbalanced split (ENG-09).
4. Whether the silent no-op in ENG-13 can be reached from the UI.
5. The real recomposition cost of the root composable (needs compiler metrics or a profile).
6. How close CI's unit-test job is to its 30-minute limit.
7. Not read in depth: the bodies of `AccountSheet`, `ConfirmBottomSheet`, `GroupDetailSheet`,
   `EverythingBackupRepository`, `LocalBackupRepository.restore`, `ReceiptSyncEngine` and the widgets.
