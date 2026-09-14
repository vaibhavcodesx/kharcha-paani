-- Migration Plan step 4, PR 2: server-side sync support for groups. Purely additive: new columns
-- with defaults on a 0-row table, triggers that only maintain those columns, indexes, and tables
-- added to the Realtime publication. No policy or privilege changes.
-- The version in this filename is provisional; it is renamed to the version Supabase assigns.

-- ============================================================
-- groups: a server-owned clock, plus the fields the app already has locally
-- ============================================================

alter table public.groups
  add column updated_at timestamptz not null default now(),
  add column description text not null default '',
  add column color_hex text not null default '#FF7A3D';

create trigger groups_set_updated_at
  before insert or update on public.groups
  for each row execute function private.set_updated_at();

-- Joining or leaving bumps the group's clock, so members' pulls and Realtime subscribers learn
-- about membership changes through the (RLS-filtered) groups table.
create or replace function private.touch_group_on_membership_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_group_id uuid;
begin
  if tg_op = 'DELETE' then
    v_group_id := old.group_id;
  else
    v_group_id := new.group_id;
  end if;
  update public.groups set updated_at = now() where id = v_group_id;
  return null;
end;
$$;

-- Trigger-only function: firing a trigger does not need EXECUTE.
revoke execute on function private.touch_group_on_membership_change() from public, anon, authenticated;

create trigger group_members_touch_group
  after insert or delete on public.group_members
  for each row execute function private.touch_group_on_membership_change();

-- ============================================================
-- Pull indexes: group rows changed since a cursor
-- ============================================================

create index transactions_group_updated_at_idx
  on public.transactions (group_id, updated_at)
  where group_id is not null;

create index settlements_group_updated_at_idx
  on public.settlements (group_id, updated_at);

-- ============================================================
-- Realtime: a "something changed, pull" signal for signed-in members
-- ============================================================

-- postgres_changes events are filtered by each subscriber's RLS SELECT policies. group_members is
-- deliberately left out: DELETE events can't be RLS-checked and would broadcast who left which group
-- to every subscriber; membership changes arrive as groups updates instead (trigger above). Splits
-- and shares are left out too: every write to them bumps their parent transaction.
alter publication supabase_realtime add table public.transactions, public.settlements, public.groups;
