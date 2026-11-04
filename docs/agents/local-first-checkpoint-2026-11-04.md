# Local-first restart checkpoint, November 4

**AI is merged. Local mode code passed CI. Backup implementation is prepared; inspect its latest integrated checks. Both remaining PRs require owner review and stay unmerged. No 1.7 build was published.** Live PR checks/comments are authoritative if they are newer than this snapshot.

## Resume safely

Use `C:/Users/VAIBHAV/.codex/worktrees/x1-store-listing/Kharcha Paani`; never the owner's Z: checkout. Preserve other agents' files/branches. Read `AGENTS.md`, full `work-split.md` and full `codex-lead-2026-11-03.md` again. Merge rights end November 6; the auth hard stop survives them. No Supabase operation, schema migration, repository settings, force push, emulator or release publication.

| Feature | Branch / PR | Verified evidence |
|---|---|---|
| AI Off everywhere | [#226](https://github.com/vaibhavcodesx/kharcha-paani/pull/226), merged6b1b189 | Final3251078 included current main7916a16; all3green CI41860028454 |
| Use without an account | `codex/local-mode`, [owner-review #230](https://github.com/vaibhavcodesx/kharcha-paani/pull/230) | Source01fca62e9c53e05db25c2f4bfdf8d2033c9bf659 all3green CI41866338463; later56843a3 changes its log only |
| Export everything / complete recovery | `codex/backup-everything`, [owner-review #227](https://github.com/vaibhavcodesx/kharcha-paani/pull/227), includes #230 | Repaired application sourceec85d6336c99b6cf905b798eb7f891e701fb6f28 plus later documentation/dependency-log commits; require all3green on current head |

Query live heads/status, do not guess the latest documentation SHA:

```powershell
gh pr view 230 --repo vaibhavcodesx/kharcha-paani --json headRefOid,statusCheckRollup
gh pr view 227 --repo vaibhavcodesx/kharcha-paani --json headRefOid,statusCheckRollup
```

Fetch main and merge normally if it advances. Inspect every conflict and wait for full tests/lint/release on that exact head. **Never self-merge #230; #227 must not bypass its auth dependency.** Only mark review-ready after final checks. All source has independent review; code is not phone acceptance.

## Regression proof now obtained

- Guest last baseline8d36ac102c14bc6c8015d66045eb27f4015b35e7, [CI41864757240](https://github.com/vaibhavcodesx/kharcha-paani/actions/runs/41864757240):1137tests, exactly5predicted failures. Seven-test XML verified: held sync acknowledgement on different-file close and same-file owner replacement; widget publication; nested registry close; stale cloud descriptor A-to-B. Two positive cases passed. Repair01fca62 passed all3jobs.
- Backup last baselineee7590d6356e77b0e9387b49e5427d60b2075dd8, [CI41865360001 attempt2](https://github.com/vaibhavcodesx/kharcha-paani/actions/runs/41865360001/attempts/2):1173tests, exactly3predicted failures. XML verifies active photo unlink and pending Undo accepted Busy incorrectly, and a new friend reused historical missing-person ID2. Fixes are committed.
- That backup run's first attempt had183class-initialization failures, ALL HTTP429 fetching the exact Robolectric SDK jar from repo1.maven.org. The regression bodies did not run. After the artifact returned200, one unchanged retry produced the expected3failures. Do not count the429 cascade as app/regression proof. No workflow/assertion/time-limit changes were made.
- Replaying the backup baseline cancelled fixed-head41866835163's unit job through per-PR concurrency; lint/release succeeded, but cancellation is not a test pass. **The baselines need no further replay now. Await the new current-head run; do not rerun old phases concurrently.** If dependency429 repeats, diagnose external download access rather than changing product tests.

Earlier executed evidence remains in PRs: initial AI6-red, guest namespace3-red, local-write/session6-red then9076d82 all3green, shared-close4-red, completed-owner1-red, core/recovery da98b95 all3green, shared-balance1-red/fix, modal/maintenance/sort3-red1165tests/fixes. Preserve original assertions. Follow the two-unpredicted-failure rule; no blind reruns or timeout increases.

## Implementation boundaries

Guest IDs/settings/photos/Room are separate from accounts, even equal UUIDs; no anonymous account or silent upload. Local money writes stay LOCAL_ONLY with exact paisa rounding. Existing proxy authentication means Gemini also needs sign-in. Shared restored history is read-only. Scoped background child jobs are registered under the DB mutex, cancelled/joined before owner replacement/close, and removed without reentering the mutex. Context markers reject self-reentry. Owner hooks survive destroyed holders and are pruned only after successful drain. Publication may finish during selection before target open, but cannot outlive target open/Ready; no atomic preference/publication promise.

Backups cover all10v10 entities/columns, latest edits, references, settings/defaults/UPI/sort and photos. No credential/consent/OS-permission or queued-network replay. Private journal + same-transaction completion hash permits idempotent cross-store recovery. Startup maintenance waits for recovery; unrelated file callbacks cannot hide it. AI restore uses saved-enabled AND current-enabled. Restoring refuses pending Undo or unfinished deletion work without force-committing it. Historical missing-person IDs reserve SQLite's existing people sequence transactionally, avoiding inherited debt. No dummy rows/remapping/schema change.

Local Gradle was skipped: last2.24GB free, below4GB gate. Recheck before running it; don't kill another agent's Java. Static936base resources/XML, changed Kotlin imports/conflict markers and diff checks passed. Actual SDK/network/Google picker/microphone and phone behavior remain separate from Room/engine/VM fixture tests.

## Owner material and other work

[Progress report](codex-lead-report-2026-11-06.md) · [1.7 local-first phone checklist](../testing/1.7.0-local-first-device-checks.md). Use test data/a second installation; keep the original unencrypted export private. Published1.6 tag/source5fb95e8 excludes later main changes. Claude received phone UI feedback and merged228/231/232/233/236/238/239; full signed smoke/proxy/deletion acceptance remains unestablished.

Review notes are posted on229 (DataStore lifetime fixtures),234 (foreground share delivery),240 (deterministic search evidence),242 (actual-shell coverage),241 (pre-AI voice integration must preserve Off/guest/stale-callback/cancellation gates). Their authors own fixes. Voice241 remains owner-review, with disclosure/legal/Play copy unresolved. No owner/security/Play gate is silently cleared; #71/Dependabot need signed-phone smoke,139 is deferred. The five audits are not all complete. Continue status/questions on [issue188](https://github.com/vaibhavcodesx/kharcha-paani/issues/188).
