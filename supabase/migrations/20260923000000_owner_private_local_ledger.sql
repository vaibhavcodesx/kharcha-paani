-- APPLIED to the live project on 2026-09-24, after owner review (new tables with new RLS
-- policies, a hard stop under this project's rules). 29/29 on the local harness before applying.
--
-- Tested on the local harness: supabase/tests/rls_owner_private_local_ledger_test.sql.
-- See docs/local-friend-sync-v2.md for the why.
--
-- What it is for. A split or an IOU with a friend who has no account lives only on the phone that
-- recorded it. 20260920000000_local_participant_shares gave split_shares a local_name, but only a
-- *group* expense can have a split at all (splits_insert requires an active group, and paid_by must
-- be a group member), so the common case -- "Rohan and I had dinner, I paid" -- still had nowhere to
-- go. Widening the group policies to cover it would have meant touching the most carefully tested
-- rules in the schema for data no groupmate should ever see.
--
-- The shape. Two tables that belong to exactly one account and are never shared with anyone:
--
--   local_splits       one per personal expense that is split; payer and shares by name
--   local_settlements  money that moved between this account and a friend with no account
--
-- Every policy on both is the same sentence: the row is yours. Nothing here is readable by a
-- groupmate, and no existing policy, table or function changes.
--
-- Atomicity. A personal expense and its split must reach the server together or not at all: an
-- expense that arrived without its split would restore as a plain full-amount expense, which is
-- worse than not syncing it. save_local_split writes both in one statement, as the caller, so RLS
-- still applies to every row it touches.
--
-- Rollback: drop the two tables and the two functions. No existing row is touched.

begin;

-- ------------------------------------------------------------
-- local_splits
-- ------------------------------------------------------------

-- A share is {"local_name": text|null, "share_amount": number, "percentage": number|null}, where a
-- null local_name is the owner. Checked here rather than trusted: a malformed split restored onto a
-- phone would be a wrong balance with no way to tell.
create or replace function private.local_shares_are_valid(p_shares jsonb, p_total numeric)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select jsonb_typeof(p_shares) = 'array'
    and jsonb_array_length(p_shares) between 1 and 50
    and not exists (
      select 1 from jsonb_array_elements(p_shares) s
      where jsonb_typeof(s) <> 'object'
         -- coalesce: a missing key is SQL null, and "null <> 'number'" is null, not true, so a
         -- share with no amount at all used to pass (found in review, PR #49).
         or coalesce(jsonb_typeof(s -> 'share_amount'), 'missing') <> 'number'
         or (s ->> 'share_amount')::numeric < 0
         or (s ? 'local_name' and jsonb_typeof(s -> 'local_name') not in ('string', 'null'))
         or (jsonb_typeof(s -> 'local_name') = 'string'
             and length(btrim(s ->> 'local_name')) not between 1 and 64)
    )
    -- Nobody twice, matching split_shares_one_share_per_local_name.
    and (select count(*) from jsonb_array_elements(p_shares) s)
      = (select count(distinct lower(btrim(coalesce(s ->> 'local_name', '')))) from jsonb_array_elements(p_shares) s)
    and (select sum((s ->> 'share_amount')::numeric) from jsonb_array_elements(p_shares) s) = p_total;
$$;

create table public.local_splits (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references public.profiles (id),
  transaction_id uuid not null unique references public.transactions (id) on delete cascade,
  split_type text not null default 'equal' check (split_type in ('equal', 'exact', 'percentage')),
  total_amount numeric(12, 2) not null check (total_amount > 0),
  -- Null means the owner paid.
  paid_by_local_name text check (paid_by_local_name is null or length(btrim(paid_by_local_name)) between 1 and 64),
  shares jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint local_splits_shares_valid check (private.local_shares_are_valid(shares, total_amount))
);

comment on table public.local_splits is
  'The split of a personal expense with friends who have no account. Private to its owner.';

create index local_splits_owner_updated_at_idx on public.local_splits (owner_id, updated_at);

create trigger local_splits_set_updated_at
  before insert or update on public.local_splits
  for each row execute function private.set_updated_at();

-- Other devices pull personal expenses by the transaction's updated_at, with the split embedded.
-- A split changing on its own (removed, say) has to move that clock or nobody sees it.
create or replace function private.touch_transaction_of_local_split()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.transactions set updated_at = now() where id = new.transaction_id;
  return null;
end;
$$;

revoke execute on function private.touch_transaction_of_local_split() from public, anon, authenticated;

create trigger local_splits_touch_transaction
  after insert or update on public.local_splits
  for each row execute function private.touch_transaction_of_local_split();

alter table public.local_splits enable row level security;

create policy local_splits_select on public.local_splits
  for select to authenticated
  using (owner_id = (select auth.uid()));

-- Yours, and only on your own personal expense: never on a group expense, never on anyone else's.
create policy local_splits_insert on public.local_splits
  for insert to authenticated
  with check (
    owner_id = (select auth.uid())
    and exists (
      select 1 from public.transactions t
      where t.id = local_splits.transaction_id
        and t.owner_id = (select auth.uid())
        and t.group_id is null
    )
  );

create policy local_splits_update on public.local_splits
  for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (
    owner_id = (select auth.uid())
    and exists (
      select 1 from public.transactions t
      where t.id = local_splits.transaction_id
        and t.owner_id = (select auth.uid())
        and t.group_id is null
    )
  );

-- No delete policy: removal is a soft delete (deleted_at), like every other synced table.
--
-- Privileges are revoked from *both* client roles before granting back: this project's default
-- privileges give anon and authenticated ALL on every new table, and TRUNCATE ignores RLS, so a
-- grant alone would have left any signed-in user able to wipe every account's private ledger.
-- Caught before applying, 2026-09-24 (the same gap 20260914033124 closed on the older tables).

revoke all on public.local_splits from anon, authenticated;
grant select, insert, update on public.local_splits to authenticated;

-- ------------------------------------------------------------
-- save_local_split: the expense and its split, together
-- ------------------------------------------------------------

-- SECURITY INVOKER: every insert and update below is checked by the caller's own policies.
create or replace function public.save_local_split(p_transaction jsonb, p_split jsonb)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_transaction_id uuid := (p_transaction ->> 'id')::uuid;
  v_amount numeric := (p_transaction ->> 'amount')::numeric;
begin
  if p_transaction ->> 'group_id' is not null then
    raise exception 'a local split belongs to a personal expense, not a group one' using errcode = '22023';
  end if;
  if (p_split ->> 'total_amount')::numeric is distinct from v_amount then
    raise exception 'split total (%) must equal the amount (%)', p_split ->> 'total_amount', v_amount
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
    null,
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
    deleted_at = null;

  insert into public.local_splits (id, owner_id, transaction_id, split_type, total_amount, paid_by_local_name, shares, deleted_at)
  values (
    (p_split ->> 'id')::uuid,
    (select auth.uid()),
    v_transaction_id,
    coalesce(p_split ->> 'split_type', 'equal'),
    v_amount,
    nullif(btrim(p_split ->> 'paid_by_local_name'), ''),
    p_split -> 'shares',
    null
  )
  on conflict (transaction_id) do update set
    split_type = excluded.split_type,
    total_amount = excluded.total_amount,
    paid_by_local_name = excluded.paid_by_local_name,
    shares = excluded.shares,
    deleted_at = null;
end;
$$;

revoke execute on function public.save_local_split(jsonb, jsonb) from public, anon;
grant execute on function public.save_local_split(jsonb, jsonb) to authenticated;

-- ------------------------------------------------------------
-- local_settlements
-- ------------------------------------------------------------

create table public.local_settlements (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references public.profiles (id),
  -- Null is the owner. A settlement between two friends of the owner (a local group of three) has
  -- both set; one between the owner and themselves is meaningless and refused.
  from_local_name text check (from_local_name is null or length(btrim(from_local_name)) between 1 and 64),
  to_local_name text check (to_local_name is null or length(btrim(to_local_name)) between 1 and 64),
  amount numeric(12, 2) not null check (amount > 0),
  is_iou boolean not null default false,
  date timestamptz not null default now(),
  note text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint local_settlements_two_parties check (
    (from_local_name is not null or to_local_name is not null)
    and lower(btrim(coalesce(from_local_name, ''))) <> lower(btrim(coalesce(to_local_name, '')))
  )
);

comment on table public.local_settlements is
  'Payments and IOUs between the owner and friends who have no account. Private to its owner.';

create index local_settlements_owner_updated_at_idx on public.local_settlements (owner_id, updated_at);

create trigger local_settlements_set_updated_at
  before insert or update on public.local_settlements
  for each row execute function private.set_updated_at();

alter table public.local_settlements enable row level security;

create policy local_settlements_select on public.local_settlements
  for select to authenticated
  using (owner_id = (select auth.uid()));

create policy local_settlements_insert on public.local_settlements
  for insert to authenticated
  with check (owner_id = (select auth.uid()));

create policy local_settlements_update on public.local_settlements
  for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

revoke all on public.local_settlements from anon, authenticated;
grant select, insert, update on public.local_settlements to authenticated;

commit;
