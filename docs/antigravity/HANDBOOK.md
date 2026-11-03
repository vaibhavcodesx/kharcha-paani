# Kharcha Paani: handbook for long-running agents

Written 2026-11-03 by Claude for **Antigravity (Gemini 3.8 Flash)** and **Claude Code cloud sessions**. You
start each session knowing nothing, so this page plus `PROGRESS.md` are your memory. The owner is Vaibhav,
and they test on their phone. Claims here about the code were true on 2026-11-03: check them before relying
on them.

## 0. Start every session here

1. `git fetch origin`, `git switch antigravity/integration`, `git pull --ff-only`.
2. Read `docs/antigravity/PROGRESS.md`. Its session log says what happened last.
3. **First session only:** do item O1. If the guard doesn't block its test command, stop and tell the owner.
4. Skim sections 1–5 so you know the product. Read the design section before any UI change.
5. Take the first `todo` item you're allowed (section 7). Run the loop in section 6. Then the next one.
6. Before stopping: update `PROGRESS.md`, push, and report what was done, what's parked and any questions.

Cloud sessions: read section 8 first; your role differs.

## 1. What this app is, and why

A native Android app for Indian friends to log what they spend and split it with each other. Offline-first:
everything saves on the phone and syncs when it can. Kotlin, Compose, Room (v10), Supabase, Gemini through
an Edge Function. Package `com.vaibhav.kharchapaani`. Read `PRD.md`.

