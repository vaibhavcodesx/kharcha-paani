# Play Store readiness, 2026-11-17

Where the launch stands against Google Play's requirements, given what the owner said on
2026-11-17: **no developer account exists yet** (the $25 is being held until the app is ready), and
the plan is now an international launch with a paid tier later (see
[03-international-readiness.md](03-international-readiness.md)).

Sources: the code and docs on `origin/main` at `8839b3f`, `docs/legal/`, `docs/store-listing/`,
`site/`, the GitHub releases, and the earlier research in
`docs/production-readiness/audits/play-store.md` (2026-10-24). Play policies change; items marked
**verify** should be re-read on the Play Console help pages before acting.

## The calendar is the real blocker

Waiting to pay the $25 until the app "is ready" costs more time than anything in the code.

- A **personal** developer account created today cannot publish to production until a closed test
  has had **at least 12 testers opted in for 14 days in a row**, and then Google reviews the
  production application (**verify** the current numbers).
- Account creation itself needs identity verification and, later, device verification through the
  Play Console mobile app. That takes days, sometimes longer.
- The closed test is also the only place where three things can be proven at all: sign-in with the
  **Play App Signing** key (the SHA-1 Google generates has to be added to the Android OAuth client,
  or every Play-installed copy fails to sign in), in-app updates, and the in-app review prompt.

**Recommendation:** open the account now and start a closed test with the current 1.7.0. The fixes
in these reports can ship into the running test. Three weeks of calendar time are otherwise lost
after the code is done.

A choice to make when creating it: **personal or organisation account.** A personal account shows
your legal name; once you sell anything (the paid tier), Play also shows your full address publicly
(**verify**). An organisation account needs a D-U-N-S number but skips the 12-tester rule. Decide
this before paying, because it cannot be switched later without moving the app.

## Decisions that cannot be changed after the first upload

| Thing | Now | Note |
|---|---|---|
| Application id | `com.vaibhav.kharchapaani` | Permanent. It contains your first name and a Hindi product name. Fine for India; think once more given the international plan. |
| App name | "Kharcha Paani" | Can change later, per country too. |
| Signing | Upload key in `kharcha-paani-release.jks`, in GitHub secrets | Enrol in Play App Signing at first upload. Keep an offline copy of the upload key. |
| `versionCode` | 8 | Must only go up. Sideloaded 1.6/1.7 testers can update to the Play build only if it is signed with the same key, which Play App Signing will not be. Tell the current testers they must uninstall and reinstall from Play (export a backup first). |

## Blockers (a first review is likely to fail on these)

### P-01 · The store listing says something the app doesn't do, and omits something it does

`docs/store-listing/full-description.txt` ends with "**Google sign-in is required.**" The app has had
a local, no-account mode since 1.7 (`LedgerMode`, and the privacy policy's first section describes
it). The Play answers file says sign-in is optional. Pick one truth and make all three agree.

The listing also says AI content goes "to Google Gemini" only. Typed quick adds go to **Groq** first
since #280, and spoken entries go to Gemini as **audio**. The privacy policy and the in-app notice
(`strings.xml`, `ai_display_notice_title` / `_body`) both say so correctly; only the listing is
behind. Rewrite its last two paragraphs from the in-app notice.

### P-02 · The reviewer needs a way in, and a script

Google sign-in cannot be automated by the review team with a throwaway account that has no data.
Prepare (owner step O8, still open): a dedicated Google account with a seeded group, a split, a
pending settlement and an item-split in progress; its credentials in App access; and three
sentences on how to reach the AI features, account deletion and the microphone. Mention that local
mode needs no sign-in, so the core can be reviewed without it.

### P-03 · Permissions need their justification on screen and in the form

