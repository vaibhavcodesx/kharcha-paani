-- Sync support for personal transactions (Migration Plan step 3). Purely additive:
-- two new columns, a trigger that only sets the new column, and an index.

alter table public.transactions
  add column updated_at timestamptz not null default now(),
  add column deleted_at timestamptz;

-- updated_at is both the pull cursor and the last-write-wins clock, so the server owns it:
-- whatever a device sends is overwritten, and device clock skew can't reorder writes.
create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- Trigger-only function; firing a trigger doesn't need EXECUTE, so nobody gets it.
revoke execute on function private.set_updated_at() from public, anon, authenticated;

create trigger transactions_set_updated_at
  before insert or update on public.transactions
  for each row execute function private.set_updated_at();

-- Pull query: own rows changed since the cursor.
create index transactions_owner_updated_at_idx on public.transactions (owner_id, updated_at);

-- Deletes are soft (deleted_at) so other devices learn about them on pull.
-- RLS is unchanged: owners can already update their own rows, which covers setting deleted_at.