**Capture is the product.** That's the conclusion of the owner's office hours. The two friends who use it
daily add entries from the home-screen widget. If adding is slow or the split comes out wrong, nothing else
matters. "Say the split" (#114) lets quick add understand "with Rohan and Ankit" so people stop fixing
splits afterwards. Baseline: 16% of group expenses edited within 30 minutes; target under 5%.

**The bar, in the owner's words:** if 1,000 strangers install it from the Play Store tomorrow, none of them
should feel it's "just another vibe-coded app with bugs". It handles people's money records and friendships:
a wrong balance or an awkward message is worse than a missing feature.

**The road ahead:**
1. **1.6.0** (now): first build with the Gemini proxy, on the owner's phone. Then delete the old Gemini key.
2. **Closed test:** Play requires 12+ testers opted in for 14 days in a row; the owner is recruiting 15–20.
3. **Production** on Play.
4. **Free for at least 3 months.** No pricing, paywall, billing or "Pro" hints, in code or in chat (D5).
5. **After that, what makes it a daily habit:** seeing where the money went, budgets with friendly nudges,
   easy settle-ups, forgiving AI quick add, then recurring roommate splits, itemised bills, Hindi/Hinglish,
   trips in other currencies. **Don't start these.** They're here so your work doesn't block them.

**Out of scope forever:** moving money (UPI hands off to the user's own app), iOS, non-Google sign-in,
self-hosted Supabase, PowerSync. **Never re-architect** the ledger/balance pattern, debt simplification, the
AI pipeline or the groups model. Improve them in place.

## 2. Hard limits

`.agents/hooks/guard.py` enforces most of these for Antigravity. **A denied command is a limit, not an
obstacle.** Don't rephrase it, split it, script around it or edit the guard. Skip the item and note it.

- **Never merge to `main` or push to it.** You merge only into `antigravity/integration`, by squash, after CI
  is green on that PR, and only for GREEN and YELLOW items.
- **Never touch Supabase:** nothing under `supabase/`, no SQL against any server, no MCP connection to it.
- **Never run** releases, workflows, repo settings, secrets, an emulator, `adb`, or release builds.
- **Never read, print or commit** `.env`, `*.jks`, `*.keystore`, `keystore.properties`. Never put a key,
  token or real person's data in code, tests, logs or PRs.
- **Never close or reopen a PR.** Don't touch #71 or Dependabot PRs. Never write `@claude` in a PR or comment:
  it spends the owner's Claude quota.
- **RLS, auth, security or a non-additive migration:** owner review. Open it, title it `[owner review]`, leave it.
- **Never weaken a test to pass:** no removed assertions, `@Ignore`, longer sleeps or narrower checks. If you can't
  fix it honestly, leave it red and park the item.
- **Two failed CI runs on one item: park it.** Three items parked in a row, or integration broken and you can't
  fix it in one PR: stop and report. Something is wrong that more attempts won't fix.
- **Stay in scope.** No reformatting, renaming, reordering imports or "while I'm here" cleanups. Each diff should be
  explainable by its item. If you find something else wrong, add it to Questions in `PROGRESS.md`.
- **Don't invent.** If a file, API or behaviour isn't where this page says, read the code and report the difference.
  Never guess a balance, an editor's name or a server rule.

## 3. How the code works

| Area | Where | Know this |
|---|---|---|
| App shell | `MainActivity.kt` (hot file) | `SplitFlowApp` collects state at the root; sheets open from here |
| State | `ui/viewmodel/MainViewModel.kt` (hot, ~1900 lines) | Kotlin initialisers run **in declaration order**: a misplaced property crashed every launch once |
| Session | `ui/session/` | Per-account ViewModel store; `AccountLeftovers` clears data on account change |
| Ledger | `data/repository/LedgerRepository.kt` | `balanceEntries()` is the one source for balances and friend history |
| Rules | `domain/` (40 files) | Pure logic: `SplitRounding`, `DebtSimplifier`, `ActivityFeed`, `HisaabItemiser`, `SplitTargets` |
| Storage | `data/db/` | Room v10, a database per account (`ledger-<uid>.db`), no destructive fallback |
| Sync | `data/sync/` | Offline-first, idempotent pushes; rows carry `SyncState`. **Never** `auth.awaitInitialization()` in workers, widgets or FCM: use `BackgroundAuth` |
| AI | `GeminiRepository`, `domain/AiPrompts.kt`, `AiContext.kt` | Through the `gemini-proxy` Edge Function; daily caps 50/10/3/20 on the server |
| Widgets | `widget/` | The main way friends add entries |

**Conventions that cause bugs if you miss them:**
- `-1L` is "me" as a person id. A positive balance means they owe me; negative means I owe them.
- Money is a `Double` rounded with `roundPaisa`. Parse with `Money.parseInput`, show with `Money.format` (Indian
  grouping: ₹1,00,000). Never `String.format`.
- Shares must add up to the amount to the paisa. The server enforces this and marks the row FAILED if not.
- Settlements: `PENDING`, `CONFIRMED`, `REJECTED`, plus `pendingResponse` while waiting to sync. **Only a
  confirmed settlement moves a balance.** IOUs are settlements with `isIou`.
- Guests are people in a shared split who aren't group members (`isGuest`, `guestPersonId`, `guestName`).
- Shared groups sync and other members see their expenses. Local groups don't.
- Category, payment and type ids are stored data. Never translate or rename them; only their display text.
- User-visible text lives in `res/values/strings.xml`. In non-Compose code, resolve it through the existing display
  functions, as X12's PRs did.

**Past disasters, each with a rule:**
- Launch crash with all tests green (init order) → UI and ViewModel changes need a test that builds the real
  `MainViewModel` or app shell (`AppShellLaunchTest`).
- Sign-in broken on 3 releases while debug worked → merged and green isn't verified. Say what wasn't checked.
- A bug hidden for months by a hand-built test object → **test the gatherer:** start from a real Room database and
  assert a real balance or row.
- Widgets hung until reopen → background work is tested with the app backgrounded.
- Kotlin corrupted by shell heredocs → edit code only with file tools, never through the shell.
- Local builds crashed this laptop → Gradle only with 4 GB+ free RAM. **CI is the authority.** Room and FileProvider
  tests are known to fail locally on Windows; read the exception type before blaming your change.

## 4. Design: you are not the designer

The owner says design isn't Flash's strength, and the app's quality bar is "hand-crafted, not AI-generated". So:
**you don't make visual decisions.** No new screens, colours, sizes, radii, fonts, icons, animations or layouts of
your own. If an item seems to need one, park it for Claude and the owner.

Use only what exists:
- **Colours** (`ui/theme/Color.kt`): dark only. `CharcoalBg/Surface/Card/Elevated`; `WarmAmberPrimary` for brand and calls
  to action; `MoneyIn` (lime) coming to you; `MoneyOut` (coral) going out. A debt is never amber and isn't an error.
  Text: `OffWhiteText`, `MutedWarmGray`, `SubMutedWarmGray`.
- **Space:** `Spacing.xxs…xxxl`, `Spacing.page`. **Shape:** `KpShapes.small/medium/large/sheet/action/ticket`.
- **Type** (`Type.kt`): `MaterialTheme.typography` roles without `.copy`; `MoneyText.hero/large/title/row/small` for money.
- **Components** (`ui/components/`): `Sheets.kt`, `KpButtons`, `Controls`, `EmptyState`, `RemovalConfirmationDialog`,
  `MoneyFigures`, `PersonAvatar`, `FriendRow`. Find the nearest existing screen and copy its pattern exactly.

**Rules that hold everywhere:**
- **Money never ellipsizes.** "₹1,23,4…" is wrong information. Auto-size or wrap it.
- Touch targets are 48dp+. Every interactive icon has a spoken label; decorative ones stay `null`.
- Loading, empty and error are different states. Don't collapse them.
- **Voice:** casual, warm, short, a little Hinglish ("hisaab", "all square"). Avoid "Transaction added successfully" or
  "No data available". Anything one person sends or shows another must never say **"you owe", "overdue" or "pay now"**, and
  must not sound formal, or like begging or a debt collector. "The person who sends this through WhatsApp shouldn't feel
  awkward."
- Play listing copy: no "best", "#1", "free", payment-app logos, "loan/lend/borrow/credit".

## 5. How to judge your own work

Before opening a PR, read your whole diff and answer in the PR body:
1. Does each changed line belong to this item?
2. Could this change a balance, a share, a stored id or what syncs? If so, which real-database test proves it didn't?
3. Does a new test fail without the fix? Say how you know.
4. What can't CI see? List it under "Check on the phone".

## 6. The loop for each item

1. **Read the code and the cited audit first.** The audits are in `docs/production-readiness/audits/`. If the item is already
   done, mark it `done (already)` with the evidence and move on.
2. Branch `antigravity/<id>-<short-name>` from the latest integration (R items: from `main`, named `antigravity/r1-…`).
3. Bug: commit the failing test, push, let CI go red for the predicted reason, then push the fix.
4. Add one `CHANGELOG.md` line under `## [Unreleased]` and update the item in `PROGRESS.md`.
5. `gh pr create --base antigravity/integration --title "[antigravity] <id>: <what changed>" --body-file <file>`. Body: what and why,
   audit id, tests, the section 5 answers, "Check on the phone". Tag RED items `[needs claude]`.
6. Wait for `unit-tests`, `lint`, `release-bundle`: `gh pr checks <n> --watch`. Read failures with `gh run view <id> --log-failed`.
7. GREEN or YELLOW and all green: `gh pr merge <n> --squash`. RED: leave it open, branch the next item from integration.
8. If `main` moved, merge it into integration through a PR `antigravity/sync-main`. Keep both sides in `CHANGELOG.md`.
Commit subjects are plain English about what changed for a user. No Co-Authored-By lines are required from you.

**Tiers:** GREEN tests, docs and mechanical work. YELLOW product code with no schema or sync change. RED risky: open, tested and
left for Claude; never merged by you. **Effort:** use High for ⚠ items.

## 7. The queue

Work in order. Say so and skip if something is blocked; don't stop the run.

### Onboarding
- **O1** GREEN. Prove the guard works: run `git push origin main --dry-run`. It must be **denied**. Note the result in `PROGRESS.md`.
- **O2** GREEN. Write `docs/antigravity/UNDERSTANDING.md`, from the code with `file:line`: how a typed "chai 40 with Rohan" becomes rows; how one
  friend's balance is computed; what happens to a row from local save to server; which settlement states move a balance; how a shared group
  differs from a local one; what happens at sign-out; what must never happen to stored ids. Anything that contradicts this page goes in Questions.
- **O3** GREEN. `docs/architecture.md`: the current map, since `docs/original-architecture-dossier.md` is out of date. Link it, don't edit the old one.

### Release lane: base `main`, the owner merges these
- **R1** YELLOW. `ui/format/AskMessage.kt:37` sends the friend "Asking ₹X of the ₹Y you owe." Change it to exactly `Asking ₹X of the ₹Y for now.` Update pinned tests;
  add one asserting "you owe" is absent from a partial ask.
- **R2** GREEN. `docs/testing/1.6.0-device-checks.md`, formatted like 1.5.0's, from "Check on the phone" in every PR in `git log v1.5.0..main`, de-duplicated and grouped. First:
  install over 1.5.0; **sign-in on the release build; quick add through the server (the proxy has never been tested end to end from a phone)**; Say the split from the widget; the crash-report
  switch; **Delete my account on a throwaway Google account only**; when a split preselects a group (#123), is it obvious on screen, given a shared group's members will see it?; the Ask message.
  Add "Can't be checked on a sideloaded APK": flexible updates and the review prompt need a Play install.
- **R3** GREEN. After R1 and R2: `versionCode = 7`, `versionName = "1.6.0"`; rename Unreleased to `## [1.6.0] - <today>` with a fresh Unreleased; fix misplaced or duplicate entries without rewording them.

### Fixes found in Claude's review
- **F1** YELLOW. Review prompt (#131) rarely fires: answering a payment shows a snackbar for ~4 s, and `ReviewAfterSettlement` checks at 750 ms, sees `canPrompt` false and drops the moment. Replace the delay with
  `withTimeoutOrNull(6000) { snapshotFlow { canPrompt }.first { it } }`, dropping on timeout. Nothing may use up the cooldown unless the requester ran. Test through the real ViewModel.
- **F2** GREEN. `InsightsAccessibilityTest` "per day keeps the whole amount at double text size" (`:67`) failed in run 41270005355 on #186, a workflow-only PR; the same app code passed on `cb270f6`. Find out why. Report even if you change nothing.
- **F3** GREEN. Read #133, #135, #136, #138, #140, #141, which Claude didn't review. Write findings to `docs/antigravity/review-notes.md`. Code changes only as new items.

### Codex's parked work
Start from integration, take the commits you need from the old branch and say "Supersedes #N" in the body. Don't close #N. **#132 and #139 hold only failing tests: the fix isn't written.**
- **F4** ⚠ YELLOW #130 statement re-import. Reserve **every** exact-date match first, then near-date, so a new neighbouring purchase can't use up a match. Wrong here means duplicate money or a dropped row.
- **F5** ⚠ YELLOW #132 cold-start flash. A nullable Home snapshot from direct Room flows; show the brand mark until feed, people, balance and analytics have all emitted. Don't infer loaded from default-empty StateFlows.
- **F6** YELLOW #139 Home large text. When animated digits can't fit, one auto-sized whole figure; keep the animation when they fit.
- **F7** GREEN #143 Home strings, after F5 and F6. No wording change. Probably a fixture issue (await both feed and analytics): confirm first.

### Safety net: tests only, before anything riskier
Check what exists first; extend it. Seeded scenarios on real Room where relevant. If a test finds a bug, don't fix it in that PR: add it to Questions with the failing case.
- **T1** Group balances sum to zero; shares sum to amount; after random add, edit, delete and settle sequences. **T2** `DebtSimplifier`: every net preserved, at most n−1 transfers. **T3** `SplitRounding`: equal, exact and share modes sum to the paisa, for many amounts and sizes.
- **T4** `Money.parseInput` and `format`: grouping, NaN, overflow, round trips. **T5** `LocalHeuristicParser`: a golden table including Hinglish. **T6** `LocalStatementParser`, `StatementChecks`: golden tests with invented data only.
- **T7** Backup round trip covers every column of every entity. **T8** Migration chain from the oldest supported version to 10 keeps rows. **T9** `UpiLink`: encoding, amounts. **T10** `InviteLink`: malformed input.
- **T11** `AskMessage`: lines add up to the total for all kinds. **T12** A test scanning outgoing and notification strings for "you owe", "overdue", "pay now". In-app labels about yourself are fine.
- **T13** A test on `strings.xml`: no stray `%`, plural forms with equal placeholders, positional args. **T14** CSV export: does a merchant starting with `=`, `+`, `-` or `@` survive safely? Test and report; a fix goes to Claude.

### Reliability, client only
Check each against current code first; some may be partly done. One PR each. `AppSettings.kt`, `AccountSheet.kt` and `GeminiRepository.kt` may be edited **only** where an item names them.
- **Y1** REL L14: `ReplaceFileCorruptionHandler` in `AppSettings`. **Y2** REL L3: remaining unguarded `startActivity`; activity captured across suspension becomes a one-shot event. **Y3** REL L9: `maxLength` on fields, matching limits read from `supabase/migrations` (read only).
- **Y4** REL L4: statement ticks and category fixes survive rotation. **Y5** ⚠ REL M5, part 1 only: track `parseJob` and cancel it in `openManualAdd`, `openEditTransaction`, `dismissConfirmSheet`, so a late AI result can't overwrite what someone typed or turn an edit into a new entry. Same for `parseReceiptImage`.
- **Y6** ⚠ REL M2: the half-filled entry survives process death, using `EntryDraft`. **Y7** ⚠ REL L11: pending swipe-deletes aren't resurrected or lost on process death. **Y8** REL M8: feed building off Main, search debounced ~150 ms. **Y9** REL M8: `remember` around `getPersonLedgerFlow` in composition. **Y10** REL L2: remaining `collectAsState`, one screen per PR.
- **Y11** Debug-only `StrictMode`. **Y12** SEC L-10: the export warning line, in the app's voice. **Y13** REL M3 part 1: "Not synced" label with spoken text on FAILED rows, no schema change, nearest existing status pattern. **Y14** REL M11: pull-to-refresh on Home and Splits via `requestSync`, stopping on `isSyncing` false.
- **Y15** TODOS 6, part: search and frequent-people-first in split and payer pickers, with existing fields. No new row style. **Y16** REL M8: `LazyColumn` for long sheet lists in `GroupDetailSheet`, `PersonLedgerSheet`, one each.

### Mechanical polish
- **M1** Remaining hard-coded copy into resources, no wording changes, one area per PR: `AccountSheet`, `DeleteAccountSheet`, `LicencesSheet`, `GroupSort`, `MainViewModel`, `AuthRepository` messages. Not AI prompts. Not stored ids.
- **M2** The 48 `contentDescription = null`: decide decorative or needs a label; add labels only, no visual change. **M3** The 47 `maxLines = 1`: fix any on money with the existing auto-size helper. Report the rest.

### Reports only, no code
- **D1** SDKs in the build versus `docs/legal/play-console-answers.md` and the privacy policy: anything undeclared? **D2** `docs/store-listing/` against section 4's banned words. **D3** Draft `docs/incident-plan.md` (T3.13): who to tell, 72 hours for the DPDP Board. Not legal advice. **D4** What #71 R8 would need in keep rules for serialization and the new Play libraries.

### RED: open, tested and left unmerged, in order
Only after every item above has a result. One at a time, each from integration.
- **N1** ⚠ T2.4: Room v11 nullable error column, `lastSyncError`, stop retrying on 401/403, "Couldn't sync since…". Needs Migration, schema JSON and migration test. **N2** ⚠ T2.6 part 3: sign-out offers to remove this account's data when nothing is unsynced. Deletes data. **N3** ⚠ T3.2 app lock, opt-in `[owner review]`. **N4** ⚠ REL M2: cached statement result, cleared at sign-out. **N5** ⚠ T3.7: repository flow sharing.

### Not yours
- **Design or product calls:** light theme, first-run walkthrough, budgets and section 1's future list, sheet-first quick add, compact friend rows, Say-the-split follow-ups, Hindi.
- **Server:** T2.2 limits, N2+ notifications, "Edited by X", App Links, minimum app version. **Needs a phone:** Baseline Profile, Accessibility Scanner. **Needs a decision:** TODOS 3b saved cards.
- Already done, don't redo: "one source for per-friend balance" (`balanceEntries`), account deletion, Gemini proxy.

## 8. Claude Code cloud sessions: your role

The owner may start you from a fresh clone. You have **no `.env`, no signing keys and possibly no Supabase access**, so you can't release or apply migrations. The guard doesn't run for you; section 2's limits still do. Follow `CLAUDE.md` and `docs/agents/work-split.md`.

You're the reviewer and senior engineer. Quota is scarce, so do the highest-value thing and stop:
1. **Read `PROGRESS.md`, then review** `antigravity/integration` against `main`, especially ⚠ items, money logic and test changes. Look for weakened tests, scope creep and design drift. Fix or list problems.
2. **Land good work on `main`:** one PR from integration or small batches, green CI on the exact head. Low-risk merges are allowed; RLS, auth, security and non-additive migrations stay for the owner.
3. **Finish RED PRs** left as `[needs claude]`.
4. **Your own lane, if there's quota left:** "Edited by X" (T3.8) with its server column and Room migration, as owner review; T2.2; T2.7 notifications phase 2. Write migrations; the owner applies them.

Also true: `claude-code-review.yml` skips `antigravity/` branches to save quota, and `.agents/hooks/` is Antigravity's guard: leave it working. Record what you decide in `PROGRESS.md`.

## 9. What Claude's review found (2026-11-03, `main` at `a73fe8f`)

All 62 `[codex]` PRs since 2026-10-26: no protected files touched, three CI jobs green on each recorded head. #185's head has the same tree as `main`, so its green run covers `main` (CI doesn't run on `main` pushes). No lint baseline change, no `@Ignore` added. Script check of 801 string call sites: no format mismatch or missing key. Read closely with nothing further to report: #112, #115, #120, #122, #123, #125, #127, #128, #129, #134, #137, #142. **Not reviewed:** F3's list, X12 logic beyond the script, why #97 fails. **Nothing since 1.5.0 has been on a phone.**

Open owner questions: did the office-hours assignment happen (watch a friend add from the widget)? The #103 SQL-editor results were never shown to Claude.

**About Antigravity:** 3.8 Flash has a 1M-token context and is pitched at long multi-file work, with known hallucination and occasional timeouts. Claude didn't test it hands-on, hence the order above: low risk first, real-database tests before riskier changes.
