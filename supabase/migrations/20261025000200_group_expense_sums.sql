-- NOT APPLIED. Staged for owner review (plan T2.1, security audit H-2). Additive: one function and
-- three constraint triggers. Tested against live inside a transaction that rolled back:
-- supabase/tests/group_expense_sums_test.sql.
--
-- What it is for. Only save_group_expense checked that a group expense's shares add up to its amount.
-- RLS lets the payer (20260918073251) and the adder write split_shares, splits and transactions
-- directly, so one PATCH of split_shares.share_amount, or of transactions.amount, could leave an
-- expense whose shares don't add up, and that throws off every member's balance in the group.
--
-- Now the database itself refuses it, whoever writes: at commit, every group expense that was
-- touched must have its split total equal to its amount, and its live shares (not soft-deleted) sum
-- to the same, to the paisa. A refusal is SQLSTATE 23514 (check_violation), which the app already
-- treats as "the server refused this row" and marks FAILED.
--
-- Deferred to the end of the transaction, because save_group_expense rewrites the shares one
-- statement at a time and is only whole when it finishes. Soft-deleted expenses aren't checked, so
-- deleting one still works; an expense with no split (one person's, in a group) has nothing to add up.
--
-- Rollback: drop the three triggers, then drop functions private.check_group_expense_sums(uuid) and
-- private.check_group_expense_sums_trigger().

begin;

create or replace function private.check_group_expense_sums(p_transaction_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_amount numeric;
  v_deleted timestamptz;
  v_splits integer;
  v_split_total numeric;
  v_shares numeric;
begin
  select amount, deleted_at into v_amount, v_deleted from public.transactions where id = p_transaction_id;
  if not found or v_deleted is not null then
    return;
  end if;

  select count(*), max(total_amount) into v_splits, v_split_total from public.splits where transaction_id = p_transaction_id;
  if v_splits = 0 then
    return;
  end if;

  select coalesce(sum(ss.share_amount), 0) into v_shares
  from public.split_shares ss
  join public.splits s on s.id = ss.split_id
  where s.transaction_id = p_transaction_id and ss.deleted_at is null;

  if v_splits > 1 or v_split_total <> v_amount or v_shares <> v_amount then
    raise exception 'expense %: shares add up to % and the split to %, but the amount is %', p_transaction_id, v_shares, v_split_total, v_amount
      using errcode = '23514';
  end if;
end;
$$;

revoke execute on function private.check_group_expense_sums(uuid) from public, anon, authenticated;

create or replace function private.check_group_expense_sums_trigger()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_transaction_id uuid;
begin
  if tg_table_name = 'transactions' then
    v_transaction_id := coalesce(new.id, old.id);
  elsif tg_table_name = 'splits' then
    v_transaction_id := coalesce(new.transaction_id, old.transaction_id);
  else
    select transaction_id into v_transaction_id from public.splits where id = coalesce(new.split_id, old.split_id);
  end if;
  if v_transaction_id is not null then
    perform private.check_group_expense_sums(v_transaction_id);
  end if;
  return null;
end;
$$;

revoke execute on function private.check_group_expense_sums_trigger() from public, anon, authenticated;

create constraint trigger transactions_check_sums
  after insert or update of amount, deleted_at on public.transactions
  deferrable initially deferred
  for each row execute function private.check_group_expense_sums_trigger();

create constraint trigger splits_check_sums
  after insert or update or delete on public.splits
  deferrable initially deferred
  for each row execute function private.check_group_expense_sums_trigger();

create constraint trigger split_shares_check_sums
  after insert or update or delete on public.split_shares
  deferrable initially deferred
  for each row execute function private.check_group_expense_sums_trigger();

commit;
