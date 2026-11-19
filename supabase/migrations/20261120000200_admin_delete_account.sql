-- STAGED, NOT APPLIED (owner review: replaces the LIVE function public.delete_my_account with a thin wrapper, and adds a
-- function that deletes someone else's account). Issues #355, #440. Design: docs/designs/admin-page.md. Step S3 of four.
-- Apply after 20261120000100_admin_writes.sql.
--
-- What it is for. A person who has uninstalled the app can only ask for deletion by email (site/delete-account.html). Today the
-- owner runs the SQL by hand (docs/engineering/runbooks/delete-account.md). The admin page needs the same deletion as a
-- function the owner can call after checking who is asking.
--
-- What changes.
--   1. The body of `public.delete_my_account` moves, line for line, into `private.delete_account(user, keep_name)`.
--      `public.delete_my_account(boolean)` stays: same name, same signature, same grants, same errors, but it only
--      checks the caller and calls the private function. delete_my_account_test.sql passes unchanged.
--   2. `public.admin_find_account(email)`: a preview (groups, who inherits the groups the person created, photos).
--   3. `public.admin_delete_account(user, typed_email, keep_name)`: asks for a session created in the last 10 minutes,
--      requires the typed email to match, refuses an admin, refuses an already deleted account, allows at most 5
--      deletions per India day, logs the user id (never the email), and returns the photo files left behind, for the
--      page to remove through Storage (a SQL function cannot remove Storage files; S4 adds the narrow policies).
--
-- Behaviour for the app is identical. Rollback: re-run the function body from 20261025000100_delete_my_account.sql, then
--   drop function public.admin_find_account(text), public.admin_delete_account(uuid, text, boolean); drop function private.delete_account(uuid, boolean);

begin;

create function private.delete_account(p_user uuid, p_keep_name boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := p_user;
  v_group record;
  v_heir uuid;
begin
  if v_me is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  if exists (select 1 from public.profiles where id = v_me and deleted_at is not null) then
    raise exception 'account already deleted' using errcode = 'P0002';
  end if;

  -- Private rows. Personal expenses carry their private splits with them (local_splits cascades).
  delete from public.local_settlements where owner_id = v_me;
  delete from public.local_splits where owner_id = v_me;
  delete from public.transactions where owner_id = v_me and group_id is null;
  delete from public.group_invites where created_by = v_me;

  -- Groups they made: to the longest-standing other member, or gone with their records if nobody's left.
  for v_group in select id from public.groups where created_by = v_me loop
    select profile_id into v_heir
    from public.group_members
    where group_id = v_group.id and profile_id <> v_me
    order by joined_at, profile_id
    limit 1;
    if v_heir is not null then
      update public.groups set created_by = v_heir where id = v_group.id;
    end if;
  end loop;

  delete from public.group_members where profile_id = v_me;

  -- Any group of theirs that's now empty, created by them or not.
  for v_group in
    select g.id from public.groups g
    where not exists (select 1 from public.group_members m where m.group_id = g.id)
      and (g.created_by = v_me
        or exists (select 1 from public.transactions t where t.group_id = g.id and t.owner_id = v_me)
        or exists (select 1 from public.settlements s where s.group_id = g.id and v_me in (s.from_profile_id, s.to_profile_id)))
  loop
    delete from public.settlements where group_id = v_group.id;
    delete from public.transactions where group_id = v_group.id;
    delete from public.groups where id = v_group.id;
  end loop;

  update public.profiles
  set display_name = case when p_keep_name then display_name else 'Former member' end,
      avatar_url = null,
      avatar_emoji = null,
      hue = null,
      upi_id = null,
      deleted_at = now()
  where id = v_me;

  delete from auth.users where id = v_me;
end;
$$;

revoke execute on function private.delete_account(uuid, boolean) from public, anon, authenticated;

create or replace function public.delete_my_account(p_keep_name boolean default false)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  perform private.delete_account(v_me, p_keep_name);
end;
$$;

revoke execute on function public.delete_my_account(boolean) from public, anon;
grant execute on function public.delete_my_account(boolean) to authenticated;

-- Who is this, and what would deleting them do? Exact, case-insensitive match; zero rows for a deleted account.
create function public.admin_find_account(p_email text)
returns table (
  user_id uuid,
  email text,
  display_name text,
  created_at timestamptz,
  last_sign_in_at timestamptz,
  deleted boolean,
  groups integer,
  groups_created integer,
  heirs jsonb,
  photos integer
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  perform private.assert_admin();
  if p_email is null or char_length(p_email) > 320 then
    raise exception 'bad email' using errcode = '22023';
  end if;
  return query
  select
    pr.id,
    u.email::text,
    pr.display_name,
    pr.created_at,
    u.last_sign_in_at,
    pr.deleted_at is not null,
    (select count(*) from public.group_members m where m.profile_id = pr.id)::integer,
    (select count(*) from public.groups g where g.created_by = pr.id)::integer,
    coalesce((
      select jsonb_agg(jsonb_build_object(
        'group', g.name,
        'heir', (select hp.display_name
                 from public.group_members m
                 join public.profiles hp on hp.id = m.profile_id
                 where m.group_id = g.id and m.profile_id <> pr.id
                 order by m.joined_at, m.profile_id
                 limit 1)
      ) order by g.name)
      from public.groups g where g.created_by = pr.id), '[]'::jsonb),
    (select count(*) from public.transactions t where t.owner_id = pr.id and t.receipt_storage_path is not null)::integer
  from auth.users u
  join public.profiles pr on pr.id = u.id
  where lower(u.email) = lower(btrim(p_email));
end;
$$;

-- Deletes the account exactly as the person would from the app, after the owner typed the person's email.
-- Returns the stored photo files of that person which no surviving expense points at, so the page can remove them.
create function public.admin_delete_account(p_user uuid, p_confirm_email text, p_keep_name boolean default false)
returns table (leftover_paths text[])
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_email text;
  v_groups integer;
  v_photos integer;
  v_today timestamptz := date_trunc('day', now() at time zone 'Asia/Kolkata') at time zone 'Asia/Kolkata';
begin
  perform private.assert_admin(10);
  perform private.admin_spend();

  if (select count(*) from private.admin_audit where action = 'delete_account' and at >= v_today) >= 5 then
    raise exception 'at most 5 deletions a day' using errcode = '54000';
  end if;

  if exists (select 1 from public.profiles where id = p_user and deleted_at is not null) then
    raise exception 'account already deleted' using errcode = 'P0002';
  end if;
  select lower(u.email) into v_email from auth.users u where u.id = p_user;
  if v_email is null then
    raise exception 'no such person' using errcode = 'P0002';
  end if;
  if exists (select 1 from private.admins where user_id = p_user) then
    raise exception 'an admin cannot be deleted from here' using errcode = '22023';
  end if;
  if p_confirm_email is null or lower(btrim(p_confirm_email)) <> v_email then
    raise exception 'the typed email does not match' using errcode = '22023';
  end if;

  select count(*) into v_groups from public.group_members where profile_id = p_user;
  select count(*) into v_photos from public.transactions where owner_id = p_user and receipt_storage_path is not null;

  perform private.delete_account(p_user, coalesce(p_keep_name, false));

  -- The user id only: the policy promises the email is gone.
  perform private.admin_log('delete_account', p_user, null,
    jsonb_build_object('groups', v_groups, 'photos', v_photos),
    jsonb_build_object('keep_name', coalesce(p_keep_name, false)));

  return query
  select coalesce(array_agg(o.name order by o.name), '{}'::text[])
  from storage.objects o
  where o.bucket_id = 'receipts'
    and (storage.foldername(o.name))[1] = p_user::text
    and not exists (select 1 from public.transactions t where t.receipt_storage_path = o.name);
end;
$$;

revoke execute on function public.admin_find_account(text), public.admin_delete_account(uuid, text, boolean) from public, anon;
grant execute on function public.admin_find_account(text), public.admin_delete_account(uuid, text, boolean) to authenticated;

commit;
