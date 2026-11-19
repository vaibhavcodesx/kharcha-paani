-- Checks for the migration 20261120000000_admin_core (issues #355, #438): who counts as the admin, the append-only log,
-- and the read-only functions for the Today, People and Limits screens.
--
-- Same conventions as ai_plans_and_ceiling_test.sql: one transaction ending in ROLLBACK, one result row per check.
-- Staged, not applied. Run it on a local Postgres with `node supabase/tests/local/run.mjs supabase/tests/admin_core_test.sql`
-- (the runner replays every migration first), or on the live project only inside this rolled-back transaction after the
-- migration body has been applied.

begin;

create table public._rls_results (
  n serial primary key,
  test text not null,
  passed boolean not null,
  detail text
);

-- A signed-in caller with a full token: uid, email, a session, and an anonymous flag.
create function public._as_jwt(p_user uuid, p_email text, p_session uuid default null, p_anonymous boolean default false)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object(
    'sub', p_user, 'role', 'authenticated', 'email', p_email,
    'session_id', p_session, 'is_anonymous', p_anonymous)::text, true);
  set local role authenticated;
end $$;

create function public._as_anon() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '{"role":"anon"}', true);
  set local role anon;
end $$;

create function public._as_admin() returns void language plpgsql as $$
begin
  reset role;
  perform set_config('request.jwt.claims', '', true);
end $$;

create function public._record(p_test text, p_passed boolean, p_detail text default null) returns void
language sql as $$
  insert into public._rls_results (test, passed, detail) values (p_test, p_passed, p_detail);
$$;

