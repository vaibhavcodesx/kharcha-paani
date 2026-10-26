# Changelog

All notable changes to Kharcha Paani are recorded here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Server changes (Supabase
migrations) are listed with the release they shipped alongside and marked **(server)**; a
migration that is staged but not applied is not a change yet and appears only under
*Unreleased* as staged.

The day-by-day engineering record behind 1.0.0 and 1.1.0, including how each change was verified,
is archived in [`docs/history/prd-status-log-2026-09.md`](docs/history/prd-status-log-2026-09.md).

## [Unreleased]

### Fixed

- Insights fits complete cash-flow and per-day amounts at large text sizes; the ring keeps its
  spoken total and omits cramped duplicate centre text (X7 / T2.10, REL M7).

- Group cards grow with large text and keep complete Indian-formatted amounts visible
  (X7 / T2.10, REL M7; Splits).

- The dock hides visual tab labels above 150% font size while keeping their spoken names and
  full navigation targets (X7 / T2.10, REL M7).

- Overlapping statement parses use separate date formatters, keeping dates and month labels
  consistent across threads (X10 / REL L10).

- Home calls the gallery action “Bill photo”, so its label matches what opens (X10 / REL L7).

- Ambiguous Google picker errors suggest reconnecting before Play services or account advice
  (X10 / REL L5).

### Added

- Saving an entry, recording a payment or importing a nonempty statement batch gives a confirmation
  haptic only after the ledger write succeeds (X10 / REL L6).

- A confirmed settlement can offer Play’s own review flow after a settling-in week, at most once
  per 180 days; errors, old history and busy screens do not trigger it (X9 / T3.9).

- Play-installed builds can offer a flexible update, keep working during download, and show an
  explicit Restart action when it is ready; drafts and sheets defer the offer (X6 / T2.8).

- New splits with one selected friend suggest the last active group where you added an expense
  together; choosing a group or No group keeps your choice (X5 / TODOS 9).

- Group expense rows show saved colour dots beside the payer and split participants, including
  category expansions; names remain beside the dots (X5 / TODOS 2).

- Sort Groups by latest activity, name, either balance direction or newest; the choice stays on
  this phone when you reopen the app (X5 / TODOS 5).

- Groups have a Settlements view for payments and IOUs, including dates, people and confirmation
  status, while All activity keeps the combined history (X5 / TODOS 8).

- Tap an IOU card to read its whole note, full date and saved recorder, then tap again to collapse.
  Answer and removal actions remain available; entries without recorder metadata say so instead
  of guessing who added them (X5 / TODOS 4).

- Play Store listing draft: short and full descriptions, a six-screen capture plan and a
  1024×500 feature graphic brief, with copy validation checks (X1 / T2.9).

- A launch screen with the Kharcha Paani mark on the app's dark background, held while your account
  opens (at most 1.5 s), so the app goes straight from it to Home. The launcher icon has a themed
  (monochrome) version too: just the ₹.