`RECORD_AUDIO` was added in #241. Play expects a prominent in-app explanation before the system
prompt and a matching Data safety entry ("Audio, voice or sound recordings, shared"). The answers
file has the entry. `strings.xml` has no microphone rationale text (the only mic string is
`voice_no_mic`), so the system dialog appears to come up cold on the first tap. Add a one-line sheet
before it ("To hear your entry, Kharcha Paani needs the microphone, only while this bar is open"),
and check on a phone that denying it leaves the fallback working.

### P-04 · Account deletion must work end to end from the web

The in-app path exists and the web page exists. The web path is an email that the owner handles by
hand within 7 days, and there is no tool for doing it (see security S-08). Reviewers do test this.
Have the runbook ready before submission.

### P-05 · Data safety form: three things to fix before pasting it in

`docs/legal/play-console-answers.md` is careful, but:
- Bill photos and group data are visible to **other users** (groupmates). Play's form treats
  user-to-user visibility separately from "sharing with third parties"; say so in the notes so the
  answer does not look inconsistent with the policy.
- It says crash logs are optional and off by default unless a Firebase project is configured. Make
  sure the production build's actual default matches what is declared.
- If the plan moves to the paid Gemini tier (required for EU/UK users, see the international
  report), the "shared with Google for product improvement" rows change. Keep one source of truth.

### P-06 · AI-generated content policy

Insights answers are generated text. Play requires an in-app way to report or flag offensive
AI output. "Report this answer by email" exists (`strings.xml`, `ai_display_report_action`), so
this is covered for Insights. Not a blocker; listed so nobody removes it. Check on a phone with no
email app that the button doesn't fail silently.

## High (fix during the closed test)

- **P-07 · No server-driven minimum version.** With Play you cannot recall a build. See security S-06.
- **P-08 · No app lock** on a finance app. See security S-09. Reviews will mention it.
- **P-09 · The Supabase free plan** (no backups, 1 GB photo storage). See security S-15. A data loss
  in the first month ends the app's reputation.
- **P-10 · Only a dark theme.** Google's quality guidelines expect light and dark support; T3.12 is
  not started. It will not block review, but it will cost ratings from people who use phones outdoors.
- **P-11 · Pre-launch report.** Play runs the app on real devices with a crawler at first upload and
  reports crashes, accessibility issues (touch target size, contrast, missing labels) and slow
  starts. T2.10's Accessibility Scanner pass is still outstanding and #139/#276 are stuck with
  conflicts. Expect a list; budget a day to clear it.
- **P-12 · Tablets, foldables and landscape.** Not designed for. targetSdk 36 on large screens
  ignores orientation locks, so the app will be shown resizable on tablets and Chromebooks whether
  designed for or not. At minimum check nothing is cut off at 600 dp and wider.
- **P-13 · Store assets.** Six screenshots and the feature graphic exist in
  `docs/store-listing/assets/` and are rendered from real screens. They show rupees and UPI
  throughout, which is right for India only (see the international report).

## What is already in good shape

- targetSdk 36, compileSdk 37, minSdk 24; AAB built and attached by `release.yml`; R8 on, mapping kept.
- No SMS, contacts, location, storage or exact-alarm permissions; pickers are the system ones.
- Privacy policy and terms are live, specific and honest about the free Gemini tier; deletion page live.
- 18+ line at sign-in; Restrict Minor Access planned.
- "Does not hold or move money" is stated in the listing; the Financial features answer is drafted.
- In-app update (flexible) and in-app review are wired, with tests.
- An incident plan exists (`docs/incident-plan.md`).

## Order of work for the owner (console side)

1. Decide account type, pay, verify identity. Create the app entry with the final application id.
2. Enrol in Play App Signing; upload the 1.7.0 AAB to **internal testing** first.
3. Add the Play signing SHA-1 to the Android OAuth client; test sign-in from the Play-installed build.
4. OAuth consent screen to "In production".
5. Fill App content from the answers file (after P-01 and P-05), add the reviewer account (P-02).
6. Start the closed test, recruit 15–20 people, start the 14-day clock.
7. During the 14 days: the High items here and in the security report, then apply for production.
