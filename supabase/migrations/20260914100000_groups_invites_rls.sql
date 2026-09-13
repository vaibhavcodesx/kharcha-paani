-- Migration Plan step 4, PR 1: groups, invites and multi-user splits — RLS and database functions.
--
-- NOT purely additive: replaces and drops RLS policies. Reviewed before being applied.
-- The version in this filename is provisional; it is renamed to the version Supabase assigns
-- when applied. At authoring time the live project had 3 personal transactions, 1 profile and
-- no group, member, split or settlement rows. Personal-transaction sync (step 3) must keep working:
-- rows with group_id null are unaffected by every rule below.
--
-- Fixes, per the approved design:
--   1. Joining a group by knowing its UUID      -> joins only via join_group(invite code)
--   2. Profiles readable by every signed-in user -> only yourself and people you share a group with
--   3. Group members can't see each other's shares -> any member of the transaction's group can
--   4. Transactions attachable to any group      -> writer must be an active member of that group
-- Plus: splits, shares and settlements may only reference members; members can leave;
-- groups are archived instead of hard-deleted; settlements are group-scoped.

-- ============================================================
-- Schema additions (all target tables have 0 rows)
-- ============================================================

-- Archived groups are hidden and read-only; history stays readable by members.
alter table public.groups add column archived_at timestamptz;