- Delete my account, at the bottom of the account sheet. It says what goes (your sign-in, your own
  expenses, splits with friends who aren't on the app, your profile) and what stays (shared group
  expenses and payments, so nobody's balance changes), and asks how you appear on past group
  expenses: "Former member" unless you choose to keep your name. A second confirm, and it's done
  at once: you're signed out and the phone keeps nothing of the account. With changes not synced
  yet, it offers to sync them first. **(server, staged, not applied)**
- Daily AI reads per person, generous enough that normal use never meets them: 50 quick adds,
  10 bills, 3 statements and 20 Insights questions a day, back at midnight IST. Reaching one says
  "That's today's AI reads used up. They're back tomorrow; you can still add entries by hand."
  **(server, staged, not applied)**
- Statement reads are cleaned on the server before they reach the app: at most 1,000 rows, no
  amount the database couldn't store, merchant and note cut to 80 and 200 characters.
- A Notifications screen, from the account sheet: whether your phone shows Kharcha Paani's
  notifications at all (with a Turn on button that asks, or opens the phone's settings once Android
  won't ask any more), a switch for payments to confirm (turning it off says what you'd miss), the
  daily reminder and its time, and quiet hours, 10:30 PM to 8 AM unless you change them, when payment
  nudges wait till morning.
- The app now offers to turn notifications on right after something a notification would follow
  up: joining or creating a shared group, recording a payment in one, or your first own entry with
  the daily reminder on. It explains what they're for first, offers at most twice, and "Not now"
  leaves everything working. Before, someone who only used groups was never asked at all.
- A privacy policy and terms of use (`docs/legal/`, published from `site/`), written from what the
  app really does: who handles your data (Supabase in Tokyo, Google Gemini on its free tier, Google
  Sign-In, crash reports), what groupmates see, how long data is kept, how deleting your account
  works, your rights under India's DPDP Act and a grievance contact who replies within 90 days.
- The sign-in screen says what continuing agrees to, with the Terms and Privacy policy a tap away,
  and that the app is for people 18 or older.
- Help & about at the bottom of the account sheet: send feedback (your mail app opens with the app
  and phone versions filled in), the privacy policy, the terms, open-source licences, and the
  version you're on.
- `docs/legal/play-console-answers.md`: the Data safety and Financial features answers for the Play
  Console, and the account deletion page for people who no longer have the app.
- Before the first quick add, bill scan, statement, Insights question or trip read goes to Google
  Gemini, the app now says so, once: "Bills, statements and quick-add text are read by Google
  Gemini", that while the app is free Google may use what's sent to improve Gemini and its
  reviewers may see it, that it's never shown to other users and never sold. "Type it myself"
  sends nothing: a quick add is filled in on your phone instead, and a bill photo is kept on a
  blank entry for you to type.
- "Report this answer" under each AI answer opens your mail app with the answer, so a wrong or
  offensive one can be reported.
- Crash reports through Firebase Crashlytics, so problems on people's phones can be found and
  fixed. They never include amounts, names, notes or documents, and stay off in any build without
  the Firebase project. Handled problems (a sync the server refused, a ledger that wouldn't open,
  an unusual AI failure) are reported with their reason only.
- - Say the split: a quick add that names a group ("petrol 500 Goa trip") now opens in that group
  with its members ticked, and "with Rohan" works with first names ("Ankit" for Ankit Shah) and
  with "bhai" or "ji" after the name, from the widget's mic and text field as much as from Home.
  A group and a friend together ("lunch 900 Goa trip with Rohan") splits with just that friend. A
  name that matches more than one friend, or nobody, is said on the sheet instead of being dropped.
- - Three new categories: Beverages, Packaged goods, and Self-care (haircuts, salon, spa, grooming,
  the gym and wellness), each with its own icon and colour. Quick add, bills and statements can
  choose them, and so can the phone's own guess: "haircut 300", "juice 60", "maggi 14". The gym
  now counts as Self-care instead of Health.
- - A "Send crash reports" switch in Help & about, as the privacy policy promises. Crash reports stay
  on unless you turn them off, and the switch only appears in builds that can send them.

### Fixed

- Retracting a settlement, deleting an IOU or deleting a local group now asks first and explains
  what happens to balances or group history. Cancel keeps everything, and the accessibility
  delete-group action uses the same confirmation (X4 / T2.5).

- The ledger no longer appears in Recents thumbnails on Android 13+, while manual screenshots
  still work. Payment reminders have a generic lock-screen version without names or amounts,
  and incoming image/PDF shares reject this app's own files and non-content URIs (X3 / T2.6).

- Tapping a category in a group's "Where it went" now opens its expenses; tap again to close,
  or choose another category to switch. Entries open in the editor when editable; archived
  groups stay read-only, and switching groups clears the selection (X2 / TODOS 7).

- An amount that isn't a plain number ("Infinity", "1e999", a pasted 19-digit number) could be
  saved. Its upload then failed on every sync, so nothing else in the account synced again, and
  drawing it crashed the app. Amount fields now take only digits with up to two decimals, up to
  ₹9,99,99,99,999.99 (what the server stores); commas, spaces and a ₹ sign in a pasted amount are
  ignored. A row that still can't be uploaded is marked as not synced, and the rest sync on.
- Saving more than ₹10 lakh asks first ("That's ₹12,00,000. Right?"), and an expense's date can no
  longer be set in the future.
- On a phone in light mode, the clock, battery and other status bar icons were dark on the app's
  dark background, so they couldn't be seen, and 3-button navigation had a white strip. The bars
  now always have light icons over the app.
- Tapping Save twice quickly logged the expense twice, and tapping Create group twice made two
  groups. A second tap while the first is still saving now does nothing, and Save shows a spinner
  meanwhile. The same goes for recording a payment or an IOU, importing a statement, adding a
  friend and adding a subscription.
- "Reading your statement" couldn't be closed: Back did nothing for the minutes a long statement
  takes. It now has a Cancel button, Back and a tap outside cancel it too, and what was being read
  is dropped rather than opening later. After 20 seconds it says long statements take up to 3
  minutes and that a shorter date range is quicker.
- A "payment to confirm" notification stayed up after the payment was answered on your other
  phone, withdrawn by whoever recorded it, or gone with its group, and tapping it opened a payment
  that no longer waited for you. Each sync now takes those down.
- With only the "Payments to confirm" notifications blocked in the phone's settings, a reminder was
  counted as shown although nothing appeared, so you never got it. It now waits until they're
  allowed again; the daily reminder checks its own channel the same way.
- Payment reminders came only after a fully successful sync, so one part of the sync failing
  stopped all of them. They now go out whatever the sync managed.
- Both notification channels exist from the first launch, so either can be turned off in the
  phone's settings before its first notification.
- A save that failed in a way the app didn't expect (a full phone, signing out while it was
  saving) could close the app. Every action that writes or sends something now says what happened
  instead ("Couldn't save. Your phone may be out of space."), and the reading spinner on a quick
  add or a bill always goes away, even when the read fails. Sharing an invite on a phone with no
  share sheet no longer crashes.
