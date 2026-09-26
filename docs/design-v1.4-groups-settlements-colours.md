# v1.4 design: mixed groups, settlement confirmation, member colours

Status: **proposal, for owner review.** Nothing here is applied to live. Two migrations are staged
in `supabase/staged/` with harness tests; both change RLS or who-may-write rules, so both are hard
stops until approved. Item 11 has no migration yet: its server part is described, not written.

Harness results (PGlite, every applied migration plus both staged files, 2026-09-27):

| Suite | Result |
|---|---|
| `rls_settlement_confirmation_test.sql` (new) | 35/35 |
| `rls_group_guests_test.sql` (new) | 23/23 |
| `rls_groups_test.sql` | 100/100 |
| `rls_payer_edit_test.sql` | 28/28 |
| `rls_local_participant_shares_test.sql` | 12/12 |
| `group_sync_columns_test.sql` | 10/10 |
| `rls_owner_private_local_ledger_test.sql` | 33/33 |
| `rls_leave_and_immutability_test.sql` | 50/51: check 16 is the intended change in item 10 (see there) |

---

## 9. Groups with friends who aren't on the app

### What the schema already has

`split_shares.local_name` (20260920000000) lets a share name a person with no account, and the
insert policy already limits that to the expense's owner. What's missing is the write path:
`save_group_expense` ignores `local_name`, so the app has never sent one.

### Proposal: guests belong to a host

A friend without an account, in a group expense, is a **guest of the member who added them**.

- The host's share of the group expense includes their guests. Groupmates settle only with
  members. Ravi's ₹225 is part of what Alice owes (or is owed) in the group, so nobody else has to
  deal with someone who can't sign in, confirm a payment or be notified.
- The guest's debt to the host lives in the host's own ledger, as an ordinary local-friend split.
  Since 1.3 that syncs owner-private, so the host sees "Ravi owes you ₹225" on every phone and
  groupmates never see Ravi's balance, only his name on the expense.
- Everyone in the group sees the guest's line on the expense ("Ravi (Alice's guest) · ₹225"), so
  the total visibly adds up.
- Only the host adds, changes or removes their guests. The payer's edit rights (20260918073251)
  still work, but they must send the guests back unchanged.

**Rejected alternative: guests as full group participants anyone can settle with.** Their balance
would hang in the group with no one able to confirm it, and when they later join, merging a
name-only participant into a real account is ambiguous (two "Ravi"s). "Claim this guest" can come
later as an explicit, host-approved step.

### Staged: `20260926000100_group_expense_guests.sql`

- A new `save_group_expense` body that accepts `local_name` shares. Each share names exactly one
  member or one guest, and shares still add up to the amount exactly.
- A guest change by anyone but the owner is refused (42501).
- A changed guest share is retired and re-inserted, never updated in place.
- **RLS change:** `split_shares_update` now lets only the expense's owner touch a guest share. The
  harness found that the payer could soft-delete a guest share with a direct UPDATE, getting around
  the function. There are no guest shares in groups on live today, so nothing existing changes.
- Fixes a latent bug in the current body. `profile_id not in (select ...)` is null as soon as any
  share in the payload has no profile_id, so removing a member would silently leave their share
  live. That can't happen today, because the app never sends such a payload, but it would have
  broken with guests.

### App work (v1.4)

- Split picker in a group: the groupmates plus "Add a friend who isn't on the app" (your local
  friends).
- Balances: the guest's share is counted as the host's in the group's debt simplification. The
  host's ledger gets the matching local split.
- Room: nothing new. Local friends and share rows already exist.

---

## 10. Settlement confirmation between registered users

### The problem

A settle-up or IOU between two members is final the moment either one taps it, and it moves both
balances straight away. A mistyped ₹5,000 for ₹500 goes straight into the other person's balance.

### States

| State | Balances | Recorder sees | Other party sees |
|---|---|---|---|
| **pending** | not moved yet; shown as "₹500 waiting for Rohan" beside the balance | "Waiting for Rohan", with Remind and Withdraw | a prompt card: "Priya says she paid you ₹500", with Confirm and "That's not right" |
| **confirmed** | moved | settled, as today | settled, as today |
| **rejected** | not moved | "Rohan says this didn't happen: *note*", with Clear | "You said this didn't happen" |
| **withdrawn** | not moved | gone (soft-deleted) | gone |

Pending money doesn't move balances. The alternative, moving them provisionally and undoing on
rejection, makes balances jump back and forth on both phones, and the whole point is that one
person can't change the other's balance alone.

Each state gets its own look in the activity feed: pending has a dashed outline and a clock,
rejected is struck through with the note, confirmed looks like today's settlement.

### Edge cases

- **Never responds.** There's no auto-confirm: the server would need a scheduled job, and silence
  isn't agreement. After 3 days the recorder's card offers **Remind** (a fresh notification on the
  other phone at its next sync). They can **Withdraw** at any time while it's pending. It stays
  visible as pending indefinitely, and the balance line keeps saying "₹500 waiting for Rohan".
