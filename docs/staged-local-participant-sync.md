# Splits with a friend who has no account

**Status: the schema is APPLIED to live (2026-09-20, after review).**

> **Superseded in part, 2026-09-23.** This design only covers a *group* expense: a personal
> expense can't have a `splits` row at all, so the common case still had nowhere to go. See
> [`local-friend-sync-v2.md`](local-friend-sync-v2.md) for why, and for the staged fix.

So the database can hold this today and nothing writes it yet. That is deliberate: turning it on
means `SyncDao.pendingPersonalTransactions` stops filtering `isSplit = 0`, which changes what
previously-local-only data begins syncing, and that wants its own session.

- Migration: `supabase/migrations/20260920000000_local_participant_shares.sql`
- Test: `supabase/tests/rls_local_participant_shares_test.sql` — **12/12 against the local harness**
- The other two suites still pass with it in place: `rls_groups_test` 100/100,
  `rls_payer_edit_test` 28/28

## The problem

A split expense never reaches the server at all. Not its shares — the whole transaction:

```sql
-- SyncDao.pendingPersonalTransactions
SELECT * FROM transactions WHERE syncState = 'PENDING'
  AND groupId IS NULL AND isSplit = 0
```

`isSplit = 0`. And the group path can't take it either: `split_shares.profile_id` is `uuid not
null`, and the insert policy requires `private.is_member_of(group_id, profile_id)`. A friend with
no account has no profile, so there is nowhere for their share to go, and the app doesn't try.

The local backup now covers this, but a backup is something you have to remember to take. This is
the fix that doesn't depend on remembering.

## The design

One column, one constraint, one policy.

```sql
profile_id uuid null   -- a real person, as before
local_name text null   -- a friend of the owner's, who has no account
```

A check constraint requires **exactly one** of the two, so a share can never be both or neither.
Two partial unique indexes stop the same person taking two shares of one split.

**Why a name rather than a synthetic id.** A local friend exists only inside one account's ledger.
There is nobody else for an id to be meaningful to, and the owner's own device already keys them by
name — it is what the backup format uses, for the same reason.

## What changes about who can do what

This is the part worth your attention, since it is why this is staged.

`split_shares_insert` ends today with `private.is_member_of(..., split_shares.profile_id)`. With a
null `profile_id` that predicate is null and the row is refused, so the policy has to change. The
replacement keeps the existing rule for real profiles and adds a second branch for local names:

| Writing a share for | Allowed when |
|---|---|
| A registered profile | Unchanged — they must be an active member of the group |
| A local name | Only if you own the transaction |

So you can write a local participant into **your own** expense and into nobody else's.

**Nothing about reading changes.** `private.can_access_split` is untouched. A local participant
cannot sign in, has nothing to authenticate, and is never shown anything — the name is a label
inside its owner's expense, visible to exactly the people who could already read that split.

## What the test proves

| | |
|---|---|
| The owner can record a share for a friend with no account | ✅ |
| It stores as a local name with no profile | ✅ |
| A share can't name both a profile and a local person | ✅ |
| A share can't be for nobody | ✅ refused by the policy *and* the constraint |
| A blank name isn't a name | ✅ |
| The same local person can't take two shares of one split | ✅ |
| **A groupmate can't add a local participant to an expense they don't own** | ✅ |
| **Someone outside the group can't either** | ✅ |
| …and still can't read the split at all | ✅ |
| A groupmate still sees the split, local participant included | ✅ |
| A share for a real profile still needs group membership | ✅ |
| A share for a profile outside the group is still refused | ✅ |

## What is left

1. ~~Apply `20260920000000_local_participant_shares.sql`~~ — done, 2026-09-20. 12/12 against live,
   rolled back cleanly, advisors clean.
2. The client work is **not** written yet and is the larger half: `RemoteShare` gains
   `local_name`; `GroupSyncEngine.buildGroupExpense` stops returning null when a sharer has no
   profile and sends the name instead; the pull side maps a `local_name` back to a local
   `PersonEntity`, creating one if this device has never seen it; and
   `SyncDao.pendingPersonalTransactions` drops `isSplit = 0` so split expenses upload at all.
3. That last change is the one to think hardest about — it makes previously local-only rows start
   syncing, so it wants its own pass over what happens to expenses already on people's phones.

## Rollback

Drop the constraint and the two indexes, drop the column, restore the previous policy body. No data
is rewritten by the migration, so a rollback loses only rows written after it.

---

## Separately: two migrations that were missing from this repo

While setting up the harness I found that `20260918210534_add_upi_id_to_profiles` and
`20260918210646_fix_upi_id_format_constraint` were applied to the live project last session
**through the MCP tool and never written to `supabase/migrations/`**.

The repository and production therefore disagreed. Anyone replaying the folder — including the
local RLS harness, including a rebuild from scratch — got a `profiles` table with no `upi_id`, and
so tested a schema production does not have.

Both files are now committed, reconstructed from the live definitions rather than from memory.
Nothing was applied to fix this; the live database already had them. The lesson is the obvious one:
a migration applied through the tool still has to land in the folder, or the folder stops being the
truth.
