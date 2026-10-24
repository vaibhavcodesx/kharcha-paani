# Notifications and permissions: audit and production design

Repo: `Z:\V\.The_Future\Vibeeee\Kharcha Paani`, branch `main` at `7a1342a` (v1.4.0, minSdk 24, targetSdk 36,
`app/build.gradle.kts:79-80`). Read-only audit, 2026-10-24. Nothing was modified, built or run.

Open PRs that touch this area and must land first, or be rebased onto: **#74** (internal intents
through a non-exported `.InternalEntry` alias, `IncomingIntents.route`), **#75** (account change
cancels all notifications; settlement intents carry `EXTRA_USER_ID`; stacked on #74), **#64** (removes
`RECORD_AUDIO`, firebase-bom/ai/appcheck and the google-services plugin), **#76** (bill photos, takes
Room **v10**), **#71** (R8).

---

## TL;DR

- **There is no push at all.** No `firebase-messaging`, no `FirebaseMessagingService`, no token
  table, no `supabase/functions/`. Firebase in the build is only `firebase-ai` + App Check
  (`app/build.gradle.kts:165,190,206`; `gradle/libs.versions.toml:44,107-111`), all unused and removed
  by #64. Confirmed.
- **Two notifications exist**: the 9 PM "nothing logged today" reminder (local, WorkManager) and
  "payment to confirm" settlement reminders (local, run after every sync). Two channels, both
  `IMPORTANCE_DEFAULT`.
- Other people's changes reach a phone only by **pull**: on every foreground, a Realtime "doorbell"
  while the app is in the foreground, and a **15-minute periodic WorkManager sync** in the background.
  A groupmate's new expense produces **no notification at all** today.
- POST_NOTIFICATIONS is requested **once**, **only if the daily reminder is on and the person has
  logged an entry of their own**, with **no pre-prompt**. Someone who only uses groups is never asked,
  so their settlement reminders can never appear.
- Recommendation: **hybrid**. The server writes an `activity` row per (event, recipient) from
  database triggers (one source of truth for both the push and an in-app Updates inbox). An Edge
  Function sends an **FCM data message** for each row. The phone renders the copy, collapses bursts,
  and starts a sync. The existing periodic sync also pulls `activity` and posts anything the push
  didn't deliver (no Google Play services, OEM battery killers, a dropped message). Seven phases,
  one PR each; phases 0-1 need no server work at all.

---

## Part 1: what exists (with file:line)

### 1.1 Notification channels

| Channel id | Name | Importance | Created at |
|---|---|---|---|
| `daily_reminder` | "Daily reminder" | DEFAULT | `util/NotificationHelper.kt:33-40` |
| `settlements_to_confirm` | "Payments to confirm" | DEFAULT | `util/NotificationHelper.kt:41-47` |

- `createChannels()` (`NotificationHelper.kt:31-52`) is called **only** from inside the two `show*`
  functions (`:57`, `:94`), never at startup. So until the first notification is posted, system
  Settings shows no categories, and a person can't tune them in advance.
- The file's KDoc still says "There is one: the daily reminder" (`:15-20`). That's stale: there are
  two.
- No channel groups, no `setGroup`, no summary notifications.

### 1.2 Notifications posted

1. **Daily reminder** ("Nothing logged today" / "Add today's spending before it slips your mind.")
   - Posted by `DailyReminderWorker` (`reminder/DailyReminderWorker.kt:29-59`). It checks `canNotify`
     (`:48`), finds the signed-in account through `BackgroundAuth.signedInUserId` (`:51`; not
     `awaitInitialization`, see `data/remote/BackgroundAuth.kt:8-18`), opens that account's Room
     database (`:53`) and calls `ReminderRule.nothingLoggedToday`.
   - Scheduled as a self-re-enqueuing `OneTimeWorkRequest` with an initial delay
     (`ReminderScheduler`, `:66-100`; `APPEND_OR_REPLACE` from inside the worker, `:88-90`). It is
     inexact by design, with no exact alarm (`:25-27`). Default 9 PM (`data/settings/AppSettings.kt:34-38`).
   - Fixed id `2001`. Tapping it sends `ACTION_QUICK_ADD` to `MainActivity` (`NotificationHelper.kt:97-106`).
     Cleared once something is logged (`ui/viewmodel/MainViewModel.kt:217-222`).
2. **Settlement reminders** ("Priya says they paid you ₹500" → "Still waiting on you: ₹500 with Priya")
   - `reminder/SettlementReminders.kt:18-57`. Stage 0 when it arrives, then every 3 days (`:24`).
     Each stage is shown once per phone, through `SettlementEntity.remindedStage` (`Entities.kt:254-255`;
     migration in `Migrations.kt:125`). Copy is in `:26-38`, tested in
     `app/src/test/.../reminder/SettlementRemindersTest.kt`.
   - Runs **only** at the end of a successful `SyncWorker` pass (`data/sync/SyncWorker.kt:49-50`). A pass
     that throws (offline, a 5xx) shows nothing until the next good one.
   - id `30_000 + settlementId % 100_000` (`NotificationHelper.kt:63`). Tapping it sends
     `ACTION_ANSWER_SETTLEMENT` + `settlement_id` (`:58-62`), which opens the answer sheet
     (`MainActivity.kt:288-290`).
   - The design doc said the first nudge comes on day 3 (`docs/design-v1.4-groups-settlements-colours.md:92`).
     The code nudges on arrival, per the owner decision of 2026-09-27 (`SettlementReminders.kt:10-12`).
     The doc also says "FCM can come later without a data change" (`:98`).
3. **Nothing else.** No sync-failure, subscription, digest, group or expense notifications. The
   AI-Studio "monthly digest" was removed (`NotificationHelper.kt:18-19`). The PRD still claims
   "notification channels for spending milestones" (`PRD.md:39-41`) and the dossier describes
   high-priority BigText digests (`docs/original-architecture-dossier.md:190-192,252-253`). Both are
   out of date.

### 1.3 POST_NOTIFICATIONS (Android 13+)

- Declared: `AndroidManifest.xml:7`.
- **Auto-ask** (`MainActivity.kt:403-425`): fires once, when `SDK >= 33 && hasOwnEntry && reminder.enabled
  && !notificationPermissionAsked && !canNotify`. It launches the **system dialog directly**, with no
  rationale or pre-prompt. The "asked" flag is persisted (`AppSettings.kt:50-60`).
- **Manual path** (`MainActivity.kt:426-445`): asks again while `!asked ||
  shouldShowRequestPermissionRationale`. Otherwise it opens `Settings.ACTION_APP_NOTIFICATION_SETTINGS`
  (pre-O: app details). That's correct: the persisted flag resolves the "rationale=false before the
  first ask and after a permanent deny" ambiguity.
- **Where the manual path is reachable:** only the Daily reminder row in the Account sheet
  (`ui/auth/AccountSheet.kt:366-376`). The "Allow" button appears only when the reminder is **on** and
  notifications are off (`:587-594`). Turning the reminder on triggers the ask (`:369-373`).
- **What happens when it's denied:** the reminder row says "Notifications are off for Kharcha Paani,
  so it can't appear" (`AccountSheet.kt:579`). Nothing else does.
- **Gaps**
  - **G1.** Someone who only joins groups and never logs a personal entry, or who turned the daily
    reminder off, is never asked and has **no UI to allow** notifications. Their "payment to confirm"
    reminders are silently dropped (`SettlementReminders.kt:42`).
  - **G2.** No pre-prompt explains why. The OS dialog appears cold, right after the first save.
  - **G3.** `canNotify` is `areNotificationsEnabled()` (`NotificationHelper.kt:89`), which is app-level
    only. If the person blocks just the "Payments to confirm" channel, `notify()` silently no-ops,
    `showSettlementReminder` still returns `true`, and `remindedStage` advances
    (`SettlementReminders.kt:50-52`), so that stage is spent invisibly.

