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
| Statement-row golden tests consistently use the parser timezone, with an India-zone regression | [#206](https://github.com/vaibhavcodesx/kharcha-paani/pull/206) |
| Review requests wait through the confirmation snackbar, with a bounded timeout and untouched cooldown rules | [#218](https://github.com/vaibhavcodesx/kharcha-paani/pull/218) |
| Split-rounding representative invariants and whole-paisa checks | [#203](https://github.com/vaibhavcodesx/kharcha-paani/pull/203) |
| Large-amount test holds friends live with original wait bounds and unchanged amount assertions | [#223](https://github.com/vaibhavcodesx/kharcha-paani/pull/223) |
| Backup coverage now encodes/decodes the file before checking the restored Room data | [#207](https://github.com/vaibhavcodesx/kharcha-paani/pull/207) |
| Real ViewModel fixtures stop and await their original scopes before Room closes, with six lifecycle regressions | [#224](https://github.com/vaibhavcodesx/kharcha-paani/pull/224) |
| Cloud handoff and working lead report | [#222](https://github.com/vaibhavcodesx/kharcha-paani/pull/222) |
| Source-corrected onboarding map with portable immutable links | [#196](https://github.com/vaibhavcodesx/kharcha-paani/pull/196) |
| Source-corrected architecture, diagrams and release boundaries | [#197](https://github.com/vaibhavcodesx/kharcha-paani/pull/197) |
| Money formatting, input limits and representative round trips | [#204](https://github.com/vaibhavcodesx/kharcha-paani/pull/204) |
| Thirty deterministic English/Hinglish local-parser cases | [#205](https://github.com/vaibhavcodesx/kharcha-paani/pull/205) |
| Legacy v2-to-v10 migration preservation, UUID uniqueness and reliable cleanup | [#208](https://github.com/vaibhavcodesx/kharcha-paani/pull/208) |
| UPI-link encoding and amount cases | [#209](https://github.com/vaibhavcodesx/kharcha-paani/pull/209) |
| Invite-link malformed-input and alphabet boundaries | [#211](https://github.com/vaibhavcodesx/kharcha-paani/pull/211) |
| Ask-message line/total reconciliation across five history kinds | [#217](https://github.com/vaibhavcodesx/kharcha-paani/pull/217) |
| Outgoing-message and notification wording safety checks | [#219](https://github.com/vaibhavcodesx/kharcha-paani/pull/219) |
| Resource-format checker corrected through two isolated red runs | [#221](https://github.com/vaibhavcodesx/kharcha-paani/pull/221) |

Each merged head had latest main, independent review and green unit-tests, lint and release-bundle.
**#201, #213, #216, #195 and #218 are next-build changes; they are not in the published v1.6 APK.**
Their superseded drafts #130, #132 and #143 are closed. No history was deleted.

## Open and held

- [#212](https://github.com/vaibhavcodesx/kharcha-paani/pull/212): audit captures need managed ViewModel teardown, actual readiness/date guards and evidence-scoped report corrections.
- [#214](https://github.com/vaibhavcodesx/kharcha-paani/pull/214): larger compact amounts still exceed the clipped day-cell bound; real-Room regression and integration gates remain.
- [#215](https://github.com/vaibhavcodesx/kharcha-paani/pull/215): no additional production defect found, but regression/log gates and its blocked #214 base remain. Specific reviews are posted on all three.
- [#139](https://github.com/vaibhavcodesx/kharcha-paani/pull/139): parked under the owner's 200% font deferral. Normal-size clipped money is still in scope.
- [#71](https://github.com/vaibhavcodesx/kharcha-paani/pull/71) and Dependabot [#86](https://github.com/vaibhavcodesx/kharcha-paani/pull/86), [#97](https://github.com/vaibhavcodesx/kharcha-paani/pull/97), [#186](https://github.com/vaibhavcodesx/kharcha-paani/pull/186): held until the owner's required signed-release acceptance. No server/owner-review work was merged.

The first #211 integration run 41712418103 stopped emitting test progress for 10m27s and was cancelled during the mandatory main refresh. Lint/release passed; partial artifact 13268468371 contains no XML/HTML result or confirmed cause. It was never counted as passing. Only the later green exact-head run is merge evidence.

#210's missing PR-body phone heading was added after merge; that metadata oversight is disclosed on the PR. Source, latest-main and exact-head build checks had passed. No Play upload occurred.

The shared pre-test Room failure on #197/#219 is addressed by [#224](https://github.com/vaibhavcodesx/kharcha-paani/pull/224): six new lifecycle assertions failed first in 1065 tests, then the existing fixtures gained ownership and bounded cancellation completion. All original business test bodies remain unchanged. No production revert was justified by the old-fixture evidence; the decision is recorded on #188.

This report is updated alongside the corrected source review notes in [#199](https://github.com/vaibhavcodesx/kharcha-paani/pull/199); its merge state is on GitHub.

## Resume and remaining work

Use the managed worktree `C:\Users\VAIBHAV\.codex\worktrees\x1-store-listing\Kharcha Paani`;
leave the owner's Z: checkout alone. Read the [lead instructions](codex-lead-2026-11-03.md),
[work split](work-split.md) and latest #188 comments before acting.

1. Owner phone reports first. Current owner priority is parallel PR review/cleanup; merge source-ready PRs sequentially.
2. Recheck reviewed PRs after author fixes; merge only latest-main, all-three-green exact heads, with log and phone list.
3. Continue Y5 receipt-result cancellation, Y6 draft survival, Y7 pending-delete survival, then T2.4 sync-error visibility.
   Coordinate the receipt cancellation job with #195; do not create competing AI-job ownership.
4. Prepare sign-out data removal, abuse limits, authoritative last editor and notifications for owner review.
   Auth/security, Supabase files and non-additive migrations remain unmerged. Never apply server changes.
5. Coordinate Cloud’s approved whole-rupee Home hero and shorter Home top section; those decisions are approved, not built.
6. Keep this report and the solo log current. Merge rights end November 6 unless the owner extends them on #188.

## Still not verified

No device/emulator run; no live release sign-in/proxy/group/widget acceptance; no Play update/review acceptance.
The owner's #103 SQL-editor results and old Gemini-key deletion are unconfirmed. Do not delete that key
before successful phone checks. No Supabase operation, settings/secret change or further release was performed.
Local Gradle remains skipped below the 4 GB free-RAM floor; CI is the build authority.
