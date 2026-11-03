# Antigravity brief (from 2026-11-03)

Claude and Codex are both out of quota, so Antigravity takes the queue below. Written by Claude after
reviewing Codex's solo period. Work top to bottom, one item per branch and PR. If an item here
disagrees with your own judgement, follow this page and write the disagreement in the PR body.

## Read first

1. `AGENTS.md`, then `docs/agents/work-split.md`, especially "Files: who may touch what". Codex's rules
   there apply to you too.
2. `docs/production-readiness/README.md` §1 (rules) and §10 (owner decisions).

## Hard limits

- **Never merge, never push to `main`, never force-push.** Open a PR and leave it. The owner or Claude
  merges.
- **Never** run anything against Supabase, run the release workflow, publish a release, or change
  repository settings.
- **Don't edit** anything listed as protected in `work-split.md`: `supabase/**`, `data/db/**`,
  `app/schemas/**`, `data/sync/**`, `GeminiRepository.kt` and AI prompts, `AccountSheet.kt`,
  `AppSettings.kt`, `.github/**`, `PRD.md`, `docs/production-readiness/README.md`. If an item seems to
  need one, stop that item, say why in the PR, and go to the next.
- **Don't touch** #71 or the Dependabot PRs (#86, #97, #186).
- **No Room schema changes.** Anything touching auth, RLS or security is not yours.
- **Never make a test pass by weakening it**: no deleting assertions, no `@Ignore`, no longer sleeps,
  no narrowing what a test checks. If the honest fix is out of reach, leave the test red and park.
- **Two failed CI runs on one item: stop**, write what failed in the PR, go to the next item.
- **Stay in scope:** no reformatting, renaming or tidying nearby code. Keep diffs small in the hot files
  (`MainViewModel.kt`, `MainActivity.kt`, `Sheets.kt`, `NotificationHelper.kt`).

## How to work

- Branch `antigravity/<item>-<short-name>` from the latest `main`. PR title `[antigravity] <item>: <what
  changed>`. For AG6–AG9, build on Codex's existing branch instead: merge the latest `main` into it, add
  commits, and open a new PR only if pushing to it is refused.
- Every PR body: what changed and why, tests added, a short "Check on the phone" list.
- One entry under `## [Unreleased]` in `CHANGELOG.md`, nothing else in that file (AG3 is the exception).
- Fixing a bug: push the failing test first, let CI go red for the predicted reason, then push the fix.
- CI on the PR is the authority (unit-tests, lint, release-bundle). Read the failing test's message, not
  only the red mark.
- Don't run Gradle locally below 4 GB free RAM; stop leftover `java.exe` afterwards. Never run an
  emulator: the owner tests on the phone. Room and FileProvider tests are known to fail locally on
  Windows and pass on CI.
- UI or ViewModel changes need a test that builds the real `MainViewModel` or app shell, starting from
  a real Room database and asserting a real row or balance (see `AppShellLaunchTest`).
- Append one line per PR to `docs/agents/solo-log.md` under a new `## Antigravity log` heading. Questions
  go there too. Don't stop to ask: take the safe, reversible option and note it.
- If the queue runs out, stop and say so. Don't borrow other work.

## The queue

### For release 1.6.0

**AG1. Ask message wording.** `ui/format/AskMessage.kt:37` sends the friend "Asking ₹X of the ₹Y you
owe." That breaks the tone rule (README §1 rule 13: never "you owe" towards the person who owes). Change
it to exactly `Asking ₹X of the ₹Y for now.` Update any test that pins the old text and add one asserting
"you owe" is absent from a partial ask. Don't change anything else in the message.