- - An equal split with friends now adds up to the amount to the paisa, as a shared group's already
  did: ₹500 over three is ₹166.66, ₹166.66 and ₹166.68 on whoever paid, not ₹166.67 three times
  (₹500.01).

### Security

- The Gemini key is no longer in the app. Every AI read goes through a Supabase Edge Function
  (`gemini-proxy`) that holds the key as a server secret, accepts only signed-in people, and
  passes back Google's reply. Until now the key shipped inside every APK, where anyone could
  copy it.

### Security

- The database now refuses a group expense whose shares don't add up to its amount, whoever writes
  it. Before, only saving through the app checked, so editing one share or the amount directly could
  throw off everyone's balance in the group. **(server, staged, not applied)**

### Security

- Signed-out callers now get nothing from the database, not even the table access that only the
  row-level rules were holding back, and new tables or functions aren't opened to them by default.
  A colour too close to a groupmate's with no display name is now refused like any other, and a
  profile picture has to be an https link. **(server, staged, not applied)**

## [1.5.0] - 2026-10-25

### Added

- `docs/production-readiness/`: the plan for launching on the Play Store, from four audits of the
  code (security, reliability and UX, notifications and permissions) plus Play policy and DPDP
  research and the owner's saved reels. It answers which keys, database changes and features have to
  change, lists the console steps only the owner can do, and orders the work in three tiers, each
  item a PR with tests. The audits are kept alongside it.
- Bill photos. Any expense can keep a photo of its bill: "Bill photo · optional" in the add and
  edit sheet, from the camera or the gallery, with no permission asked. Scan bill and pictures
  shared into the app keep the picture they read, and one tap removes it. Tap the thumbnail to see
  the bill full screen and zoom in; expenses with a photo show a small receipt icon in the list.
  Photos stay on this phone only (in your account's own folder, out of the 25 MB cloud backup, and
  not in the JSON backup), are shrunk to about 300 KB, and carry no location or phone details.
  Deleting an expense deletes its photo, after Undo has gone.
