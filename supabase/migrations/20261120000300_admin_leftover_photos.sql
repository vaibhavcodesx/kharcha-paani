-- STAGED, NOT APPLIED (owner review: two NEW storage policies that let the admin delete files, plus a leftover-photo
-- view). Issues #355, #441. Design: docs/designs/admin-page.md. Step S4 of four. Apply after 20261120000200_admin_delete_account.sql.
-- This file is separate on purpose: the owner can decline it. Without it, removing a deleted account's photos is a manual
-- dashboard step (docs/engineering/runbooks/delete-account.md, step 5) and everything else keeps working.
--
-- What it is for. Deleting an account removes database rows, but not the person's photo files: a SQL function cannot remove
-- Storage files (deleting rows from storage.objects leaves the file behind). The admin page removes them through the Storage
-- API with the owner's own session, which needs a policy. This file adds exactly two:
--
--   receipts_admin_select_leftover   the admin can SEE a "leftover" file
--   receipts_admin_delete_leftover   the admin can DELETE a "leftover" file
--
-- A file is leftover only when BOTH hold: its folder's account is deleted (or gone), and no expense row points at the file
-- any more (a photo on a shared group expense that stays in history is not leftover). So even the admin cannot see or delete a
-- live person's photos, or the photo of an expense somebody still has. Both policies need the admin check (uid, email
-- and Google identity together), so they grant nothing to anyone else.
--
-- Also here: `admin_leftover_photos` (the list for the page), `admin_record_photo_cleanup` (recounts and logs), and
-- `admin_overview` extended with the leftover totals.
--
-- Additive only. Rollback: drop policy receipts_admin_select_leftover on storage.objects; drop policy receipts_admin_delete_leftover on storage.objects;
--   drop function public.admin_leftover_photos(uuid, integer), public.admin_record_photo_cleanup(uuid, integer);
--   drop function private.admin_can_touch_receipt(text), private.is_leftover_receipt(text);
--   and re-run public.admin_overview() from 20261120000000_admin_core.sql.

begin;