**AG2. `docs/testing/1.6.0-device-checks.md`.** Docs only. Same format as
`docs/testing/1.5.0-device-checks.md`. Gather every "Check on the phone" list from the PRs in
`git log v1.5.0..main` (`gh pr view <n> --json body`), merge duplicates, and group by area. Install over
1.5.0 without uninstalling comes first. Mark **(important)**, and put first after install:
- Google sign-in on the release build, then quick add through the server (the Gemini proxy has never
  been tested end to end from a phone, and the old key can't be deleted until it has).
- Say the split from the widget; the "Send crash reports" switch.
- Delete my account, **on a throwaway Google account only**. Say so in bold.
- Splitting with one friend: when the app preselects a group (#123), is it obvious on screen? If that
  group is shared, the other members will see the expense. Ask the owner whether it feels safe.
- The Ask message read aloud as something you'd happily send.

Add a section "Can't be checked on a sideloaded APK": flexible updates (#129) and the review prompt
(#131) only work on a Play-installed build, so they wait for the closed test. Don't list AG4–AG9.

**AG3. Release prep.** `app/build.gradle.kts`: `versionCode = 7`, `versionName = "1.6.0"`. `CHANGELOG.md`:
rename Unreleased to `## [1.6.0] - <today>` with a fresh empty Unreleased above it, and read the section
through for misplaced or duplicate entries. Keep every entry's wording. Open it after AG1 and AG2 and say
in the PR that it should merge last of the three. The owner runs the release workflow, not you.

### Fixes found in review

**AG4. The review prompt almost never fires after answering a payment (#131).** `respondToSettlement`
in `MainViewModel` sets the snackbar "Confirmed. It counts for both of you now." at the same moment it
emits a review moment. `ReviewAfterSettlement` waits a fixed 750 ms, then `canPrompt` is false because the
snackbar lasts about 4 s, and the moment is dropped. Fix: instead of the fixed delay, wait for `canPrompt`
to become true with a timeout of about 6 s (`withTimeoutOrNull` around `snapshotFlow { canPrompt }.first
{ it }`), then call `requester.request()`. If it times out, drop the moment. No cooldown may be used up
when nothing was shown to the requester. Test through the real ViewModel path with a snackbar showing.

**AG5. A test that may be flaky.** `InsightsAccessibilityTest` "Insights per day keeps the whole amount at
double text size" (`InsightsAccessibilityTest.kt:67`) failed in run 41270005355 on #186, which changes only
workflow files; the same app code passed on `cb270f6`. Find out why (rerun; look for timing or font
dependence). Fix the cause in the test's setup or the code. See the weakening rule above. Report what you
found even if you change nothing.

### Codex's parked PRs, in this order

Home-screen PRs conflict with each other, so the plain string move goes last. #130 goes first because a
wrong answer there can put a duplicate amount in someone's ledger. **#132 and #139 contain only failing
tests so far: the fix isn't written yet.**

**AG6. #130 statement re-import** (`codex/x8-statement-reimport`). Reserve every exact-date match first,
then do near-date matching, so a new purchase next to an existing one can't use up that row's match.

**AG7. #132 cold-start flash** (`codex/x10-home-loading`). Add a nullable Home snapshot built from direct
Room flows; draw the brand mark until the first feed, people, balance and analytics values have arrived.
Don't infer "loaded" from the default-empty public StateFlows.

**AG8. #139 Home large text** (`codex/x7-home-accessibility`). When the animated digits can't fit, show one
auto-sized whole figure. Keep the animation when they fit. Money never ellipsizes.

**AG9. #143 Home strings** (`codex/x12-home-strings`). Merge `main` after AG7 and AG8. No wording changes.
Likely fixture fix: wait for both feed and analytics before checking day headers. Confirm that's really the
cause first.

### Then

**AG10. T3.10 L2:** `collectAsState` to `collectAsStateWithLifecycle`, one screen per PR.

**AG11. T3.10 SEC L-10:** the export warning line. See `docs/production-readiness/audits/security.md`.

## Not yours: waiting for Claude or the owner

- **"Edited by X"** (rest of T3.8): needs a server column, a trigger and a Room migration, so it's Claude's
  and goes to owner review. Never show a guessed editor.
- T2.2 abuse limits, T2.4 sync visibility, T2.6 part 3, T2.7 notifications phase 2, TODOS 3b, 6 and 10,
  account and reminder copy in `AccountSheet`, AI prompts.
- #71 R8, Dependabot, releases, deleting the old Gemini key.

## What Claude's review found (for whoever comes next)

Checked against GitHub on 2026-11-03, `main` at `a73fe8f`:

- All 62 `[codex]` PRs since 2026-10-26: no protected files touched, all three CI jobs green on the recorded
  head. #185's head has the same tree as `main`, so its green run covers `main` (CI doesn't run on `main`
  pushes). No lint baseline change, no `@Ignore` added, no test removed apart from two `tearDown`
  rewrites.
- String move: all 801 `R.string`/`R.plurals` call sites checked by script, with no format-argument mismatch,
  missing key or unused key.
- Read closely, no problem beyond the items above: #112, #115, #120, #122, #123, #125, #127, #128, #129, #134,
  #137, #142.
- **Not reviewed:** #133, #135, #136, #138, #140, #141, the logic of each X12 PR beyond the script, and #97's
  failure. None of this has been on a phone.

## Answers to Codex's questions (for when it returns)

1. Parked PRs: #130 first, then #132, #139, #143, unless Antigravity has finished them. Check first.
2. Claude owns "Edited by X". The Ask wording is decided above (AG1); localising the message body waits for a
   Hindi build.
3. Next: whatever remains of this queue. Normal no-self-merge rules apply.