- Ask a friend to pay you back without the awkward message. On the settle sheet's "{Friend} paid
  you" side, "Ask {name} to pay" shares a ready-made hisaab: each expense, guest share and payment
  since you were last square, a total that matches their balance, your UPI ID, and your payment
  code as a picture they can scan or pick from their gallery. Change the amount first to ask for
  part of it ("Asking ₹200 of the ₹340 you owe"). The message is previewed on the sheet and can be
  edited in WhatsApp (or wherever you send it) before it goes. It works for friends who aren't on
  the app.
- A friend you've asked shows "Owes you · Asked 3 days ago" on their row until they're square. An
  ask counts only when you pick an app to send it with; closing the share sheet doesn't. Kharcha
  Paani itself isn't offered in that sheet.
- Local database version 9: a new table logging asks (on this phone only). Additive; nothing
  already on the phone changes.
- A test now opens the whole signed-in app, Home and every tab, the way the app builds it on
  launch, so a crash in the first frames fails CI instead of every phone. The same check runs on
  a real phone before a release (`./gradlew :app:connectedDebugAndroidTest`).
- `docs/release-checklist.md`: what to check on the release-signed build before a release goes
  out, with `scripts/release-smoke.sh` for the parts adb can do (release key, install over the
  existing app, cold launch, shared text, invite links, widgets in the background).

### Changed

- PRD.md is now a short page on what the product is and which rules still hold. The September
  brief it replaced (cloud migration, robustness, premium UI pass), which shipped in v1.0–v1.4, is
  archived in `docs/history/prd-2026-09-cloud-migration.md`. CLAUDE.md now points agents at the
  production readiness plan and asks them to gather every question for the owner at the start of a
  session, then work through it and save new questions for the end.

- Every pull request now also lints and builds the release variant, the one people install, and
  not only the debug build the tests use. The release workflow runs the unit tests first, refuses
  a release whose versionCode didn't go up, and attaches an AAB for Play next to the APK (plus R8's
  mapping file once shrinking is on). GitHub Actions are pinned to exact commits, and Dependabot
  proposes updates weekly.
- Back on Splits or Insights now goes to Home instead of closing the app. Back on Home still
  leaves.
- A friend's balance and their history are now worked out from the same rows, so the history
  always adds up to the balance. No figure moves: a test pins every friend's balance, both its
  totals and their history on a ledger with every kind of row, checked against the old calculation.

### Removed

- The app no longer asks for microphone access in its manifest. Voice entry hands off to the phone's
  own speech screen, which never needed it, so the permission was never used.
- Dropped libraries nothing used: Retrofit, Moshi and its code generator, OkHttp's logging
  interceptor, Firebase AI and App Check, and the Google Services plugin (which ran without a
  config file). The app is smaller and has less in it to keep updated.

### Fixed

- A backup could bring back a split paid by a friend you'd since deleted as paid by you, so everyone
  else on it suddenly owed you their share. The backup names people, "no name" means you, and a
  deleted friend had no name to write. Such a split is now backed up as what's left of it for you
  (your share, as your own expense), a share or payment naming someone no longer on the phone is
  left out rather than given to you, and a restore never takes a payer it can't place to be you.
  Backups made before this fix still carry the old mistake for those splits.
- What deleting a friend leaves as your own expense, and anything else kept on the phone only, was
  left out of the backup as if it were in your account. It's in the backup now.
- A split with a friend who has no account, saved on another phone while this one was syncing,
  could arrive before its expense and be skipped for good, leaving the expense unsplit here and
  the balance with that friend wrong. A split that arrives first now waits and is applied on the
  next sync, once its expense is here. A test saves two splits between the two pulls and checks
  the balance on both phones.
- Opening the app on the 1st of a month, Home still showed last month's spending as "this month"
  until something was saved. Home now moves on to the new month by itself, within a minute of
  midnight.
- After travelling to a time zone behind the one a subscription was set up in (India to the US,
  say), the app could add that month's charge a second time: the date it had charged through read
  as the evening before. Subscription dates are now calendar days that read the same in any zone,
  including ones saved by earlier versions.