### 1.4 Exact alarms

None. No `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` or `AlarmManager` anywhere. The daily reminder is
explicitly inexact (`DailyReminderWorker.kt:25-27`). Keep it that way: Play restricts `USE_EXACT_ALARM`
to alarm and calendar apps, and `SCHEDULE_EXACT_ALARM` is denied by default for new installs on Android 14+.

### 1.5 Other runtime permissions

| Feature | Mechanism | Permission | Denial / failure handling |
|---|---|---|---|
| Voice entry | `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via `StartActivityForResult` (`ui/home/CaptureCard.kt:93-106`) | none (the speech app records) | `runCatching`, then the snackbar "Voice input isn't available on this phone…" (`:104-105`) |
| `RECORD_AUDIO` | declared, `AndroidManifest.xml:6` | never requested, unused | #64 removes it |
| Image / PDF capture | `ActivityResultContracts.GetContent()` (`CaptureCard.kt:117-130`) | none (SAF) | "Couldn't open that image/PDF", too-large message |
| Backup restore | `OpenDocument()` (`MainActivity.kt:593-596`) | none | n/a |
| Bill photos (#76) | system camera via FileProvider (`TakePicture`) + Photo Picker | none | per #76 body |
| Location | `play-services-location` is in the catalog but commented out (`app/build.gradle.kts:212`) | none | n/a |

Pitfall for #76 and anything later: **never declare `android.permission.CAMERA`** (directly or through a
merged library manifest). Once it's declared, `ACTION_IMAGE_CAPTURE` throws `SecurityException` unless
the permission is granted.

### 1.6 WorkManager workers

| Worker | Purpose | Request | Constraints | Period |
|---|---|---|---|---|
| `SyncWorker` (`data/sync/SyncWorker.kt:24-52`) | push+pull personal, group and local-ledger rows; widgets; colours; **settlement reminders** | one-time `sync-<uid>`, 2 s debounce, `REPLACE` (`SyncScheduler.kt:28-37`) | `NetworkType.CONNECTED` | on demand |
| same | background catch-up | periodic `sync-periodic-<uid>`, `KEEP` (`SyncScheduler.kt:40-48`) | `CONNECTED`, exponential backoff 30 s | **15 min** (the minimum; `:66-68`) |
| `DailyReminderWorker` | 9 PM reminder | self-re-enqueuing one-time `daily-reminder` | none | daily, inexact |

- Periodic sync is scheduled when a session opens (`ui/session/AccountSessionHolder.kt:64`) and
  cancelled on account change (`:51`, `SyncScheduler.kt:51-56`).
- One pass runs at a time (a process-wide `Mutex`, `SyncWorker.kt:58-65`).
- Under Doze and App Standby, 15-minute periodic work in practice runs in maintenance windows
  (often hourly or worse). On aggressive OEM ROMs (Xiaomi/MIUI, Oppo/ColorOS, Vivo/FuntouchOS,
  Realme: the Indian market) it can stop entirely until the app is opened.

### 1.7 Supabase Realtime and how the phone learns about others' changes

- Realtime is installed only as a **doorbell** (`data/remote/SupabaseClientProvider.kt:44-45`).
  `SupabaseLedgerChanges` subscribes to postgres_changes on `transactions`, `settlements` and `groups`
  (`data/sync/SyncDoorbell.kt:68-92`; publication `supabase/migrations/20260914034325_group_sync_columns.sql:57-66`).
  Any event leads, after a 2 s debounce, to `requestSync()` (`SyncDoorbell.kt:44-57`). `group_members`
  is deliberately not published, so DELETE events don't broadcast who left (`20260914034325:57-62`).
- Collected **only while the activity is STARTED** (`MainActivity.kt:210-217`, `repeatOnLifecycle`).
- Every start or foreground triggers a sync (`MainActivity.kt:205-209`). Local edits trigger a debounced
  sync.
- **Summary:** foreground means near-instant. Background means the 15-minute periodic sync at best,
  and never on a killed or OEM-restricted app. The pull applies rows (`GroupSyncEngine.kt:255-460`)
  but **doesn't record what changed or who changed it**, so nothing can say "Priya added…". Also a
  remote delete **hard-deletes the local row** (`:333-336`, `:420-423`), so a local diff can't even
  tell what was removed.

### 1.8 Tap → routing

- `MainActivity` is exported (launcher, share target, `kharchapaani://join/…`;
  `AndroidManifest.xml:40-73`). `onNewIntent` queues the intent (`MainActivity.kt:184-187`) and
  `routeIntent` handles it once the account session is ready (`:218-223`, `:243-294`).
