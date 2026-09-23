# Local-friend splits and IOUs in the cloud (v2)

**Status (2026-09-24): server half staged, not applied, waiting for owner review. Client half built
and tested behind `LocalFriendSync.ENABLED = false`.**

- Migration: `supabase/staged/20260923000000_owner_private_local_ledger.sql`. It's kept out of
  `supabase/migrations/` so nothing can apply it by accident.
- Test: `supabase/tests/rls_owner_private_local_ledger_test.sql`, **28/28 on the local harness**.
  Each refusal was checked to fail for the intended reason (constraint `23514` vs RLS `42501`).
- The existing suites still pass with it in place: `rls_local_participant_shares_test` 12/12,
  `rls_groups_test` 100/100, `rls_payer_edit_test` 28/28, `rls_leave_and_immutability_test` 51/51.

## Why v1 was not enough

I got this wrong in the 2026-09-20 design, and I'm saying so up front.

`20260920000000_local_participant_shares` gave `split_shares` a `local_name` and is live. Its
12 tests all used a **group** expense, and so did the design. But the common case is not a
group expense. It's "Rohan and I had dinner, I paid": a personal expense split with a friend
who has no account. That case still can't be stored, because the share hangs off a `splits` row:

```sql
-- splits_insert, live
private.is_active_group_member(private.transaction_group_id(transaction_id))  -- null group → false
and private.is_member_of(private.transaction_group_id(transaction_id), paid_by) -- a member's profile
```

A personal expense has no group, so it can't have a split at all. `paid_by` is also a non-null
profile, so "Rohan paid" can't be written even inside a group. `save_group_expense`, the only
path the app uses to write a split, ignores `local_name`. Its removed-share logic
(`profile_id not in (...)`) never matches a null profile either.

So the live column is useful in exactly one place: an account-holder's shared-group expense with
an extra local participant. The app never builds that case. Shared groups only list their
members, and every member has an account.

I found this on 2026-09-23 while starting the client work, by reading the live policies before
writing any code.

## Why not widen the group policies instead

It could be done: make `splits.paid_by` nullable, add `paid_by_local_name`, and branch
`splits_insert`, `splits_update` and `split_shares_update` on `group_id is null`. That means
editing the most heavily tested policies in the schema (100 checks in `rls_groups_test`) for data
that no groupmate should ever see. Every new branch in a shared-data policy is one more way
for someone to read something they shouldn't.

## The design

Two tables that belong to exactly one account. **Every policy on both is the same sentence:
the row is yours.**

| Table | One row per | Payer / parties |
|---|---|---|
| `local_splits` | split personal expense (`transaction_id` unique) | `paid_by_local_name`, null = the owner |
| `local_settlements` | payment or IOU | `from_local_name` / `to_local_name`, null = the owner |

- **Shares are `jsonb`** on the split row: `[{"local_name": null | text, "share_amount": n,
  "percentage": n | null}]`. One row per split means one upsert and one pull, with no child
  table to keep in step.
- **A check constraint validates the shares**, not the client:
  - it must be a non-empty array of objects;
  - no negative amounts;
  - names are 1–64 characters after trimming;
  - nobody appears twice (case-insensitive, trimmed, the same rule as the live unique index);
  - the shares sum exactly to the total.

  A malformed split restored onto a phone would be a wrong balance with no way to tell.
- **`save_local_split(transaction, split)`** writes the personal expense and its split in one
  statement. It runs as the caller (SECURITY INVOKER), so RLS applies to every row it touches.
  Neither half can reach the server without the other. That matters: an expense that
  arrived without its split would restore as a plain full-amount expense, which is worse than
  not syncing it at all.
- **A trigger bumps the parent transaction's `updated_at`** when its split changes, because other
  devices pull personal expenses by that clock with the split embedded.
- Soft deletes only (`deleted_at`), no delete policy, and `anon` has no grants. This matches every
  other synced table.

### What changes about who can do what

| | Before | After |
|---|---|---|
| Groupmates | read shared data | **unchanged**, and they can't see either new table |
| Owner | writes own personal expenses | also writes splits and IOUs on **their own personal** expenses |
| Anyone else | — | refused (test 19–25) |
| Existing tables, policies, functions | — | **untouched** |

## The guardrail: only new records sync

Owner rule, 2026-09-23: splits and IOUs already on people's phones must not start uploading
by themselves. Only records made after the feature is switched on sync.

The Room schema already carries this. Every row has a `syncState`, and every existing local
split and local IOU is `LOCAL_ONLY`. The sync engines only ever push `PENDING` rows.

1. **New** personal splits and local IOUs are saved `PENDING` once the feature flag is on. Today
   they're saved `LOCAL_ONLY`.