- Importing a statement no longer ticks every row it read. Rows whose date couldn't be read
  (they'd have landed on today), rows dated after today or outside the statement's own dates, and
  income far bigger than the rest of the statement arrive unticked, each with the reason under
  it. A tampered PDF could otherwise slip a ₹5,00,000 "salary" in among real payments.
- Restoring a backup now shows what's in it (when it was taken, and how many friends, expenses
  and payments) and waits for Restore. It used to apply the moment a file was picked. A file
  with impossible numbers in it, such as a negative amount or shares adding up to more than the
  expense, is refused with the reason, and nothing changes. Big files are read without freezing
  the app.
- The app couldn't run on Android 7.0 and 7.1, although it said it supported them: its dates use
  Java's `java.time`, which those versions don't have. The build now includes it (core library
  desugaring), so Android 7 phones get it too. Found by the new lint check.
- The app opens without internet again. Opening it offline more than about an hour after you last
  used it left a blank dark screen until the connection came back: the sign-in token had expired,
  it couldn't be refreshed offline, and the app lost track of which account was signed in while
  it waited. It now opens your ledger straight away (within a second and a half at most) and
  reconnects on its own; the account sheet says "Offline" meanwhile. A signed-out or revoked
  account still goes to sign-in.
- Restoring a backup after signing in on a new phone could count a payment twice. A payment typed
  to a fraction of a paisa (₹100.555) and backed up before it had synced comes back from your
  account rounded (₹100.56), so the restore didn't recognise it as already there and added the
  backup's copy as well. Payments are now matched to the paisa. A test restores a backup over the
  same records pulled from your account and checks that no balance moves, and that a split from
  before 1.3 that a later payment settled is filled in.
- The backup carried shared groups' split expenses, groupmates' included, and restoring it could
  bring back ones deleted or settled in the group since, as your own. Shared groups' expenses now
  stay out of the backup (the group keeps them), and an older backup's copies aren't restored.
- The UPI screen said your UPI ID "stays on this phone". Since 1.0.0 it has also been on your
  profile, where people in your shared groups can read it so they can pay you. It now says so.
- `docs/splitting-with-people-not-on-the-app.md` still said a friend who isn't on the app "lives
  only on this phone" and that their name "never leaves your device". Both stopped being true in
  1.3.0 (splits and IOUs with them back up to your account, private to you) and 1.4.0 (as a guest
  on a shared group's expense, their name syncs to that group). The doc now says what syncs, who
  can read it, and what survives an uninstall, including that older records stay on the phone and
  that a restored phone can show a balance backwards until the JSON backup is restored. Code
  comments that repeated the old claim are corrected too.
- An edit could be lost when two syncs ran at once (the regular background sync and the one
  after a change): the older copy could reach your account last and overwrite the newer one on
  the next pull. Syncs now run one at a time.
- A subscription could add the same month's charge twice if the app was closed at the wrong
  moment, or when opening the app and saving a subscription ran together.
- Deleting a friend who isn't on the app left their IOUs and payments in your account, so a
  reinstall brought them back, along with the friend. They're now removed from your account too.
- Joining a shared group could miss its older expenses and payments if the sync stopped right
  after the group arrived.
- Two friends who aren't on the app could share a name, and the next sync then moved one's IOUs
  onto the other. Adding a second friend with a name you already use (any case or spacing) now
  asks you to tell them apart, like "Rahul K".
- A payment recorded inside a local group didn't change that group's "you're owed" figure. It
  does now.
- Scanning or sharing a very large photo (from a 100+ MP camera) or a huge file could freeze or
  crash the app: it was opened at full size on the main thread. Photos are now opened in the
  background at most 2048 pixels on the long side, which is still sharper than what's read, and a
  statement over the size limit is turned away before it's read into memory.
- A split by percentage with friends who aren't on the app (33.3% each, say) came out a few
  paisa to a rupee short. It never reached your account, so it was lost on a new phone, and the
  gap was owed by nobody. Percentage splits now divide the exact amount, as they already did in
  shared groups.
- Switching an expense with a split to Income still saved the hidden split, so the income showed
  up as friends owing you.
- Signing out while "Undo" was showing after a delete could crash the app.
- "Last synced" could say "just now" while group sync was failing.
- The spreadsheet export listed payments still waiting for the other person, and disputed ones,
  as if they had happened.
- A statement whose dates came back day-first (05-10-2026) could be imported into 2011.

### Security

- Signing out, or switching to another account, now takes down the previous account's
  notifications and deletes the CSV exports and ask-to-pay QR images it left in the app's cache. A
  payment-to-confirm notification also says whose it is, so one left over from another account
  can't open a different payment that happens to share its number.
- Other apps could make Kharcha Paani open its add sheet, start voice entry or open a payment
  to answer, by sending its own widget and notification actions straight to it. Those now count
  only from the app's own widgets and notifications. Shared text has to say it's text before it's
  read with AI, and a shared picture or PDF has to come from the sharing app, not as a path to a
  file on the phone.

## [1.4.0] - 2026-09-27

### Added

- Payments between groupmates now need both people. A settle-up or IOU someone records is
  "waiting" until the other person confirms it or says it isn't right, and only a confirmed one
  moves anyone's balance. There's no deadline and nothing confirms itself. The person who has to
  answer gets a notification when it arrives, then reminders on day 3, 6, 9 and so on. Whoever
  recorded it can withdraw it while it waits, or clear it away after it's disputed. A "Waiting for
  you" section on Splits and distinct looks in the feed show where each one stands.
- Friends who aren't on the app can be in a group expense. Split a dinner with Shubham (on the
  app) and Hist (not on it), pick the group, and it syncs to Shubham with Hist on it as your guest.
  You cover Hist's share in the group, and Hist owes it to you. If Hist paid, it stays on your
  phone.