- Notification PendingIntents use `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, with a unique request code
  per notification (`NotificationHelper.kt:64,101-106`). The intent flags are `NEW_TASK | CLEAR_TOP |
  SINGLE_TOP`. Widgets are the same (`widget/WidgetIntents.kt:28-32`). The ask-to-pay chooser callback
  is `FLAG_MUTABLE` on purpose (`ask/AskShare.kt:85-90`).
- **G4.** On `main`, any app can send `ACTION_ANSWER_SETTLEMENT` with any id. Fixed by **#74**.
- **G5.** A previous account's settlement notification opens a different settlement in the new
  account. Fixed by **#75** (`EXTRA_USER_ID` + `cancelAll()` on account change).

### 1.9 Other gaps found

- **G6. Stale "payment to confirm" notifications.** `clearSettlementReminder` is called only when you
  answer **on this phone** (`MainViewModel.kt:498`). If the recorder withdraws it, you answer on
  another device, or the group is purged (`GroupSyncEngine.kt:307-312`), the notification stays, and
  tapping it opens a settlement that no longer waits for you (or no longer exists).
- **G7. Subscriptions are charged only when the app opens** (`runDueSubscriptions` from
  `MainViewModel` init, `:165-170`). Any "renewed today" notification needs that run moved to a
  worker first.
- **G8.** Settlement reminders depend on a *fully successful* sync pass (`SyncWorker.kt:32-51`, all
  inside `syncIfSignedIn`). A failing local-ledger engine suppresses group reminders.
- **G9.** The notification id `30_000 + id % 100_000` (`NotificationHelper.kt:63`) can collide in
  principle. A `tag` ("settlement:<remoteId>") with a fixed id is cleaner and lets
  `getActiveNotifications()` find stale ones.
- **There's no in-app notification settings screen** beyond the Daily reminder switch and time
  (`AccountSheet.kt:564-627`). There's no inbox: the Home "Recent activity" is the transaction feed
  (`MainViewModel.kt:412-423`), not a list of what other people did.
- **No "added to a group" event is possible.** Membership is self-insert only, via `join_group(code)`
  (`20260914033124_groups_invites_rls.sql:515-550`, redefined in `20260914142135:82`). Leaving is a
  hard `DELETE` (`group_members_delete_self`, `20260914033124:226`).

---

## Part 2a: notification catalogue

Tone rules, shared by every row:
- Plain, warm, a little Hinglish where it fits naturally ("hisaab", "all square"). No "!!!", no guilt.
- Never "you owe", "overdue" or "pay now" towards the recipient. State facts and leave the choice
  with them ("whenever it suits").
- The actor is never notified about their own action.
- Friends who aren't on the app (local ledger, guest shares in `split_shares.local_name`) can't
  receive anything. Their reminders are the *user's own* nudges (row 15).
- Amounts use `Money.format`. `{share}` is the recipient's own share. Names are the local display
  name, falling back to the profile `display_name`.

Channel ids (create **all** at app start, with channel groups; never rename existing ids):

| Channel | Name (group) | Importance |
|---|---|---|
| `group_activity` | Group expenses (Groups & splits) | DEFAULT |
| `settlements_to_confirm` (existing) | Payments to confirm (Groups & splits) | DEFAULT |
| `settlement_updates` | Payment updates (Groups & splits) | DEFAULT |
| `group_members` | Group members (Groups & splits) | LOW |
| `subscriptions` | Subscription renewals (Your money) | DEFAULT |
| `summaries` | Weekly and monthly summaries (Your money) | LOW |
| `daily_reminder` (existing) | Daily reminder (Reminders) | DEFAULT |
| `nudges` | Reminders you set (Reminders) | DEFAULT |

No channel is HIGH. Nothing here justifies heads-up pop-ups, full-screen intents or bypassing
Do Not Disturb.

| # | Event (`kind`) | Trigger | Recipients | Copy (title / body) | Channel | Grouping / collapse | Tap → | Opt-out |
|---|---|---|---|---|---|---|---|---|
| 1 | `expense_added` | group expense inserted (server) | active share holders with a profile + the payer, minus the actor | **Priya added Dinner at Toit** / "Goa trip · your share ₹450 of ₹1,800." If the recipient paid: "Goa trip · paid by you, ₹1,350 comes back to you." | group_activity | `setGroup("g:<groupId>")`. Same actor + group within 30 min: one notification, "**Priya added 4 expenses in Goa trip**" / InboxStyle lines + "Your share so far: ₹1,230". `setOnlyAlertOnce` | group detail, scrolled to the expense (or to the group if more than one) | "Group expenses"; per-group mute |
| 2 | `expense_edited` | amount, title, date or **your share** changed (server compares with the last snapshot; a no-op re-push is ignored) | same as 1, minus the actor | **Priya changed Dinner at Toit** / "Your share is now ₹520 (was ₹450)." If only the title or date changed: "Updated the details. Your share is still ₹450." | group_activity | An edit by the adder within 30 min of their own add, still unread, *updates the add* instead of a new row | the expense | as 1 |
| 3 | `expense_deleted` | `deleted_at` set | share holders + payer, minus the actor | **Priya removed Dinner at Toit** / "Goa trip · the ₹450 share is off your hisaab." | group_activity | in the group bundle | group detail | as 1 |
| 4 | `removed_from_split` | your share soft-deleted in this save | that person | **Priya took you off Cab to airport** / "Goa trip · your ₹300 share is gone." | group_activity | in the group bundle | group detail | as 1 |
| 5 | `settlement_recorded` (exists locally today) | pending settlement inserted | the other party | **Priya says she paid you ₹500** / "Confirm it, or say it isn't right. It counts once you do." (IOU wording as today, `SettlementReminders.kt:29-34`) | settlements_to_confirm | one per settlement (tag `s:<remoteId>`); the 3-day escalation stays local | answer sheet | "Payments to confirm" (default on; warn when turned off: "Payments will wait silently") |
| 6 | `settlement_confirmed` | pending → confirmed | the recorder | **Rohan confirmed your ₹500** / "It counts for both of you now. Hisaab updated." | settlement_updates | replaces any earlier notification for the same settlement | the person's ledger | "Payment updates" |
| 7 | `settlement_rejected` | pending → rejected | the recorder | **Rohan says the ₹500 didn't happen** / note if any, in quotes, + "You can clear it or record the right amount." | settlement_updates | as 6 | the person's ledger, rejected card | as 6 |
| 8 | `settlement_withdrawn` | pending withdrawn by the recorder | the other party | **Priya withdrew the ₹500 she'd recorded** / "Nothing to confirm any more." Posted **silently** (`setSilent`), and **cancels #5** for it | settlement_updates | replaces #5 (same tag) | the person's ledger | as 6 |
| 9 | `settlement_retracted` | confirmed → `deleted_at` | the other party | **Rohan undid the ₹500 settle-up** / "Your balance with Rohan is back to what it was." | settlement_updates | as 6 | the person's ledger | as 6 |
| 10 | `member_joined` | `group_members` insert (join by invite) | other members | **Ankit joined Goa trip** / "Now 5 of you. You can add Ankit to splits." | group_members (LOW) | several joins within 1 h: "3 people joined Goa trip" | group detail | "Group members" |
| 11 | `member_left` | `group_members` delete | remaining members | **Ankit left Goa trip** / "Old expenses with Ankit stay as they are." | group_members (LOW) | as 10 | group detail | as 10 |
| 12 | `group_archived` | `archived_at` set | members minus the actor | **Goa trip was archived** / "It's read-only now. Everything's still there to look back on." | group_members (LOW) | single | group detail | as 10 |
| 13 | weekly digest (local) | Sunday ~11:00, only if open balances ≠ 0 | me | **This week: ₹2,350 to come back to you** / "Rohan ₹1,200 · Priya ₹800 · 1 more. Settle up whenever it suits." If I owe: "Also open: ₹600 with Ankit." | summaries (LOW) | one, replaced weekly | Splits tab | "Weekly summary" (default **on**, but only shown once the person has a non-zero balance) |
| 14 | monthly summary (local) | 1st of the month ~10:00, if the month had entries | me | **September: ₹24,300 spent** / "Food was the biggest at ₹7,800. Tap for the full picture." | summaries (LOW) | one per month | Insights, that month | "Monthly summary" (default **off**; offer it in the pre-prompt) |
| 15 | user nudge (local) | a time the user picked on the settle-up sheet ("Remind me to ask") | me | **Time to ask Rohan about ₹1,200** / "You wanted a nudge today. Send the ask whenever you're ready." Actions: **Ask now** (opens the ask sheet; nothing is sent automatically), **In 2 days**, **Done** | nudges | one per nudge (tag `n:<id>`) | settle-up sheet with Ask | per nudge; delete it |
| 16 | subscription due (local) | the day before a due date, ~09:00 | me | **Netflix renews tomorrow · ₹649** / "If you're done with it, cancel it in the app first." | subscriptions | if several are due the same day: "3 subscriptions renew tomorrow · ₹1,247" | Subscriptions sheet | "Subscription renewals" (default on) |
| 17 | subscription charged (local) | the worker booked a charge (needs G7 fixed) | me | **Netflix ₹649 added for today** / "Logged as Entertainment. Tap to change it." Silent | subscriptions | daily bundle | that entry | as 16 |
| 18 | daily reminder (exists) | as today | me | unchanged | daily_reminder | single | quick add | existing switch, moved into the new screen |
| 19 | settlement escalation (exists) | day 3/6/9 while pending | the answerer | unchanged (`SettlementReminders.kt:35-37`) | settlements_to_confirm | same tag as #5 | answer sheet | as 5 |
| 20 | (optional) recorder waiting (local) | my pending settlement is 7 days old | me | **Rohan hasn't answered your ₹500 yet** / "You can wait, or withdraw it and record it again." | settlement_updates (silent) | single per settlement | the person's ledger | as 6 |
| 21 | (optional) sync stuck (local) | rows still FAILED or PENDING after 24 h of retries | me | **3 entries haven't backed up yet** / "They're safe on this phone. Tap to try again." | a `system` channel, LOW | single | Account sheet → Sync now | always (low) |

Not included, on purpose: auto-reminders sent *to* the person who owes (an open question in
`docs/designs/ask-to-pay.md:746`, waiting on evidence), "big expense" alerts, AI insights pushed as
notifications, and anything that says "you owe" to the debtor except inside the weekly digest.

Quiet hours (device-side): 22:30–08:00 by default, editable. During quiet hours, group and settlement
notifications post with `setSilent(true)` (still in the shade, no sound). Local scheduled ones (13,
14, 16) never fire during quiet hours.

Foreground rule: if the app is in the foreground, post nothing to the tray for rows 1-12. Badge the
Updates bell instead (the Realtime doorbell already refreshes the screen).

---

## Part 2b: delivery architecture

### Options

| | A. FCM push from the server (triggers → Edge Function → FCM v1) | B. Local only (notifications generated after the WorkManager pull) | C. Hybrid (recommended) |
|---|---|---|---|
| Latency | seconds | 15 min at best; hours under Doze; **never** on OEM-killed apps until opened | seconds when push works; otherwise the periodic sync catches up |
| Reliability in India (MIUI/ColorOS/FuntouchOS) | high-priority FCM is the one channel these ROMs mostly respect | poor | best of both |
| No Google Play services (Huawei, custom ROMs) | nothing | works | works (fallback) |
| Server work | token table, prefs, triggers, Edge Function, secrets, Vault, pg_net | none (but the client must diff pulls, and it can't see deletes or the actor, §1.7) | same as A, but `activity` also feeds B |
| Who did what | trigger sees `auth.uid()`, which is exact | must be inferred; a payer edit (`20260918073251`) and the owner are indistinguishable | exact (server) |
| Spam control | server dedupe + device collapse | device only | both |
| Privacy | names and amounts pass through Google's FCM (data payload) | nothing leaves Supabase | same as A; payload can be reduced to ids (see trade-off below) |
| Cost | free tiers (FCM free; Edge Function invocations well inside the free quota at this scale) | free | free |
| Effort | ~3 PRs + owner RLS review | ~1-2 PRs, but a weak result | ~5 PRs, staged so value lands early |

### Recommendation: C, hybrid, built on one server-side `activity` table

1. **Source of truth: `public.activity`**, one row per (event, recipient), written by `SECURITY
   DEFINER` triggers on `transactions` (deferred constraint trigger, so the split and shares written
   later in the same `save_group_expense` call are visible), `settlements`, `group_members` and
   `groups`. The actor comes from `auth.uid()` (exact even for a payer editing someone else's
   expense). Service-role writes (`auth.uid()` null) notify nobody, as with `stamp_new_settlement`
   (`20260926000000:80-82`).
2. **Inbox:** the app pulls `activity` (cursor on `updated_at`, like the other pulls) into a Room
   `inbox_items` table. That feeds the Updates screen (§2d) and the **fallback** notifications.
3. **Push:** an `AFTER INSERT` trigger on `activity` calls `net.http_post` (pg_net, sent only after
   commit; a rolled-back transaction sends nothing) to an Edge Function `push-dispatch`. It reads
   preferences, mutes and tokens with the service key, and sends an FCM HTTP v1 **data** message per
   token. A pg_cron sweep every 2 minutes retries `push_state = 'pending'` rows younger than 1 h.
4. **Phone:** `FirebaseMessagingService.onMessageReceived` checks that `uid` matches the signed-in
   account, inserts the row into `inbox_items`, renders the copy locally (one tested
   `NotificationCopy` for push, fallback and inbox), collapses with recent unread rows of the same
   group and actor, posts, then enqueues a sync. Whichever path (push or periodic pull) reaches a row
   first posts it. `inbox_items.notifiedAt` dedupes, set inside a Room transaction.
5. **No flood after install or sign-in:** the first `activity` pull after a sign-in or reinstall
   marks everything older than "now" as notified (inbox only, no notifications). The fallback path
   only posts rows created in the last 48 h.

**Why data messages, not notification messages:** the app controls the channel, collapse, quiet
hours, the account check (#75) and the copy. Every high-priority message **must** end in a visible
notification, or FCM deprioritises the app. So the server never sends a push for a category the
user turned off, and the phone always posts something (silently, during quiet hours or with the app
in the foreground). Use `android.priority = "HIGH"`, `ttl = "86400s"` and
`collapse_key = "<recipient>:g:<groupId>"` (the newest push per group survives an offline device;
the inbox pull fills in the rest).

**Payload trade-off (owner decision, Q2):**
(i) structured fields: kind, actor name, merchant, amounts, group name (works with no network on
the phone, but this data passes through Google), or (ii) ids only, with the phone fetching the
`activity` row (more private, but each push needs a background network call through
`BackgroundAuth.sessionForNetwork`, which can take up to 18 s, `BackgroundAuth.kt:56-57`, against
FCM's ~10 s window, so it would have to hand off to a worker and show a generic "New update in Goa
trip" first). **Recommended: (i)**, disclosed in the privacy policy and Play Data safety.

### Schema sketch: **NEEDS OWNER REVIEW** (new RLS, SECURITY DEFINER functions, triggers on existing tables)

Conventions match the repo: `private.*` helpers, `set search_path = ''`, revoke EXECUTE on trigger
functions, no client hard deletes, comments stating the rules, and a test suite in `supabase/tests/`.
This is a sketch: names and details are for the implementing agent to finalise and test.

```sql
-- DRAFT. NOT APPLIED. Needs owner review: new RLS policies, SECURITY DEFINER code, triggers on
-- transactions / settlements / group_members / groups. Purely additive otherwise.
begin;

