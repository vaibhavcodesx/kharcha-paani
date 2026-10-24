# Kharcha Paani: Play Store launch checklist (researched 2026-10-24)

Scope: a first-time Play submission from a **personal developer account in India**, aimed at about 1,000 users.
Repo snapshot: `main` at 7a1342a. Several open PRs (#64, #68, #70, #71, #74, #75, #76) change items below. Where one does, it is noted. File references are relative to `Z:\V\.The_Future\Vibeeee\Kharcha Paani`.

Confidence levels:
- **High**: read on an official Google or Government of India page today.
- **Med**: official page plus my inference.
- **UNVERIFIED**: no official source says it; this is my judgement.

---

## 0. Blockers at a glance (fix before the closed test starts)

| # | Blocker | Status in repo |
|---|---|---|
| B1 | **Account deletion**, in the app and by web link | Missing. Only sign-out exists (`app/src/main/java/com/vaibhav/kharchapaani/data/auth/AuthRepository.kt:184`). |
| B2 | **Privacy policy**, at a public URL and also linked inside the app | Missing. No "privacy" string anywhere in `app/src/main`. |
| B3 | **Gemini paid tier**: link billing to the key's project | Owner action. The free tier lets Google use inputs for training, and its terms say not to send personal info (see §12). |
| B4 | **Play App Signing SHA-1** registered with the Google OAuth client and the Gemini key restriction | Owner action. Otherwise every Play-installed build fails sign-in, which is already documented in `docs/distribution.md:114`. |
| B5 | **Unused `RECORD_AUDIO` permission** | Still in `app/src/main/AndroidManifest.xml:6`. Voice input uses `RecognizerIntent` (`ui/home/CaptureCard.kt:100`), which needs no permission. Removed in PR #64, not yet merged. |
| B6 | **Sign-in details for reviewers** (the app is Google-sign-in only) | Owner action. See §8. |
| B7 | **12 testers opted in for 14 continuous days**, then apply for production | Owner action. See §2. |

---

## 1. Target SDK, 16 KB pages, edge-to-edge, predictive back

**1a. Target API level**
- **Requirement:** from **31 Aug 2026**, new apps and updates must target **API 36 (Android 16)**. An extension to 1 Nov 2026 was available. Existing apps must target API 35 or higher to stay visible to new users on newer Android versions. Google has not yet published a 2027 deadline. Expect API 37 around Aug 2027 (UNVERIFIED).
- **Applies:** yes.
- **Repo:** compliant. `app/build.gradle.kts:80` has `targetSdk = 36`, and `:75` has compileSdk 36.1.
- **Sources:**
  - https://support.google.com/googleplay/android-developer/answer/11926878
  - https://developer.android.com/google/play/requirements/target-sdk
- **Confidence:** High.

**1b. 16 KB memory page size**
- **Requirement:** apps targeting API 35 or higher must support 16 KB pages on 64-bit devices. The developer page reads: "Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates." Third-party sites give earlier dates (Nov 2025, May 2026); treat the official date as current. Pure Kotlin/Java apps comply automatically, but bundled `.so` files must be aligned.
- **Applies:** yes. The release APK ships two transitive native libraries: `libandroidx.graphics.path.so` (from Compose) and `libdatastore_shared_counter.so` (from DataStore).
- **Repo:** compliant. I parsed `app/build/outputs/apk/release/app-release.apk` (built 2026-10-23) read-only:
  - every arm64-v8a and x86_64 LOAD segment has `p_align = 0x4000`;
  - the libraries are stored uncompressed at 16 KB-aligned zip offsets;
  - AGP is 9.1.1 (`gradle/libs.versions.toml:2`), above the 8.5.1 minimum.
- **Action:** after uploading the AAB, check Play Console → App bundle explorer → "Memory page size". Re-check whenever a dependency with native code is added.
- **Source:** https://developer.android.com/guide/practices/page-sizes
- **Confidence:** High for the requirement and the APK check. Med for the date (an official page and forum posts disagree).

**1c. Behaviour changes at targetSdk 36**
- **Edge-to-edge** cannot be turned off on Android 16 (`windowOptOutEdgeToEdgeEnforcement` is ignored).
  - Repo: `enableEdgeToEdge()` at `MainActivity.kt:123`. Insets are handled in `MainActivity`, `SignInScreen`, `Sheets`, `ConfirmBottomSheet` and `LedgerOpenFailedScreen`. No opt-out attribute exists.
  - Action: on an Android 16 device, check that nothing sits under the status bar, the gesture bar or the keyboard, on every screen and sheet.
- **Predictive back** is on by default. `onBackPressed` is no longer called and `KEYCODE_BACK` is no longer dispatched.
  - Repo: no `onBackPressed` and no `BackHandler` in `app/src/main`. Back on a tab therefore exits the app. PR #70 ("Back on Splits/Insights goes Home") fixes this. Compose `ModalBottomSheet` already handles back.
  - Action: merge #70, then test back-to-home on Android 15 and 16.
- **Large screens (sw600dp and up):** orientation and resizability restrictions are ignored.
  - Repo: content is capped with `widthIn(max=…)` (`MainActivity.kt:502`, `KpDock.kt:98`).
  - Action: run once on a tablet or foldable emulator so the pre-launch report's tablet devices show nothing broken.
- **Source:** https://developer.android.com/about/versions/16/behavior-changes-16
- **Confidence:** High.

## 2. New personal developer account rules

- **Testing gate:** personal accounts created after 13 Nov 2023 need **at least 12 testers opted in to a closed test, continuously for the preceding 14 days**. A tester who opts out and back in restarts their own 14 days.
  - You then press **Apply for production** and answer three groups of questions:
    - how recruiting went, whether testers used every feature, and a summary of their feedback and how you collected it;
    - your target audience, value proposition and estimated first-year installs;
    - what you changed and why you think the app is ready.
  - Review usually takes 7 days or less.
  - Tip (UNVERIFIED): recruit 15–20 testers, since some will drop out. Make sure they actually use the app, because engagement is assessed.
  - Source: https://support.google.com/googleplay/android-developer/answer/14151465. Confidence: High.
- **Device verification:** before you can publish, the account must prove access to a real, non-rooted Android 10+ phone through the Play Console mobile app.
  - Source: https://support.google.com/googleplay/android-developer/answer/14316361. Confidence: High (found in search results; the help page itself was not fetched).
- **Identity:**
  - You provide a legal name and address (from the payments profile), plus a contact email, contact phone and developer email, each verified by one-time password.
  - **D-U-N-S is for organizations only.**
  - **Google shows your legal name, country and developer email on Google Play**. If you monetize, your full address is shown too.
  - Source: https://support.google.com/googleplay/android-developer/answer/13628312. Confidence: High.
- **Android developer verification (rolling out 2026–27):**
  - Aug 2026: launch.
  - 30 Sep 2026: enforcement starts in Brazil, Indonesia, Singapore and Thailand.
  - 2027: expands globally.
  - Google Play "automatically registers 99% of apps", and Play Console is the place to register. Nothing extra is needed for a Play-only app beyond normal Play identity verification (Med).
  - If you also keep sharing APKs from GitHub Releases, register the package in Play Console so those sideloaded installs still work in India once enforcement reaches it in 2027.
  - Source: https://developer.android.com/developer-verification. Confidence: Med.
- **Distribution format:** upload an **AAB** with **Play App Signing**. Play then re-signs the app with its own key, which gives a new SHA-1. Register that SHA-1 for Google sign-in (Credential Manager) and for the Gemini key restriction (`AppIdentity.restrictionInterceptor`, `GeminiRepository.kt:668`). This is blocker B4.
  - Source: https://support.google.com/googleplay/android-developer/answer/9842756. Confidence: High.

## 3. Account deletion, Data safety, privacy policy

**3a. Account deletion (blocker B1)**
- **Requirement:** an app that creates accounts must offer:
  - a "readily discoverable" way to start deletion **inside the app**;
  - a **web link** where users can request deletion. It must name the app or developer exactly as Play shows them, and you enter it in the Data safety form.
- Deletion must remove the data tied to the account, including copies held by service providers.
- "Temporary account deactivation, disabling, or 'freezing'… does not qualify."
- If you keep anything (for example, a tombstone on shared group records), say so in the privacy policy.
- **Applies:** yes. Supabase accounts via Google sign-in.
- **Repo:** no in-app deletion exists, and `docs/handoffs/2026-10-24-owner-decisions.md:39` already scopes it. Needed:
  - an Edge Function using the service role that deletes private rows, tombstones the profile on shared records, and deletes the `auth.users` row;
  - wipe of the local Room file and the receipts folder;
  - a static web page (it can be a form or a mailto, plus a description) for people who have already uninstalled.
- **Sources:**
  - https://support.google.com/googleplay/android-developer/answer/13327111
  - https://support.google.com/googleplay/android-developer/answer/10144311
- **Confidence:** High.

**3b. Data safety form**
- **Definitions:**
  - "Collected" means sent off the device. Data that stays on the device is not declared.
  - Sending data to a **service provider** acting on your instructions is not "sharing".
  - **Ephemeral** processing (held only in memory) is still declared, but is not shown on the listing.
  - A transfer the user explicitly starts (the UPI app chooser, the WhatsApp share) is exempt from "sharing".
- What this app should declare, from reading the repo (Med):

| Data type | Collected? | Why | Notes |
|---|---|---|---|
| Personal info → Name, Email, User IDs | Yes | Google sign-in, then a Supabase profile | Required; used for app functionality and account management |
| Personal info → Other info (friends' and guests' names) | Yes | Synced groups and participants | Third-party names. Mention them in the privacy policy |
| Financial info → User payment info | Yes | The `profiles.upi_id` column (`supabase/migrations/20260918210534_add_upi_id_to_profiles.sql:15`) is visible to group members | A UPI ID is a payment identifier. Declaring it is the cautious choice (UNVERIFIED) |
| Financial info → Purchase history / Other financial info | Yes | Expenses, splits, settlements and IOUs synced to Supabase; statement rows read by Gemini | |
| Photos and videos → Photos | Yes (ephemeral) | Bill photos and shared screenshots are sent to Gemini to be read | PR #76 stores bill photos **on the device only**; declare cloud storage only if photos ever sync |
| Files and docs | Yes (ephemeral) | Statement PDFs sent to Gemini | |
| App activity → Other user-generated content | Yes | Notes, typed or spoken quick-add text sent to Gemini, Insights questions | |
| Audio | **No** | Speech is recognised by the system's recognizer, and the app receives only text | Only true once RECORD_AUDIO is removed (B5) |
| Contacts, location, device IDs, ads ID | No | No such permission. No `AD_ID` in the merged manifest | Check the merged manifest again after PR #64 removes `firebase-ai` and `firebase-appcheck-recaptcha`, both unused, so no Firebase Installation ID gets sent (UNVERIFIED) |
| Crash logs / diagnostics | Not today | No crash reporter | If Crashlytics or Sentry is added (decision #16), declare App info and performance → Crash logs and Diagnostics |

- **Encrypted in transit:** yes. Supabase and Gemini are both HTTPS, and cleartext is blocked by default at targetSdk 36.
- **Deletion can be requested:** yes, once B1 ships.
- **Gemini counts as a "service provider" only on the paid tier.** On the free tier Google uses inputs "to provide, improve" its products, which arguably makes it "sharing". This is another reason for B3 (UNVERIFIED reasoning).
- **Sources:**
  - https://support.google.com/googleplay/android-developer/answer/10787469
  - https://support.google.com/googleplay/android-developer/answer/10144311 (the User Data policy states the rules "also apply to third-party AI integrations")
- **Confidence:** High for the definitions, Med for the mapping.

**3c. Privacy policy (blocker B2)**
- **Requirement:** the policy must:
  - have a link in the Play Console field **and** a link or the text **inside the app**;
  - be an active, public, non-geofenced URL and not a PDF;
  - cover what data is collected, used and shared, how it is secured, and how long it is kept and how it is deleted.
- **Repo:** missing. Add it to the sign-in screen (`ui/auth/SignInScreen.kt`) and to the account sheet (`ui/auth/AccountSheet.kt`).
- **What it must cover:** Google sign-in, Supabase as processor (state the region), Gemini as processor (paid tier), group members seeing each other's names and UPI IDs, local backups, retention, deletion, and an 18+ statement. It should also include the DPDP items in §11.
- **Source:** https://support.google.com/googleplay/android-developer/answer/10144311
- **Confidence:** High.

**3d. Prominent disclosure before AI use (recommended)**
- The policy requires an in-app disclosure, followed by consent, when data use is something "a user might not reasonably expect". Sending a bank statement to a third-party AI is close to that line.
- **Repo:** no string in the app tells users their data goes to Google or Gemini.
- **Action:** show a one-time sheet before the first scan or statement import: "Photos, PDFs and text you send are read by Google Gemini…", with Continue and Cancel buttons. This is cheap, and it also serves as DPDP notice and consent.
- **Source:** https://support.google.com/googleplay/android-developer/answer/10144311
- **Confidence:** Med.

## 4. Financial features declaration

- **Requirement:** **every** app on any track, including closed testing, must complete it. You may answer "My app doesn't provide any financial features".
- **Categories offered:**
  - Banking and loans
  - Payments and transfers (mobile payments and wallets, money transfer)
  - Purchase agreements
  - Trading and funds
  - Support services (credit monitoring, financial advice, insurance, Other)
- Expense tracking and splitting is **not listed**.
- **Personal loans policy:** "lending money from one individual… to an individual consumer". In India, such apps must appear on RBI's "Digital lending apps (DLAs) deployed by Regulated Entities" list. **This should not apply.** Kharcha Paani never lends, arranges loans, holds money or moves money: the IOU feature (`ui/home/CaptureCard.kt:188`) only *records* a debt, and UPI is handed to the user's own UPI app through a `upi://pay` intent (`AndroidManifest.xml:17`).
  - The policy text does not mention informal IOUs or bill splitting. This conclusion is UNVERIFIED by any official statement.
- **What to declare (UNVERIFIED; no official guidance for splitters):**
  - Choose "My app doesn't provide any financial features", or, to be cautious, Support services → "Other" with this note: "Records shared expenses and IOUs between friends; creates UPI payment links opened in the user's own UPI app; does not lend, hold, or transfer money."
  - **Do not tick** Mobile payments, Money transfer, or either loan option. Each of those requests licences or lender documents.
- **Listing wording:** keep "loan", "lend", "borrow" and "credit" out of the title, short description and screenshots. Automated classifiers do look at the listing (UNVERIFIED). "IOU" and "settle up" are fine.
- **Payments policy:** peer-to-peer payments **must not** use Play Billing. UPI settle-up is fine. A future "Plus" subscription sold inside the app **must** use Play Billing, unless you enrol in an alternative-billing programme where India is eligible (check that list).
- **Sources:**
  - https://support.google.com/googleplay/android-developer/answer/13849271
  - https://support.google.com/googleplay/android-developer/answer/9876821
  - https://support.google.com/googleplay/android-developer/answer/9858738
- **Confidence:** High for the requirement, Med for the classification.

## 5. Permissions: photos, notifications, exact alarms, foreground services

- **Photo and Video Permissions policy:**
  - `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` are allowed only when system pickers are not enough for core functionality. Otherwise remove them.
  - Full compliance has been mandatory since **28 May 2025**.
  - **Repo:** compliant. No media permission is declared. Images use `ActivityResultContracts.GetContent()` with `image/*` (`ui/home/CaptureCard.kt:126`), and PDFs use `GetContent` (`:117`).
  - Optional polish: use `PickVisualMedia(ImageOnly)` for bills. It opens the modern photo picker, which looks more native than the document UI.
  - Source: https://support.google.com/googleplay/android-developer/answer/14115180. Confidence: High.
- **POST_NOTIFICATIONS:**
  - Google's guidance: ask in context, after the user has seen the app's value, and show a rationale only when `shouldShowRequestPermissionRationale` is true.
  - **Repo:** good. The app asks only after the first own entry, with the reminder enabled (`MainActivity.kt:417-424`), and opens system settings when permanently denied (`:426-440`). Channels are created in `util/NotificationHelper.kt:33-50`.
  - Source: https://developer.android.com/develop/ui/views/notifications/notification-permission. Confidence: High.
- **Exact alarms:**
  - `USE_EXACT_ALARM` is only for alarm, timer and calendar apps, and needs a declaration.
  - **Repo:** not used. The daily reminder runs on WorkManager (`reminder/DailyReminderWorker.kt:92-98`). Keep it that way. A reminder a few minutes late is fine for this app.
  - Source: https://support.google.com/googleplay/android-developer/answer/16558241 (from search). Confidence: Med.
- **Foreground service types:**
  - Apps targeting 14 and up must declare each foreground service type they use in Play Console, with a video.
  - **Repo:** the merged release manifest has only the generic `FOREGROUND_SERVICE` (from WorkManager's `SystemForegroundService`), no `FOREGROUND_SERVICE_<TYPE>` permission, no `foregroundServiceType`, and no `setForeground` call. Nothing should need declaring (Med).
  - If Play Console asks anyway, answer that the app has no foreground services.
  - Source: https://support.google.com/googleplay/android-developer/answer/13392821. Confidence: Med.
- **Other permissions in the merged manifest:**
  - `USE_BIOMETRIC` and `USE_FINGERPRINT` come from a library. They are harmless, and normal-level permissions need no declaration.
  - `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` come from WorkManager.
  - `RECORD_AUDIO` must go (B5).

## 6. AI-Generated Content policy

- **Requirement:** this applies to apps whose generative AI creates content from user prompts, with chatbots and image generators given as examples. In-scope apps need:
  - **in-app reporting or flagging of offensive AI output**, without leaving the app;
  - measures that prevent restricted content.
- **Applies:** partly (Med).
  - Receipt and statement extraction produces structured data, not "content". Probably out of scope (UNVERIFIED).
  - **Insights** (`domain/AskBoard.kt`) shows free-text answers generated by Gemini. This is closest to "text-to-text". It only uses three fixed prompts, which lowers the risk, but the cheap fix is still worth it.
- **Action:**
  - Add a "Report this answer" item on each Insights answer. It can send an email or write a Supabase row.
  - Keep Gemini's default safety settings. The app already handles a "safety or policy block" (`domain/AiPrompts.kt:145`).
- **Source:** https://support.google.com/googleplay/android-developer/answer/13985936
- **Confidence:** Med.

## 7. Play Integrity API: worth it?

- **What it is:** a server-verified attestation that requests come from your genuine Play-installed app on a certified device. It is free up to **10,000 requests/day** by default and is **not required by Play**. Google recommends using it alongside other signals, not as the only defence.
- **Verdict for this app:** **don't add it standalone for v1.** The real abuse risk is the Gemini key compiled into the APK (`GeminiRepository.kt:674-676`). Google's API-key page says "Do not hardcode API keys directly in web or mobile apps" and recommends a backend proxy.
- **Two good fixes:**
  1. A Supabase Edge Function that holds the key and enforces per-user quotas, which is decision #4. Play Integrity could later guard that function.
  2. **Firebase AI Logic with App Check (Play Integrity provider).** The `firebase-ai` and App Check dependencies are already in `app/build.gradle.kts:190,206` but unused. Two catches:
     - the App Check provider is wired to reCAPTCHA, the wrong one for Android, and needs to be Play Integrity;
     - Firebase states: "Starting November 2, 2026, Firebase App Check enforcement will be required to use Firebase AI Logic."
- **Sources:**
  - https://developer.android.com/google/play/integrity/overview
  - https://ai.google.dev/gemini-api/docs/api-key
  - https://firebase.google.com/docs/ai-logic/app-check
- **Confidence:** High for the facts, Med for the recommendation.

## 8. Android vitals, pre-launch report, reviewer access

- **Bad-behaviour thresholds** (28-day window; Play may act sooner on a spike):
  - user-perceived crash rate: **1.09%** overall, or **8%** on any single device model;
  - user-perceived ANR rate: **0.47%** overall, or **8%** per device;
  - excessive partial wake locks: more than 5% of sessions (beta, no visibility penalty yet).
  - Crossing a threshold lowers discoverability and can put a warning on the listing.
  - Source: https://support.google.com/googleplay/android-developer/answer/9844486. Confidence: High.
- **Staying under them:**
  - Add a crash reporter before the closed test (decision #16). Without one you only get Play's vitals, which lag and lack context.
  - With R8 on (PR #71), upload `mapping.txt` with every release.
  - Keep disk, PDF and image work off the main thread (PR #59 did this).
  - Test background work with the app backgrounded (see the supabase-kt `awaitInitialization` memory note).
  - Run the signed release build on a real device before every upload (memory: "smoke-test the release build"; PR #67 adds a script).
  - Merge #68 (java.time desugaring), or Android 7 devices will crash and count against the crash rate.
- **Pre-launch report:**
  - Runs automatically on bundle upload or when a production release is saved, "where capacity permits", usually within an hour.
  - Tests on Firebase Test Lab devices (phones, tablets, Chromebooks, Wear).
  - Reports stability (including non-SDK interface use), performance, accessibility (labels, touch targets, contrast) and screenshots.
  - With **Sign in with Google**, the crawler can log in by itself. Otherwise add test credentials under Test and release → Pre-launch report → Settings.
  - Fix every red stability and accessibility cluster before applying for production.
  - Sources: https://support.google.com/googleplay/android-developer/answer/9842757 and https://support.google.com/googleplay/android-developer/answer/9844487. Confidence: High.
- **App access for human reviewers (B6):** the app is fully gated behind Google sign-in (`MainActivity.kt:142-151`). Under App content → **Sign-in details**:
  - give reviewers a dedicated test Google account (a fresh Gmail account without 2-Step Verification, or with a backup code) and instructions;
  - credentials must be "accessible at all times, reusable, and valid regardless of user location", and written in English;
  - seed that account with a little sample data (a group, a split, a settlement) so reviewers see the features.
  - A missing or expired login is a common cause of rejection.
  - Sources: https://support.google.com/googleplay/android-developer/answer/15748846 and https://support.google.com/googleplay/android-developer/answer/9859455. Confidence: High.

## 9. Store listing and App content declarations

- **Graphics:**
  - Icon: 512×512, 32-bit PNG, at most 1 MB.
  - **Feature graphic: 1024×500**, JPEG or 24-bit PNG, no alpha.
  - Screenshots: 2–8 per device type, 320–3840 px, long side no more than twice the short side. **Provide at least 4 portrait screenshots of at least 1080×1920** to be eligible for recommendations.
  - **Short description: 80 characters at most.**
- **Not allowed in the feature graphic or short description:**
  - "Best", "#1", "Top", "New", "Free", "Discount" or install counts;
  - calls to action such as "Download now";
  - device frames or Play badges in screenshots;
  - third-party trademarks without permission. **Do not show GPay, PhonePe or Paytm logos.**
- **Repo:** the `assets/` folder is empty, so all listing art still has to be made. Use real screenshots, which you can take from the Roborazzi or `ScreenRenderCapture` setup, at 1080×1920 or higher.
- **Content rating (IARC):** required, or the app is removed. Answer honestly: no violence or gambling. **Users interact** (shared groups and names) and **users share info** (UPI IDs). Expect an "Everyone/3+" or similar rating, plus interactive-element notices.
- **Target audience:** choose **"18 and over" only**, and consider **Restrict Minor Access**, which blocks users Google identifies as minors from finding or installing the app. This lines up with the Gemini API's 18+ rule (§12) and with DPDP parental consent (§11). The Target audience section requires the ads declaration, app access and the privacy policy to be done first.
- **Ads declaration:** "No ads". Accurate today.
- **Category:** **Finance** (UNVERIFIED as the best fit, but standard for expense trackers). Tags: budgeting, expense tracker.
- **Also in App content:** government apps (no), news (no), health (no), COVID (no), Data safety, Financial features, Advertising ID (no, no `AD_ID` permission).
- **Sources:**
  - https://support.google.com/googleplay/android-developer/answer/9866151
  - https://support.google.com/googleplay/android-developer/answer/9898843
  - https://support.google.com/googleplay/android-developer/answer/9867159
- **Confidence:** High for specs and rules, Med for category and rating guesses.

## 10. Core app quality: what reviewers and users notice

These come from Google's core app quality guidelines (https://developer.android.com/docs/quality-guidelines/core-app-quality, High):

| Guideline | Status in repo | Action |
|---|---|---|
| Back_Button_Nav / Back_Gesture_Nav | Back on a tab exits the app | Merge #70. Test predictive back |
| Theme_Support (light *and* dark) | **Dark only**: `ui/theme/Theme.kt:30` defaults to `darkTheme = true`, and there is no light scheme | Not a policy blocker, but it is a quality-guideline gap; the backlog already lists a light theme. Make sure system bars and splash look right |
| Touch_Target_Size ≥ 48 dp, Content_Description, Visual_Contrast (4.5:1 small text) | 39 `contentDescription = null` (some are decorative) | Run Accessibility Scanner and read the pre-launch report's accessibility tab. Give interactive icons labels for TalkBack |
| Font scaling (not in the guideline list; Android a11y guidance) | `SignInScreen.kt:113` adapts to `fontScale`. 12 fixed `.height(N.dp)` modifiers in `ui/` | Test at 200% font size. Fixed heights around text clip it |
| State_Preservation | Room plus saved state | Check rotation and "Don't keep activities" mid-flow (sheets, IOU entry) |
| Runtime_Permissions / Minimize_Permissions | Good, apart from RECORD_AUDIO | B5 |
| Sensitive_Data_Logging | 54 `Log.*` calls; spot-checked `Log.i` calls are benign | Grep for amounts, names or tokens in log calls before release. R8 can strip `Log.d`/`Log.v` |
| Network_Security_Configuration | None declared (cleartext is off by default at targetSdk 36) | Optional: add a `network_security_config.xml` that blocks cleartext, to pass the guideline literally |
| App_Startup_Time / Stability_ANR | Startup shows a blank charcoal frame while the session opens (`MainActivity.kt:150`) | Add a splash screen through `androidx.core:core-splashscreen`. Measure cold start on a low-end phone |
| Offline behaviour | Local-first Room ledger with a sync queue (WorkManager) | Make sure AI buttons fail with a clear "needs internet" message, not a spinner. Test airplane mode |
| Large screens | Content width capped | One tablet pass |
| Localisation | 0 `stringResource` calls; all strings are hard-coded English | Fine for launch. Hindi needs string extraction first |

- **In-app review API** (`com.google.android.play:review`):
  - Ask after a success moment, for example after a settlement is confirmed.
  - **Never put it behind a button**, and never ask "Do you like the app?" first.
  - Play enforces an unpublished quota, so the dialog may not appear.
  - Source: https://developer.android.com/guide/playcore/in-app-review. Confidence: High.
- **In-app updates API:** a flexible update suits normal releases, and an immediate one suits critical fixes, for example a sign-in break. It only works for installs from Play. Not required, but cheap.
  - Source: https://developer.android.com/guide/playcore/in-app-updates. Confidence: High.

## 11. India DPDP Act 2023 + DPDP Rules 2025

**Timeline (official):** the Rules were notified on 13/14 Nov 2025.
- Rules 1, 2 and 17–21 (the Board) took effect at once.
- **Rule 4 (Consent Managers) starts after 1 year, about 13 Nov 2026.** It is not relevant to this app.
- **Rules 3 and 5–16 (notice, security, breach, retention, children, rights, grievance) start after 18 months, about 13 May 2027.**
- MeitY proposed cutting the 18 months to 12 in Jan 2026. **I found no amending notification.** That comes from secondary sources only (UNVERIFIED), so check the Gazette before relying on May 2027.

**What a small app has to do, cheaply** (do it now, since it is the same work as the Play items):
1. **Notice (Rule 3).** Keep it standalone and in plain language:
   - an itemised list of the data and the purposes;
   - how to withdraw consent ("as easy as giving it");
   - how to exercise rights;
   - how to complain to the Data Protection Board.

   The §3d sheet plus the privacy policy covers it.
2. **Contact (Rule 9).** Publish a contact person for data questions in the app and on the website. A personal email is fine. No DPO is needed: that is only for Significant Data Fiduciaries.
3. **Grievances (Rule 14).** Publish your response period and answer within **90 days**.
4. **Rights.** Access, correction, erasure and nomination must be answered within 90 days.
   - Erasure: B1 covers it.
   - Access: the existing CSV and JSON export largely covers it.
5. **Security (Rule 6).** Reasonable safeguards: TLS, Supabase RLS (already tested in `supabase/tests/`), no keys in the APK (§7), and access logs.
   - Rule 8(3) says to keep processing logs for at least 1 year. Check what Supabase log retention your plan gives you.
6. **Breach (Rule 7).**
   - Tell each affected user without delay: what happened, the likely impact, what you did, what they should do, and a contact.
   - Tell the Board without delay, with a detailed report **within 72 hours**.
   - Write a one-page incident plan now.
7. **Retention (Rule 8).** The fixed 3-year limit and the 48-hour pre-erasure warning apply only to Third Schedule entities (e-commerce or social media with 2 crore+ users, gaming with 50 lakh+). **Not this app.** You still owe the general duty to erase data once its purpose ends. State a retention rule in the policy, for example "deleted within 30 days of account deletion; backups roll off within N days".
8. **Children (Rule 10).** Processing data of anyone under 18 needs verifiable parental consent. The cheapest compliant path is **adults only**:
   - Play's 18+ target audience with Restrict Minor Access;
   - an "I confirm I'm 18 or older" line on sign-in;
   - a policy statement.
9. **Penalties for scale:** up to ₹250 crore for security failures, and up to ₹200 crore each for breach-notice and children's-data violations.

**Sources:**
- PIB explainer (17 Nov 2025): https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf
- Rules text: https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf (Rule 1 commencement, and Rules 3, 7, 8, 9, 10 and 14 quoted from the PDF)
- Acceleration proposal (secondary): https://www.mondaq.com/india/data-protection/1773554/ and https://indiadatalaw.org/deadlines/

**Confidence:** High for the Rules text and dates, UNVERIFIED for the amendment status, Med for the small-app interpretation. This is not legal advice.

## 12. Gemini API terms

(Additional Terms effective 23 Mar 2026, last updated 2026-04-28.)
- **Free ("Unpaid") tier:**
  - Google uses prompts and responses "to provide, improve" its products, and **human reviewers may read** them.
  - The terms say: "**Do not submit sensitive, confidential, or personal information to the Unpaid Services.**"
  - Receipts, bank statements and friends' names are exactly that. **The free tier cannot be used for this app in production (B3).**
- **Paid tier:**
  - Prompts and responses are not used to improve Google's products.
  - They are logged for a limited time only to detect abuse, and may be cached in any country. Grounding with Search keeps data 30 days; the app doesn't use it.
  - How to move: link a Cloud Billing account and **prepay at least $5**. Billing is per project.
  - Sources: https://ai.google.dev/gemini-api/terms and https://ai.google.dev/gemini-api/docs/billing.
- **Age:** "You must be 18 years of age or older to use the APIs". The app must not be "directed towards or likely to be accessed by individuals under the age of 18". This is the second reason for the 18+ audience setting.
- **Regions:** you may make API clients available only in available regions. **India is listed** (page updated 2026-10-22). If the app is distributed in the EEA, Switzerland or the UK, you must use paid services there. Simplest: limit Play distribution to India at first, or pay anyway (you have to regardless). Source: https://ai.google.dev/gemini-api/docs/available-regions.
- **Key security:** "Do not hardcode API keys directly in web or mobile apps… Keys compiled in client-side code can be extracted." Today the key is in `BuildConfig` (`GeminiRepository.kt:674-676`). It is restricted to the app's package and SHA-1, which helps but can be spoofed. See §7 for the proxy options. Source: https://ai.google.dev/gemini-api/docs/api-key.
- **Model note** (from the repo, not verified): `gemini-3.6-flash` with a `gemini-3.5-flash-lite` fallback (`GeminiRepository.kt:623-626`). Check the models page for deprecation dates before launch.
- **Confidence:** High.

---

## Items I could not verify from an official source

- Which Financial-features answer Google expects from an expense splitter with UPI links and IOUs (§4).
- Whether Play treats Insights answers as "AI-generated content" (§6).
- Whether a UPI ID belongs under "User payment info" or "Other financial info" in Data safety (§3b).
- Whether an amendment shortening the DPDP 18-month period was notified in 2026. I found none; secondary sources say none had been published as of about Sep 2026 (§11).
- The 16 KB date: the official page says 1 Feb 2027 for updates, while forum and vendor posts cite Nov 2025 or May 2026. The app complies either way (§1b).
- The next target-API deadline, likely API 37 in Aug 2027 (§1a).
- Whether the Firebase libraries in the merged manifest (unused today) send any device identifier (§3b). PR #64 removes them.