-- One split per transaction (matches the app's model); save_group_expense upserts on it.
create unique index splits_transaction_id_key on public.splits (transaction_id);

-- A person appears at most once in a split.
create unique index split_shares_split_profile_key on public.split_shares (split_id, profile_id);

create table public.group_invites (
  -- 8 characters from a 32-symbol alphabet without look-alikes (0/O, 1/I): 40 bits, multi-use until expiry.
  code text primary key
    constraint group_invites_code_format check (code ~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$'),
  group_id uuid not null references public.groups (id) on delete cascade,
  created_by uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days',
  revoked_at timestamptz
);

create index group_invites_group_id_idx on public.group_invites (group_id);
create index group_invites_created_by_idx on public.group_invites (created_by);

alter table public.group_invites enable row level security;

-- Members can list a group's invites (to share or revoke them). There are no write policies:
-- codes are generated server-side and revoked only through the functions below.
create policy group_invites_select_member
  on public.group_invites for select
  to authenticated
  using (private.is_group_member(group_id));

-- ============================================================
-- Private helpers (SECURITY DEFINER to read membership without recursive RLS)
-- ============================================================

-- The caller is a member of a group that isn't archived: required for every write into a group.
create or replace function private.is_active_group_member(p_group_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.group_members gm
    join public.groups g on g.id = gm.group_id
    where gm.group_id = p_group_id
      and gm.profile_id = (select auth.uid())
      and g.archived_at is null
  );
$$;

-- Any profile (not just the caller) is a member: checks payers, share holders and settlement parties.
create or replace function private.is_member_of(p_group_id uuid, p_profile_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.group_members
    where group_id = p_group_id
      and profile_id = p_profile_id
  );
$$;

create or replace function private.shares_group_with(p_profile_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.group_members mine
    join public.group_members theirs on theirs.group_id = mine.group_id
    where mine.profile_id = (select auth.uid())
      and theirs.profile_id = p_profile_id
  );
$$;

create or replace function private.transaction_group_id(p_transaction_id uuid)
returns uuid
language sql
security definer
stable
set search_path = ''
as $$
  select group_id from public.transactions where id = p_transaction_id;
$$;

create or replace function private.can_access_split(p_split_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.splits s
    where s.id = p_split_id
      and private.can_access_transaction(s.transaction_id)
  );
$$;

revoke execute on function private.is_active_group_member(uuid) from public;
revoke execute on function private.is_member_of(uuid, uuid) from public;
revoke execute on function private.shares_group_with(uuid) from public;
revoke execute on function private.transaction_group_id(uuid) from public;
revoke execute on function private.can_access_split(uuid) from public;
grant execute on function private.is_active_group_member(uuid) to authenticated;
grant execute on function private.is_member_of(uuid, uuid) to authenticated;
grant execute on function private.shares_group_with(uuid) to authenticated;
grant execute on function private.transaction_group_id(uuid) to authenticated;
grant execute on function private.can_access_split(uuid) to authenticated;

-- ============================================================
-- profiles: gap 2
-- ============================================================

drop policy profiles_select_authenticated on public.profiles;

create policy profiles_select_self_or_groupmate
  on public.profiles for select
  to authenticated
  using (id = (select auth.uid()) or private.shares_group_with(id));

-- ============================================================
-- groups: creation via create_group(); archive instead of delete
-- ============================================================

-- A direct insert would create a group without its creator's membership; create_group() does both.
drop policy groups_insert_self on public.groups;

-- Hard delete would erase every member's shared history. The creator archives instead
-- (groups_update_creator, unchanged, allows setting archived_at).
drop policy groups_delete_creator on public.groups;

-- ============================================================
-- group_members: gap 1
-- ============================================================

-- Any signed-in user could insert themselves into any group. Memberships are now created only by
-- create_group() and join_group().
drop policy group_members_insert_self on public.group_members;

create policy group_members_delete_self
  on public.group_members for delete
  to authenticated
  using (profile_id = (select auth.uid()));

-- ============================================================
-- transactions: gap 4
-- ============================================================

drop policy transactions_insert_own on public.transactions;
drop policy transactions_update_own on public.transactions;

create policy transactions_insert_own
  on public.transactions for insert
  to authenticated
  with check (
    owner_id = (select auth.uid())
    and (group_id is null or private.is_active_group_member(group_id))
  );

-- The check also stops moving a row into a group the owner isn't in, and makes group expenses
-- read-only once the group is archived or the owner has left.
create policy transactions_update_own
  on public.transactions for update
  to authenticated
  using (owner_id = (select auth.uid()))
  with check (
    owner_id = (select auth.uid())
    and (group_id is null or private.is_active_group_member(group_id))
  );

-- ============================================================
-- splits: group expenses only, payer must be a member
-- ============================================================

drop policy splits_insert on public.splits;
drop policy splits_update on public.splits;

-- is_active_group_member(null) is false, so personal transactions can't carry a split server-side
-- (decision 1: sharing happens only inside groups).
create policy splits_insert
  on public.splits for insert
  to authenticated
  with check (
    private.owns_transaction(transaction_id)
    and private.is_active_group_member(private.transaction_group_id(transaction_id))
    and private.is_member_of(private.transaction_group_id(transaction_id), paid_by)
  );

create policy splits_update
  on public.splits for update
  to authenticated
  using (private.owns_transaction(transaction_id))
  with check (
    private.owns_transaction(transaction_id)
    and private.is_active_group_member(private.transaction_group_id(transaction_id))
    and private.is_member_of(private.transaction_group_id(transaction_id), paid_by)
  );

-- ============================================================
-- split_shares: gap 3, and shares only for members
-- ============================================================

drop policy split_shares_select on public.split_shares;
drop policy split_shares_insert on public.split_shares;
drop policy split_shares_update on public.split_shares;

create policy split_shares_select
  on public.split_shares for select
  to authenticated
  using (private.can_access_split(split_id));

create policy split_shares_insert
  on public.split_shares for insert
  to authenticated
  with check (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.owns_transaction(s.transaction_id)
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
        and private.owns_transaction(s.transaction_id)
    )
  )
  with check (
    exists (
      select 1
      from public.splits s
      where s.id = split_shares.split_id
        and private.owns_transaction(s.transaction_id)
        and private.is_active_group_member(private.transaction_group_id(s.transaction_id))
        and private.is_member_of(private.transaction_group_id(s.transaction_id), split_shares.profile_id)
    )
  );

-- ============================================================
-- settlements: group-scoped, either party records, members can read
-- ============================================================

drop policy settlements_select on public.settlements;
drop policy settlements_insert on public.settlements;
drop policy settlements_update on public.settlements;

create policy settlements_select
  on public.settlements for select
  to authenticated
  using (
    from_profile_id = (select auth.uid())
    or to_profile_id = (select auth.uid())
    or (group_id is not null and private.is_group_member(group_id))
  );

-- Decision 3: either party records it. Both parties must be members; the group must be active.
create policy settlements_insert
  on public.settlements for insert
  to authenticated
  with check (
    (from_profile_id = (select auth.uid()) or to_profile_id = (select auth.uid()))
    and from_profile_id <> to_profile_id
    and private.is_active_group_member(group_id)
    and private.is_member_of(group_id, from_profile_id)
    and private.is_member_of(group_id, to_profile_id)
  );

create policy settlements_update
  on public.settlements for update
  to authenticated
  using (from_profile_id = (select auth.uid()) or to_profile_id = (select auth.uid()))
  with check (
    (from_profile_id = (select auth.uid()) or to_profile_id = (select auth.uid()))
    and from_profile_id <> to_profile_id
    and private.is_active_group_member(group_id)
    and private.is_member_of(group_id, from_profile_id)
    and private.is_member_of(group_id, to_profile_id)
  );

-- ============================================================
-- Functions exposed to the app (RPC)
-- ============================================================

-- p_id is generated on the device (offline creation, idempotent retries), like transaction ids.
create or replace function public.create_group(p_id uuid, p_name text, p_emoji text default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_name text := btrim(p_name);
  v_created_by uuid;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;
  if v_name is null or length(v_name) = 0 or length(v_name) > 60 then
    raise exception 'group name must be 1 to 60 characters' using errcode = '22023';
  end if;

  select created_by into v_created_by from public.groups where id = p_id;
  if found then
    -- A retried call from the creator's device succeeds without creating anything twice.
    if v_created_by = v_user then
      return p_id;
    end if;
    raise exception 'group id already in use' using errcode = '23505';
  end if;

  insert into public.groups (id, name, emoji, created_by) values (p_id, v_name, p_emoji, v_user);
  insert into public.group_members (group_id, profile_id) values (p_id, v_user);
  return p_id;
end;
$$;

create or replace function public.create_group_invite(p_group_id uuid)
returns table (code text, expires_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_user uuid := (select auth.uid());
  v_alphabet constant text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  -- Bytes 0-5 and 10-11 of a v4 UUID are fully random (the version and variant bits live in
  -- bytes 6 and 8). 256 is a multiple of 32, so taking each byte modulo 32 is unbiased.
  v_random_bytes constant int[] := array[0, 1, 2, 3, 4, 5, 10, 11];
  v_bytes bytea;
  v_code text;
  v_expires timestamptz;
  v_index int;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;
  if not private.is_active_group_member(p_group_id) then
    raise exception 'not a member of an active group' using errcode = '42501';
  end if;

  loop
    v_bytes := uuid_send(gen_random_uuid());
    v_code := '';
    foreach v_index in array v_random_bytes loop
      v_code := v_code || substr(v_alphabet, get_byte(v_bytes, v_index) % 32 + 1, 1);
    end loop;
    begin
      insert into public.group_invites (code, group_id, created_by)
      values (v_code, p_group_id, v_user)
      returning group_invites.expires_at into v_expires;
      exit;
    exception when unique_violation then
      -- One in 2^40 per existing code; retry with a fresh code rather than fail.
      null;
    end;
  end loop;

  return query select v_code, v_expires;
end;
$$;

create or replace function public.revoke_group_invite(p_code text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.group_invites i
  set revoked_at = now()
  from public.groups g
  where g.id = i.group_id
    and i.code = upper(btrim(p_code))
    and i.revoked_at is null
    and (i.created_by = (select auth.uid()) or g.created_by = (select auth.uid()));
  if not found then
    raise exception 'invite not found' using errcode = 'P0002';
  end if;
end;
$$;

-- What someone sees before joining: no rows for an unknown, expired, revoked or archived code,
-- so a caller can't tell which of those it was.
create or replace function public.preview_invite(p_code text)
returns table (group_id uuid, group_name text, group_emoji text, inviter_name text, member_count integer)
language sql
security definer
stable
set search_path = ''
as $$
  select g.id,
         g.name,
         g.emoji,
         p.display_name,
         (select count(*)::integer from public.group_members m where m.group_id = g.id)
  from public.group_invites i
  join public.groups g on g.id = i.group_id
  left join public.profiles p on p.id = i.created_by
  where (select auth.uid()) is not null
    and i.code = upper(btrim(p_code))
    and i.revoked_at is null
    and i.expires_at > now()
    and g.archived_at is null;
$$;

create or replace function public.join_group(p_code text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_group_id uuid;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;

  select i.group_id into v_group_id
  from public.group_invites i
  join public.groups g on g.id = i.group_id
  where i.code = upper(btrim(p_code))
    and i.revoked_at is null
    and i.expires_at > now()
    and g.archived_at is null;

  if v_group_id is null then
    -- One message for unknown, expired, revoked or archived: don't confirm which codes exist.
    raise exception 'invite code is invalid or expired' using errcode = 'P0002';
  end if;

  -- Joining twice is a no-op.
  insert into public.group_members (group_id, profile_id)
  values (v_group_id, v_user)
  on conflict (group_id, profile_id) do nothing;
  return v_group_id;
end;
$$;

-- Saves a group expense with its split and shares in one transaction, so members never pull a
-- half-written expense. SECURITY INVOKER: every row still passes the caller's RLS policies.
-- Shares are replaced wholesale; members pull the transaction with its current shares embedded.
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
  v_split_id uuid;
  v_share_total numeric;
begin
  if v_group_id is null then
    raise exception 'a group expense needs group_id' using errcode = '23502';
  end if;
  if jsonb_typeof(p_shares) is distinct from 'array' or jsonb_array_length(p_shares) = 0 then
    raise exception 'a group expense needs at least one share' using errcode = '22023';
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
    (select auth.uid()),
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

  delete from public.split_shares where split_id = v_split_id;

  insert into public.split_shares (id, split_id, profile_id, share_amount, percentage)
  select coalesce((s ->> 'id')::uuid, gen_random_uuid()),
         v_split_id,
         (s ->> 'profile_id')::uuid,
         (s ->> 'share_amount')::numeric,
         (s ->> 'percentage')::numeric
  from jsonb_array_elements(p_shares) s;
end;
$$;

-- New functions in public get EXECUTE for anon by this project's default privileges; only
-- signed-in users may call these.
revoke execute on function public.create_group(uuid, text, text) from public, anon;
revoke execute on function public.create_group_invite(uuid) from public, anon;
revoke execute on function public.revoke_group_invite(text) from public, anon;
revoke execute on function public.preview_invite(text) from public, anon;
revoke execute on function public.join_group(text) from public, anon;
revoke execute on function public.save_group_expense(jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.create_group(uuid, text, text) to authenticated;
grant execute on function public.create_group_invite(uuid) to authenticated;
grant execute on function public.revoke_group_invite(text) to authenticated;
grant execute on function public.preview_invite(text) to authenticated;
grant execute on function public.join_group(text) to authenticated;
grant execute on function public.save_group_expense(jsonb, jsonb, jsonb) to authenticated;
