# v1.4 design: mixed groups, settlement confirmation, one colour per person

Status: **decided (owner, 2026-09-27); migrations staged, awaiting final review before live.**
Three migrations are staged in `supabase/staged/` with harness tests. Each changes RLS or a
server-enforced write rule, so applying any of them to live is a hard stop until approved.

Owner decisions this reflects:

1. Guests: a local friend's share in a group expense is covered by whoever hosts (adds) it.
2. Settlement confirmation: never auto-confirmed. Pending indefinitely, with escalating reminders
   (day 3, then repeating); the payer can retract or cancel a stuck or disputed one.
3. Colours: one persistent colour per person, everywhere, not per group.
4. "Who pays whom" only ever suggests a payment between two people who share at least one group.

Harness results (PGlite, every applied migration plus all three staged files, 2026-09-27):

| Suite | Result |
|---|---|
| `rls_settlement_confirmation_test.sql` (new) | 35/35 |
| `rls_group_guests_test.sql` (new) | 23/23 |
| `rls_profile_hue_test.sql` (new) | 25/25 |
| `rls_groups_test.sql` | 100/100 |
| `rls_leave_and_immutability_test.sql` | 52/52 (check 16 updated: see item 10) |
| `rls_owner_private_local_ledger_test.sql` | 33/33 |
| `rls_payer_edit_test.sql` | 28/28 |
| `rls_local_participant_shares_test.sql` | 12/12 |
| `profile_avatar_emoji_test.sql` | 12/12 |
| `group_sync_columns_test.sql` | 10/10 |

---

## 9. Groups with friends who aren't on the app

**Decision: guests belong to their host.** A friend without an account, in a group expense, is a
guest of the member who added the expense.

- The host covers their guests' shares within the group. Groupmates settle only with members, so
  nobody else deals with someone who can't sign in, confirm a payment or be notified.
- The guest's debt to the host comes from the guest's line on the expense. On the host's phone
  that line is linked to the friend, so "Ravi owes you ₹225" appears under Friends. After a
  reinstall the link is found again by name. Groupmates see only the name on the expense, never
  Ravi's balance.
- Everyone in the group sees the guest's line on the expense ("Ravi · Lal's guest · ₹225"), so the
  total visibly adds up.
- Only the host adds, changes or removes guests. The payer can still edit a groupmate's expense
  (20260918073251), but must send the guests back unchanged.

### Staged: `20260926000100_group_expense_guests.sql` (unchanged by the decisions)

- `save_group_expense` accepts `local_name` shares. Each share names exactly one member or one
  guest; shares still add up to the amount exactly; a guest change by anyone but the owner is
  refused (42501); a changed guest share is retired and re-inserted, never updated in place.
- **RLS change:** `split_shares_update` lets only the expense's owner touch a guest share (the
  harness found the payer could otherwise soft-delete one directly). No guest shares exist in
  groups on live, so nothing existing changes.
- Fixes a latent bug: `profile_id not in (select ...)` is null once any share has no profile_id,
  so removing a member alongside a guest would have left their share live.

### App

- A group expense's split picker offers the group's members plus your friends who aren't on the
  app. Picking one makes them your guest.
- On every phone a guest's share is stored under the host, with the guest's name. Every group
  balance and "who owes whom" therefore counts it as the host's to cover.
- Spending totals count only the host's own share: covering a friend is lending, not spending.
- A groupmate editing the host's expense (the payer) can change the details but not the split
  or the amount while guests are in it. The server needs the guests sent back unchanged, and the
  editor says why.

---

## 10. Settlement confirmation between registered users

**Decision: never auto-confirmed.** A settle-up or IOU between two members is pending until the
other party answers it.

| State | Balances | Recorder sees | Other party sees |
|---|---|---|---|
| **pending** | not moved; "₹500 waiting for Rohan" beside the balance | "Waiting for Rohan", with Withdraw | "Priya says she paid you ₹500", with Confirm and "That's not right" |
| **confirmed** | moved | as today | as today |
| **rejected** | not moved | "Rohan says this didn't happen: *note*", with Clear | "You said this didn't happen" |
| **withdrawn** | not moved | gone | gone |