-- True when [p_name] ('<account id>/<expense id>/<file>') belongs to a deleted or missing account and no expense row points at it.
create function private.is_leftover_receipt(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_folder text := (storage.foldername(p_name))[1];
begin
  if v_folder is null or v_folder !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    return false;
  end if;
  -- A live account: its photos are never leftover.
  if exists (select 1 from public.profiles p where p.id = v_folder::uuid and p.deleted_at is null) then
    return false;
  end if;
  -- Still pointed at by an expense (for example on a shared group expense that stays in history).
  if exists (select 1 from public.transactions t where t.receipt_storage_path = p_name) then
    return false;
  end if;
  return true;
end;
$$;

revoke execute on function private.is_leftover_receipt(text) from public, anon, authenticated;

-- What the two policies call: the admin check and the leftover check together, so neither helper has to be callable by clients.
create function private.admin_can_touch_receipt(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.is_admin() and private.is_leftover_receipt(p_name);
$$;

revoke execute on function private.admin_can_touch_receipt(text) from public, anon;
grant execute on function private.admin_can_touch_receipt(text) to authenticated;

create policy receipts_admin_select_leftover on storage.objects
  for select to authenticated
  using (
    bucket_id = 'receipts'
    and private.admin_can_touch_receipt(name)
  );

create policy receipts_admin_delete_leftover on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'receipts'
    and private.admin_can_touch_receipt(name)
  );

-- The leftover files, for the page. [p_user] narrows it to one account's folder.
create function public.admin_leftover_photos(p_user uuid default null, p_limit integer default 200)
returns table (user_id uuid, path text, bytes bigint)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  perform private.assert_admin();
  return query
  select (storage.foldername(o.name))[1]::uuid,
         o.name,
         coalesce(nullif(o.metadata ->> 'size', '')::bigint, 0)
  from storage.objects o
  where o.bucket_id = 'receipts'
    and private.is_leftover_receipt(o.name)
    and (p_user is null or (storage.foldername(o.name))[1] = p_user::text)
  order by o.name
  limit least(greatest(coalesce(p_limit, 200), 1), 1000);
end;
$$;

-- After the page removed files through Storage: how many are still left for that account, and a log row.
create function public.admin_record_photo_cleanup(p_user uuid, p_removed integer)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_left integer;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if p_removed is null or p_removed < 0 or p_removed > 100000 then
    raise exception 'bad count' using errcode = '22023';
  end if;
  select count(*) into v_left
  from storage.objects o
  where o.bucket_id = 'receipts'
    and (storage.foldername(o.name))[1] = p_user::text
    and private.is_leftover_receipt(o.name);
  perform private.admin_log('photo_cleanup', p_user, null,
    null, jsonb_build_object('removed', p_removed, 'still_left', v_left));
  return v_left;
end;
$$;

-- admin_overview, now with the leftover totals under storage.
create or replace function public.admin_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_day date := (now() at time zone 'Asia/Kolkata')::date;
  v_total integer;
  v_deleted integer;
  v_active integer;
  v_friends integer;
  v_paid integer;
  v_ai jsonb;
  v_storage jsonb;
  v_versions jsonb;
begin
  perform private.assert_admin();

  select count(*) filter (where deleted_at is null), count(*) filter (where deleted_at is not null)
    into v_total, v_deleted
  from public.profiles;

  select count(distinct u) into v_active
  from (
    select owner_id as u from public.transactions where updated_at >= now() - interval '7 days'
    union
    select user_id as u from private.ai_reads where day >= v_day - 6
  ) x
  where u in (select id from public.profiles where deleted_at is null);

  select count(*) filter (where p.plan = 'friends'), count(*) filter (where p.plan = 'paid')
    into v_friends, v_paid
  from private.user_plans p
  join public.profiles pr on pr.id = p.user_id and pr.deleted_at is null;

  select coalesce(jsonb_agg(jsonb_build_object(
      'feature', c.feature,
      'used', coalesce((select sum(r.used) from private.ai_reads r where r.day = v_day and r.feature = c.feature), 0),
      'ceiling', (select per_day from private.ai_project_ceilings k where k.feature = c.feature)
    ) order by c.feature), '[]'::jsonb)
    into v_ai
  from private.ai_read_caps c;

  select jsonb_build_object(
      'objects', count(*),
      'bytes', coalesce(sum(nullif(o.metadata ->> 'size', '')::bigint), 0),
      'leftover_objects', count(*) filter (where private.is_leftover_receipt(o.name)),
      'leftover_bytes', coalesce(sum(nullif(o.metadata ->> 'size', '')::bigint) filter (where private.is_leftover_receipt(o.name)), 0))
    into v_storage
  from storage.objects o
  where o.bucket_id = 'receipts';

  select jsonb_build_object(
      'min', c.min_version_code,
      'recommended', c.recommended_version_code,
      'update_screen', s.update_screen_version_code,
      'latest_published', s.latest_published_version_code)
    into v_versions
  from public.app_config c, private.admin_settings s;

  return jsonb_build_object(
    'as_of', now(),
    'people', jsonb_build_object('total', v_total, 'active_7d', v_active, 'deleted', v_deleted),
    'plans', jsonb_build_object('free', v_total - v_friends - v_paid, 'friends', v_friends, 'paid', v_paid),
    'ai_today', v_ai,
    'ai_paused', exists (select 1 from private.admin_settings where ai_pause_snapshot is not null),
    'storage', v_storage,
    'db', jsonb_build_object('bytes', pg_database_size(current_database())),
    'versions', v_versions
  );
end;
$$;

revoke execute on function public.admin_leftover_photos(uuid, integer), public.admin_record_photo_cleanup(uuid, integer)
  from public, anon;
grant execute on function public.admin_leftover_photos(uuid, integer), public.admin_record_photo_cleanup(uuid, integer)
  to authenticated;

commit;
