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

- Signing back in after signing out could do nothing at all: no account picker, no message, and
  no request reaching the server. Google's Credential Manager holds its bottom-sheet picker back
  for a while after it has been dismissed a few times and reports that as "cancelled", which the
  app treated as the person changing their mind. A cancel that arrives faster than anyone could
  react, or carries Google's cooldown message, now retries with Google's own sign-in dialog, which
  is not under that cooldown, and says so plainly if that is refused too.
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

### Staged, not applied

- **(server)** `supabase/staged/20260923000000_owner_private_local_ledger.sql`: owner-private
  `local_splits` and `local_settlements` tables and an atomic `save_local_split` function, so a
  personal split or IOU with a friend who has no account can be kept in the cloud. New tables
  with new RLS policies, so it waits for owner review. 28/28 on the local harness. See
  [`docs/local-friend-sync-v2.md`](docs/local-friend-sync-v2.md).
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

[Unreleased]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/vaibhavcodesx/kharcha-paani/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/vaibhavcodesx/kharcha-paani/releases/tag/v1.0.0