-- ------------------------------------------------------------
-- activity: one row per (event, recipient). Written only by triggers. Read only by its recipient.
-- ------------------------------------------------------------
create table public.activity (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  actor_id uuid references public.profiles (id) on delete set null,
  kind text not null check (kind in (
    'expense_added', 'expense_edited', 'expense_deleted', 'removed_from_split',
    'settlement_recorded', 'settlement_confirmed', 'settlement_rejected',
    'settlement_withdrawn', 'settlement_retracted',
    'member_joined', 'member_left', 'group_archived')),
  group_id uuid references public.groups (id) on delete cascade,
  transaction_id uuid references public.transactions (id) on delete cascade,
  settlement_id uuid references public.settlements (id) on delete cascade,
  -- Snapshot for copy and the edit filter: merchant, amount, your_share, paid_by, is_iou, note.
  data jsonb not null default '{}'::jsonb,
  -- kind:subject:txid. Makes a deferred trigger that fires once per row event write one row per save.
  dedupe_key text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),   -- pull cursor; read_at changes bump it
  read_at timestamptz,
  push_state text not null default 'pending'
    check (push_state in ('pending', 'sending', 'sent', 'skipped', 'failed')),
  pushed_at timestamptz,
  unique (recipient_id, dedupe_key)
);
create index activity_recipient_updated_idx on public.activity (recipient_id, updated_at);
create index activity_push_pending_idx on public.activity (created_at) where push_state = 'pending';
create trigger activity_set_updated_at before update on public.activity
  for each row execute function private.set_updated_at();

alter table public.activity enable row level security;
create policy activity_select_own on public.activity for select to authenticated
  using (recipient_id = (select auth.uid()));
-- No insert/update/delete policies. Writes come from triggers (definer) and the RPC below.
revoke insert, update, delete, truncate on public.activity from anon, authenticated;

create or replace function public.mark_activity_read(p_ids uuid[] default null, p_up_to timestamptz default null)
returns integer language plpgsql security definer set search_path = '' as $$
declare v_me uuid := (select auth.uid()); v_n integer;
begin
  if v_me is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  update public.activity set read_at = now()
   where recipient_id = v_me and read_at is null
     and ((p_ids is not null and id = any (p_ids)) or (p_up_to is not null and created_at <= p_up_to));
  get diagnostics v_n = row_count;
  return v_n;
