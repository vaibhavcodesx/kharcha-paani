-- APPLIED to live on 2026-09-27 as version 20260927073436, after owner review and approval. Re-verified
-- against live in rolled-back transactions the same day (its suite passed in full).
--
-- Tested on the local harness: supabase/tests/rls_group_guests_test.sql.
-- Design: docs/design-v1.4-groups-settlements-colours.md, item 9.
--
-- What it is for. A group expense can already *hold* a share for a friend with no account
-- (split_shares.local_name, 20260920000000), but save_group_expense ignored local_name, so the app
-- never sent one. This version accepts them. A friend without an account in a group expense is a
-- *guest* of the member who added it (the transaction's owner): the host covers their guests, so
-- groupmates only ever settle with members.
--
-- The rules:
--
--   * Each share names exactly one of a member (profile_id) or a guest (local_name).
--   * Shares, guests included, must add up to the amount exactly (unchanged).
--   * Only the host (the expense's owner) can add, change or remove guests. The payer can still edit
--     a groupmate's expense (20260918073251), but must send the guests back exactly as they are.
--   * A changed guest share is replaced (old row soft-deleted, new row inserted), never updated:
--     split_shares_update only lets a guest share be soft-deleted, and this avoids changing it.
--
-- Also fixes a latent fault in the old body: `profile_id not in (select ... from p_shares)` is null
-- for every row as soon as one share in the payload has no profile_id, so no removed member was ever
-- soft-deleted. Members are now matched with `not exists`.
--
-- Rollback: re-run the function body from 20260914033124_groups_invites_rls.sql. Guest shares
-- written in the meantime stay in the table and are ignored by that body, as today.

begin;

create or replace function public.save_group_expense(p_transaction jsonb, p_split jsonb, p_shares jsonb)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_transaction_id uuid := (p_transaction ->> 'id')::uuid;
  v_group_id uuid := (p_transaction ->> 'group_id')::uuid;
  v_amount numeric := (p_transaction ->> 'amount')::numeric;
  v_me uuid := (select auth.uid());
  v_owner uuid;
  v_split_id uuid;
  v_share_total numeric;
  v_guests_now text;
  v_guests_sent text;
begin
  if v_group_id is null then
    raise exception 'a group expense needs group_id' using errcode = '23502';
  end if;
  if jsonb_typeof(p_shares) is distinct from 'array' or jsonb_array_length(p_shares) = 0 then
    raise exception 'a group expense needs at least one share' using errcode = '22023';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_shares) s
    where ((s ->> 'profile_id') is null) = (nullif(btrim(s ->> 'local_name'), '') is null)
  ) then
    raise exception 'each share names one member or one guest' using errcode = '22023';
  end if;

  -- Compared unrounded: 33.333 x 3 is rejected rather than stored as 33.33 x 3 = 99.99.
  select sum((s ->> 'share_amount')::numeric) into v_share_total
  from jsonb_array_elements(p_shares) s;
  if v_share_total <> v_amount or (p_split ->> 'total_amount')::numeric <> v_amount then
    raise exception 'shares (%) and split total (%) must equal the amount (%)',
      v_share_total, p_split ->> 'total_amount', v_amount
      using errcode = '23514';
  end if;

  insert into public.transactions
    (id, owner_id, type, amount, category, merchant, payment_method, date, note, group_id, created_at, deleted_at)
  values (
    v_transaction_id,
    v_me,
    p_transaction ->> 'type',
    v_amount,
    p_transaction ->> 'category',
    coalesce(p_transaction ->> 'merchant', ''),
    coalesce(p_transaction ->> 'payment_method', ''),
    (p_transaction ->> 'date')::timestamptz,
    coalesce(p_transaction ->> 'note', ''),
    v_group_id,
    coalesce((p_transaction ->> 'created_at')::timestamptz, now()),
    null
  )
  on conflict (id) do update set
    type = excluded.type,
    amount = excluded.amount,
    category = excluded.category,
    merchant = excluded.merchant,
    payment_method = excluded.payment_method,
    date = excluded.date,
    note = excluded.note,
    group_id = excluded.group_id,
    deleted_at = null;

  select t.owner_id into v_owner from public.transactions t where t.id = v_transaction_id;

  insert into public.splits (id, transaction_id, total_amount, paid_by, split_type)
  values (
    (p_split ->> 'id')::uuid,
    v_transaction_id,
    v_amount,
    (p_split ->> 'paid_by')::uuid,
    coalesce(p_split ->> 'split_type', 'equal')
  )
  on conflict (transaction_id) do update set
    total_amount = excluded.total_amount,
    paid_by = excluded.paid_by,
    split_type = excluded.split_type
  returning id into v_split_id;

  -- ---------------- Members (profile_id) ----------------

  update public.split_shares ss
  set deleted_at = now()
  where ss.split_id = v_split_id
    and ss.deleted_at is null
    and ss.profile_id is not null
    and not exists (
      select 1 from jsonb_array_elements(p_shares) s
      where (s ->> 'profile_id')::uuid = ss.profile_id
    );

  update public.split_shares ss
  set share_amount = (s ->> 'share_amount')::numeric,
      percentage = (s ->> 'percentage')::numeric
  from jsonb_array_elements(p_shares) s
  where ss.split_id = v_split_id
    and ss.deleted_at is null
    and ss.profile_id = (s ->> 'profile_id')::uuid;

  insert into public.split_shares (id, split_id, profile_id, share_amount, percentage)
  select coalesce((s ->> 'id')::uuid, gen_random_uuid()),
         v_split_id,
         (s ->> 'profile_id')::uuid,
         (s ->> 'share_amount')::numeric,
         (s ->> 'percentage')::numeric
  from jsonb_array_elements(p_shares) s
  where (s ->> 'profile_id') is not null
    and not exists (
      select 1
      from public.split_shares ss
      where ss.split_id = v_split_id
        and ss.deleted_at is null
        and ss.profile_id = (s ->> 'profile_id')::uuid
    );

  -- ---------------- Guests (local_name) ----------------

  select coalesce(string_agg(lower(btrim(ss.local_name)) || '=' || ss.share_amount::numeric(12, 2)::text, ','
                             order by lower(btrim(ss.local_name))), '')
  into v_guests_now
  from public.split_shares ss
  where ss.split_id = v_split_id and ss.deleted_at is null and ss.local_name is not null;

  select coalesce(string_agg(lower(btrim(s ->> 'local_name')) || '=' || (s ->> 'share_amount')::numeric(12, 2)::text, ','
                             order by lower(btrim(s ->> 'local_name'))), '')
  into v_guests_sent
  from jsonb_array_elements(p_shares) s
  where nullif(btrim(s ->> 'local_name'), '') is not null;

  if v_guests_now is distinct from v_guests_sent then
    -- Guests belong to their host. The payer editing a groupmate's expense sends them back as they
    -- are; anything else is a change only the host can make.
    if v_me is distinct from v_owner then
      raise exception 'only the member who added this expense can change their guests'
        using errcode = '42501';
    end if;

    update public.split_shares ss
    set deleted_at = now()
    where ss.split_id = v_split_id
      and ss.deleted_at is null
      and ss.local_name is not null
      and not exists (
        select 1 from jsonb_array_elements(p_shares) s
        where lower(btrim(s ->> 'local_name')) = lower(btrim(ss.local_name))
          and (s ->> 'share_amount')::numeric(12, 2) = ss.share_amount
      );

    -- Always a new id: the client's id for a guest share may already belong to the row just retired.
    insert into public.split_shares (id, split_id, local_name, share_amount, percentage)
    select gen_random_uuid(),
           v_split_id,
           btrim(s ->> 'local_name'),
           (s ->> 'share_amount')::numeric,
           (s ->> 'percentage')::numeric
    from jsonb_array_elements(p_shares) s
    where nullif(btrim(s ->> 'local_name'), '') is not null
      and not exists (
        select 1
        from public.split_shares ss
        where ss.split_id = v_split_id
          and ss.deleted_at is null
          and ss.local_name is not null
          and lower(btrim(ss.local_name)) = lower(btrim(s ->> 'local_name'))
      );
  end if;
end;
$$;

-- Unchanged grants, restated so the file stands on its own.
revoke execute on function public.save_group_expense(jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.save_group_expense(jsonb, jsonb, jsonb) to authenticated;

-- ------------------------------------------------------------
-- RLS POLICY CHANGE (the reason this file is a hard stop). split_shares_update let anyone who can
-- edit the expense — the payer too, since 20260918073251 — soft-delete any share, a guest's
-- included. The function above refuses a payer's guest change, but a direct UPDATE went around it
-- (found by rls_group_guests_test.sql). A guest share is now changed only by the expense's owner.
-- Member shares are exactly as before. No guest shares exist in groups yet, so nothing live moves.
-- ------------------------------------------------------------

drop policy split_shares_update on public.split_shares;

create policy split_shares_update
  on public.split_shares for update
  to authenticated
  using (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.can_edit_group_expense(s.transaction_id)
        and (split_shares.local_name is null or private.owns_transaction(s.transaction_id))
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
        and (split_shares.local_name is null or private.owns_transaction(s.transaction_id))
    )
  );

commit;
