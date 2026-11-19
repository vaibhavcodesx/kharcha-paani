-- STAGED, NOT APPLIED (owner review: new identity check and new security-definer functions). Issues #355, #438.
-- Design: docs/designs/admin-page.md (sections 1 and 2). Step S1 of four (S2 writes, S3 delete, S4 photos).
--
-- What it is for. The owner's admin page needs a way to prove "this caller is the owner" inside the database,
-- an append-only log of what the owner did, and read-only functions for the first screens. This file adds those
-- and nothing that changes data: every function here only reads, except that the helper `private.admin_log`
-- appends to the log (used by the write functions in S2).
--
-- Who can do what:
--   * The tables are in `private`, which is not exposed, and nobody (anon, authenticated) has any privilege on them.
--   * Every `admin_*` function is security definer, `set search_path = ''`, executable by `authenticated` only,
--     and starts with `private.assert_admin()`, which raises 42501 "not an admin" unless ALL hold: the caller is
--     signed in and not anonymous; the uid has a row in `private.admins`; that row's email equals the token's
--     email; and the same user has a Google identity with that email. The email alone is never enough.
--   * `admin_whoami()` never raises: it is how the page decides what to show.
--   * The owner adds themself once, by hand, in the SQL editor (the row is never in a migration, so no email
--     enters the repo):
--       insert into private.admins (user_id, email)
--         select id, lower(email) from auth.users where lower(email) = lower('<owner Google email>');
--   * Break-glass: `delete from private.admins;` in the dashboard SQL editor locks everyone out at once.
--
-- Additive only. Until a row is added to `private.admins`, every admin function refuses everybody.
-- Rollback: drop function public.admin_whoami(), public.admin_overview(), public.admin_list_users(text, integer, integer),
--   public.admin_get_limits(), public.admin_list_audit(integer, bigint);
--   drop function private.assert_admin(integer), private.is_admin(), private.admin_log(text, uuid, text, jsonb, jsonb);
--   drop table private.admin_rate, private.admin_settings, private.admin_audit, private.admins;

begin;

create table private.admins (
  user_id uuid primary key references auth.users (id) on delete cascade,
  email text not null check (email = lower(email)),
  added_at timestamptz not null default now()
);

-- Append-only: no function updates or deletes a row. A deletion logs the user id and never the email.
create table private.admin_audit (
  id bigserial primary key,
  at timestamptz not null default now(),
  admin_id uuid not null,
  admin_email text not null,
  session_id uuid,
  action text not null,
  target_user uuid,
  target text,
  before jsonb,
  after jsonb
);

-- One row only: facts the owner types in, and the saved ceilings while AI is paused (S2).
create table private.admin_settings (
  id boolean primary key default true check (id),
  update_screen_version_code integer check (update_screen_version_code >= 0),
  latest_published_version_code integer check (latest_published_version_code >= 0),
  ai_pause_snapshot jsonb,
  updated_at timestamptz not null default now()
);
insert into private.admin_settings (id) values (true);

-- Writes per admin per 10-minute window (S2 spends from it).
create table private.admin_rate (
  admin_id uuid not null,
  window_start timestamptz not null,
  writes integer not null default 0 check (writes >= 0),
  primary key (admin_id, window_start)
);

revoke all on private.admins, private.admin_audit, private.admin_settings, private.admin_rate
  from public, anon, authenticated;
revoke all on sequence private.admin_audit_id_seq from public, anon, authenticated;

-- True when the caller passes checks 1 to 3 (never raises). Safe to call from a storage policy as
-- `(select private.is_admin())`.
create function private.is_admin()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_email text := lower(coalesce(auth.jwt() ->> 'email', ''));
begin
  if v_uid is null or v_email = '' then
    return false;
  end if;
  if coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    return false;
  end if;
  return exists (
    select 1
    from private.admins a
    where a.user_id = v_uid
      and a.email = v_email
      and exists (
        select 1
        from auth.identities i
        where i.user_id = v_uid
          and i.provider = 'google'
          and lower(coalesce(i.identity_data ->> 'email', '')) = v_email
      )
  );
end;
$$;