2. **Editing a historical split keeps it `LOCAL_ONLY`.** An edit is not consent to upload, and
   "only new records" means new, not "anything touched since".
3. Nothing ever rewrites `LOCAL_ONLY` to `PENDING` in bulk. A test asserts that turning the flag
   on leaves every existing row's state unchanged.

**What the owner should know about forward-only.** On a phone that's in use, nothing
changes, because all the data is still there. The cost shows up **only on restore from the
cloud**: you get the new records but not the history. Say a later "Rohan paid you ₹500" settles a
split that was local-only. On a restored phone the payment appears without the debt, so the
balance reads backwards. The JSON backup covers this today. The opt-in below removes the gap.

### Later, not built: an explicit opt-in for history

The guardrail says no automatic upload. It doesn't say history can never be uploaded. When the
owner wants that, here's the design:

- A row in the account sheet: *"Back up older splits with friends who aren't on the app (N
  entries)."* It's shown only when N > 0, and it tells you how many entries and with whom.
- Tapping it opens a confirmation listing the names involved. It sets those rows to `PENDING`
  in one transaction and records `local_history_opt_in_at` in `sync_meta`, so the choice is
  auditable.
- It can't be triggered by sync, migration, update or restore. It only runs from that tap.

Not built this session, on purpose.

## The client half

**Built, off.** `data/sync/LocalLedgerRemote.kt` (flag, payloads, Supabase calls),
`data/sync/LocalLedgerSyncEngine.kt` (push, pull, and `LocalPeople`, the one name-matching helper),
the rules in `LedgerRepository.syncStateFor`, and `LocalLedgerSyncTest` (7 tests: a two-phone
round trip asserting the balance and both timestamps, the guardrail, the flag-off case, a groupmate
keeping a split local, paisa rounding, unsplitting, and retracting an IOU). Mutation-checked:
dropping the guardrail fails the history test; keying the split tombstone like the expense's own
fails the unsplit test.

It stays behind a single flag, off, until the migration is applied. With the flag off, the
app's requests are exactly what they are today: `LocalLedgerSyncEngine` is never constructed, and
it is the only code that names the new tables. Asking for a table that doesn't exist yet would
fail every sync.

1. **Shipped 2026-09-23 (no flag needed):** `RemoteShare` decodes `local_name` with a nullable
   `profile_id`. Before this, one such share would have failed every member's group pull.
2. **Push:** `SyncDao` gets a separate `pendingPersonalSplits()` query, and splits go up through
   `save_local_split`.

   `pendingPersonalTransactions` keeps `AND isSplit = 0`. This is a deliberate departure from
   the 2026-09-20 backlog note that said to drop that clause. Dropping it would send a split
   expense through the plain upsert, *without* its split: the server and every restore would
   then see ₹600 of your own spending instead of ₹300 plus Rohan owing ₹300. The clause stays
   as the guarantee that a split never travels without its shares.
3. **Un-splitting** (an edit, or a friend deleted) queues a split tombstone. The existing
   `pending_remote_deletes` table takes a new entity type, keyed `local_split:<expense id>` so the
   expense's own tombstone, which a later edit clears on purpose, can't take it with it.
4. **Pull:** `local_splits` is pulled by its own cursor, right after the personal expenses, so a
   split's expense is always here first; a split changed on its own moves its expense's clock
   too (the trigger). Names map back to local friends through `LocalPeople`, the one helper the
   group pull now also uses. A name this phone has never seen creates the friend. (The JSON
   restore still has its own matcher; folding it in is a small follow-up.)
5. **IOUs and settlements with local friends** push and pull through `local_settlements` the
   same way.
6. **Tests:** `LocalLedgerSyncTest`, as described above.

**Not covered, on purpose:** splits inside a *local* (unshared) group. They'd need the group
itself on the server, which is its own design. The JSON backup keeps covering them.

## To switch it on

1. Owner reviews and approves the staged migration.
2. Move it into `supabase/migrations/`, apply it, then run
   `rls_owner_private_local_ledger_test.sql` against live in its rolled-back transaction and
   confirm nothing is left behind. Check the advisors.
3. Flip the flag, release, and verify on a device: record a split with a local friend, confirm
   it's on the server, uninstall, reinstall, sign in, and check the balance matches.

## Running the test locally

```sh
# from a folder with @electric-sql/pglite installed
mkdir mig && cp supabase/migrations/*.sql supabase/staged/*.sql mig/
KP_MIGRATIONS_DIR=mig node supabase/tests/local/run.mjs supabase/tests/rls_owner_private_local_ledger_test.sql
```
