-- Kharcha Paani: core cloud data model, per PRD.md "New Data Model" section.
-- profiles, groups, group_members, transactions, splits, split_shares, settlements
-- + RLS policies per PRD's plain-terms description.

-- ============================================================
-- Private schema for RLS helper functions (not exposed via PostgREST)
-- ============================================================
create schema if not exists private;

-- ============================================================
-- profiles
-- ============================================================
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  avatar_url text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Display name/avatar are low-sensitivity and need to be visible to anyone
-- you share a group or split with, so any signed-in user can read profiles.
create policy profiles_select_authenticated
  on public.profiles for select
  to authenticated
  using (true);

create policy profiles_update_own
  on public.profiles for update
  to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

-- Auto-create a profiles row on first sign-in (Google Sign-In via Supabase Auth).
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'),
    new.raw_user_meta_data ->> 'avatar_url'
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============================================================
-- groups
-- ============================================================
create table public.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  emoji text,
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now()
);

create index groups_created_by_idx on public.groups (created_by);

alter table public.groups enable row level security;

-- ============================================================
-- group_members (junction table; drives RLS on group-scoped data)
-- ============================================================
create table public.group_members (
  group_id uuid not null references public.groups (id) on delete cascade,
  profile_id uuid not null references public.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (group_id, profile_id)
);

create index group_members_profile_id_idx on public.group_members (profile_id);

alter table public.group_members enable row level security;

-- SECURITY DEFINER helper: is the current user a member of this group?
-- Runs as owner (bypasses RLS on group_members) so it can be used inside
-- group_members' own policy without recursive-policy errors, and is a
-- single indexed lookup rather than a per-row re-evaluation.
create or replace function private.is_group_member(p_group_id uuid)
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
      and profile_id = (select auth.uid())
  );
$$;

revoke execute on function private.is_group_member(uuid) from public;
grant execute on function private.is_group_member(uuid) to authenticated;

-- groups policies (defined after the helper function exists)
create policy groups_select_member
  on public.groups for select
  to authenticated
  using (private.is_group_member(id) or created_by = (select auth.uid()));

create policy groups_insert_self
  on public.groups for insert
  to authenticated
  with check (created_by = (select auth.uid()));

create policy groups_update_creator
  on public.groups for update
  to authenticated
  using (created_by = (select auth.uid()))
  with check (created_by = (select auth.uid()));

create policy groups_delete_creator
  on public.groups for delete
  to authenticated
  using (created_by = (select auth.uid()));

-- group_members policies
create policy group_members_select_member
  on public.group_members for select
  to authenticated
  using (private.is_group_member(group_id));

-- Covers both "creator joins their own new group" and "friend joins via invite code/link".
create policy group_members_insert_self
  on public.group_members for insert
  to authenticated
  with check (profile_id = (select auth.uid()));

-- ============================================================
-- transactions
-- ============================================================
create table public.transactions (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id),
  type text not null check (type in ('expense', 'income')),
  amount numeric(12, 2) not null check (amount > 0),
  category text not null,
  merchant text not null default '',
  payment_method text not null default '',
  date timestamptz not null default now(),
  note text not null default '',
  group_id uuid references public.groups (id) on delete set null,
  receipt_storage_path text,
  created_at timestamptz not null default now()
);

create index transactions_owner_id_idx on public.transactions (owner_id);
create index transactions_group_id_idx on public.transactions (group_id);
create index transactions_date_idx on public.transactions (date desc);

alter table public.transactions enable row level security;

-- SECURITY DEFINER helper: can the current user see this transaction?
-- (own transaction, or a group transaction they belong to)
create or replace function private.can_access_transaction(p_transaction_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.transactions t
    where t.id = p_transaction_id
      and (
        t.owner_id = (select auth.uid())
        or (t.group_id is not null and private.is_group_member(t.group_id))
      )
  );
$$;

revoke execute on function private.can_access_transaction(uuid) from public;
grant execute on function private.can_access_transaction(uuid) to authenticated;

-- SECURITY DEFINER helper: does the current user own this transaction?
-- (used to gate writes to its child splits / split_shares)
create or replace function private.owns_transaction(p_transaction_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.transactions t
    where t.id = p_transaction_id
      and t.owner_id = (select auth.uid())
  );
$$;

revoke execute on function private.owns_transaction(uuid) from public;
grant execute on function private.owns_transaction(uuid) to authenticated;