-- Raises 42501 "not an admin" unless is_admin(). With p_fresh_minutes (deletion uses 10), the token's session
-- must also have been created that recently, so an old open tab cannot destroy anything. Returns the admin's uid.
create function private.assert_admin(p_fresh_minutes integer default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_session_started timestamptz;
begin
  if not private.is_admin() then
    raise exception 'not an admin' using errcode = '42501';
  end if;
  if p_fresh_minutes is not null then
    select s.created_at into v_session_started
    from auth.sessions s
    where s.id = nullif(auth.jwt() ->> 'session_id', '')::uuid
      and s.user_id = auth.uid();
    if v_session_started is null or v_session_started < now() - make_interval(mins => p_fresh_minutes) then
      raise exception 'not an admin' using errcode = '42501';
    end if;
  end if;
  return auth.uid();
end;
$$;

-- Appends one audit row for the caller. The write functions (S2 to S4) call it inside the same transaction as
-- their change. Not executable by anyone but the functions that own it.
create function private.admin_log(
  p_action text,
  p_target_user uuid,
  p_target text,
  p_before jsonb,
  p_after jsonb
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id bigint;
begin
  insert into private.admin_audit (admin_id, admin_email, session_id, action, target_user, target, before, after)
  values (
    auth.uid(),
    lower(coalesce(auth.jwt() ->> 'email', '')),
    nullif(auth.jwt() ->> 'session_id', '')::uuid,
    p_action, p_target_user, p_target, p_before, p_after
  )
  returning id into v_id;
  return v_id;
end;
$$;

revoke execute on function private.is_admin(), private.assert_admin(integer),
  private.admin_log(text, uuid, text, jsonb, jsonb) from public, anon;
-- (S4 grants `private.is_admin()` to authenticated for its storage policies; nothing here does.)

-- Never raises: false for everybody except the owner.
create function public.admin_whoami()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.is_admin();
$$;

-- The numbers for the Today screen.
create function public.admin_overview()
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
      'bytes', coalesce(sum(nullif(o.metadata ->> 'size', '')::bigint), 0))
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

-- People, searched on the server, 50 a page. The query matches email or name; % and _ are taken literally.
create function public.admin_list_users(
  p_query text default null,
  p_limit integer default 50,
  p_offset integer default 0
)
returns table (
  user_id uuid,
  email text,
  display_name text,
  plan text,
  created_at timestamptz,
  last_sign_in_at timestamptz,
  deleted boolean,
  ai_today integer,
  photos integer,
  photo_bytes bigint,
  groups integer,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_day date := (now() at time zone 'Asia/Kolkata')::date;
  v_like text;
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 100);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
  perform private.assert_admin();
  if p_query is not null and char_length(p_query) > 100 then
    raise exception 'query too long' using errcode = '22023';
  end if;
  if nullif(btrim(p_query), '') is not null then
    v_like := '%' || replace(replace(replace(btrim(p_query), '\', '\\'), '%', '\%'), '_', '\_') || '%';
  end if;

  return query
  select
    pr.id,
    u.email::text,
    pr.display_name,
    coalesce(up.plan, 'free'),
    pr.created_at,
    u.last_sign_in_at,
    pr.deleted_at is not null,
    coalesce((select sum(r.used) from private.ai_reads r where r.user_id = pr.id and r.day = v_day), 0)::integer,
    (select count(*) from public.transactions t where t.owner_id = pr.id and t.receipt_storage_path is not null)::integer,
    coalesce((select sum(nullif(o.metadata ->> 'size', '')::bigint) from storage.objects o
              where o.bucket_id = 'receipts' and (storage.foldername(o.name))[1] = pr.id::text), 0)::bigint,
    (select count(*) from public.group_members gm where gm.profile_id = pr.id)::integer,
    count(*) over ()
  from public.profiles pr
  left join auth.users u on u.id = pr.id
  left join private.user_plans up on up.user_id = pr.id
  where v_like is null
     or u.email ilike v_like escape '\'
     or pr.display_name ilike v_like escape '\'
  order by pr.deleted_at is not null, pr.created_at desc, pr.id
  limit v_limit offset v_offset;
end;
$$;

-- Caps per plan, the project ceilings with today's use, and whether AI is paused.
create function public.admin_get_limits()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_day date := (now() at time zone 'Asia/Kolkata')::date;
begin
  perform private.assert_admin();
  return jsonb_build_object(
    'caps', coalesce((
      select jsonb_agg(jsonb_build_object(
        'feature', c.feature,
        'free', c.per_day,
        'friends', coalesce((select pc.per_day from private.ai_plan_caps pc where pc.plan = 'friends' and pc.feature = c.feature), c.per_day)
      ) order by c.feature)
      from private.ai_read_caps c), '[]'::jsonb),
    'ceilings', coalesce((
      select jsonb_agg(jsonb_build_object(
        'feature', c.feature,
        'per_day', k.per_day,
        'used_today', coalesce((select pr.used from private.ai_project_reads pr where pr.day = v_day and pr.feature = c.feature), 0)
      ) order by c.feature)
      from private.ai_read_caps c
      left join private.ai_project_ceilings k on k.feature = c.feature), '[]'::jsonb),
    'paused', exists (select 1 from private.admin_settings where ai_pause_snapshot is not null)
  );
end;
$$;

-- The log, newest first, 50 a page, keyset paged: pass the smallest id you have to get older rows.
create function public.admin_list_audit(p_limit integer default 50, p_before bigint default null)
returns table (
  id bigint,
  at timestamptz,
  admin_email text,
  action text,
  target_user uuid,
  target text,
  before jsonb,
  after jsonb
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  perform private.assert_admin();
  return query
  select a.id, a.at, a.admin_email, a.action, a.target_user, a.target, a.before, a.after
  from private.admin_audit a
  where p_before is null or a.id < p_before
  order by a.id desc
  limit least(greatest(coalesce(p_limit, 50), 1), 200);
end;
$$;

revoke execute on function public.admin_whoami(), public.admin_overview(),
  public.admin_list_users(text, integer, integer), public.admin_get_limits(),
  public.admin_list_audit(integer, bigint) from public, anon;
grant execute on function public.admin_whoami(), public.admin_overview(),
  public.admin_list_users(text, integer, integer), public.admin_get_limits(),
  public.admin_list_audit(integer, bigint) to authenticated;

commit;