create function public._expect_state(p_test text, p_sql text, p_state text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have failed with ' || p_state);
exception when others then
  perform public._record(p_test, sqlstate = p_state, sqlstate || ': ' || sqlerrm);
end $$;

create function public._expect_value(p_test text, p_sql text, p_expected bigint) returns void language plpgsql as $$
declare v bigint;
begin
  execute p_sql into v;
  perform public._record(p_test, v is not distinct from p_expected, format('got %s, expected %s', v, p_expected));
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

create function public._expect_text(p_test text, p_sql text, p_expected text) returns void language plpgsql as $$
declare v text;
begin
  execute p_sql into v;
  perform public._record(p_test, v is not distinct from p_expected, format('got %s, expected %s', v, p_expected));
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

-- The client roles cannot reach the private schema at all (that is the point), so the two private helpers are
-- exercised through test-only wrappers owned by the test's own role.
create function public._t_assert_fresh(p_minutes integer) returns boolean language plpgsql security definer
set search_path = '' as $$ begin return private.assert_admin(p_minutes) is not null; end $$;
create function public._t_log(p_action text, p_target text, p_before jsonb, p_after jsonb) returns bigint language plpgsql security definer
set search_path = '' as $$ begin perform private.assert_admin(); return private.admin_log(p_action, null, p_target, p_before, p_after); end $$;

grant all on public._rls_results to authenticated, anon;
grant usage on sequence public._rls_results_n_seq to authenticated, anon;
grant execute on function public._as_jwt(uuid, text, uuid, boolean), public._as_anon(), public._as_admin(),
  public._record(text, boolean, text), public._expect_state(text, text, text), public._expect_value(text, text, bigint),
  public._expect_text(text, text, text), public._t_assert_fresh(integer), public._t_log(text, text, jsonb, jsonb) to authenticated, anon;

-- Setup.
--   Owen   : the admin, with a Google identity and an admins row.
--   Bob    : an ordinary signed-in person with a Google identity (not an admin).
--   Eve    : has an admins row, but her only identity is an email login (no Google identity).
--   Zed    : has an admins row and a Google identity with a different email than the admins row.
--   Gone   : a deleted account (tombstone profile, no sign-in).
insert into auth.users (id, email, raw_user_meta_data, last_sign_in_at) values
  ('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test', '{"full_name":"Owen"}', now()),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@admin.test',  '{"full_name":"Bob_Smith"}', now()),
  ('00000000-0000-4000-8000-0000000000c1', 'eve@admin.test',  '{"full_name":"Eve"}', now()),
  ('00000000-0000-4000-8000-0000000000d1', 'zed@admin.test',  '{"full_name":"Zed"}', now());
insert into auth.identities (user_id, provider, identity_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'google', '{"email":"owen@admin.test"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'google', '{"email":"bob@admin.test"}'),
  ('00000000-0000-4000-8000-0000000000c1', 'email',  '{"email":"eve@admin.test"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'google', '{"email":"someone-else@admin.test"}');
insert into private.admins (user_id, email) values
  ('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test'),
  ('00000000-0000-4000-8000-0000000000c1', 'eve@admin.test'),
  ('00000000-0000-4000-8000-0000000000d1', 'zed@admin.test');
insert into auth.sessions (id, user_id, created_at) values
  ('00000000-0000-4000-8000-0000000000e1', '00000000-0000-4000-8000-0000000000a1', now()),
  ('00000000-0000-4000-8000-0000000000e2', '00000000-0000-4000-8000-0000000000a1', now() - interval '1 hour');
insert into private.user_plans (user_id, plan) values ('00000000-0000-4000-8000-0000000000b1', 'friends');
-- A deleted account: a profile that is a tombstone, with no auth row.
insert into public.profiles (id, display_name, deleted_at)
  values ('00000000-0000-4000-8000-0000000000f1', 'Former member', now());
-- Some use today, a photo reference, and a stored photo for Bob.
insert into private.ai_reads (user_id, day, feature, used)
  values ('00000000-0000-4000-8000-0000000000b1', (now() at time zone 'Asia/Kolkata')::date, 'quick_add', 7);
insert into storage.buckets (id, name, public) values ('receipts', 'receipts', false) on conflict (id) do nothing;
insert into storage.objects (bucket_id, name, metadata)
  values ('receipts', '00000000-0000-4000-8000-0000000000b1/00000000-0000-4000-8000-0000000000aa/t1.webp', '{"size": 1000}');
insert into public.transactions (id, owner_id, type, amount, category, receipt_storage_path)
  values ('00000000-0000-4000-8000-0000000000aa', '00000000-0000-4000-8000-0000000000b1', 'expense', 10, 'Food',
          '00000000-0000-4000-8000-0000000000b1/00000000-0000-4000-8000-0000000000aa/t1.webp');

-- 1. Signed out: every admin function is refused (permission denied, not even "not an admin").
select public._as_anon();
select public._expect_state('anon: admin_whoami is refused', $q$ select public.admin_whoami() $q$, '42501');
select public._expect_state('anon: admin_overview is refused', $q$ select public.admin_overview() $q$, '42501');
select public._expect_state('anon: admin_list_users is refused', $q$ select * from public.admin_list_users() $q$, '42501');
select public._expect_state('anon: admin_get_limits is refused', $q$ select public.admin_get_limits() $q$, '42501');
select public._expect_state('anon: admin_list_audit is refused', $q$ select * from public.admin_list_audit() $q$, '42501');
select public._expect_state('anon: the helpers are refused', $q$ select private.assert_admin() $q$, '42501');

-- 2. A signed-in person who is not an admin: the page's question is answered no, everything else raises 42501.
select public._as_jwt('00000000-0000-4000-8000-0000000000b1', 'bob@admin.test');
select public._expect_text('non-admin: admin_whoami is false', $q$ select public.admin_whoami()::text $q$, 'false');
select public._expect_state('non-admin: admin_overview raises 42501', $q$ select public.admin_overview() $q$, '42501');
select public._expect_state('non-admin: admin_list_users raises 42501', $q$ select * from public.admin_list_users() $q$, '42501');
select public._expect_state('non-admin: admin_get_limits raises 42501', $q$ select public.admin_get_limits() $q$, '42501');
select public._expect_state('non-admin: admin_list_audit raises 42501', $q$ select * from public.admin_list_audit() $q$, '42501');
select public._expect_state('non-admin: cannot write the log helper', $q$ select public._t_log('x', null, null, null) $q$, '42501');

-- 3. The identity check needs the uid, the email and the Google identity together.
select public._as_jwt('00000000-0000-4000-8000-0000000000c1', 'eve@admin.test');
select public._expect_text('admins row but no Google identity: refused', $q$ select public.admin_whoami()::text $q$, 'false');
select public._as_jwt('00000000-0000-4000-8000-0000000000d1', 'zed@admin.test');
select public._expect_text('admins row but the Google identity has another email: refused', $q$ select public.admin_whoami()::text $q$, 'false');
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'someone-else@admin.test');
select public._expect_text('right uid, wrong token email: refused', $q$ select public.admin_whoami()::text $q$, 'false');
select public._as_jwt('00000000-0000-4000-8000-0000000000b1', 'owen@admin.test');
select public._expect_text('right email, wrong uid: refused', $q$ select public.admin_whoami()::text $q$, 'false');
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test', null, true);
select public._expect_text('an anonymous token: refused', $q$ select public.admin_whoami()::text $q$, 'false');
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'OWEN@admin.test');
select public._expect_text('the token email is compared in lower case', $q$ select public.admin_whoami()::text $q$, 'true');

-- Freshness: a session made now passes a 10-minute check, one from an hour ago does not, and a token with no session does not.
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test', '00000000-0000-4000-8000-0000000000e1');
select public._expect_text('a fresh session passes assert_admin(10)', $q$ select public._t_assert_fresh(10)::text $q$, 'true');
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test', '00000000-0000-4000-8000-0000000000e2');
select public._expect_state('an hour-old session fails assert_admin(10)', $q$ select public._t_assert_fresh(10) $q$, '42501');
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test');
select public._expect_state('no session fails assert_admin(10)', $q$ select public._t_assert_fresh(10) $q$, '42501');

-- The admin.
select public._as_jwt('00000000-0000-4000-8000-0000000000a1', 'owen@admin.test');
select public._expect_text('admin: admin_whoami is true', $q$ select public.admin_whoami()::text $q$, 'true');

-- 13. The numbers equal the fixtures.
select public._expect_value('overview: 4 live people', $q$ select (public.admin_overview() -> 'people' ->> 'total')::int $q$, 4);
select public._expect_value('overview: 1 deleted', $q$ select (public.admin_overview() -> 'people' ->> 'deleted')::int $q$, 1);
select public._expect_value('overview: 1 friend, 3 free', $q$ select (public.admin_overview() -> 'plans' ->> 'friends')::int * 10 + (public.admin_overview() -> 'plans' ->> 'free')::int $q$, 13);
select public._expect_value('overview: quick_add used 7 today',
  $q$ select (e ->> 'used')::int from jsonb_array_elements(public.admin_overview() -> 'ai_today') e where e ->> 'feature' = 'quick_add' $q$, 7);
select public._expect_value('overview: 1 stored photo, 1000 bytes',
  $q$ select (public.admin_overview() -> 'storage' ->> 'objects')::int * 10000 + (public.admin_overview() -> 'storage' ->> 'bytes')::int $q$, 11000);
select public._expect_text('overview: not paused', $q$ select public.admin_overview() ->> 'ai_paused' $q$, 'false');
select public._expect_value('overview: the database size is a positive number', $q$ select ((public.admin_overview() -> 'db' ->> 'bytes')::bigint > 0)::int $q$, 1);
select public._expect_value('overview: min version is 0 and nothing is published yet',
  $q$ select (public.admin_overview() -> 'versions' ->> 'min')::int + coalesce((public.admin_overview() -> 'versions' ->> 'latest_published')::int, 0) $q$, 0);

select public._expect_value('list: everyone, deleted last', $q$ select count(*) from public.admin_list_users() $q$, 5);
select public._expect_value('list: total_count is the whole count', $q$ select max(total_count) from public.admin_list_users(null, 2, 0) $q$, 5);
select public._expect_value('list: the limit is honoured', $q$ select count(*) from public.admin_list_users(null, 2, 0) $q$, 2);
select public._expect_text('list: the deleted account is last',
  $q$ select display_name from public.admin_list_users() order by deleted desc, created_at limit 1 $q$, 'Former member');
select public._expect_value('list: search by email', $q$ select count(*) from public.admin_list_users('bob@') $q$, 1);
select public._expect_value('list: search by name', $q$ select count(*) from public.admin_list_users('eve') $q$, 1);
select public._expect_value('list: a percent sign is taken literally, so it matches nobody', $q$ select count(*) from public.admin_list_users('a%') $q$, 0);
select public._expect_value('list: an underscore is taken literally, so b_b matches nobody', $q$ select count(*) from public.admin_list_users('b_b') $q$, 0);
select public._expect_value('list: a literal underscore finds Bob_Smith', $q$ select count(*) from public.admin_list_users('Bob_S') $q$, 1);
select public._expect_value('list: Bob shows 7 AI reads and 1 photo of 1000 bytes',
  $q$ select ai_today * 10000 + photos * 1000 + (photo_bytes / 1000) from public.admin_list_users('bob@') $q$, 71001);
select public._expect_text('list: Bob is on friends', $q$ select plan from public.admin_list_users('bob@') $q$, 'friends');
select public._expect_state('list: a 101-character query is refused', $q$ select * from public.admin_list_users(repeat('a', 101)) $q$, '22023');

select public._expect_value('limits: four kinds with caps', $q$ select jsonb_array_length(public.admin_get_limits() -> 'caps') $q$, 4);
select public._expect_value('limits: the friends quick_add cap is 150',
  $q$ select (e ->> 'friends')::int from jsonb_array_elements(public.admin_get_limits() -> 'caps') e where e ->> 'feature' = 'quick_add' $q$, 150);
select public._expect_value('limits: the quick_add ceiling is 2000',
  $q$ select (e ->> 'per_day')::int from jsonb_array_elements(public.admin_get_limits() -> 'ceilings') e where e ->> 'feature' = 'quick_add' $q$, 2000);

select public._expect_value('audit: empty at the start', $q$ select count(*) from public.admin_list_audit() $q$, 0);

-- 12. The log helper appends exactly one row, with the caller's identity and no email in a target.
select public._expect_value('the log helper returns the new id', $q$ select (public._t_log('test', 'x', '{"a":1}', '{"a":2}') > 0)::int $q$, 1);
select public._expect_value('audit: one row after one log call', $q$ select count(*) from public.admin_list_audit() $q$, 1);
select public._expect_text('audit: the row names the admin by email and keeps before and after',
  $q$ select admin_email || ' ' || (before ->> 'a') || (after ->> 'a') from public.admin_list_audit() $q$, 'owen@admin.test 12');
select public._expect_value('audit: paging by id returns older rows only',
  $q$ select count(*) from public.admin_list_audit(50, (select max(id) from public.admin_list_audit())) $q$, 0);

-- Nobody but the functions can touch the private tables, including the admin.
select public._expect_state('admin: cannot select the admins table directly', $q$ select * from private.admins $q$, '42501');
select public._expect_state('admin: cannot select the log table directly', $q$ select * from private.admin_audit $q$, '42501');
select public._expect_state('admin: cannot write the log table directly',
  $q$ insert into private.admin_audit (admin_id, admin_email, action) values ('00000000-0000-4000-8000-0000000000a1', 'x', 'y') $q$, '42501');
select public._expect_state('admin: cannot read the settings table directly', $q$ select * from private.admin_settings $q$, '42501');

-- Bob cannot call the private helpers directly either.
select public._as_jwt('00000000-0000-4000-8000-0000000000b1', 'bob@admin.test');
select public._expect_state('non-admin: cannot call private.is_admin directly', $q$ select private.is_admin() $q$, '42501');
select public._expect_state('non-admin: cannot select the admins table', $q$ select * from private.admins $q$, '42501');

select public._as_admin();
select n, test, passed, detail from public._rls_results order by n;

rollback;