-- transactions policies: always read/write your own; read a group
-- transaction only if you're a member of that group.
create policy transactions_select
  on public.transactions for select
  to authenticated
  using (
    owner_id = (select auth.uid())
    or (group_id is not null and private.is_group_member(group_id))
  );

create policy transactions_insert_own
  on public.transactions for insert
  to authenticated
  with check (owner_id = (select auth.uid()));

create policy transactions_update_own
  on public.transactions for update
  to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

create policy transactions_delete_own
  on public.transactions for delete
  to authenticated
  using (owner_id = (select auth.uid()));

-- ============================================================
-- splits
-- ============================================================
create table public.splits (
  id uuid primary key default gen_random_uuid(),
  transaction_id uuid not null references public.transactions (id) on delete cascade,
  total_amount numeric(12, 2) not null check (total_amount > 0),
  paid_by uuid not null references public.profiles (id)
);

create index splits_transaction_id_idx on public.splits (transaction_id);
create index splits_paid_by_idx on public.splits (paid_by);

alter table public.splits enable row level security;

create policy splits_select
  on public.splits for select
  to authenticated
  using (private.can_access_transaction(transaction_id));

create policy splits_insert
  on public.splits for insert
  to authenticated
  with check (private.owns_transaction(transaction_id));

create policy splits_update
  on public.splits for update
  to authenticated
  using (private.owns_transaction(transaction_id))
  with check (private.owns_transaction(transaction_id));

create policy splits_delete
  on public.splits for delete
  to authenticated
  using (private.owns_transaction(transaction_id));

-- ============================================================
-- split_shares
-- ============================================================
create table public.split_shares (
  id uuid primary key default gen_random_uuid(),
  split_id uuid not null references public.splits (id) on delete cascade,
  profile_id uuid not null references public.profiles (id),
  share_amount numeric(12, 2) not null check (share_amount >= 0)
);

create index split_shares_split_id_idx on public.split_shares (split_id);
create index split_shares_profile_id_idx on public.split_shares (profile_id);

alter table public.split_shares enable row level security;

-- Readable by either party referenced: the person owing the share, or the
-- person who paid the parent split.
create policy split_shares_select
  on public.split_shares for select
  to authenticated
  using (
    profile_id = (select auth.uid())
    or exists (
      select 1 from public.splits s
      where s.id = split_shares.split_id
        and s.paid_by = (select auth.uid())
    )
  );

create policy split_shares_insert
  on public.split_shares for insert
  to authenticated
  with check (
    exists (
      select 1 from public.splits s
      where s.id = split_id
        and private.owns_transaction(s.transaction_id)
    )
  );

create policy split_shares_update
  on public.split_shares for update
  to authenticated
  using (
    exists (
      select 1 from public.splits s
      where s.id = split_id
        and private.owns_transaction(s.transaction_id)
    )
  )
  with check (
    exists (
      select 1 from public.splits s
      where s.id = split_id
        and private.owns_transaction(s.transaction_id)
    )
  );

create policy split_shares_delete
  on public.split_shares for delete
  to authenticated
  using (
    exists (
      select 1 from public.splits s
      where s.id = split_id
        and private.owns_transaction(s.transaction_id)
    )
  );

-- ============================================================
-- settlements
-- ============================================================
create table public.settlements (
  id uuid primary key default gen_random_uuid(),
  from_profile_id uuid not null references public.profiles (id),
  to_profile_id uuid not null references public.profiles (id),
  amount numeric(12, 2) not null check (amount > 0),
  date timestamptz not null default now(),
  note text not null default '',
  created_at timestamptz not null default now()
);

create index settlements_from_profile_id_idx on public.settlements (from_profile_id);
create index settlements_to_profile_id_idx on public.settlements (to_profile_id);

alter table public.settlements enable row level security;

-- Readable/writable by either party referenced in the row.
create policy settlements_select
  on public.settlements for select
  to authenticated
  using (
    from_profile_id = (select auth.uid())
    or to_profile_id = (select auth.uid())
  );

create policy settlements_insert
  on public.settlements for insert
  to authenticated
  with check (
    from_profile_id = (select auth.uid())
    or to_profile_id = (select auth.uid())
  );

create policy settlements_update
  on public.settlements for update
  to authenticated
  using (
    from_profile_id = (select auth.uid())
    or to_profile_id = (select auth.uid())
  )
  with check (
    from_profile_id = (select auth.uid())
    or to_profile_id = (select auth.uid())
  );

create policy settlements_delete
  on public.settlements for delete
  to authenticated
  using (
    from_profile_id = (select auth.uid())
    or to_profile_id = (select auth.uid())
  );
