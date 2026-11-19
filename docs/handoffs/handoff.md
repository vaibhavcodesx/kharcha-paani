# Handoff: Kharcha Paani to production

Updated 2026-10-24. This is the single document a new session starts from. It replaces the
2026-10-23 version. The older [session report](2026-10-23-session-report.md) and
[brief](2026-10-23-fix-then-harden-brief.md) are background only.

**Rules that still apply:**
- `CLAUDE.md`.
- The memory notes:
  - Gradle on this laptop: capped, with no daemon left behind;
  - no destructive Room migrations;
  - test the gatherer, not the format;
  - run the app before pushing UI work;
  - smoke-test the release build;
  - no Kotlin through Bash heredocs.
- One branch and one PR per item, cut from `main`.
- CHANGELOG under *Unreleased*.
- The owner reviews anything touching RLS, auth or security, and any non-additive migration.
- The auto-mode permission check **blocks the agent from merging PRs**. The owner merges.
- The laptop often has under 1 GB of RAM free:
  - check before any Gradle run;
  - CI is the authority for tests;
  - after a local build, stop the leftover `java.exe` processes (GradleDaemon,
    KotlinCompileDaemon);
  - **the owner runs the emulator; agents don't.**

---

## 1. Open PRs: the owner tests and merges these

Merge them **in this order**. Every PR adds to CHANGELOG *Unreleased*, so after each merge, rebase
or merge `main` into the next one and resolve that small conflict.

