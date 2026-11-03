# Codex lead: working progress report

Last updated **2026-11-04**. This is a working report for the lead period ending November 6,
not a claim that the remaining queue or phone testing is complete.

## For Vaibhav

**v1.6.0 (7) is available:** [download the signed APK](https://github.com/vaibhavcodesx/kharcha-paani/releases/download/v1.6.0/KharchaPaani-v1.6.0.apk).
Open it and choose **Update over 1.5.0 without uninstalling**. Start with section 0 of the
[phone checklist](../testing/1.6.0-device-checks.md); a downloadable copy is also attached to
[the release](https://github.com/vaibhavcodesx/kharcha-paani/releases/tag/v1.6.0).
Report item numbers and results on [#188](https://github.com/vaibhavcodesx/kharcha-paani/issues/188).

The signed release run [41670911242](https://github.com/vaibhavcodesx/kharcha-paani/actions/runs/41670911242)
passed; its certificate matches the existing release key. This is a GitHub phone-test prerelease,
not a Play rollout. **Installation and acceptance on the owner's phone are still unconfirmed.**
Release source: `5fb95e8b308b7b352d5139dd806c7afbfb002720`.

## Merged

| Change | PR |
|---|---|
| Friendly partial-payment Ask wording, with the real figures retained | [#190](https://github.com/vaibhavcodesx/kharcha-paani/pull/190) |
| Stable first-day Insights test; correct restore-preview ViewModel teardown | [#192](https://github.com/vaibhavcodesx/kharcha-paani/pull/192), [#194](https://github.com/vaibhavcodesx/kharcha-paani/pull/194) |
| Combined phone checklist and version 1.6.0 (7) preparation | [#193](https://github.com/vaibhavcodesx/kharcha-paani/pull/193), [#198](https://github.com/vaibhavcodesx/kharcha-paani/pull/198) |
| Statement re-import unticks saved payments, reserving exact dates before neighbours | [#201](https://github.com/vaibhavcodesx/kharcha-paani/pull/201) |
| Home waits for actual activity, people, balances and analytics before showing money | [#213](https://github.com/vaibhavcodesx/kharcha-paani/pull/213) |
| Home labels move to resources with unchanged English; the old test waits for its real feed | [#216](https://github.com/vaibhavcodesx/kharcha-paani/pull/216) |
| Quick add opens immediately; late AI preserves typing, confirmed amounts and newly added split friends | [#195](https://github.com/vaibhavcodesx/kharcha-paani/pull/195) |
| Group-balance and debt-simplification invariants, with independent whole-paisa checks | [#200](https://github.com/vaibhavcodesx/kharcha-paani/pull/200), [#202](https://github.com/vaibhavcodesx/kharcha-paani/pull/202) |
| Reviewed fictional Play screenshots and feature graphic; owner approval/upload still separate | [#210](https://github.com/vaibhavcodesx/kharcha-paani/pull/210) |
| Back-to-Home test clears and awaits both ViewModels before closing Room | [#220](https://github.com/vaibhavcodesx/kharcha-paani/pull/220) |

Each merged head had latest main, independent review and green unit-tests, lint and release-bundle.
**#201, #213, #216 and #195 are next-build changes; they are not in the published v1.6 APK.**
Their superseded drafts #130, #132 and #143 are closed. No history was deleted.

## Open and held

- [#218](https://github.com/vaibhavcodesx/kharcha-paani/pull/218): draft; review-prompt timing attempt is not green or merge-ready.
  Real-Room/composable red run 41694356770 ran 980 tests with only the intended snackbar failure;
  its timeout/fresh-moment control passed. Post-fix readiness observation is still failing; the attempts are documented in the PR.
- [#212](https://github.com/vaibhavcodesx/kharcha-paani/pull/212): UI audit needs capture lifecycle,
  readiness/date and report corrections. Store assets are merged; owner approval still precedes any Play upload.
- [#214](https://github.com/vaibhavcodesx/kharcha-paani/pull/214): compact day amounts still exceed
  their short-label bound at larger values. Its dependent [#215](https://github.com/vaibhavcodesx/kharcha-paani/pull/215)
  has no new production blocker found, but regression/log/merge gates remain.
- Antigravity reviews are posted on #196, #197, #199, #200 and #202–#211, #217, #219 and #221.
  Some need factual/test corrections; even otherwise clean PRs need their log, latest main and final CI.
  Lead is resolving safe main/log/changelog integration while preserving author changes. #207 now tests actual
  backup serialization. #206 has an isolated India-timezone red and a prepared fix. #221 has two formatter-oracle defects.
- [#139](https://github.com/vaibhavcodesx/kharcha-paani/pull/139): 200% font work remains parked under
  the owner deferral reported on #188. Normal-size clipped money is still in scope.

## Resume and remaining work

Use the managed worktree `C:\Users\VAIBHAV\.codex\worktrees\x1-store-listing\Kharcha Paani`;
leave the owner's Z: checkout alone. Read the [lead instructions](codex-lead-2026-11-03.md),
[work split](work-split.md) and latest #188 comments before acting.

1. Owner phone reports first. Current owner priority is parallel PR review/cleanup; merge source-ready PRs sequentially.
   #218 stays draft until its readiness-observation test and implementation are green; record unexpected runs honestly.
2. Recheck reviewed PRs after author fixes; merge only latest-main, all-three-green exact heads, with log and phone list.
3. Continue Y5 receipt-result cancellation, Y6 draft survival, Y7 pending-delete survival, then T2.4 sync-error visibility.
   Coordinate the receipt cancellation job with #195; do not create competing AI-job ownership.
4. Prepare sign-out data removal, abuse limits, authoritative last editor and notifications for owner review.
   Auth/security, Supabase files and non-additive migrations remain unmerged. Never apply server changes.
5. Keep this report and the solo log current. Merge rights end November 6 unless the owner extends them on #188.

## Still not verified

No device/emulator run; no live release sign-in/proxy/group/widget acceptance; no Play update/review acceptance.
The owner's #103 SQL-editor results and old Gemini-key deletion are unconfirmed. Do not delete that key
before successful phone checks. No Supabase operation, settings/secret change or further release was performed.
Local Gradle remains skipped below the 4 GB free-RAM floor; CI is the build authority.
