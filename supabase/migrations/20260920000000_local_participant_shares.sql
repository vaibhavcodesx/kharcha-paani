-- STAGED, NOT APPLIED. Awaiting owner review, because it changes an RLS policy.
--
-- Lets a split name someone who has no account.
--
-- The problem. `split_shares.profile_id` is `uuid not null`, and the insert policy additionally
-- requires `private.is_member_of(group_id, profile_id)`. Between them there is no way to record
-- that a friend without an account owes a share, so today the app does not try: a split expense is
-- excluded from upload entirely (`SyncDao.pendingPersonalTransactions` filters `isSplit = 0`) and
-- lives only on the device. Lose the phone and the whole history goes with it.
--
-- The shape. A share is for exactly one of two things: a registered profile, or a name typed on
-- this account's phone. That is a check constraint, not a convention, so a row can never be both
-- or neither.
--
--   profile_id uuid null          -- a real person, as before
--   local_name text null          -- a friend of the owner's, who has no account
--
-- Why a name and not a synthetic id. A local friend exists only inside one account's ledger; there
-- is nobody else for an id to be meaningful to, and a name is what the owner's own device already
-- keys them by in a backup. Uniqueness is per split, which is all that is needed to stop the same
-- person appearing twice in one division.
--
-- What this deliberately does NOT do:
--
--   * It does not let a local participant *see* anything. They have no account; there is nothing to
--     authenticate and nothing to show. Reads stay exactly as they are.
--   * It does not widen who may read a split. `private.can_access_split` is untouched.
--   * It does not make local names visible to groupmates beyond the group they are already in,
--     because a share is only ever readable by people who can already read its split.
--
-- The one policy that changes, and why it has to. `split_shares_insert` currently ends with
-- `private.is_member_of(..., split_shares.profile_id)`. With a null profile_id that predicate is
-- null, so the row is refused. The replacement keeps the membership requirement for real profiles
-- and, for a local name, requires instead that the writer is the transaction's owner — i.e. you may
-- write a local participant into your own expense, and nobody may write one into anyone else's.
--
-- Rollback: drop the constraint, drop the column, restore the previous policy body. No data is
-- rewritten by this migration, so a rollback loses only rows written after it.

begin;

alter table public.split_shares
  add column if not exists local_name text;

-- Existing rows all have a profile; make the column nullable only after the constraint below is in
-- place so there is never a window where a share can be for nobody.
alter table public.split_shares
  alter column profile_id drop not null;

alter table public.split_shares
  drop constraint if exists split_shares_participant_is_one_of_two;

alter table public.split_shares
  add constraint split_shares_participant_is_one_of_two check (
    (profile_id is not null and local_name is null)
    or
    (profile_id is null and local_name is not null and length(btrim(local_name)) between 1 and 64)
  );

-- The same person cannot take two shares of one split. Two partial indexes rather than one
-- constraint, because null profile_id would otherwise defeat a composite unique.
create unique index if not exists split_shares_one_share_per_profile
  on public.split_shares (split_id, profile_id)
  where profile_id is not null and deleted_at is null;

create unique index if not exists split_shares_one_share_per_local_name
  on public.split_shares (split_id, lower(btrim(local_name)))
  where local_name is not null and deleted_at is null;

comment on column public.split_shares.local_name is
  'A participant with no account, named by the transaction owner. Exactly one of profile_id and '
  'local_name is set. Never used for authorisation: a local participant cannot sign in.';

-- The only policy this migration touches.
drop policy if exists split_shares_insert on public.split_shares;

create policy split_shares_insert on public.split_shares
  for insert to authenticated
  with check (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.can_edit_group_expense(s.transaction_id)
        and private.is_active_group_member(private.transaction_group_id(s.transaction_id))
        and (
          -- A share for a real person: unchanged — they must be in the group.
          (
            split_shares.profile_id is not null
            and private.is_member_of(private.transaction_group_id(s.transaction_id), split_shares.profile_id)
          )
          or
          -- A share for someone with no account: only the expense's own owner may add one, so a
          -- local name can never be written into somebody else's expense.
          (
            split_shares.local_name is not null
            and exists (
              select 1 from public.transactions t
              where t.id = s.transaction_id
                and t.owner_id = (select auth.uid())
            )
          )
        )
    )
  );

commit;
