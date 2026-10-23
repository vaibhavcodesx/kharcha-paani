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


### Changed

- A friend's balance and their history are now worked out from the same rows, so the history
  always adds up to the balance. No figure moves: a test pins every friend's balance, both its
  totals and their history on a ledger with every kind of row, checked against the old calculation.

### Fixed

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

[Unreleased]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.4.0...HEAD
[1.4.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/vaibhavcodesx/kharcha-paani/releases/tag/v1.0.0
