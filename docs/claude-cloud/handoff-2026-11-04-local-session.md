# Handoff: local Claude session, 2026-11-04

Written when the owner moved this work to a cloud session. Read after the usual set
(CLAUDE.md, docs/claude-cloud/HANDOFF.md, PRD.md, docs/production-readiness/README.md §0 and §10).

## What the owner asked for (v1.6.0 on their phone; screenshots 1-6 attached to the cloud message)

1. **Activity tab** (screenshot 1): "Mayank paid Aaru" (a group settlement) shows in Activity. Only group
   items the owner is involved in should show. Group expenses they didn't pay and have no share in
   ("10 log graph papers", "Instamart", ₹0 share) count too.
2. **Splits > Groups** (screenshot 2): the "Sort: Latest activity" button sits oddly on the left with empty
   space on the right. Redesign it as a professional UI/UX designer would (`GroupSortPicker` in
   `ui/screens/SplitsScreen.kt`, line ~349).
3. **Group detail expense list** (screenshot 3): the rows are big and wordy ("Paid by ● You / Split with ● You ● Aaru
   ● Pruthva"). The colour dots exist to save space, so use dots only (no repeated names), and make rows
   compact. File: `ui/components/GroupDetailSheet.kt` (`GroupExpenseRow`). Write a short `docs/designs/` note first.
4. **Account sheet / Settings** (screenshots 4 and 5): "Improve UI, layout." File: `ui/auth/AccountSheet.kt`.
5. **New entry sheet** (screenshot 6): improve UI/layout (optional). File: `ui/components/Sheets.kt`.
6. **Widget that opens quick add.** There is already a 4x1 bar (`widget/QuickAddWidgetProvider.kt`), but its tap
   opens the *New entry form*. The owner wants it to open **quick add** (Home's "What did you spend on?"
   capture line, `ui/home/CaptureCard.kt`) with the keyboard up. Route: `IncomingIntents.Route.QuickAdd`.
   Consider a compact 2x1 / 1x1 variant too.
7. **Quick add:** the placeholder text should rotate (every few seconds, with example lines that teach "chai 40 cash",
   "dinner 1200 with Rohan"...), recognition should be better and quicker.
8. **Voice:** Google's mic dialog (`RecognizerIntent` in `CaptureCard.kt`) transcribes badly.
9. "Identify other problems and fix them."

## Owner decisions made this session

- **Voice: record in-app and send the audio to Gemini**, which returns the parsed entry in one step.
  Use the existing `gemini-proxy` with feature `quick_add`. It forwards the body unfiltered (limit 15 MB), so **no
  Supabase change is needed**. Add `RECORD_AUDIO`, a small in-app listening bar, and a fallback to Android's
  `SpeechRecognizer` when offline or the AI is unavailable. Needs a privacy-policy and Play Data Safety line.
  Open this PR as **[owner review]** and don't merge it (new permission, voice data leaves the phone).
- **Widget:** "its add expense opens the form, we want quick add" (item 6 above).
- **Shipping:** small `[claude]` PRs, one per theme, off the latest `origin/main`. Self-merge the low-risk ones after
  green CI, per the owner's merge policy. Codex leads until 2026-11-06: claim on #188 and check `gh pr list` first.
  The groups sort (TODOS 5) is a Codex-lane item, so tell #188 you touched it at the owner's request.
- Don't stop to ask; put questions in one comment on #188 at the end.

## State of the work (local, nothing committed or pushed)

Branch `claude/ux-activity-involved` (off `origin/main` e591ba2), working tree only:

- `data/model/Models.kt`: new `FullTransaction.involvesMe` (not in a group, or I added it, or I paid, or I have a share).
- `domain/ActivityFeed.kt`: drops group expenses that aren't `involvesMe`, and settlements where `ActivityItem.Settlement.involvesMe` is false.
- `domain/ActivityFeedTest.kt`: existing expectations updated (the "Carol paid Bob" fixture, id 12, is now hidden), plus two new tests.

**Never run.** The unit-test run was interrupted, so this is unverified: run
`./gradlew :app:testDebugUnitTest --tests "*ActivityFeedTest" --tests "*ActivitySearchTest" --tests "*IouDetailsTest"`
(CI is the authority; Room tests fail locally with SQLITE_CANTOPEN). Also check other tests that build an Activity
feed with a group settlement between two other people.

Nothing else is started: Groups sort, group rows, Settings, New entry sheet, rotating hint, widget routing and voice
are all still to do. Other findings: a 4x1 widget exists; Gemini proxy already accepts any body for `quick_add`.

## Hard stops (unchanged)

Nothing applied to Supabase; RLS/auth/security and non-additive migrations as `[owner review]`; no pricing; no
releases; never write @claude on GitHub. Update CHANGELOG.md `[Unreleased]` in every PR.
