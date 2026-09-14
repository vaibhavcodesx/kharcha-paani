-- Closes the gaps found in the step 4 audit, where the app enforced a rule the database didn't.
--
-- 1. A creator who has LEFT a group could still rename, archive or un-archive it and revoke its
--    invites. Both now require current membership.
-- 2. An invite made by someone who has since left still admitted new members. Invites only work
--    while their creator is a member.
-- 3. Any owner (including an ex-member, or someone in an archived group) could set a group expense's
--    group_id to null, pulling it away from members who never see the change. A group expense can
--    no longer leave its group.
-- 4. Either party could rewrite a settlement the other recorded. Settlements are immutable after
--    insert except for deleted_at (retract / revive).
-- 5. Group names were checked (1-60 characters) only by create_group(); a creator's direct update
--    could set any name. Now a table constraint. The one live group name passes.
--
-- The two triggers raise check_violation (23514, HTTP 400), not insufficient_privilege: the app
-- treats a 400 as "this row can't be saved" and marks it failed, whereas personal sync retries a 403.

-- ============================================================
-- 1. Creator powers need current membership
-- ============================================================

drop policy groups_update_creator on public.groups;

-- private.is_group_member (not is_active_group_member): the creator must still be able to
-- un-archive, which is an update to an archived group.
create policy groups_update_creator
  on public.groups for update
  to authenticated
  using (created_by = (select auth.uid()) and private.is_group_member(id))
  with check (created_by = (select auth.uid()) and private.is_group_member(id));

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
    and (i.created_by = (select auth.uid()) or g.created_by = (select auth.uid()))
    and private.is_group_member(i.group_id);
  if not found then
    raise exception 'invite not found' using errcode = 'P0002';
  end if;
end;
$$;

-- ============================================================
-- 2. Invites stop working when their creator leaves
-- ============================================================

-- What someone sees before joining: no rows for an unknown, expired, revoked or archived code, or
-- one whose creator has left, so a caller can't tell which of those it was.
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
    and g.archived_at is null
    and private.is_member_of(i.group_id, i.created_by);
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
    and g.archived_at is null
    and private.is_member_of(i.group_id, i.created_by);

  if v_group_id is null then
    -- One message for unknown, expired, revoked, archived or orphaned: don't confirm which codes exist.
    raise exception 'invite code is invalid or expired' using errcode = 'P0002';
  end if;

  -- Joining twice is a no-op.
  insert into public.group_members (group_id, profile_id)
  values (v_group_id, v_user)
  on conflict (group_id, profile_id) do nothing;
  return v_group_id;
end;
$$;

-- ============================================================
-- 3. A group expense stays in its group
-- ============================================================

create or replace function private.keep_group_expense_in_group()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Deleting a group (service role only) sets group_id to null through the foreign key; the
  -- group is already gone by then, so that path stays open.
  if old.group_id is not null
     and new.group_id is distinct from old.group_id
     and exists (select 1 from public.groups where id = old.group_id) then
    raise exception 'a group expense cannot move out of its group'
      using errcode = '23514', hint = 'Delete it and add it again where it belongs.';
  end if;
  return new;
end;
$$;

-- Trigger-only function: firing a trigger does not need EXECUTE.
revoke execute on function private.keep_group_expense_in_group() from public, anon, authenticated;

create trigger transactions_keep_group
  before update of group_id on public.transactions
  for each row execute function private.keep_group_expense_in_group();

-- ============================================================
-- 4. Settlements are immutable except for retracting and reviving
-- ============================================================

create or replace function private.lock_settlement_columns()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- updated_at is the server clock (settlements_set_updated_at) and deleted_at is the retraction.
  -- group_id may only become null because its group was deleted (service role), as above.
  if new.id is distinct from old.id
     or new.from_profile_id is distinct from old.from_profile_id
     or new.to_profile_id is distinct from old.to_profile_id
     or new.amount is distinct from old.amount
     or new.date is distinct from old.date
     or new.note is distinct from old.note
     or new.is_iou is distinct from old.is_iou
     or new.created_at is distinct from old.created_at
     or (new.group_id is distinct from old.group_id
         and (new.group_id is not null or exists (select 1 from public.groups where id = old.group_id))) then
    raise exception 'a settlement cannot be changed after it is recorded'
      using errcode = '23514', hint = 'Retract it and record a new one.';
  end if;
  return new;
end;
$$;

revoke execute on function private.lock_settlement_columns() from public, anon, authenticated;

create trigger settlements_lock_columns
  before update on public.settlements
  for each row execute function private.lock_settlement_columns();

-- ============================================================
-- 5. Group names: 1 to 60 characters, however they are written
-- ============================================================

-- Validates existing rows (not purely additive); checked beforehand that the live group passes.
alter table public.groups
  add constraint groups_name_length check (char_length(btrim(name)) between 1 and 60);