| PR | What | CI | Owner checks on the emulator before merging |
|---|---|---|---|
| [#55](https://github.com/vaibhavcodesx/kharcha-paani/pull/55) F1 docs truth | Local friends' data, guests and UPI copy; `graphify-out/` ignored | ✅ 669/669 | The UPI sheet's new line |
| [#56](https://github.com/vaibhavcodesx/kharcha-paani/pull/56) F2 backup | Restore no longer doubles a payment backed up before it synced (red→green proven); shared groups' expenses kept out of the backup (`fe05794`) | ✅ on `420c7b5`; check `fe05794` | Export a backup, then restore it |
| [#57](https://github.com/vaibhavcodesx/kharcha-paani/pull/57) F3 one balance source | Balance and history read the same entries; figures pinned and identical on old and new code | ✅ 672/672 | Friend balances look unchanged |
| [#58](https://github.com/vaibhavcodesx/kharcha-paani/pull/58) data-safety | Sync passes one at a time; subscriptions charged atomically; deleting a friend retracts their synced IOUs; group-join cursor made atomic; duplicate local friend names refused; local-group balance counts repayments | ✅ 674/674, all 5 new tests ran | Add a friend with a name you already use: the message appears and the sheet stays open |
| [#59](https://github.com/vaibhavcodesx/kharcha-paani/pull/59) large files | Picked or shared photos and PDFs read off the main thread, photos capped at 2048 px, PDFs at 14 MB | check | Scan bill with a normal photo; share a huge photo; share a PDF |
| [#60](https://github.com/vaibhavcodesx/kharcha-paani/pull/60) correctness | Percentage splits divide exactly (private ones synced short before); Income saves no split; no crash on sign-out during Undo; "Last synced" stamped honestly; CSV export skips unconfirmed payments; strict statement dates | check | 33.3% × 3 split syncs; split → Income has no debt; delete then sign out during Undo doesn't crash |
| [#54](https://github.com/vaibhavcodesx/kharcha-paani/pull/54) ask-to-pay Phase 1 (draft) | Ask above the QR (verified on the release build); app left out of its own share sheet; ask callback ignored after an account switch | ✅ 703/703 on `2a3592c`; check `c48b80f` | Share sheet doesn't list Kharcha Paani; WhatsApp keeps the caption (on the owner's phone). Then mark it ready and merge **after #55–#60**. It adds Room **v9**, so bill photos take **v10** |

**How to check CI properly:**
1. `gh run list --branch <b> --workflow "Android CI" --limit 1`
2. `gh run download <id> -n unit-test-results`
3. Read the per-test XML, not just the badge.

---

## 2. Work a new session can do WITHOUT asking (in this order)

Each item gets its own branch from `main` (or on top of #54 where it says so), its own tests, a
CHANGELOG entry and a PR. Verify every audit finding in the code before changing anything.

### 2a. Remaining audit bugs (all verified in code, not yet fixed)

1. **A split paid by a deleted friend restores as paid by me** (Medium).
   - Cause: `LocalBackupRepository.collect` writes `paidByName = nameOf[id]`, which is null for a
     deleted person, and null means "you". `restore` then falls back to `?: me`. Share names and
     settlement names have the same problem.
   - Fix: write null only for `-1`. In `restore`, never fall back to `me` for a non-null name:
     skip the split's balance effect or keep a "former friend" placeholder. Write the test first.
   - Do it after #56 merges.
2. **Sign-out leaves account A's notifications and cached files** (Low).
   - Fix: on account change, call `NotificationManagerCompat.cancelAll()` and delete
     `cacheDir/exports` and `cacheDir/asks` (`AccountSessionHolder.onAccountChanged`).
   - Put the userId in notification intents and ignore a mismatch.
3. **The exported `MainActivity` honours internal actions from any app** (Low).
   - Affected: `ACTION_QUICK_ADD`, `VOICE_LOG`, `ANSWER_SETTLEMENT`, `VIEW_SPLITS`; null-type text
     also triggers a Gemini call.
   - Fix:
     - route widget and notification intents through a non-exported activity-alias;
     - require a `text/*` type for shared text;
     - accept only `content://` for `EXTRA_STREAM`.
4. **Statement import pre-ticks every row Gemini returns** (`GeminiRepository.kt:524`), so a
   crafted PDF could slip in a fake ₹5,00,000 income row.
   - Fix: untick and flag rows whose date falls outside the statement period, and amount outliers.
5. **Backup restore applies immediately**, with no preview and no value checks.
   - Fix:
     - read and decode it on IO with a size cap (reuse `util/SharedFiles`);
     - confirm with `backup.summary()` before applying;
     - in `decode`, validate amounts (> 0, finite), `splitType`, and that shares add up.
6. **Subscriptions charge twice after a time-zone change** (Low).
   - Cause: `startDate` and `chargedThrough` are local-midnight epoch milliseconds converted with
     the current zone.
   - Fix: store epoch-day values (this needs a migration, so do it carefully with a test) or
     always convert with one fixed zone.
7. **Suspected: a private split can be skipped for good** when it's pulled before its expense
   (`LocalLedgerSyncEngine.applySplit` plus `pullPaged`). Prove it with a test first. Fix: don't
   advance the cursor past a skipped split.
8. **Home stays on last month after midnight on the 1st** until the next database write
   (`monthlyAnalytics`). Fix: tick the month on a clock flow.
9. **Ask QR file reused per friend** (`AskShare.writeQr`): kept on purpose (eng review). Revisit
   only if an ask is ever seen with the wrong QR.

### 2b. Production hardening that needs no product decision

- Remove the unused `RECORD_AUDIO` permission (`AndroidManifest.xml:6`). Voice input uses
  `RecognizerIntent`.
- Remove dead dependencies: Retrofit, Moshi plus its KSP, logging-interceptor, firebase-ai,
  firebase-appcheck-recaptcha, and the google-services plugin running without
  `google-services.json`. Check with grep before each removal.
- **R8:**
  - set `isMinifyEnabled = true` and `isShrinkResources = true`;
  - add the Credential Manager keep rule and `-keepattributes SourceFile,LineNumberTable`, plus
    `-dontwarn` lines for whatever R8 reports;
  - keep `mapping.txt` as a release artifact.
  - The owner must smoke-test the **signed release** build afterwards: sign-in, sync, widgets, AI,
    ask-to-pay.
- **CI:**
  - a `lintRelease` job and an unsigned `bundleRelease` job;
  - Dependabot;
  - pin actions to commit SHAs;
  - make `release.yml` run the tests and check that `versionCode` went up;
  - `release.yml` builds an AAB too.
- **One instrumented launch smoke test** that constructs `MainViewModel` behind a fake auth
  (replace the `ExampleInstrumentedTest` stub). Decide whether CI runs it on an emulator; if not,
  document a per-release manual run.
- A `BackHandler`: Back on a non-Home tab goes to Home instead of exiting.
- `docs/release-checklist.md`: the release smoke test, with an `adb` script for the parts that can
  be scripted.
- Update `docs/distribution.md`, whose versionCode/versionName are stale.
- Re-run `/graphify . --update` after the merges. The graph is a snapshot of `7856cad`.

### 2c. NEW FEATURE: attach a bill photo to an expense (the agent designs and builds it)

**What the owner asked for (2026-10-24):**
- Someone at DMart wants to keep the bill image with the expense. It's optional, never required.
- It should be properly integrated into the add-expense UI.
- When "Scan bill" is used, that image should be saved with the expense as well.
- "Needs polishing; the agent does the brainstorming and the design itself."

So: **don't ask the owner.** Write the design (use the `superpowers:brainstorming` and
`plan-design-review` skills on your own), record it in `docs/designs/bill-photos.md`, build it,
then show the owner.

**Facts already established:**
- Server: `public.transactions.receipt_storage_path text` exists and is unused
  (`20260913144006_create_core_schema_with_rls.sql:156`).
- Room: `TransactionEntity` has no photo field. `main` is at **v8**, and ask-to-pay (#54) takes
  **v9**, so this feature's migration is **v10** and must be built on top of #54 once it merges.
  Write the Migration, the exported schema JSON and a migration test; never a destructive
  fallback.
- **Scan bill:**
  - `CaptureCard` picks the image (`GetContent`);
  - `SharedFiles.decodeImage` (PR #59) decodes it to at most 2048 px;
  - `MainViewModel.parseReceiptImage(bitmap)` sends it to Gemini, which scales to 1600;
  - the result fills `ConfirmBottomSheet`.

  The bitmap is thrown away afterwards; that's the gap. Shared-in images
  (`handleSharedImageUri`) take the same path.
- The add/edit sheet is `ui/components/ConfirmBottomSheet.kt`; its "Note" row is around line 435.

**A starting design (refine it, don't just copy it):**
- **Storage:**
  - app-private `filesDir/receipts/<accountId>/<uuid>.jpg`, not `cacheDir`, which the system can
    clear;
  - long side ≤ 2000 px, JPEG quality around 80, which comes to about 200–400 KB;
  - **strip EXIF**: phone photos carry GPS location.
- **Data:** a new table `receipts` (id, transactionId with FK cascade, localPath, width, height,
  bytes, createdAt, plus a nullable remotePath for a later cloud phase). Prefer it to a column, so
  multi-page bills can come later without another migration. Phase 1 allows one photo per
  expense.
- **UI:** a "Bill photo · optional" row in the add/edit sheet:
  - empty: "Add" opens Take photo (camera intent through FileProvider, so no CAMERA permission is
    needed) or Choose from gallery (Photo Picker, no permission);
  - filled: a thumbnail that opens a full-screen pinch-zoom viewer, with Replace and Remove.
  - Expense rows in the feed get a small receipt icon.
- **Scan bill and shared images:** keep the image by default. The sheet shows the thumbnail with
  "Keep bill photo" on, and one tap removes it.
- **Lifecycle:**
  - deleting an expense deletes its file;
  - an orphan sweep runs at startup;
  - Undo-delete must not lose the photo, so commit the file deletion with the pending delete.
- **Backups and other phones:**
  - Phase 1 keeps photos on this phone, and the UI says so ("on this phone only");
  - the JSON backup doesn't carry images; consider a ZIP export later.
  - The cloud phase is separate and needs the owner: Supabase Storage, RLS, and a Plus-tier
    gate.
- **Tests:**
  - the migration test;
  - repository tests: attach, replace, delete cascade removes the file, orphan sweep;
  - a UI test for the row's states;
  - `ScreenRenderCapture` states for the sheet with and without a photo.
- **Data safety:** photos are stored on the device only. Disclose them once they sync.

---

## 3. Needs the owner's decision (raise it, don't decide)

**Security and RLS (owner review is mandatory):**
- **Account deletion**, in-app and on the web. This is a Play blocker. Several foreign keys to
  `profiles(id)` have no ON DELETE action, so an Edge Function using the service role must:
  - delete the user's private rows;
  - replace their profile on shared records with a tombstone;
  - delete the `auth.users` row.

  The phone then deletes that account's Room file.
- **Gemini behind a Supabase Edge Function**, with per-user quotas. Today the key is in every APK.
  Also enable billing on the key's project (on the free tier, Google may use the data) and set the
  target audience to 18+.
- **Invite codes can be brute-forced** (40-bit codes; `preview_invite` and `join_group` have no
  attempt limit). Fix: count failed attempts per user (about 10 per hour) and/or longer codes.
- **No length checks** on profile, transaction and settlement text columns. Fix: add `char_length`
  checks.
- **Either party can retract a *confirmed* settlement on their own.** This is intended today. A
  fairer rule: the recorder only, or the retraction needs confirming.

**Store and legal:**
- A privacy policy, using F1's facts (#55) and the Gemini processor.
- The Data safety form inputs are in the release audit; summary: personal info, financial info,
  third-party names, photos and files sent to Gemini, no analytics.
- The financial features declaration.
- A closed test with 12+ testers for 14 days.
- Play App Signing means the **Play SHA-1 must be registered** for Google sign-in and for the
  Gemini key restriction.
- An age check (DPDP: under-18s need parental consent).

**Operations and product:**
- Distribution: Play or GitHub. The repo is private, so GitHub release downloads only work for
  collaborators.
- Supabase Pro at launch.
- Crash reporting: Sentry or Crashlytics.

**Monetisation (research summary):**
- Recommended: "Kharcha Paani Plus" at **₹49/month or ₹399/year** plus a tip jar, with no ads.
- Gate AI volume above a free allowance, statement import, Q&A, receipt cloud storage and
  multi-currency. Never gate entries.
- Run a fake-door paywall first; build real billing if more than about 5% of users who hit the
  quota tap it.

| Costs | |
|---|---|
| Heavy user | ≈ ₹33/month now; Gemini 3.6 Flash doubles on 1 Jan 2027 |
| Supabase Pro | $25/month, which breaks even at about 55 subscribers |

**Product expectations users will have:**
- app lock;
- budgets;
- multi-currency trips;
- recurring roommate splits;
- reminders to friends;
- itemised bill splitting;
- Hindi or Hinglish (this needs string extraction first; there are zero `stringResource` calls);
- a light theme.

---

## 4. Then: brainstorm the product with the owner

Once section 2 is done (or blocked) and section 1 is merged, switch to a conversation.
- Use the **`office-hours`** skill, plus `plan-ceo-review` where it fits, to brainstorm the
  **"SaaS product"** the owner is building, with this app as its base.
- Bring the evidence: the market brief in section 3, the ask-to-pay design's demand and wedge
  analysis (`docs/designs/ask-to-pay.md`), and the open decisions above.
- Aim for an agreed product thesis, pricing, the first paid feature, and the distribution plan.
  Write it up as a design doc.
