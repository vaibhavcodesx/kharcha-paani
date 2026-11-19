-- STAGED, NOT APPLIED (owner review: replaces live functions, adds a creator-only delete). Issue #312, audit S-03.
--
-- Owner answers: C-03 the creator can remove a member; C-04 invites are multi-use with a cap of 20 and can
-- be reset with one tap; C-05 members are told when someone joins (that part is in the app, not here).
--
-- 1. Invite codes get a use cap (default 20, at most 20) and a use counter. A code stops working when its
--    uses are spent. Existing codes get the default and a count of zero.
-- 2. Attempt limiting. Guessing codes is limited to 10 failed tries per person per hour, across
--    `preview_invite` and `join_group`. Past that both raise 54000 until the next hour.
-- 3. `remove_group_member(group, person)`: the group's creator, while the group is active, can remove
--    another member. The creator cannot remove themselves (leaving is the existing delete).
-- 4. `reset_group_invites(group)`: any active member revokes every live code of the group and gets a new one.
--
-- BEHAVIOUR CHANGE TO READ: `join_group` now RETURNS NULL for an unknown, expired, revoked or used-up code
-- instead of raising P0002. A raised error would roll back the failed-attempt count it just wrote, so the
-- limit could never work. The app must treat a null result as "invalid or expired" (issue #312 lists this
-- app change). Versions before that change show a generic error instead of the invalid-code message, which
-- is why this should ship together with the app change and the minimum-version switch (#299).
-- Everything else about the two functions keeps its signature, grants and messages.
--
-- Rollback: re-run the function bodies from 20260914142135_leave_rules_and_immutable_records.sql
-- (preview_invite, join_group); then
--   drop function public.remove_group_member(uuid, uuid), public.reset_group_invites(uuid);
--   drop function private.check_invite_attempts(uuid), private.record_invite_failure(uuid);
--   drop table private.invite_attempts;
--   alter table public.group_invites drop column max_uses, drop column used_count;

begin;

alter table public.group_invites
  add column max_uses integer not null default 20 check (max_uses between 1 and 20),
  add column used_count integer not null default 0 check (used_count >= 0);

create table private.invite_attempts (
  user_id uuid not null references auth.users (id) on delete cascade,
  hour timestamptz not null,
  failures integer not null default 0 check (failures >= 0),
  primary key (user_id, hour)
);
create index invite_attempts_hour_idx on private.invite_attempts (hour);
revoke all on private.invite_attempts from public, anon, authenticated;

-- Raises 54000 when this person has already failed 10 times this hour.
create or replace function private.check_invite_attempts(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_failures integer;
begin
  select failures into v_failures from private.invite_attempts
    where user_id = p_user and hour = date_trunc('hour', now());
  if coalesce(v_failures, 0) >= 10 then
    raise exception 'too many invite attempts, try again in an hour' using errcode = '54000';
  end if;
end;
$$;

-- Counts one failed try, and sweeps counts older than two days so the table stays tiny.
create or replace function private.record_invite_failure(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into private.invite_attempts (user_id, hour, failures)
  values (p_user, date_trunc('hour', now()), 1)
  on conflict (user_id, hour) do update set failures = private.invite_attempts.failures + 1;
  delete from private.invite_attempts where hour < now() - interval '2 days';
end;
$$;

revoke execute on function private.check_invite_attempts(uuid), private.record_invite_failure(uuid) from public, anon, authenticated;

-- Same result columns as before. Now plpgsql so a miss can be counted.
create or replace function public.preview_invite(p_code text)
returns table (group_id uuid, group_name text, group_emoji text, inviter_name text, member_count integer)
language plpgsql
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    return;
  end if;
  perform private.check_invite_attempts(v_user);

  return query
    select g.id,
           g.name,
           g.emoji,
           p.display_name,
           (select count(*)::integer from public.group_members m where m.group_id = g.id)
    from public.group_invites i
    join public.groups g on g.id = i.group_id
    left join public.profiles p on p.id = i.created_by
    where i.code = upper(btrim(p_code))
      and i.revoked_at is null
      and i.expires_at > now()
      and i.used_count < i.max_uses
      and g.archived_at is null
      and private.is_member_of(i.group_id, i.created_by);

  if not found then
    perform private.record_invite_failure(v_user);
  end if;
end;
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
  v_code text;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;
  perform private.check_invite_attempts(v_user);

  select i.group_id, i.code into v_group_id, v_code
  from public.group_invites i
  join public.groups g on g.id = i.group_id
  where i.code = upper(btrim(p_code))
    and i.revoked_at is null
    and i.expires_at > now()
    and i.used_count < i.max_uses
    and g.archived_at is null
    and private.is_member_of(i.group_id, i.created_by)
  for update of i;

  if v_group_id is null then
    -- One outcome for unknown, expired, revoked, used-up, archived or orphaned: don't confirm which codes
    -- exist. Null, not an error, so the failed try is kept (see the header).
    perform private.record_invite_failure(v_user);
    return null;
  end if;

  -- Joining twice is a no-op and does not spend a use.
  if not exists (select 1 from public.group_members where group_id = v_group_id and profile_id = v_user) then
    insert into public.group_members (group_id, profile_id) values (v_group_id, v_user);
    update public.group_invites set used_count = used_count + 1 where code = v_code;
  end if;
  return v_group_id;
end;
$$;

-- The creator removes another member. Refused for the creator themselves and for archived groups.
create or replace function public.remove_group_member(p_group_id uuid, p_profile_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.groups g
    where g.id = p_group_id and g.created_by = v_user and g.archived_at is null
  ) or not private.is_group_member(p_group_id) then
    raise exception 'only the creator of an active group can remove a member' using errcode = '42501';
  end if;
  if p_profile_id = v_user then
    raise exception 'the creator cannot remove themselves; leave the group instead' using errcode = '22023';
  end if;

  delete from public.group_members where group_id = p_group_id and profile_id = p_profile_id;
  if not found then
    raise exception 'that person is not a member' using errcode = 'P0002';
  end if;
end;
$$;

-- Any active member revokes every live code of the group and gets a fresh one.
create or replace function public.reset_group_invites(p_group_id uuid)
returns table (code text, expires_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;
  if not private.is_active_group_member(p_group_id) then
    raise exception 'not a member of an active group' using errcode = '42501';
  end if;

  update public.group_invites
    set revoked_at = now()
    where group_id = p_group_id and revoked_at is null and expires_at > now();

  return query select * from public.create_group_invite(p_group_id);
end;
$$;

revoke execute on function public.remove_group_member(uuid, uuid), public.reset_group_invites(uuid) from public, anon;
grant execute on function public.remove_group_member(uuid, uuid), public.reset_group_invites(uuid) to authenticated;
revoke execute on function public.preview_invite(text), public.join_group(text) from public, anon;
grant execute on function public.preview_invite(text), public.join_group(text) to authenticated;

commit;