### Reminders (escalating, never auto-confirming)

- On the phone of the person who has to answer: a notification on day 3, then every 3 days after
  (6, 9, 12…), for as long as it stays pending. The wording escalates from "Priya says she paid you
  ₹500. Confirm?" to "Still waiting on you: ₹500 from Priya, 9 days now."
- On the recorder's phone: from day 7 the pending card says how long it has waited and offers
  **Withdraw**.
- Computed from the settlement's `created_at`, so every phone of the same person agrees. No server
  job, no push service: the daily worker (the one behind the 9 PM reminder) checks. FCM can come
  later without a data change.

### Retract and cancel

- **The payer recorded it** ("I paid Rohan"): they can withdraw it any time while it's pending, and
  clear it away if Rohan rejects it.
- **The payee recorded it** ("Rohan paid me"): Rohan, the payer, answers it. "That's not right"
  rejects it with an optional note, and the recorder can clear it or record a corrected one.
- The person who has to answer can't delete it: a disagreement stays visible instead of vanishing.
- A confirmed settlement can still be retracted by either party, as today.

### Staged: `20260926000000_settlement_confirmation.sql` (unchanged by the decisions)

It already has no auto-confirm, no expiry, recorder-only withdraw and clear, and answering only by
the other party. The reminders need nothing on the server. Five new columns: `status`,
`recorded_by`, `responded_by`, `responded_at`, `response_note`. There's one new RPC,
`respond_to_settlement`. Every existing row becomes `confirmed`.

**Test update in this bundle:** `rls_leave_and_immutability_test.sql` check 16 recorded a fresh
settlement and had the other party delete it. Now the other party confirms it first, then retracts
it, which is the rule this migration introduces.

### App

- Room migration for the five columns. Balance maths counts only confirmed settlements.
- Distinct feed states, a prompt card for the person who has to answer, and Withdraw and Clear
  for the recorder.
- The reminder notifications above.

### Rollout order (applies to all three migrations)

A phone still on 1.3 treats every settlement as final and can't answer one, and can't show
guests or chosen colours. The migrations and 1.4.0 should go out together. Apply the migrations,
then publish 1.4.0 the same day, and ask the group (you and your testers) to update. Nothing on
live breaks for a 1.3 phone in between: it just shows pending settlements as settled until it
updates.

---

## 11. One colour per person

**Decision: one persistent colour per person, everywhere.** Everyone sees Priya in the same colour
on every screen and every phone.

- Stored on the profile as a hue, `profiles.hue` (0-359, OKLCH degrees). The app fixes lightness
  and chroma (L 0.80, C 0.13, the band the current palette sits in), so every hue reads on the dark
  background with the same contrast.
- **Collision prevention:**
  - A hue within 25° of anyone you share a group with is refused by the server (23514).
  - The band 28–58° around the brand amber (43°) is refused, because it's kept for "You".
  - The picker greys these arcs out and snaps to the nearest free hue.
  - A lock per shared group stops two groupmates picking the same colour at the same moment.
- **Joining a group is never refused over colour.** If a joiner's hue is within 25° of a member's,
  the joiner's app asks them to pick again, and until then shows them as they are.
- **Null hue:** the app picks the first free one automatically after sign-in, so everyone ends
  up with a persistent colour without having to choose.
- **On your own phone you are still "You", in amber.** Everyone else sees your chosen colour.
- **Friends not on the app** keep a colour stored on your phone. The same picker checks it against
  the other people on your phone.

### Staged: `20260927000000_profile_hue.sql` (new)

One column with a range check, and a trigger that enforces the rules above. Who can write the
column is unchanged, because `profiles_update_own` already limits updates to your own row.

---

## "Who pays whom" (not a migration)

Suggestions now only pair two friends who share at least one group, local or shared. Anything
else settles directly with you. Fixed in `DebtSimplifier.simplify`, with tests for the 1.3 audit's
Hist/Shubham case.
