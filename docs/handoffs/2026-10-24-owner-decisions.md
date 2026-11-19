# Decisions waiting for the owner (2026-10-24)

Collected from handoff section 3, plus the ones this session's work raised. Nothing here has been
decided by the agent. Where there's a recommendation it is only that; the choice is yours.

## A. Merge the PRs opened today

None of these were merged by the agent: each changes app behaviour you said you'd test on the
device. Every one is green in CI, read per test. A suggested order, so the CHANGELOG conflicts stay
one at a time:

| # | What | Needs | Notes |
|---|---|---|---|
| [#61](https://github.com/vaibhavcodesx/kharcha-paani/pull/61) | Backup: a deleted friend's split no longer restores as paid by you | device check optional | red→green proven |
| [#62](https://github.com/vaibhavcodesx/kharcha-paani/pull/62) | Sync: a private split pulled before its expense isn't lost | — | red→green proven |
| [#63](https://github.com/vaibhavcodesx/kharcha-paani/pull/63) | Home moves on to the new month at midnight | — | |
| [#66](https://github.com/vaibhavcodesx/kharcha-paani/pull/66) | Subscriptions: no double charge after a time-zone change | — | no migration |
| [#72](https://github.com/vaibhavcodesx/kharcha-paani/pull/72) | Statement import leaves doubtful rows unticked | device: import a statement | outlier rule is income-only |
| [#73](https://github.com/vaibhavcodesx/kharcha-paani/pull/73) | Backup restore shows what's in it first, and checks its numbers | device: restore a file | |
| [#68](https://github.com/vaibhavcodesx/kharcha-paani/pull/68) | The app runs on Android 7 (java.time desugaring) | an Android 7 device if you have one | found by lint |
| [#64](https://github.com/vaibhavcodesx/kharcha-paani/pull/64) | Remove the microphone permission and dead libraries | release smoke test | |
| [#65](https://github.com/vaibhavcodesx/kharcha-paani/pull/65) | CI: lint + release bundle on every PR; release.yml checks; pinned actions; Dependabot | — | after it merges, regenerate the lint baseline |
| [#71](https://github.com/vaibhavcodesx/kharcha-paani/pull/71) | R8 shrinking (stacked on #65) | **signed release smoke test before merging** | builds clean; runtime unproven |
| [#69](https://github.com/vaibhavcodesx/kharcha-paani/pull/69) | Tests: open the signed-in app and every tab | — | |
| [#70](https://github.com/vaibhavcodesx/kharcha-paani/pull/70) | Back on Splits/Insights goes Home | device: press Back | |
| [#67](https://github.com/vaibhavcodesx/kharcha-paani/pull/67) | Release checklist + `scripts/release-smoke.sh` | run it once | |
| [#74](https://github.com/vaibhavcodesx/kharcha-paani/pull/74) | **Security:** internal actions only through a non-exported entry | **your review** | |
| [#75](https://github.com/vaibhavcodesx/kharcha-paani/pull/75) | **Security:** account change clears notifications and shared files (stacked on #74) | **your review** | |
| [#76](https://github.com/vaibhavcodesx/kharcha-paani/pull/76) | Bill photos (Room v10) | device: add, view, replace, remove, delete | design in docs/designs/bill-photos.md |

Small decisions inside them:
1. **CI on an emulator?** #69 runs the launch test under Robolectric on every PR and the device
   version by hand per release. An emulator job adds about 10 minutes and some flakiness.
2. **Bill photo thresholds** (2000 px, JPEG 80, ~300 KB) and **income-only statement flags**
   (10× median and ≥ ₹10,000) were the agent's calls; change them if they feel wrong in use.

## B. Security and RLS (your review is mandatory)

3. **Account deletion**, in the app and on the web. *A Play Store blocker.* Several foreign keys to
   `profiles(id)` have no ON DELETE action, so it needs an Edge Function with the service role that
   deletes the user's private rows, swaps their profile on shared records for a tombstone, and
   deletes the `auth.users` row; the phone then deletes that account's Room file (and, now, its
   receipts folder). Decide: tombstone wording ("Former member"?), whether shared groups they
   created survive, and a grace period.
4. **Gemini behind an Edge Function with per-user quotas.** Today the key ships in every APK. Also:
   enable billing on the key's project (on the free tier Google may use the data) and set the
   audience to 18+. Decide: when, and the per-user quota.
5. **Invite codes can be brute-forced** (40-bit, no attempt limit on `preview_invite`/`join_group`).
   Options: ~10 failed attempts per user per hour, longer codes, or both.
6. **No length checks** on profile, transaction and settlement text columns. Fix: `char_length`
   checks. Decide the limits (64 for names, 500 for notes?).
7. **Either party can retract a confirmed settlement alone.** Intended today. Fairer: only the
   recorder, or a retraction that has to be confirmed.

## C. Store and legal

8. A **privacy policy**, using #55's facts and Gemini as a processor. Bill photos stay on the phone
   for now, so they don't change it; a cloud phase would.
9. **Data safety form**: personal info, financial info, third-party names, photos and files sent to
   Gemini, no analytics.
10. The **financial features declaration**.
11. A **closed test with 12+ testers for 14 days** (Play's rule for new personal accounts).
12. **Play App Signing**: the Play SHA-1 must be registered for Google sign-in and the Gemini key
    restriction, as the release SHA-1 was for 1.2.0.
13. **Age check** (DPDP: under-18s need parental consent).

## D. Operations and product

14. **Distribution**: Play or GitHub. The repo is private, so GitHub release downloads work only
    for collaborators.
15. **Supabase Pro** at launch ($25/month; breaks even at about 55 subscribers at ₹49).
16. **Crash reporting**: Sentry or Crashlytics. With R8 on (#71), whichever it is needs the mapping
    upload.
17. **Bill photos, phase 2**: Supabase Storage with RLS, groupmates seeing a shared expense's bill,
    and whether it's a Plus feature (the monetisation notes suggest it).

## E. Monetisation (research summary, for the product conversation)

- Recommended in the notes: "Kharcha Paani Plus" at **₹49/month or ₹399/year** plus a tip jar, no
  ads.
- Gate AI volume above a free allowance, statement import, Q&A, receipt cloud storage,
  multi-currency. Never gate entries.
- Fake-door paywall first; build billing if more than ~5% of users who hit the quota tap it.
- Costs: a heavy user is ≈ ₹33/month now, and Gemini 3.6 Flash doubles on 1 Jan 2027.
- What users will expect: app lock, budgets, multi-currency trips, recurring roommate splits,
  reminders to friends, itemised bill splitting, Hindi/Hinglish (needs string extraction first:
  zero `stringResource` calls today), a light theme.
