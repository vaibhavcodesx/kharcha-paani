# Release checklist

What has to be true before a release goes to anyone, and how to check it. "Merged and green in CI"
is not "verified on a real build": 1.0.0 and 1.1.0 shipped with Google sign-in broken on every
release-signed install while every debug build worked.

Build and signing details are in [distribution.md](distribution.md).

## Before building

- [ ] `main` is green in CI, read in the per-test results and not just the badge:
      `gh run download <id> -n unit-test-results`.
- [ ] `versionCode` went up and `versionName` is the new version, both in `app/build.gradle.kts`.
- [ ] `CHANGELOG.md`: *Unreleased* is renamed to the version and today's date, with a fresh empty
      *Unreleased* above it.
- [ ] Any Supabase migration this release needs is applied, and listed under the release marked
      **(server)**.

## Build

GitHub → **Actions → Release APK → Run workflow**, with the tag `vX.Y.Z`. Download the APK from
the draft release, or build locally with `./gradlew :app:assembleRelease` (see distribution.md for
signing).

## Scripted checks

With the phone connected over adb:

```bash
scripts/release-smoke.sh KharchaPaani-vX.Y.Z.apk
```

It stops at the first failure. It checks that:

1. exactly one device is connected;
2. the APK is signed with the **release** key (its SHA-1 matches step 4b of distribution.md);
3. it installs over the existing app, keeping the data (it never uninstalls);
4. a cold launch stays up for 6 seconds with nothing in the crash log;
5. shared text opens the add sheet without a crash;
6. an invite link opens the join preview without a crash;
7. both widgets refresh with the app in the background without a crash.

Then the on-device launch test, which opens the signed-in app and every tab on the phone (CI runs
the same test under Robolectric on every PR, but has no emulator):

```bash
./gradlew :app:connectedDebugAndroidTest
```

## By hand, on the release build

Background work behaves differently with the app closed, so wherever a step says "backgrounded",
press Home first and don't reopen the app until the step is done.

**Account**
- [ ] Sign out, then sign back in with Google. This is the step that caught the broken release
      certificate.
- [ ] Profile name and colour are still there afterwards.

**Ledger and sync**
- [ ] Add an expense; it appears on Home and in this month's total.
- [ ] Split one with a friend who isn't on the app; their balance moves by their share.
- [ ] On a second phone (or after reinstalling), the same account shows the same balances.
- [ ] Backgrounded: change something on the other phone, wait for a sync, then check the widget
      shows it before opening the app.

**Shared groups**
- [ ] Open a shared group; a groupmate's expense is there.
- [ ] Record a settle-up; the other person gets the "payment to confirm" notification.

**AI** (skip for a `-no-ai` build, where the AI section should be hidden)
- [ ] Quick add: "chai 20 cash" fills the sheet.
- [ ] Scan bill with a photo of a receipt.
- [ ] Import a bank or UPI statement PDF.
- [ ] Ask a question about this month.

**Ask-to-pay**
- [ ] From a friend who owes you, ask them to pay: the share sheet has the message and the QR, and
      doesn't list Kharcha Paani itself.

**Backup**
- [ ] Export a backup, then restore it: the message says everything was already there.

**Widgets and notifications**
- [ ] Both widgets show today's figures; their buttons open the right screen.
- [ ] The daily reminder arrives in the evening on a day with nothing logged.

**Subscriptions**
- [ ] A subscription that started last month shows one charge per month, not two.

When everything is ticked, publish the release.
