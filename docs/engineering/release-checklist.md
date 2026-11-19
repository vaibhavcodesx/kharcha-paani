<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Release checklist

One line per step. Who does it is in parentheses. See [testing.md](testing.md) for the full smoke-test list.

## Before building
- [ ] (lead) `gh issue list -m <version>` is empty or every open item is deferred with a note.
- [ ] (lead) `main` CI is green; read per-test results, not just the badge.
- [ ] (lead) `versionCode` bumped and `versionName` set in `app/build.gradle.kts`.
- [ ] (lead) `python scripts/fold_changes.py <version>` run; `CHANGELOG.md` updated. One PR, merged.
- [ ] (lead) Every server change for this release listed in order per issue #407 for the owner.

## Building
- [ ] (owner) **Actions → Release APK** run with tag `vX.Y.Z`. APK and AAB attached to the draft release.
- [ ] (owner) Release SHA-1 registered in Google Cloud Console for this sign certificate — sign-in fails without it.

## Smoke test on the release build
- [ ] (owner) Install the release APK; sign out then sign in again with Google.
- [ ] (owner) Add an entry, join a group, open the split link — all without a crash.
- [ ] (owner) Background the app; confirm sync resumes when foregrounded.

## Server changes (in the order listed by the lead)
- [ ] (owner) Take a manual database dump immediately before each live migration.
- [ ] (owner) Apply migrations and deploy Edge Functions in the documented order.
- [ ] (owner) If a migration changes what old clients can upload, gate on the minimum-version bump first.

## Finalise
- [ ] (owner) Tag equals `versionName` (e.g. `v1.8.0`).
- [ ] (owner) `mapping.txt` attached to the GitHub Release for crash de-obfuscation.
- [ ] (owner) Upload AAB to Play Console; set rollout percentage; monitor crash rate before full rollout.
- [ ] (lead) Update `STATE.md` release line and archive this checklist instance.