end $$;
revoke execute on function public.mark_activity_read(uuid[], timestamptz) from public, anon;
grant execute on function public.mark_activity_read(uuid[], timestamptz) to authenticated;

-- ------------------------------------------------------------
-- Group expenses. DEFERRED so it runs at commit, after save_group_expense has written the split and
-- shares. It fires once per row event (the RPC plus touch_parent_transaction bump the row several
-- times), and the unique dedupe_key with txid_current() keeps it to one row per recipient per save.
-- ------------------------------------------------------------
create or replace function private.activity_for_group_expense()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := (select auth.uid());
  v_tx public.transactions;
  v_kind text;
  v_paid_by uuid;
begin
  if v_actor is null then return null; end if;                   -- service role: notify nobody
  select * into v_tx from public.transactions where id = new.id;  -- the row as committed
  if v_tx.group_id is null then return null; end if;             -- personal / private-split rows
  if tg_op = 'INSERT' then
    v_kind := 'expense_added';
  elsif v_tx.deleted_at is not null and old.deleted_at is null then
    v_kind := 'expense_deleted';
  elsif v_tx.deleted_at is not null then
    return null;
  else
    v_kind := 'expense_edited';
  end if;
  select s.paid_by into v_paid_by from public.splits s where s.transaction_id = v_tx.id;

  with active as (
    select ss.profile_id, sum(ss.share_amount) as share
    from public.split_shares ss join public.splits s on s.id = ss.split_id
    where s.transaction_id = v_tx.id and ss.deleted_at is null and ss.profile_id is not null
    group by ss.profile_id
  ), removed as (            -- soft-deleted in THIS transaction: now() is constant within it
    select distinct ss.profile_id, ss.share_amount as share
    from public.split_shares ss join public.splits s on s.id = ss.split_id
    where s.transaction_id = v_tx.id and ss.deleted_at = now() and ss.profile_id is not null
      and ss.profile_id not in (select profile_id from active)
  ), recipients as (
    select profile_id, share, false as was_removed from active
    union all
    select v_paid_by, 0, false where v_paid_by is not null and v_paid_by not in (select profile_id from active)
    union all
    select profile_id, share, true from removed
  )
  insert into public.activity (recipient_id, actor_id, kind, group_id, transaction_id, data, dedupe_key)
  select r.profile_id, v_actor, k.kind, v_tx.group_id, v_tx.id,
         jsonb_build_object('merchant', v_tx.merchant, 'category', v_tx.category, 'amount', v_tx.amount,
                            'your_share', r.share, 'paid_by', v_paid_by, 'date', v_tx.date),
         k.kind || ':' || v_tx.id || ':' || txid_current()
  from recipients r
  cross join lateral (select case when r.was_removed then 'removed_from_split' else v_kind end as kind) k
  where r.profile_id <> v_actor
    and private.is_member_of(v_tx.group_id, r.profile_id)
    -- Edit filter: skip when merchant, amount, date and this person's share match their latest snapshot
    -- (a re-push after a lost response, a bump with identical content).
    and not (k.kind = 'expense_edited' and exists (
      select 1 from public.activity a
      where a.recipient_id = r.profile_id and a.transaction_id = v_tx.id
        and a.created_at = (select max(created_at) from public.activity
                            where recipient_id = r.profile_id and transaction_id = v_tx.id)
        and a.data->>'merchant' = v_tx.merchant and (a.data->>'amount')::numeric = v_tx.amount
        and (a.data->>'your_share')::numeric = r.share and (a.data->>'date') = to_jsonb(v_tx.date)#>>'{}'))
  on conflict (recipient_id, dedupe_key) do nothing;
  -- TODO (implementer): merge an edit by the adder into their own unread expense_added from the last
  -- 30 min (update data, keep push_state) instead of inserting expense_edited.
  return null;
end $$;
revoke execute on function private.activity_for_group_expense() from public, anon, authenticated;

create constraint trigger transactions_activity
  after insert or update on public.transactions
  deferrable initially deferred
  for each row execute function private.activity_for_group_expense();

-- ------------------------------------------------------------
-- Settlements (group-scoped only; local_settlements are private and never notify).
-- ------------------------------------------------------------
create or replace function private.activity_for_settlement()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid()); v_kind text; v_to uuid;
begin
  if v_actor is null or new.group_id is null then return null; end if;
  if tg_op = 'INSERT' then
    if new.recorded_by is null then return null; end if;
    v_kind := 'settlement_recorded';
    v_to := case when new.from_profile_id = v_actor then new.to_profile_id else new.from_profile_id end;
  elsif old.status = 'pending' and new.status in ('confirmed', 'rejected') then
    v_kind := 'settlement_' || new.status;
    v_to := new.recorded_by;
  elsif old.deleted_at is null and new.deleted_at is not null and old.status <> 'rejected' then
    v_kind := case when old.status = 'confirmed' then 'settlement_retracted' else 'settlement_withdrawn' end;
    v_to := case when new.from_profile_id = v_actor then new.to_profile_id else new.from_profile_id end;
  else
    return null;
  end if;
  if v_to is null or v_to = v_actor then return null; end if;
  insert into public.activity (recipient_id, actor_id, kind, group_id, settlement_id, data, dedupe_key)
  values (v_to, v_actor, v_kind, new.group_id, new.id,
          jsonb_build_object('amount', new.amount, 'is_iou', new.is_iou, 'from', new.from_profile_id,
                             'to', new.to_profile_id, 'note', new.response_note),
          v_kind || ':' || new.id)
  on conflict (recipient_id, dedupe_key) do nothing;
  return null;
end $$;
revoke execute on function private.activity_for_settlement() from public, anon, authenticated;
create trigger settlements_activity after insert or update on public.settlements
  for each row execute function private.activity_for_settlement();

-- ------------------------------------------------------------
-- Membership and archiving. Recipients are the group's remaining members only, the same people
-- who can already see the membership through groups pulls. (Review: group_members is kept out of
-- Realtime for privacy, 20260914034325:57-62; this tells only co-members.)
-- ------------------------------------------------------------
create or replace function private.activity_for_membership()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_group uuid; v_who uuid; v_kind text;
begin
  if tg_op = 'INSERT' then v_group := new.group_id; v_who := new.profile_id; v_kind := 'member_joined';
  else v_group := old.group_id; v_who := old.profile_id; v_kind := 'member_left';
  end if;
  insert into public.activity (recipient_id, actor_id, kind, group_id, dedupe_key)
  select gm.profile_id, v_who, v_kind, v_group, v_kind || ':' || v_group || ':' || v_who || ':' || txid_current()
  from public.group_members gm
  where gm.group_id = v_group and gm.profile_id <> v_who
  on conflict (recipient_id, dedupe_key) do nothing;
  return null;
end $$;
-- AFTER INSERT OR DELETE on group_members; plus an AFTER UPDATE OF archived_at trigger on groups
-- (archived_at null -> not null; recipients = members minus auth.uid()).

-- ------------------------------------------------------------
-- Device tokens. Writes only through RPCs, so a token that moves between accounts on one phone is
-- reassigned, not duplicated. Hard delete is fine here: tokens aren't synced ledger data.
-- ------------------------------------------------------------
create table public.push_tokens (
  token text primary key,
  profile_id uuid not null references public.profiles (id) on delete cascade,
  installation_id text not null,          -- random UUID per install, kept in app storage
  app_version integer,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);
create index push_tokens_profile_idx on public.push_tokens (profile_id);
alter table public.push_tokens enable row level security;
create policy push_tokens_select_own on public.push_tokens for select to authenticated
  using (profile_id = (select auth.uid()));
revoke insert, update, delete, truncate on public.push_tokens from anon, authenticated;

create or replace function public.register_push_token(p_token text, p_installation_id text, p_app_version integer)
returns void language plpgsql security definer set search_path = '' as $$
declare v_me uuid := (select auth.uid());
begin
  if v_me is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if char_length(p_token) not between 20 and 4096 or char_length(p_installation_id) > 64 then
    raise exception 'invalid token' using errcode = '22023';
  end if;
  delete from public.push_tokens where installation_id = p_installation_id and token <> p_token;
  insert into public.push_tokens (token, profile_id, installation_id, app_version)
  values (p_token, v_me, p_installation_id, p_app_version)
  on conflict (token) do update
    set profile_id = v_me, installation_id = excluded.installation_id,
        app_version = excluded.app_version, last_seen_at = now();
  -- Review: whoever presents a token owns it. Tokens are unguessable, and only the device's own app
  -- can read its token, so that's acceptable. Flagged for the owner.
end $$;

create or replace function public.unregister_push_token(p_token text)
returns void language sql security definer set search_path = '' as $$
  delete from public.push_tokens where token = p_token and profile_id = (select auth.uid());
$$;
-- revoke from public, anon; grant to authenticated (both).

-- ------------------------------------------------------------
-- Preferences (server needs them to decide whether to push at all). Missing row = defaults.
-- ------------------------------------------------------------
create table public.notification_prefs (
  profile_id uuid primary key references public.profiles (id) on delete cascade,
  group_expenses boolean not null default true,
  settlements_to_confirm boolean not null default true,
  settlement_updates boolean not null default true,
  group_members boolean not null default true,
  updated_at timestamptz not null default now()
);
alter table public.notification_prefs enable row level security;
create policy notification_prefs_own on public.notification_prefs for all to authenticated
  using (profile_id = (select auth.uid())) with check (profile_id = (select auth.uid()));
revoke delete, truncate on public.notification_prefs from anon, authenticated;

create table public.group_notification_mutes (
  group_id uuid not null references public.groups (id) on delete cascade,
  profile_id uuid not null references public.profiles (id) on delete cascade,
  muted_until timestamptz,                -- null = until turned back on
  primary key (group_id, profile_id)
);
alter table public.group_notification_mutes enable row level security;
create policy mutes_select_own on public.group_notification_mutes for select to authenticated
  using (profile_id = (select auth.uid()));
create policy mutes_insert_own on public.group_notification_mutes for insert to authenticated
  with check (profile_id = (select auth.uid()) and private.is_group_member(group_id));
create policy mutes_update_own on public.group_notification_mutes for update to authenticated
  using (profile_id = (select auth.uid())) with check (profile_id = (select auth.uid()));
-- Unmute = set muted_until to now() (no client deletes, matching the project's rule).

commit;
```

Then a **second migration, hosted only**: the `activity` → pg_net dispatch trigger, the Vault
secrets (`push_dispatch_url`, `push_webhook_secret`), a pg_cron sweep and a 90-day retention job
(`delete from activity where created_at < now() - interval '90 days'`). Keep it separate. The PGlite
harness (`supabase/tests/local/run.mjs:16-24`) replays **every** migration, and PGlite has no
`pg_net`, `pg_cron` or `vault`, so either guard the body with `if exists (select 1 from pg_extension
where extname = 'pg_net')` or keep that file out of the harness.

```sql
-- DRAFT, hosted only, needs owner review.
create or replace function private.dispatch_activity_push() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'push_dispatch_url'),
    headers := jsonb_build_object('content-type', 'application/json',
      'x-kp-webhook-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'push_webhook_secret')),
    body := jsonb_build_object('activity_ids', jsonb_build_array(new.id)),
    timeout_milliseconds := 5000);
  return null;
end $$;
create trigger activity_dispatch_push after insert on public.activity
  for each row execute function private.dispatch_activity_push();
-- pg_cron, every 2 min: POST {activity_ids:[...]} for push_state='pending' and created_at between
-- now()-1h and now()-30s. Hourly: push_state='sending' older than 10 min -> 'pending'.
```

**Testing trap for the SQL suites:** the suites run in one transaction that ends in `ROLLBACK`
(`supabase/tests/rls_settlement_confirmation_test.sql:10`), so a `DEFERRED` trigger never fires.
Each check must run `set constraints transactions_activity immediate;` after the write (that fires
the pending events) before reading `activity`.

### Edge Function `push-dispatch` (sketch)

`supabase/functions/push-dispatch/index.ts`, Deno. Deploy with `verify_jwt = false` and check the
shared secret header instead. The project uses the new publishable/secret API keys
(`BuildConfig.SUPABASE_PUBLISHABLE_KEY`), and gateway JWT verification doesn't fit a pg_net caller.
**Confirm against current Supabase docs** when building.

```ts
// Secrets: FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY (from a service account with only the
// "Firebase Cloud Messaging API Admin" role), PUSH_WEBHOOK_SECRET. SUPABASE_URL and the service key
// come with the function's environment.
Deno.serve(async (req) => {
  if (req.headers.get("x-kp-webhook-secret") !== Deno.env.get("PUSH_WEBHOOK_SECRET")) return new Response(null, { status: 401 });
  const { activity_ids } = await req.json();
  // 1. Claim: update activity set push_state='sending' where id = any($1) and push_state='pending' returning *.
  //    (Claiming stops the webhook and the cron sweep double-sending.)
  // 2. Per row: load notification_prefs (default on), group_notification_mutes (muted_until null or > now()),
  //    and the recipient's push_tokens. Pref off or muted -> push_state='skipped' (still in the inbox).
  // 3. Resolve the actor's display name and the group name (service role).
  // 4. POST https://fcm.googleapis.com/v1/projects/{FCM_PROJECT_ID}/messages:send
  //    { message: { token, data: { v:"1", uid, aid, kind, gid, gname, actor, tx, st, merchant, amount, share, note, at },
  //                 android: { priority: "HIGH", ttl: "86400s", collapse_key: `${uid}:g:${gid}` } } }
  //    with an OAuth2 token from a JWT signed RS256 (npm:jose), scope firebase.messaging, cached in module scope.
  // 5. 404 UNREGISTERED or 400 INVALID_ARGUMENT on the token -> delete that push_tokens row.
  //    429 / 5xx -> leave 'pending' for the sweep. All tokens done -> 'sent', pushed_at = now().
  return Response.json({ ok: true });
});
```

Tests: Deno unit tests with a stubbed `fetch` (pref off → skipped; muted group → skipped;
UNREGISTERED → token deleted; 503 → stays pending; the actor never gets a push, which is guaranteed
upstream, so assert no row exists).

### Client pieces (Android)

- **Dependencies:** `firebase-messaging` only (through the BOM). #64 removes the BOM and the
  google-services plugin, so merge #64 first, then re-add *just* the BOM + messaging, deliberately.
  Two ways to initialise:
  (a) re-add the google-services plugin with `app/google-services.json`, or
  (b) no plugin: `FirebaseApp.initializeApp(context, FirebaseOptions.Builder()…)` from
  `BuildConfig` values (secrets plugin), in a new `Application.onCreate`. There's no Application class
  today: the `<application>` element has no `android:name`, `AndroidManifest.xml:21-29`.
  **Recommended: (b).** No plugin, keeps #64's clean-up, and config stays in `.env` like Supabase.
  Q4 for the owner.
- Manifest: `<meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false"/>`.
  After sign-in, set `FirebaseMessaging.getInstance().isAutoInitEnabled = true`, so no token exists
  before an account does.
- `push/KharchaMessagingService : FirebaseMessagingService` (`exported="false"`, `MESSAGING_EVENT`
  intent filter).
  - `onNewToken` → enqueue `PushTokenWorker` (CONNECTED). That worker calls `register_push_token`
    after `BackgroundAuth.sessionForNetwork` (never `awaitInitialization` in the background; see the
    memory note "test background work with the app backgrounded").
  - `onMessageReceived`: if `data["uid"] != BackgroundAuth.signedInUserId(ctx)`, drop it. Otherwise
    `LedgerDatabases.open(ctx, uid)`, upsert into `inbox_items`, `ActivityNotifier.post(...)`, then
    `SyncScheduler.requestSync`. Expedite only on API 31+ (`setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`).
    Below 31, expedited work runs as a foreground service and `CoroutineWorker.getForegroundInfo`
    isn't implemented in `SyncWorker`, so it would crash. Also avoid a `dataSync` FGS type entirely
    (Android 14 FGS declarations and the Play FGS form).
- Also register on every session open (the token can rotate while the app is dead). Keep
  `{token, userId}` last registered in DataStore to skip redundant calls.
- **Sign-out / account switch** (extend #75's `AccountLeftovers.clear`): best-effort
  `unregister_push_token` with a 3 s timeout *before* the Supabase sign-out (it needs the session),
  then `FirebaseMessaging.deleteToken()`. Offline sign-out is covered by the next registration
  reassigning the token, and by the `uid` check on the phone.
- R8 (#71): Firebase ships consumer rules, but the signed-release smoke test must include "receive a
  push with the app swiped away" (memory: "smoke-test the release build").

---

## Part 2c: permission UX

**When to ask (contextual, never on first launch, at most twice from the app's own prompts):**
1. Right after **joining or creating a shared group** (the moment groupmates' activity starts to
   matter).
2. Right after **recording a settlement** that waits for the other person: "Want to know when Rohan
   confirms?"
3. Right after the **first own entry** while the daily reminder is on (today's moment, kept).
4. When the person **turns on any category** in the Notifications screen, or taps a "Turn on" banner.

**Pre-prompt** (an in-app bottom sheet before the system dialog, on API 33+ only while the dialog can
still appear):
> **Stay in the loop on shared money?**
> We'll ping you when a groupmate adds an expense with you in it, or when a payment needs your
> OK. No spam, no ads, and you can fine-tune it anytime.
> [Turn on] [Not now]

"Turn on" launches `RequestPermission(POST_NOTIFICATIONS)`. "Not now" records `prePromptDeclinedAt`
and won't pre-prompt again for 14 days, and never more than twice in total. Dismissing the sheet
counts as "Not now".

**State machine** (a pure function, unit-tested). Inputs: SDK, granted, `askedBefore` (exists), the
rationale flag, `areNotificationsEnabled`, per-channel importance. Output:

| State | Action |
|---|---|
| granted, channel enabled | nothing |
| API < 33, app notifications off | "Turn on" opens `ACTION_APP_NOTIFICATION_SETTINGS` |
| 33+, never asked | pre-prompt → system dialog |
| 33+, asked, rationale = true (denied once) | pre-prompt with a "why" line → system dialog (last chance) |
| 33+, asked, rationale = false, not granted (denied for good) | "Turn on" opens `ACTION_APP_NOTIFICATION_SETTINGS` (`EXTRA_APP_PACKAGE`) |
| app on, **this channel** blocked (`importance == NONE`) | the row says "Off in phone settings" and opens `ACTION_CHANNEL_NOTIFICATION_SETTINGS` + `EXTRA_CHANNEL_ID` |

**When it's denied:** nothing breaks. Everything still lands in the Updates inbox. The inbox shows a
dismissible banner: "Notifications are off, so you'll see these only when you open the app.
[Turn on]". The Splits tab shows a small line under a pending "payment to confirm" card. Never nag on
launch.

**In-app Notifications screen** (Account sheet → "Notifications", replacing the lone reminder rows,
`AccountSheet.kt:366-376`):
- A master status line (on, off, or blocked) with the right fix button.
- Per category (stored in `notification_prefs` on the server for the push kinds, and in DataStore
  for the local ones): Group expenses · Payments to confirm · Payment updates · Group members ·
  Subscription renewals · Weekly summary · Monthly summary · Daily reminder (+ time) · Quiet hours
  (+ range).
- Each row reflects the channel's system state too (G3).
- Per group: group detail overflow → "Mute this group" (8 hours / 1 week / until I turn it back
  on) → `group_notification_mutes`.
- Fix G3: before posting, check `getNotificationChannel(id).importance != IMPORTANCE_NONE`, and only
  advance `remindedStage` when the post was really visible.

**Platform and Play policy notes**
- POST_NOTIFICATIONS is a normal runtime permission. No Play declaration is needed.
- No full-screen intents (Android 14 restricts `USE_FULL_SCREEN_INTENT` to calling and alarm apps; not
  applicable). No exact alarms (§1.4). No foreground service (§Client pieces).
- Play Data safety: FCM adds "Device or other IDs" (FCM token / Firebase installation ID) collected
  for App functionality, not shared. Notification payload data (names, amounts) is processed by
  Google as a service provider. Update the privacy policy (`docs/distribution.md` checklist).
- Notification content on the lock screen: set `setVisibility(VISIBILITY_PRIVATE)` with a
  `setPublicVersion` ("New update in Goa trip"), because amounts and names are financial details.
- Inline "Confirm" buttons on settlement notifications: **not in v1**. Confirming moves balances, and
  a mis-tap from the shade is too easy. If added later, use `setAuthenticationRequired(true)` (API 31+).

---

## Part 2d: in-app Updates inbox

- **Entry:** a bell in the Home and Splits top bars with an unread count badge (capped "9+").
  Not called "Activity", because Home already has "Recent activity" (the transaction feed).
- **Room `inbox_items`** (a new migration after #76's v10, so **v11**; additive; exported schema JSON +
  `Migration10To11Test`, per the memory note "Room migrations, no destructive fallback"):
  `id` (PK), `remoteId` (unique, nullable for local items), `source` (`server` / `local`), `kind`,
  `actorProfileId`, `groupRemoteId`, `transactionRemoteId`, `settlementRemoteId`, `dataJson`,
  `createdAt`, `readAt`, `readPending` (bool, to push to `mark_activity_read`), `notifiedAt`.
- **Fill:** a new `ActivitySyncEngine` in `SyncWorker` after the group pull (cursor
  `activity.updated_at`; idempotent upsert; read state from the server wins unless `readPending`).
  The FCM service upserts too. Local items (subscription charged, digests) insert with `source=local`.
- **Screen:** sections Today / This week / Earlier. Each row: avatar (person colour, design item 11),
  the rendered copy from `NotificationCopy`, the relative time, and an unread dot. Tapping a row marks
  it read (locally, then the RPC on the next sync), cancels its system notification, and opens the
  same destination as the notification. "Mark all read" calls `mark_activity_read(p_up_to)`.
  Empty state: "Nothing new. When someone adds an expense with you in it, it shows up here."
- **Cross-device clean-up:** when a sync sees `read_at` set from another device, cancel that
  notification here. Also fixes the spirit of G6: when a sync sees a settlement no longer pending,
  cancel its tag.
- **Retention:** 90 days (server cron), and the same locally.

**Routing:** one internal action, `ACTION_OPEN_UPDATE`, through #74's `.InternalEntry` /
`IncomingIntents`, with extras `EXTRA_USER_ID` (#75), `kind`, `groupRemoteId`, `transactionRemoteId`,
`settlementRemoteId`, `inboxId`. Ignore the intent if the user id doesn't match. If the target row
isn't pulled yet, open the group, request a sync, and focus the row when it arrives (5 s timeout,
then leave it at the group). PendingIntent: `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, request code =
hash of the notification tag. Settlement kinds keep opening the answer sheet or the person's
ledger, through local-id lookup by `remoteId`.

---

## Phased build plan (one PR each, tests in each)

Gate per `CLAUDE.md`: confirm with the owner before starting. Every phase with server SQL needs
**owner review before merge** (RLS / auth / security, per the merge-policy memory). Phases 0, 1 and 5
are client-only and low-risk.

**Phase 0: fix what exists (client only; after #74/#75)**
- Create all channels and channel groups at app start (an `Application` class or `MainActivity.onCreate`).
  Keep the existing ids. Fix the KDoc.
- G6: in `SettlementReminders.remind`, cancel notifications (tag `settlement:<remoteId>`) whose
  settlement is no longer awaiting my answer, using `getActiveNotifications()`. Switch to tags (G9).
- G3: per-channel enabled check. Advance `remindedStage` only when the post was visible.
- G8: run `SettlementReminders.remind` in a `finally`-style step after the group engine, not gated on
  the local-ledger engine.
- Tests: Robolectric `ShadowNotificationManager`: channels exist after start; a withdrawn settlement's
  notification is cancelled by the next `remind`; a blocked channel doesn't advance the stage.

**Phase 1: permission UX + Notifications screen (client only)**
- The `NotificationPermission` state machine (a pure function) + pre-prompt sheet + the 4 ask moments
  (G1, G2). Persist `prePromptDeclinedAt` / count in `AppSettings`.
- The Notifications screen with the local categories (daily reminder moved here, quiet hours),
  per-channel status rows and deep links.
- Tests: a decision-table unit test for the state machine; Compose tests for the screen rows and
  banners; a test that the group-join moment shows the pre-prompt exactly once.
- Run on a device: deny → banner → Settings deep link → back → row updates (`LifecycleResumeEffect`
  as `MainActivity.kt:409-413`).

**Phase 2: `activity` table + triggers + RLS (server) + Updates inbox (client, pull-only)**
- The schema-sketch migration (activity, the four triggers, `mark_activity_read`). **Owner review.**
- SQL suite `supabase/tests/activity_test.sql` (PGlite harness; `set constraints … immediate`):
  - the actor gets nothing;
  - share holders and the payer each get one row per save, even with several row bumps;
  - guests (`local_name`) and non-members get nothing;
  - `removed_from_split` fires only for the removed person;
  - a no-op re-push writes nothing;
  - the five settlement transitions, including a clear-after-reject that notifies nobody;
  - join and leave;
  - mallory can't select, insert or update activity; `mark_activity_read` touches only own rows;
  - personal and private-split (`local_splits`) transactions write nothing;
  - service-role writes write nothing.
- Client: Room v11 `inbox_items` + migration test, `ActivitySyncEngine`, `NotificationCopy`
  (copy table tests for every kind incl. IOU and "paid by you" variants), Updates screen + bell badge,
  `ACTION_OPEN_UPDATE` routing (`IncomingIntentsTest` cases).
- **Fallback notifications from the pull** (`ActivityNotifier`: 48 h window, first-sync baseline,
  per group+actor collapse, foreground suppression, quiet hours). Even with no push, the owner's ask
  ("everyone in the split gets notified") works within the sync latency.
- Test the gatherer, not the format (memory): start from a real Room DB with a pulled activity page
  and assert the posted notification text and count.

**Phase 3: FCM client + token RPCs**
- Migration: `push_tokens`, `register_push_token`, `unregister_push_token`, `notification_prefs`,
  `group_notification_mutes`. **Owner review.** SQL tests: token reassignment between accounts,
  isolation, a non-member can't mute a group.
- Android: Firebase init (option b), `KharchaMessagingService`, `PushTokenWorker`, sign-out
  unregister (in `AccountLeftovers`), the uid check, inbox upsert + post + sync request.
- Notifications screen: server-backed categories write `notification_prefs`; the group mute UI.
- Tests: service logic as a plain class (`PushMessageHandler`) with a fake clock and DB: wrong uid
  dropped; duplicate of an already-pulled row not re-posted; collapse.
- Device check with the app **backgrounded and swiped away** (memory note), plus the release build.

**Phase 4: Edge Function + dispatch**
- `supabase/functions/push-dispatch`, Deno tests, deploy notes, secrets list, the hosted-only
  migration (pg_net trigger, Vault secrets, pg_cron sweep + retention). **Owner review.** The owner
  creates the Firebase project and service account and sets the secrets: an agent must not handle
  the private key.
- End-to-end check on two devices: A adds an expense split with B → B's phone (app killed) shows it
  within seconds. A adds 6 expenses quickly → B hears one sound and sees "A added 6 expenses in …".

**Phase 5: local scheduled notifications**
- Move `runDueSubscriptions` into a daily morning worker (G7). Add "renews tomorrow" and "charged
  today".
- Weekly digest + monthly summary workers (inexact WorkManager, quiet-hours aware; balances from
  `LedgerRepository.personBalances.first()`, the single balance source after #57).
- User nudges: Room `nudges` table (another additive migration), a "Remind me to ask" chip on the
  settle-up sheet, a per-nudge unique one-time work, and notification actions through a non-exported
  receiver (Snooze / Done). "Ask now" opens the ask sheet via `.InternalEntry`. **Nothing is ever
  sent to the friend automatically.**
- Tests: schedule maths with a fixed clock (DST-free IST, but test a zone change like #66);
  "nothing to say" produces no notification (zero balances, empty month).

**Phase 6: polish (optional)**
- Cross-device read clearing, lock-screen public versions, the "recorder waiting" (row 20) and
  "sync stuck" (row 21) notifications, and per-channel state synced to `notification_prefs` so the
  server stops pushing a category the user blocked in system settings.

---

## Open questions for the owner

1. Hybrid as recommended, or start with Phase 2's pull-only fallback and decide on FCM after living
   with it? (At 4 users, pull-only may be enough for a while; on MIUI-class phones it won't be.)
2. FCM payload: structured (names and amounts pass through Google; works instantly) or ids only
   (more private; generic text first)?
3. Defaults: weekly summary on, monthly off, quiet hours 22:30–08:00. OK?
4. Firebase init: no plugin, options from `.env` (recommended), or bring back `google-services.json`
   after #64?
5. `member_joined` / `member_left` go to all remaining members. OK given `group_members` was kept out
   of Realtime for privacy?
6. Should turning off "Payments to confirm" be allowed at all? Recommended: allowed, with a warning.

## Pitfalls checklist for the implementer

- Never `auth.awaitInitialization()` in a worker or the FCM service: use `BackgroundAuth`.
- Expedited work only on API 31+, or implement `getForegroundInfo`.
- Every high-priority FCM message must end in a posted notification.
- Deferred triggers + rollback-only SQL tests: `set constraints … immediate`.
- Keep pg_net / pg_cron / Vault out of the PGlite harness run.
- No destructive Room migrations. #76 is v10, so these are v11+.
- Run the app (debug **and** signed release) before pushing UI work. Unit tests never construct
  `MainViewModel` init order.
- Record changes in `CHANGELOG.md`; update `PRD.md` only to fix the stale "spending milestones" claim
  (`PRD.md:39-41`) once a decision lands.
- Never declare `CAMERA`. Never add `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT`.
