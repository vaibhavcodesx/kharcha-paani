-- Backlog item 11 (owner, 2026-09-15): the person who PAID for a group expense can edit and delete it,
-- not only the person who added it. This reverses step 4 decision 2 ("only the person who added a
-- group expense can change it") for the payer only.
--
-- Rules:
-- 1. A group expense can be edited or soft-deleted by whoever added it (owner_id) or whoever its split
--    says paid (splits.paid_by). Personal expenses are unchanged: owner only.
-- 2. The payer can't make someone else the payer; only the adder can reassign that. So a payer's
--    rights can't be handed around, and they end when the adder names a different payer.
-- 3. Nobody, adder included, can change owner_id: an expense stays with the person who added it.
--    Previously the update policy's check stopped this; with a second path through the policy it's
--    now a trigger (23514, like the other immutability triggers, so the app marks the row failed).
-- 4. Everything else stays: writes need current membership of a non-archived group, payers and
--    shares must be members, hard deletes stay revoked.
--
-- Unchanged: select policies, insert policy on transactions, save_group_expense (security invoker,
-- so it runs under these policies).

-- ============================================================
-- Helper
-- ============================================================

-- The caller added this expense, or it's a group expense whose split says the caller paid.
create or replace function private.can_edit_group_expense(p_transaction_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.transactions t
    left join public.splits s on s.transaction_id = t.id
    where t.id = p_transaction_id
      and (
        t.owner_id = (select auth.uid())
        or (t.group_id is not null and s.paid_by = (select auth.uid()))
      )
  );
$$;

revoke execute on function private.can_edit_group_expense(uuid) from public, anon;
grant execute on function private.can_edit_group_expense(uuid) to authenticated;

-- ============================================================
-- transactions
-- ============================================================

drop policy transactions_update_own on public.transactions;

-- Soft delete is an update of deleted_at, so this covers deleting too.
create policy transactions_update_owner_or_payer
  on public.transactions for update
  to authenticated
  using (owner_id = (select auth.uid()) or private.can_edit_group_expense(id))
  with check (
    (owner_id = (select auth.uid()) or private.can_edit_group_expense(id))
    and (group_id is null or private.is_active_group_member(group_id))
  );

create or replace function private.lock_transaction_owner()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.owner_id is distinct from old.owner_id then
    raise exception 'an expense stays with the person who added it'
      using errcode = '23514';
  end if;
  return new;
end;
$$;

-- Trigger-only function: firing a trigger does not need EXECUTE.
revoke execute on function private.lock_transaction_owner() from public, anon, authenticated;

create trigger transactions_lock_owner
  before update on public.transactions
  for each row execute function private.lock_transaction_owner();

-- ============================================================
-- splits
-- ============================================================

drop policy splits_insert on public.splits;
drop policy splits_update on public.splits;

-- save_group_expense upserts the split, and an upsert is checked against the insert policy even when
-- it updates, so the payer needs a path here too. can_edit_group_expense needs an existing split
-- paid by the caller, so a payer can't attach a split to an expense that has none.
create policy splits_insert
  on public.splits for insert
  to authenticated
  with check (
    private.can_edit_group_expense(transaction_id)
    and (private.owns_transaction(transaction_id) or paid_by = (select auth.uid()))
    and private.is_active_group_member(private.transaction_group_id(transaction_id))
    and private.is_member_of(private.transaction_group_id(transaction_id), paid_by)
  );

create policy splits_update
  on public.splits for update
  to authenticated
  using (private.can_edit_group_expense(transaction_id))
  with check (
    private.can_edit_group_expense(transaction_id)
    -- Rule 2: only the adder can name a different payer.
    and (private.owns_transaction(transaction_id) or paid_by = (select auth.uid()))
    and private.is_active_group_member(private.transaction_group_id(transaction_id))
    and private.is_member_of(private.transaction_group_id(transaction_id), paid_by)
  );

-- ============================================================
-- split_shares
-- ============================================================

drop policy split_shares_insert on public.split_shares;
drop policy split_shares_update on public.split_shares;

create policy split_shares_insert
  on public.split_shares for insert
  to authenticated
  with check (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.can_edit_group_expense(s.transaction_id)
        and private.is_active_group_member(private.transaction_group_id(s.transaction_id))
        and private.is_member_of(private.transaction_group_id(s.transaction_id), split_shares.profile_id)
    )
  );

create policy split_shares_update
  on public.split_shares for update
  to authenticated
  using (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.can_edit_group_expense(s.transaction_id)
    )
  )
  with check (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.can_edit_group_expense(s.transaction_id)
        and private.is_active_group_member(private.transaction_group_id(s.transaction_id))
        and (split_shares.deleted_at is not null or private.is_member_of(private.transaction_group_id(s.transaction_id), split_shares.profile_id))
    )
  );