- One colour per person, the same on every phone. Pick yours from the whole spectrum in your
  account. You see yourself in it too: avatar, the ring that turns while it syncs, "You" in groups.
  Nobody can take a colour too close to someone they share a group with. Anyone who hasn't chosen
  gets one automatically.
- A much bigger colour picker for friends not on the app: the whole spectrum in three shades, with
  room for about 80 friends. It used to run out after about ten.
- A payment QR code on both sides of settling up and in IOUs. Whoever is receiving the money comes
  first, and both codes are offered when both people have a UPI ID. With a friend who isn't on the
  app, your code is always there for them to scan.
- **(server)** Migrations `settlement_confirmation`, `group_expense_guests` (including an RLS
  change: only the member who added an expense can change their guests) and `profile_hue`,
  applied 2026-09-27 after owner review. Each suite also passed against live in rolled-back
  transactions (35/35, 23/23, 25/25).

### Fixed

- "Who pays whom" suggested payments between people who don't know each other (a friend not on
  the app paying a groupmate from another trip). It now only suggests a payment between two people
  who share a group; everything else settles with you.
- Deleting a synced split with a friend not on the app left its split row on the server. The
  split is now retracted with it.

## [1.3.0] - 2026-09-27

### Added

- Splits and IOUs with friends who aren't on the app now back up to your account and come back
  on a new phone. Only ones added from 1.3 on; older ones stay on the phone, as the account
  screen says. Private to you: nobody else can read them.
- Swipe left and right to move between Activity, Splits and Insights. Swiping a row to delete it
  still works and never changes tab.
- Two home-screen widgets in the app's look: a balance card (this month's spend, today, what's
  owed, Log expense / voice / Splits) and a one-line quick-add bar.
- A daily reminder at 9 PM if nothing of your own was logged that day. Change the time or turn
  it off in settings. It asks for notification permission only after your first entry, and
  tapping it opens a new expense.
- Choose what a new entry starts on: payment method (plain "UPI", now first in the list) and
  category (Food), in Categories & payment methods.
- Twice as many avatar emoji (64), all of them drawable on Android 7.
- **(server)** `local_splits`, `local_settlements` and `save_local_split`, applied 2026-09-24
  after review; used from this release.

### Changed

- AI features use gemini-3.6-flash. The old model (now listed by Google as legacy) took 15-18 s
  to read one typed line and often answered "high demand" instead. Quick-add now takes about
  6 s and a receipt about 8 s on a phone. If the main model is overloaded, the app tries a
  lighter one once before giving up.
