# Production readiness plan: Kharcha Paani on the Play Store

Written 2026-10-24 against `main` at 7a1342a, plus the open PRs #61–#77. This is the work order for
the agent who implements it. The goal is the owner's own words: if 1,000 strangers install the app
from the Play Store tomorrow, none of them should feel it's "just another vibe-coded app with bugs".
It has to pass Play review the first time, survive a security look, ask for permissions properly, and
tell people what their friends did through notifications.

What fed it:

| Source | File | What it is |
|---|---|---|
| Security audit (code, migrations, git history, keys) | [audits/security.md](audits/security.md) | IDs `H-1…H-3`, `M-1…M-5`, `L-1…L-10` |
| Reliability and UX audit | [audits/reliability-ux.md](audits/reliability-ux.md) | IDs `B1`, `H1…H5`, `M1…M11`, `L1…L14` |
| Notifications and permissions audit + design | [audits/notifications.md](audits/notifications.md) | gaps `G1…G9`, catalogue rows 1–21, phases 0–6 |
| Play Store and DPDP research (official sources, 2026-10-24) | [audits/play-store.md](audits/play-store.md) | blockers `B1…B7` in its §0 |
| The owner's saved reels (Notion "Reel Brain") | [audits/notion-reels.md](audits/notion-reels.md) | 27 reels, master checklist |
| Decisions already waiting on the owner | `docs/handoffs/2026-10-24-owner-decisions.md` (untracked, owner's copy) | items 1–17 |

Every finding in the audits was read in the code and cites `file:line`. Before changing anything, the
implementer checks the finding again on the current `main`: line numbers drift as PRs merge.

In this file the audit IDs carry a prefix so they don't collide: **SEC-** (security), **REL-**
(reliability/UX), **PLAY-** (Play research), **NOTIF-** (notifications).

---

## 0. Start here (next agent)

0. **Ask everything up front, then run on autopilot** (the owner's rule, also in CLAUDE.md). Before
   starting, read through the items you plan to do this session and send the owner one batch of
   every question, decision and permission they need: confirming the tier, merges, migrations,
   approach choices, and the open owner decisions those items depend on. After that, don't stop to
   ask. If a new question comes up, make the safe, reversible choice and note it, or skip that item
   and continue with the next. Put every new question on a list and ask the whole list at the end of
   the session, along with what was done and what's next. Owner-review PRs (RLS/auth/security,
   non-additive migrations) are still opened and left unmerged, never decided by you.
1. Read §10 first: the owner's decisions of 2026-10-24 override anything in the tiers that disagrees.
2. Check which of #61–#77 have merged (`gh pr list`). Build on `main`; rebase anything stacked.
3. With the owner's go-ahead for Tier 1 (part of the up-front batch), take the items in this order:
   T1.4 (amounts), T1.5 (status bar), T1.7 (double saves), T1.8 (safety net), T1.9 (statement
   cancel), N0, N1, T1.10 (AI notice, wording in §10), T1.2 (policy + Help & about), T1.6 (crashes),
   T1.3 (Gemini proxy + caps from §10), T1.1 (account deletion per §10). The last two need owner review.
4. Work in a git worktree per branch, follow §1, and keep this file's tiers up to date: mark an item
   "done in #NN" when its PR merges.

---

## 1. Rules for the implementing agent

These come from `CLAUDE.md`, the memory notes and the owner's instructions, and they are not optional.

1. **One item, one branch, one PR**, each with its own tests and a `CHANGELOG.md` entry under
   *Unreleased* (Keep a Changelog). `PRD.md` changes only when a spec or decision changes.
2. **Confirm with the owner before starting each tier** in §5 (CLAUDE.md: confirm before each priority
   area). Within a tier, items can go in any order that keeps CHANGELOG conflicts one at a time.
3. **Owner review is a hard stop** for anything touching RLS, auth, privileges, SECURITY DEFINER code,
   or a non-additive migration. Those PRs are opened, tested and left unmerged. They're marked
   **[owner review]** below.
4. **The owner tests on a device. Agents don't run the emulator.** Every PR says what to check on the
   phone.
5. **Check free RAM before any Gradle run** (`Get-CimInstance Win32_OperatingSystem`), and stop any
   leftover `java.exe` after a build. With less than about 4 GB free, don't build locally: CI is the
   authority for tests. Room tests and FileProvider tests fail locally on Windows (known), so read the
   exception type before blaming a change.
6. **Room:** no destructive migrations. Every schema change needs a `Migration`, the exported schema
   JSON (`:app:kspDebugKotlin` then `:app:copyRoomSchemas`) and a migration test. #76 takes **v10**,
   so the next ones are v11, v12…
7. **Test the gatherer, not the format.** Start from a real Room database or the real flow and assert
   a balance, a row or a state. A hand-built object only tests its constructor. (#77's bug was hidden
   for months by exactly that.)
8. **Prove the bug first.** Where a fix is for a bug, commit the failing test, let CI fail for the
   predicted reason, then push the fix (as #61, #62 and #77 did). CI's concurrency group cancels an
   in-progress run for the same PR, so wait for the red run before pushing the fix.
9. **Smoke-test the signed release build** before calling a release ready (`scripts/release-smoke.sh`
   from #67). Debug builds hid a total sign-in failure for three releases.
10. **Test background work with the app backgrounded and swiped away.** Never call
    `auth.awaitInitialization()` from a worker, widget or FCM service. Use `BackgroundAuth`.
11. **No Kotlin through Bash heredocs** (backslash escapes corrupt it). Use Edit/Write.
12. **Commits** end with the `Co-Authored-By` line from the session's attribution reminder. **PR bodies** end
    with the Claude Code line.
13. **Copy tone.** Anything one person sends or shows to another (ask-to-pay messages, notifications,
    reminders) must sound casual and friendly, never formal, never like begging, never like a debt
    collector. The owner's words: "the person who sends this through WhatsApp shouldn't feel awkward,
    or hesitate." Never "you owe", "overdue" or "pay now" towards the person who owes.

---

## 2. The owner's questions, answered

### Do we need to change any API keys?

**Yes, one: the Gemini key must be rotated and moved off the phone.** (SEC §0, PLAY §7 and §12.)

- The key is compiled into `BuildConfig.GEMINI_API_KEY` and has shipped in every release (v1.0.0–v1.4.0).
  Anyone with any of those APKs has it. Git history is clean, so that isn't the reason; distribution is.
- The package/SHA-1 headers (`AppIdentity.kt:64-71`) can be copied by anyone, so they don't protect it.
- Google's own terms: "Do not hardcode API keys directly in web or mobile apps."
- **Do:** put Gemini behind a Supabase Edge Function with a per-user daily quota (item **T1.3**), create a
  **new** key that exists only as an Edge Function secret (Generative Language API only, quota caps, a
  budget alert), then delete the old key.
- **Also do:** enable billing on that Google Cloud project. On the free tier Google may use and
  human-review prompts, and its terms say not to send personal information. Receipts, bank statements
  and friends' names are exactly that (PLAY §12, SEC H-1).

**No change needed:** the Supabase publishable key (RLS is the control) and the Google web client ID
(public). Two settings to make, though: register the **Play App Signing SHA-1** on the Android OAuth
client (or every Play-installed copy fails sign-in), and disable the legacy `anon`/`service_role`
JWT keys in the Supabase dashboard if nothing uses them.

### Do we need to change the database?

**Yes, additively. Nothing has to be dropped or rebuilt.** All of these are new migrations:

| Change | Why | Item |
|---|---|---|
| Deferred constraint trigger: an expense's shares must add up to its amount | Today only `save_group_expense` checks it; a payer can PATCH `split_shares.share_amount` directly and corrupt a whole group's balances (SEC H-2) | T2.1 |
| Account deletion Edge Function (service role) + tombstone profile | Play requires in-app **and** web deletion | T1.1 |
| `char_length` checks on free-text columns; attempt limits on `preview_invite`/`join_group`; per-user write rate limits | Abuse and invite brute force (owner decisions 5–6, SEC M-5) | T2.2 |
| Revoke default privileges from `anon` | Every new object is granted to `anon` by default; one forgotten revoke becomes a hole (SEC M-4) | T2.3 |
| Hue-clash trigger fix (null display name skips the check) | SEC L-9 | T2.3 |
| `activity` table + triggers + `push_tokens` + `notification_prefs` | The notifications the owner asked for | N2–N4 |
| Gemini quota table | Per-user AI limits | T1.3 |

On the phone: Room v11+ for the Updates inbox (`inbox_items`) and, later, user nudges. All additive.

Supabase dashboard checks the repo can't show (SEC §0 item 9) **[owner review]**: turn off Email,
Phone and Anonymous sign-in if Google is the only way in; exposed schemas = `public` only; disable
`pg_graphql` if unused; Realtime "Allow public access" off; run the Security Advisor; set a database
size alert; confirm the plan's log retention (DPDP asks for a year of processing logs).

### Do we need to change any features?

**Yes. Add these, and nothing that exists needs removing:**

- **Delete my account**, in the app and on a web page (Play blocker).
- **Privacy policy, terms, "Help & about"** (version, feedback email, licences) in the app and on the
  sign-in screen.
- **A one-time "your photos and PDFs are read by Google Gemini" notice** before the first AI scan, plus
  an "I'm 18 or older" line at sign-in. Gemini's terms are 18+ and DPDP requires parental consent for minors.
- **Notifications that tell you what your friends did**, an in-app Updates inbox, a Notifications
  settings screen and a proper permission ask (§6).
- **Crash reporting** (opt-in disclosed in the policy).
- **App lock (fingerprint/screen lock)**, opt-in, and hiding the Recents thumbnail. Indian finance
  apps are expected to have it.
- **In-app updates**, so 1,000 users actually get the fixes.

The existing features stay: the ledger/balance pattern, debt simplification, the AI pipeline and the
groups model are explicitly out of scope for re-architecture (CLAUDE.md, PRD "Out of Scope").

### "When there's no internet, the app doesn't open at all."

**Fixed in [#77](https://github.com/vaibhavcodesx/kharcha-paani/pull/77)** (open, needs owner review
because it touches auth state). The cause was found in supabase-kt 3.6's own source. Offline, an
expired session reports `Initializing` and then `RefreshFailure`, neither of which carries the user.
The app read the user from supabase-kt's current session, which is empty in both states, so it never
opened the ledger and sat on a blank screen. CI proved the bug first (4 of 7 new tests failed with
`userId=null`).

---

## 3. What has to land first: the open PRs

**Status 2026-10-25: all merged except #71.** The owner approved merging them, #74, #75 and #77
included, before the device tests. They were tested together on CI first, then shipped in 1.5.0;
the device checklist is [docs/testing/1.5.0-device-checks.md](../testing/1.5.0-device-checks.md).
**#71 (R8) is still open:** it needs its own signed-release smoke test before merging.

The order they were merged in (keeps the CHANGELOG conflicts one at a time):

#61 → #62 → #63 → #66 → #72 → #73 → #68 → #64 → #65 → #71 (only after a signed-release smoke test) →
#69 → #70 → #67 → #74 → #75 (both **[owner review]**, security) → #76 (Room v10) → #77 (**[owner review]**, auth).

What they already cover, so it isn't repeated below: backup restore safety (#61, #73), sync
correctness (#62), month rollover (#63), subscription time zones (#66), statement import flags (#72),
Android 7 crash (#68), RECORD_AUDIO + dead Firebase/Retrofit deps (#64; Play blocker PLAY-B5), CI lint
+ release bundle + pinned actions + Dependabot (#65), R8 shrinking (#71), launch test (#69), Back
goes Home (#70; Play's back-navigation guideline), release checklist (#67), internal intents only
(#74), account-switch cleanup (#75), bill photos (#76), offline launch (#77).

After #65 and #68 merge, regenerate `app/lint-baseline.xml` from CI.

---

## 4. Things only the owner can do (consoles, money, legal)

These block the launch but aren't code. The implementing agent prepares the material (the drafts, the
answers, the screenshots); the owner does the clicking.

| # | Action | Blocks | Notes |
|---|---|---|---|
| O1 | Enable **billing** on the Gemini key's Google Cloud project (paid tier, prepay ≥ $5) | closed test | PLAY §12 |
| O2 | After T1.3 deploys: create the new Gemini key as an Edge Function secret, quota + budget alert, then **delete the old key** | closed test | SEC §0.1 |
| O3 | Play Console: enroll in **Play App Signing**, upload an AAB, register the **Play signing SHA-1** on the Android OAuth client | closed test | PLAY B4; sign-in fails otherwise |
| O4 | OAuth consent screen "In production", with branding | closed test | SEC §0.4 |
| O5 | Supabase dashboard checks (§2 list) | closed test | SEC §0.9 |
| O6 | Host the **privacy policy**, **terms** and **account deletion** pages at a public URL (GitHub Pages or a small site; the repo is private, so not a repo link) | closed test | Drafts in T1.2 |
| O7 | Play Console App content: Data safety (answers drafted in T1.2), Financial features ("doesn't provide financial features", or Support services → Other with the note in PLAY §4), content rating, target audience **18+ with Restrict Minor Access**, ads = none, category Finance | closed test | PLAY §3–§4, §9 |
| O8 | A **reviewer Google account** with sample data (a group, a split, a settlement) under App access → Sign-in details | closed test | PLAY B6; a common rejection reason |
| O9 | Device verification in the Play Console mobile app | publishing | PLAY §2 |
| O10 | **12+ testers opted in for 14 continuous days** (recruit 15–20), then Apply for production | production | PLAY B7 |
| O11 | Firebase project + service account for FCM, secrets set by the owner (an agent must not handle the private key) | N3/N4 | NOTIF phase 4 |
| O12 | 2FA on Google, Play Console, Supabase and GitHub accounts | before launch | Notion checklist |
| O13 | Decide Supabase Pro at launch ($25/month) and whether to keep GitHub-release APKs (register the package for Android developer verification if so) | launch | owner decisions 14–15, PLAY §2 |

---

## 5. The work, in tiers

Effort is a rough guide: S ≈ under a day, M ≈ 1–2 days, L ≈ 3+ days of agent work including tests.

### Tier 1: before the closed test starts (blockers)

Nothing in this tier is optional: each one is a Play rejection, a data-loss bug, or something every
user sees in the first minute.

**T1.1 Delete my account** · L · **[owner review]** (auth, RLS, service role)
- Edge Function `delete-account` (service role): delete the caller's private rows (`local_splits`,
  `local_settlements`, personal `transactions`), replace their profile on shared records with a
  tombstone ("Former member"), remove their group memberships, delete the `auth.users` row. Several
  FKs to `profiles(id)` have no `ON DELETE` action, so the order matters; write it as one transaction
  and test it in `supabase/tests/` (a groupmate's balances are unchanged after deletion; nothing of
  the deleted user is readable afterwards).
- App: Account sheet → "Delete my account" with a two-step confirm that says what goes and what
  stays ("Shared expenses stay in your groups, under 'Former member'"). It's refused while changes are
  unsynced, or the user is offered to discard them. On success: delete `ledger-<uid>.db*`, the user's
  DataStore keys, `cache/exports`, `cache/asks`, `filesDir/receipts/<uid>`, cancel notifications and
  work (reuse #75's `AccountLeftovers`), sign out.
- Web page for people who already uninstalled (a form or mailto plus what gets deleted and within
  how long). Its URL goes in the Data safety form.
- Owner decisions first: tombstone wording, whether groups they created survive, grace period
  (owner decisions item 3).
- Sources: PLAY §3a, SEC L-2, owner decisions 3.

**T1.2 Privacy policy, terms, Help & about, and the store answers** · M
- Draft `docs/legal/privacy-policy.md` and `docs/legal/terms.md` from the real data flows: Google
  sign-in; Supabase as processor (state the region); Gemini as processor on the paid tier (receipts,
  statements, ledger text); groupmates see each other's names and UPI IDs; FCM once N3 lands; crash
  reporting once T1.6 lands; local backups; retention ("deleted within 30 days of account deletion");
  18+ only; grievance contact and a 90-day response promise (DPDP Rules 9 and 14); how to withdraw
  consent. The owner hosts them (O6). Not legal advice: say so in the PR.
- In the app: links on the sign-in screen ("By continuing you agree to the Terms and Privacy policy"
  and "I'm 18 or older"), and a **Help & about** section at the bottom of the Account sheet:
  Send feedback (`ACTION_SENDTO mailto:` with version, device and Android version pre-filled, guarded
  for `ActivityNotFoundException`), Privacy policy, Terms, Open-source licences, "Kharcha Paani
  1.x.y (code)".
- Write the Data safety answers (PLAY §3b table) and the Financial features answer into
  `docs/legal/play-console-answers.md` for O7.
- Sources: PLAY B2, §3, §11; REL H5; Notion legal checklist.

**T1.3 Gemini behind an Edge Function, with per-user quotas** · L · **[owner review]**
- `supabase/functions/gemini-proxy`: verifies the caller's JWT, checks and increments a per-user daily
  quota (a table keyed by user and day; the number is the owner's call, owner decisions item 4),
  forwards the request to Gemini with the server-side key, validates the response shape, **never logs
  request bodies**.
- App: `GeminiRepository` calls the function instead of Google; the key leaves `BuildConfig` entirely;
  `AppIdentity.restrictionInterceptor` goes. Keep the existing failure classifier (offline, timeout,
  rate-limited…) and add "You've used today's AI reads; they come back tomorrow."
- Also fold in SEC M-2 here (the function is the right place): cap statement rows at 1,000, drop
  amounts above the server's `numeric(12,2)` ceiling, truncate merchant to 80 and note to 200
  characters, and pre-tick only rows inside the parsed statement period.
- Then O2 (rotate). Until T1.3 ships, at least send the key as the `x-goog-api-key` header instead of
  the URL and drop the response-body excerpts from release logs (SEC L-4).
- Sources: SEC §0.1, H-1, M-2, L-4; PLAY §7, §12; owner decisions 4.

**T1.4 Amounts that can't break the app or the sync** · M
- One `Money.parseInput(text): Double?`: strip grouping commas and spaces; digits with at most one
  "." and 2 decimals; finite; at most ₹9,99,99,99,999.99 (the server's `numeric(12,2)`). Use it
  everywhere an amount is read: `MainViewModel` (the three `toDoubleOrNull` sites), `ConfirmBottomSheet`,
  `SettleUpSheet`, `IouSheet`, `SubscriptionsSheet`, `SplitEditor`.
- Filter keystrokes in `BigAmountField` (`Sheets.kt`) so "NaN", "1e999" or a pasted 19-digit number
  can't be entered; make `Money.format`/`roundPaisa` return "₹0" for a non-finite value instead of
  throwing inside composition.
- Repository guard: `require(amount.isFinite() && amount > 0)` on inserts.
- Sync guard: a row whose upload conversion throws is marked FAILED and the queue moves on, so one bad
  row can never stop all sync again.
- Confirm above ₹10 lakh ("That's ₹10,00,000. Right?"), and no future dates in the date picker.
- Tests: NaN, Infinity, "1,200", 1e999 and a 19-digit paste at save; a seeded NaN row in a real Room DB
  is marked FAILED while the others sync.
- Why it's Tier 1: today a pasted "Infinity" **silently stops all cloud sync for that account
  forever**, and a pasted "1e999" crashes the app (SEC H-3, REL H3, both verified).

**T1.5 Status bar and launch screen** · S
- `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))`; explicit
  dark `windowBackground` in `themes.xml`; `core-splashscreen` with the brand mark, kept on screen
  while the auth gate is loading (at most 1.5 s, which matches #77's grace period); the loading gate
  draws the brand mark instead of a blank box; a real monochrome launcher icon (only the ₹ strokes).
- Why: on a phone in light mode (most phones), the clock and battery icons are dark on the app's
  near-black background, which means invisible on every screen, and 3-button navigation gets a white
  strip (REL B1). **Device check in light mode, gesture and 3-button navigation.**
- Sources: REL B1, M10; PLAY §10.

**T1.6 Crash reporting** · M
- Recommended: Firebase Crashlytics (FCM in N3 needs Firebase anyway). Initialise without the
  google-services plugin if the owner prefers (options from `.env`, NOTIF open question 4). Upload the
  R8 mapping in `release.yml` (after #71).
- Non-fatals (reason only, never amounts, names or document content) at: `SyncWorker` catches (with
  status code), `AccountSessionHolder` Failed, `GeminiRepository.failedCall`, and T1.8's safety net.
- Declare "Crash logs, Diagnostics" in Data safety and the privacy policy (T1.2).
- Sources: REL H4; PLAY §8; owner decisions 16.

**T1.7 No double saves** · S · **done in #88**
- An `isSubmitting` flag per action (save expense, import statement, record settlement, IOU, create
  group, add friend): return early if set, cleared in `finally`, passed to `GlassActionBar` as
  disabled-with-spinner.
- Test on a real Room DB: `saveCurrentTransaction()` twice back to back → one row; create shared group
  twice → one PENDING group.
- Why: a double tap logs the dinner twice, and on Create group makes two shared groups on the server
  (REL H1).

**T1.8 Nothing crashes on a full disk or a closed database** · M
- A `launchAction(failMessage) { … }` helper in `MainViewModel` (and `SharedGroupsController`) that
  rethrows `CancellationException`, maps known domain exceptions, and otherwise logs, records a
  non-fatal and shows a human message ("Couldn't save. Your phone may be out of space."). Use it for
  every user action. `try/finally` on every `_isParsing…` flag. Guard every `startActivity` for
  `ActivityNotFoundException`.
- Sources: REL M1, L3.

**T1.9 The statement dialog can be cancelled** · S
- Keep `statementJob`; a Cancel button; Back/dismiss cancels it and clears `_isParsingStatement`; after
  20 s, the line "Long statements take up to 3 minutes. You can cancel and try a shorter date range."
- Why: Back does nothing for up to 3–6 minutes today (REL H2).

**T1.10 AI disclosure and the 18+ line** · S
- A one-time sheet before the first scan, photo, statement or Insights question: "Photos, PDFs and
  text you send are read by Google Gemini to fill in your entry. They aren't used to train Google's
  models. [Continue] [Not now]". Store consent in `AppSettings`; "Not now" keeps manual entry working.
- A "Report this answer" item on Insights answers (Play's AI-generated content policy).
- Sources: PLAY §3d, §6, §11, §12.

**T1.11 Notifications, phases 0 and 1** (client only): see §6. Without them, people who only use groups
are never asked for permission and never see "payment to confirm" (NOTIF G1).

### Tier 2: before applying for production (during the 14-day closed test)

**T2.1 Shares must add up, enforced by the database** · M · **[owner review]** (trigger in the RLS area)
- `private.check_group_expense_sums(transaction_id)` called from deferred constraint triggers on
  `transactions`, `splits` and `split_shares`; raises 23514 so the app marks the row FAILED. SQL tests:
  a direct PATCH of `share_amount` or `amount` is refused; `save_group_expense` still passes; a
  soft-delete still passes. (SEC H-2, verified: no such trigger exists.)

**T2.2 Abuse limits** · M · **[owner review]**
- `char_length` checks on profile, transaction, settlement and group text (names 1–60/64, notes 500?
  owner decides); matching `maxLength` on the app's fields (REL L9) so the client never sends what the
  server refuses.
- Invite brute force: about 10 failed `preview_invite`/`join_group` attempts per user per hour, and/or
  longer codes (owner decisions 5).
- Per-user write rate limits on `transactions`/`settlements`/`local_settlements` inserts; a member cap
  per group (SEC M-5).

**T2.3 Database hygiene** · S · **[owner review]**
- Revoke default privileges from `anon` + a test that `anon` has none (SEC M-4). Fix the hue trigger's
  null-name hole (SEC L-9). Optional `avatar_url` check (SEC L-8).

**T2.4 Sync problems are visible** · M (Room migration for the error column)
- A "Not synced" chip with a cloud-off icon on FAILED rows in the feed, with a TalkBack label; store the
  server's refusal reason and show it in the edit sheet; "N couldn't sync" opens the list (REL M3).
- `SyncWorker`: catch `Exception` last; record `lastSyncError` in `sync_meta`; on 401/403 stop retrying
  and show "Sign in again"; the Account sheet shows "Couldn't sync since 3 PM: <reason> · Try again"
  (REL M9).
- A "Sync now" pull-to-refresh on Home and Splits (REL M11).

**T2.5 Confirm or undo before anything that changes both phones** · S
- Retract settlement, delete IOU, delete group: the existing undo-snackbar pattern (`PendingDeletes`)
  or a dialog that states the consequence: "Rohan will owe you ₹5,000 again, on both phones." (REL M4)

**T2.6 Privacy on the device** · S
- `setRecentsScreenshotEnabled(false)` on API 33+ (don't force `FLAG_SECURE`; people screenshot splits
  on purpose). Notifications with amounts get `VISIBILITY_PRIVATE` + a public version (SEC M-3, L-5).
- Shared files: accept only `content://` URIs that aren't the app's own FileProvider (SEC L-3, the gap
  #74 leaves).
- Sign-out offers "Also remove this account's data from this phone" when nothing is unsynced (SEC L-2).

**T2.7 Notifications phase 2**: the server `activity` table and the in-app Updates inbox. This is the
phase where "everyone in the split gets told Priya added an expense" first works (§6).

**T2.8 In-app updates** · S
- Play Core `AppUpdateManager`: flexible updates normally, immediate when a server-side minimum version
  says so (REL M11, PLAY §10).

**T2.9 Store listing assets** · M (agent prepares, owner uploads)
- At least 4 portrait screenshots ≥ 1080×1920 from real UI (the `ScreenRenderCapture` setup can render
  them), a 1024×500 feature graphic, an 80-character short description, a full description. No
  "best/#1/free", no GPay/PhonePe/Paytm logos, no "loan/lend/borrow/credit" wording (PLAY §4, §9).

**T2.10 Accessibility pass** · M
- Fixed heights → `heightIn(min=…)`; money text auto-sizes instead of ellipsizing ("₹1,23,4…" is wrong
  information); the dock grows or drops labels above 1.5× font; Roborazzi captures at `fontScale = 2f`
  (REL M7). Label every interactive icon (39 `contentDescription = null` to review, PLAY §10). Run
  Accessibility Scanner and fix every red item in the pre-launch report.

### Tier 3: soon after launch (first month)

| Item | What | Size | Source |
|---|---|---|---|
| T3.1 | Notifications phases 3–5: FCM push, Edge Function dispatch, subscriptions/digests/nudges (§6) | L | NOTIF |
| T3.2 | App lock (biometric or screen lock, opt-in, after 60 s in background) | M | SEC M-3, REL M11 |
| T3.3 | Verified App Links for invites (`https://<domain>/join/CODE`) instead of the hijackable `kharchapaani://` scheme **[owner review]** | M | SEC M-1 |
| T3.4 | The half-filled entry, statement result and pending undo-deletes survive process death | M | REL M2, L11 |
| T3.5 | Quick-add: open the sheet immediately with the on-phone guess, cancel the AI job when the user moves on, never overwrite what they typed | M | REL M5 |
| T3.6 | Statement re-import marks "Already in your ledger" rows unticked | S | REL M6 |
| T3.7 | Performance: share the base Room flows, move the feed build off Main, stop per-friend ledger queries, lazy lists in sheets, a Baseline Profile | L | REL M8, L12 |
| T3.8 | Search by friend name and amount; "Edited by X · time" on shared expenses | M | REL M11 |
| T3.9 | In-app review prompt after a happy moment (a confirmed settlement), never after an error | S | REL L13, PLAY §10 |
| T3.10 | Small things: empty-state flash on cold start (L1), `collectAsStateWithLifecycle` everywhere (L2), offline sign-in error wording (L5), haptics on money confirmations (L6), "Scan bill" naming (L7), thread-safe date parsing (L10), DataStore corruption handler (L14), export warning line (SEC L-10) | S each | REL L*, SEC L-10 |
| T3.11 | Strings into `strings.xml` (0 `stringResource` today, about 640 literals). Needed before any Hindi/Hinglish build | L | REL i18n |
| T3.12 | Light theme (Google's quality guideline; the app is dark only) | L | PLAY §10 |
| T3.13 | Incident plan (one page: who to tell, within 72 hours for the DPDP Board) | S | PLAY §11 |

---

## 6. Notifications: what the owner asked for

> "If a person in a group added a split expense, every person in that split should get a notification
> that this person added a split. More such useful notifications."

**Today there is no push at all**: no FCM, no token table, no Edge Functions. Other people's changes reach
a phone only by pulling (on every open, a Realtime "doorbell" while the app is open, and a 15-minute
background sync that aggressive Indian OEM ROMs often stop entirely). A groupmate's new expense
produces no notification. Only two local notifications exist (the 9 PM reminder and "payment to
confirm"). Full audit: [audits/notifications.md](audits/notifications.md) Part 1.

**The design (recommended: hybrid).** Database triggers write one `activity` row per event per
recipient, never to the person who made the change. An Edge Function sends it to the phone as an FCM
data message. The regular sync also pulls `activity`, so anything push missed (no Play services, a
battery-killed app) still arrives, and it fills an in-app **Updates** inbox with a bell and an
unread badge.

**The catalogue** (21 kinds, full table with copy, channels, collapse rules and tap targets in
[audits/notifications.md](audits/notifications.md) Part 2a). The main ones:

- **Priya added Dinner at Toit**: "Goa trip · your share ₹450 of ₹1,800." Bursts collapse: "Priya added
  4 expenses in Goa trip".
- **Priya changed / removed Dinner at Toit**, **Priya took you off Cab to airport**.
- Payments: **Priya says she paid you ₹500** (confirm), **Rohan confirmed your ₹500**, **Rohan says the
  ₹500 didn't happen**, withdrawn and undone variants.
- **Ankit joined / left Goa trip**, **Goa trip was archived**.
- Local: weekly "₹2,350 to come back to you" digest, monthly summary, "Netflix renews tomorrow",
  "Time to ask Rohan" nudges the user sets themselves. **Nothing is ever sent to a friend automatically.**

Tone: casual, warm, a little Hinglish where it fits ("hisaab", "all square"); never guilt, never "you
owe" to the person who owes (rule 13 in §1).

**Permissions:** ask in context (after joining or creating a group, after recording a settlement that
waits for the other person, after the first own entry with the reminder on), always after an in-app
explanation sheet, at most twice; once denied for good, a "Turn on" button opens system settings. A
Notifications screen in the Account sheet with a switch per category, quiet hours (22:30–08:00
default) and per-group mute. Nothing breaks when notifications are denied: everything still shows
in the Updates inbox.

**Phases (one PR each):**

| Phase | What | Server? | Tier |
|---|---|---|---|
| N0 | Fix what exists: create all channels at start; cancel stale "payment to confirm" notifications (G6); a blocked channel doesn't use up a reminder stage (G3); reminders don't depend on the whole sync passing (G8); tags instead of computed ids (G9) | no | T1 |
| N1 | Permission state machine + pre-prompt + the four ask moments + Notifications screen | no | T1 |
| N2 | `activity` table + triggers + RLS **[owner review]**; Room v11 `inbox_items`; Updates screen; notifications posted from the pull (48 h window, no flood on first sync) | yes | T2 |
| N3 | FCM client, `push_tokens` / `notification_prefs` / group mutes **[owner review]**, unregister on sign-out | yes | T3 |
| N4 | `push-dispatch` Edge Function, pg_net trigger, retry sweep **[owner review]**; owner sets Firebase secrets (O11) | yes | T3 |
| N5 | Local scheduled ones: subscriptions moved to a morning worker (G7), weekly/monthly summaries, user nudges | no | T3 |
| N6 | Polish: cross-device read clearing, lock-screen public versions, "sync stuck" | mixed | T3 |

**Owner questions for this section** (from NOTIF "Open questions"): hybrid now or pull-only first;
FCM payload with names and amounts (works instantly; passes through Google) or ids only (more private,
generic first line); the defaults (weekly on, monthly off, quiet hours 22:30–08:00); Firebase init
without the plugin; whether joins and leaves go to everyone; whether "Payments to confirm" can be
turned off (recommended: yes, with a warning).

---

## 7. From the owner's saved reels (Notion "Reel Brain")

27 reels were relevant; their de-duplicated checklist is in [audits/notion-reels.md](audits/notion-reels.md)
Part 2. Everything in it that applies to a native Android app is covered above. The mapping:

- No secrets in the APK, server-side money checks, rate limits, upload checks → T1.3, T1.4, T2.1, T2.2
- RLS on every table, tested against ID swapping → already true (SEC §3); T2.1/T2.3 close the gaps
- Unguessable, expiring, revocable share links → invite codes: T2.2 (brute force), T3.3 (App Links)
- No personal data in logs, generic user-facing errors, a clean release build → SEC L-4 (in T1.3),
  T1.8, #71 R8
- Privacy policy, terms, deletion, data minimisation, SDK audit for Data safety → T1.1, T1.2, T1.10
- Store listing shows only the real UI, no first-run crash, a reason for every permission → T2.9,
  T1.5, N1
- Cold start on a bad network, first screen from Room, optimistic session → #77 (exactly this)
- Release-signed smoke test on a real phone, every link and UPI intent checked, backups tested → #67,
  rule 9 in §1
- Not covered by any reel (the owner's reels had nothing on these, so they came from the Play research
  instead): notifications, retention, crash reporting, Play-specific policy.

---

## 8. Already solid (don't touch)

From the audits, so nobody "fixes" these: RLS is enabled on all 10 tables and every function sets
`search_path`; no storage buckets; cleartext traffic off; the Google sign-in nonce is correct;
supabase-kt masks tokens in its logs; no known CVEs in direct dependencies (OSV, 2026-10-24); targetSdk
36 and 16 KB page alignment are compliant; no media permissions (system pickers); no exact alarms or
foreground services; offline-first writes with idempotent pushes; paisa-exact split rounding; the AI
failure classifier; TalkBack labels on amounts and custom actions on swipe rows; the Realtime doorbell
backs off and retries.

---

## 9. After Tier 1 and 2: the product conversation (office hours)

**No pricing, paid plans, paywalls or billing work for now** (decision D5). Office hours focus on one
question: what makes Kharcha Paani a daily habit and a necessity, something people would miss if it
were gone? Topics, in the owner's priority:

1. **Seeing where the money went:** clear monthly picture, categories, trends.
2. **Budget limits (new feature):** per-month and per-category budgets, with friendly, never-preachy
   nudges as a budget is approached and when it's crossed ("Food's at 80% for October, ₹1,200 left").
3. **Easy splits and getting money back:** settle-up, ask-to-pay messages that feel casual and never
   like begging (the owner's tone rule), reminders the user controls.
4. **AI quick add:** fast, forgiving, works for Hinglish.
5. Then: retention loops (the weekly digest, streaks only if they don't nag), recurring roommate
   splits, itemised bill splitting, Hindi/Hinglish, multi-currency trips.

Use the office-hours skill in builder/startup mode on this framing. The earlier monetisation notes
(₹49/month Plus, feature gating, fake-door test) stay parked until after the free period.

---

## 10. Owner decisions, 2026-10-24 (these override the tiers above)

### D1. Account deletion: the leaving member chooses how they're remembered (T1.1)

The owner's idea: at deletion time, ask whether their name may stay on past shared expenses.

- The confirm sheet asks one question with two choices:
  **"Keep my name on past group expenses"** (groupmates keep seeing "Priya" on old splits) or
  **"Show me as Former member"**. Default: Former member, the privacy-safe choice if they skip.
- Implementation: the profile row isn't deleted, it's **tombstoned**. `display_name` becomes either
  their current name (frozen) or "Former member". Email, UPI ID, avatar, hue and every other personal
  field are cleared, and the `auth.users` row is deleted, so nobody can sign in as them and nothing
  else about them remains. Shared history and everyone else's balances stay exactly as they were.
  Their private rows (personal expenses, private splits, local friends) are deleted outright.
- Groups they created: ownership passes to the longest-standing remaining member; a group with no
  one left is deleted. (Agent's call, as the owner asked.)
- No grace period: deletion is immediate after the two-step confirm. Unsynced changes block it, with
  "Sync first, or discard N unsynced changes".
- The privacy policy states this choice and that a kept name is only the name, nothing else.

### D2. Gemini: free tier for the first months, lenient caps (T1.3, T1.10)

**Owner's choice:** the app is free at first and uses the **free (unpaid) Gemini tier**.

**What the agent must not do:** write a notice that says or implies the data isn't used anywhere
else. On the unpaid tier, Google's terms (PLAY §12, read 2026-10-24) say it **uses prompts and
responses to improve its products and human reviewers may read them**, and they ask apps **not to send
personal or sensitive information**. The owner is right that this isn't a "leak". But it is a use
beyond reading the bill, so Play's User Data policy and DPDP require saying it plainly. A notice that
hides it is the thing most likely to get the app pulled. The notice can be calm and short:

> **Bills, statements and quick-add text are read by Google Gemini**
> That's how the app fills in amounts for you. While Kharcha Paani is free, Google may use what's
> sent to improve Gemini, and its reviewers may see it. It's never shown to other users, and we
> never sell your data. Prefer not to? Type entries yourself: everything else works the same.
> [Got it] [Type it myself]

The same facts go in the privacy policy and Data safety form ("shared with Google for AI
processing and product improvement").

**Recommendation to the owner (not a decision):** move to the paid tier before the public production
launch. Prepay is $5; with the caps below the cost is small, and the notice can then drop the
"improve Gemini / reviewers" sentence. It also ends the conflict with Google's "no personal info on
the unpaid tier" term, which is a risk to the Gemini account itself.

**Daily caps per user (lenient, so normal people never hit them):**

| Feature | Per day |
|---|---|
| Quick add (typed or spoken text) | 50 |
| Bill / receipt photos | 10 |
| Bank or UPI statements | 3 |
| Insights questions | 20 |

Hitting a cap shows, once: "That's today's AI reads used up. They're back tomorrow; you can still
add entries by hand." Counts reset at midnight IST. Keep the caps server-side so they can change
without an app update.

### D3. Notifications rollout (§6): agent's call

Build it in the order of §6. First N0 and N1. Then N2: the server `activity` table, the Updates
inbox and notifications from the pull, so everyone in a split already hears about an expense at sync
speed. Then FCM push (N3–N4). Defaults: weekly summary on, monthly off, quiet hours 22:30–08:00;
joins and leaves go to all members (LOW channel); "Payments to confirm" can be turned off, with a
warning.

### D4. Push payload: ids only

The FCM data message carries only `activity_id`, `kind`, `group_id` and the recipient's user id. No
names, amounts or merchants pass through Google. On receipt the phone:

1. Drops it if the user id isn't the signed-in account (#75).
2. Enqueues an expedited sync (through `BackgroundAuth.sessionForNetwork`, never `awaitInitialization`).
3. When the pull brings the `activity` row (usually 1–3 s), posts the full notification
   ("Priya added Dinner at Toit · your share ₹450").
4. If the pull hasn't finished in about 8 s, posts a placeholder from what's already on the phone,
   "New expense in Goa trip" (the group name comes from the local Room copy, not the push), and
   replaces it with the full text when the row arrives. Every high-priority push must end in a
   visible notification, or FCM deprioritises the app.

The privacy policy can then say notification content never passes through Google.

### D5. Free first, paid later (product strategy)

- The app is **free for at least the first 3 months**. During that time the goal is habit and
  necessity: people should rely on it daily for seeing what they spent, budgets, easy splits and
  settle-ups, and AI quick add.
- After that, the owner reviews user response and feedback, then adds paid plans that feel worth
  paying for. AI caps (D2) will then vary by plan.
- **Until then: no paid-plan, paywall, billing or pricing work or discussion.** Build features and
  production readiness only. Nothing in the app should hint at a future paywall or feel limited.