- **Dispute.** "That's not right" rejects the settlement with an optional note (up to 200
  characters). The recorder can clear it away or record a corrected one. The other party can't
  delete a pending one to make it vanish: they answer it instead, so a disagreement stays visible.
- **Both record the same payment.** Each one is a pending settlement awaiting the other. The
  prompt card shows "You also recorded ₹500 to Priya on 12 Sep", and confirming one offers to
  withdraw your own.
- **Older settlements.** Every existing row becomes `confirmed` with no recorder, and none of the
  new rules apply to it. Either party can still retract it, as today.
- **Local friends** (no account): unchanged. There's no one to confirm, so they stay immediate.
- **Offline.** Recording queues as today. Answering is a call to `respond_to_settlement`, queued
  like any other write and retried. The card shows "Confirming…" until it syncs.

### Notification

There's no push server (no FCM) and this doesn't add one. The sync worker already runs after every
change and periodically. When a pull brings in a settlement pending **for me** that I haven't been
notified about, it posts a local notification ("Priya says she paid you ₹500 · Confirm") on its own
channel. Tapping it opens the prompt card. Delay is the sync interval, usually minutes. FCM can
come later without changing the data model.

### Staged: `20260926000000_settlement_confirmation.sql`

Five columns on `settlements`: `status`, `recorded_by`, `responded_by`, `responded_at`,
`response_note`. The server enforces all the rules:

- `recorded_by` comes from the caller's session, and a new row is always `pending`, whatever the
  client sent.
- Only the other party can answer, once: pending → confirmed or rejected.
- While it's pending, only the recorder can withdraw it. Only the recorder can clear a rejected one.
- The existing immutability of the amount, the parties, the date and the note stays as it is.
- New RPC `respond_to_settlement(id, confirm, note)`, SECURITY INVOKER. It returns the same
  "not found" error for someone who isn't a party as for an id that doesn't exist, so it can't be
  used to probe for ids.
- No change to who can read or insert settlements.

**Intended behaviour change:** `rls_leave_and_immutability_test.sql` check 16 ("the other party
can still retract it") fails with this migration, because that check records a fresh (now pending)
settlement and has the other party delete it. The new rule is that they answer instead. When this
is applied, that check is updated to confirm first and then retract, matching check 30 of the new
suite.

### App work (v1.4)

- Room migration for the five columns. Balance maths skips anything that isn't confirmed. Feed
  states, prompt card, Remind and Withdraw, and the sync-worker notification.
- Tests: the balance ignores pending, a real two-account flow on the release build (record on one
  phone, confirm on the other, both balances move only then).

---

## 11. Colour picker and members choosing their own colour

### Today

`PersonColours` has eight fixed hues about 40° apart, with brand amber reserved for "You". Each
phone assigns colours to the people on it and removes duplicates locally. A groupmate's colour is
therefore per phone: Priya can be green on my phone and blue on Rohan's. Groups have a
`color_hex`, but members don't.

### Proposal

**Spectrum picker.** A hue ring or slider instead of eight swatches. The picker fixes lightness
and chroma in OKLCH (L≈0.80, C≈0.13, the band the current palette sits in), so every pick reads
on the dark background with the same contrast, and only the hue moves. A live preview shows the
avatar, a member chip and a donut slice.

**Collision prevention by distance, not equality.** Two hues within 25° look alike on a donut.
The picker greys out the arcs held by others in the same context (±25° around each taken hue, plus
the reserved band around brand amber, 15–45°) and snaps to the nearest free hue. A context is a
group for group members, or the phone for local friends. With more than about 12 people there's
no room left, so the band shrinks to ±12° and a "colours are getting close" hint appears.

**Members choose their own colour, per group.** A member's colour lives on their membership, so
it's the same on every groupmate's phone:

- `group_members.hue smallint null`, 0–359. Null means "not chosen": the app derives one exactly
  as today.
- `set_my_group_hue(group_id, hue)` RPC. It updates only the caller's own row and takes a
  transaction-level advisory lock on the group, so two members picking at once can't both get the
  same arc. It refuses (23514) a hue within 25° of another active member's, or inside the reserved
  band.
- Viewing your own group, you're still amber ("You"). Your chosen hue is what others see.
- Local friends: the same picker, stored only on the phone (`PersonEntity.avatarColor`), with
  collisions checked against the other people on that phone.

This needs **a new RLS path**: members updating their own `group_members` row, which today only
group admins or leave-group flows touch. So its migration and harness test come for review before
any app work. Not written yet: I'd like a yes on the model (per-group, hue only, ±25°) first.

### Open questions for the owner

1. Item 9: is "the host covers their guests" the right model, versus every member settling with
   the guest directly?
2. Item 10: no auto-confirm, remind after 3 days. OK, or should unanswered settlements confirm
   themselves after, say, 14 days?
3. Item 11: one colour per group, or one colour per person everywhere (a `profiles.hue`)?
   Per-person is simpler, but collisions can only be avoided within a group, not across all of
   someone's groups.