- Every AI failure says what went wrong (offline, out of quota, a refused key, Google busy, a
  blocked answer) instead of a generic "couldn't". A typed or spoken line that the AI couldn't
  read still fills in from the phone's own guess, marked as a guess.
- A redesigned, animated sign-in screen (motion is reduced when the phone asks for less).
- UPI: the "Pay in a UPI app" button appears only when you're the one paying, and always carries
  the amount you're recording. "Any amount" now changes only the QR code.

### Fixed

- Anything running with the app in the background waited for the app to be opened again:
  background sync did nothing, a widget never filled in, and a reminder would have arrived the
  next time the app was opened. The sign-in library marks the session as "starting up" whenever
  the app is backgrounded; background work no longer waits on that.
- Widgets now refresh after every sync, so changes from groupmates and restores reach them.
- The search box's placeholder sat below where you type.
- The "pay" button under your own QR code (when a friend pays you, and in UPI settings) opened a
  payment to yourself.
- New subscriptions started on the first payment method in the list rather than your default.

### Security

- **(server)** Those two tables revoke every privilege from `anon` and `authenticated` before
  granting back select, insert and update. The reviewed draft only granted, which under this
  project's default privileges would have left TRUNCATE (which ignores RLS) available to every
  signed-in user. Caught on the harness before it reached live.

## [1.2.0] - 2026-09-24

### Fixed

- Release builds couldn't sign in with Google at all: Google refuses any build whose signing
  certificate isn't registered as an Android OAuth client, and only the debug certificate was.
  That is set up in Google Cloud, not in the app (docs/distribution.md, step 4b), and fixes every
  copy already installed once done. The app used to report this as "No account picked"; it now
  says Google didn't accept this copy of the app.
- Pressing Back on the account picker was taken for Google's own cooldown and told the person
  the picker had never opened. Found on the release build before publishing.

- No sign-in attempt ends in silence any more. Every way Google can decline (a person closing the
  picker, Google holding the picker back after repeated dismissals, or Google refusing the app)
  now leaves a specific message, and a cancel that arrives faster than anyone could react is
  retried once with Google's own sign-in dialog. The cooldown case is handled but was never
  reproduced: about 17 dismissals on an emulator didn't trigger it.
- A group's sync could stop for every member if any expense in it had a share naming a friend
  with no account. The server has allowed such shares since the 2026-09-20 migration, but the app
  still required every share to have a profile and failed to decode the whole page.

### Changed

- No sign-in attempt ends with the screen unchanged. Dismissing the account picker yourself now
  leaves a one-line note instead of nothing, reversing the original "a dismissed picker shows
  nothing" rule, because an unchanged screen after a tap reads as a broken button.
- A share for a friend with no account now comes back as that friend on the phone of whoever
  added the expense (found by name, or re-created after a reinstall). On anyone else's phone it
  is left out rather than adding a stranger to their friends list.
- Cancelling a subscription asks first; it can't be undone.
- Save stays disabled, and says why, while an exact or percentage split doesn't add up or has
  nobody in it. An even three-way percentage split no longer reads as 99.9%.
- Typed quick-add says when the AI couldn't be reached and the details are a best guess.
- The account sheet says exactly what backs up to your account, and that splits and IOUs with
  friends who aren't on the app stay on the phone.
- A friend's history shows both figures to the paisa ("You paid ₹150.50 · their share ₹75.25")
  in the same words as the group feed.
- New entries default to your first visible category and payment method, not a hidden one.
- Insights' daily chart has its own day axis.
- `PRD.md` is back to being the spec: vision, architecture and decisions. What changed and when
  now lives here.

### Built, switched off

- The app side of that sync, built and tested but switched off (`LocalFriendSync.ENABLED`) until
  the migration is applied: new personal splits and IOUs with friends who have no account go up
  with their split in one call, come back by name on a reinstall, and never take history with
  them. Splits and IOUs recorded before it is switched on stay on the phone, even when edited.

## [1.1.0] - 2026-09-20

### Added

- Local backup and restore as a JSON file (format v2), for the part of the ledger the server
  cannot hold: every expense, split and IOU involving a friend with no account, in either
  direction, with each entry's original date and logged-at time, plus custom categories and
  payment methods.
- A group's settlements and IOUs now appear in its activity feed, interleaved with its expenses.
- **(server)** `split_shares.local_name`: a share can name a friend with no account. Only the
  transaction's owner may write one; reads are unchanged. Applied after review, 12/12 against
  live.

### Fixed

- Backups reported success while leaving out every split and IOU. The splits were gathered and
  never written to the file, settings were never gathered at all, and the rule for what needed
  backing up was narrower than what actually never syncs.
- CSV export left out every settlement, including from the range named "Everything".
- A group sheet showed balances that counted settlements it never listed.

### Security

- **(server)** Two migrations that were live but missing from the repository
  (`add_upi_id_to_profiles`, `fix_upi_id_format_constraint`) are now committed, reconstructed
  from the live definitions, so the local test harness tests the schema production has.

## [1.0.0] - 2026-09-19

The first release: the AI Studio app, moved onto a signed-in cloud ledger, made safe to share,
and redesigned.

### Added

- Google Sign-In (Credential Manager, nonce-protected ID token), with offline-first sessions: an
  expired token with no network keeps you signed in.
- A separate on-device ledger per account.
- Cloud sync for personal expenses: batched, idempotent, last write to reach the server wins, a
  change still queued on the phone beats a pulled one, and a row the server rejects is marked
  failed instead of blocking the queue. Periodic and on-change sync; Realtime rings for a pull.
- Shared groups: invite codes and `kharchapaani://join/CODE` links that preview before joining,
  archive and restore, leave, and group expenses and settlements synced to every member.
- The payer of a groupmate's expense can edit and delete it; only the adder can change who paid.
- A UPI ID on your profile, shared with groupmates, with QR codes and a `upi://` fallback when
  settling up.
- Profile editing (name and emoji), custom categories and payment methods, subscriptions that
  post each charge on its date, Insights by month with charts, CSV export by date range, and a
  rebuilt home-screen widget.
- An AI prompt set for insights with a daily cap, and statement scanning.
- **(server)** The core schema with row-level security on every table, groups and invites,
  soft deletes with server-owned `updated_at`, and the Realtime publication.

### Changed

- The package and application ID became `com.vaibhav.kharchapaani` (from the AI Studio
  placeholder), which is why this installs as a new app beside any AI Studio build.
- A full design pass: bundled fonts, a measured-contrast palette, button tiers, sheets in place of
  dialogs, glass snackbars, and "Spent this month" as the home figure.
- Room schema changes are real migrations; the destructive fallback that wiped the ledger on any
  schema change is gone.
- Release builds are signed, minified, versioned, and published through a manual GitHub Releases
  workflow; the Gemini key is restricted to this app's signature.

### Fixed

- Among many found by testing and audit: deleting a friend left expenses marked as split; an exact
  split share of ₹12.50 was saved as ₹125; a refused delete stalled sync forever; archived groups
  still accepted expenses; settle-up, IOUs and group-less splits did not sync; a failed statement
  parse showed eight fake transactions; the widget's Speak button did not start voice capture; the
  UPI ID was invisible to groupmates because the group query never asked for it; AI answers were
  cut off mid-sentence because the output budget included the model's thinking; and the app
  crashed on every launch through an initialisation-order bug that 447 passing tests never saw.

### Security

- **(server)** Closed four RLS gaps before any group was shared: joining any group by UUID,
  profiles readable by everyone, shares visible to too few members, and writes that could
  reference non-members. Hard deletes and `TRUNCATE` revoked from client roles.
- Every `INSERT OR REPLACE` on a table with children switched to abort, after reproducing that it
  silently deleted splits, shares and members through cascades.
- The file provider narrowed to exported files only.

[Unreleased]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.5.0...HEAD
[1.5.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/vaibhavcodesx/kharcha-paani/releases/tag/v1.0.0
