-- Checks for the migration 20261120000200_admin_delete_account (issues #355, #440): deleting an account by email from the
-- admin page, and the refactor that makes delete_my_account a wrapper (its own test file passes unchanged).
--
-- Same conventions as admin_core_test.sql: one transaction ending in ROLLBACK, one result row per check.
-- Staged, not applied. Run: `node supabase/tests/local/run.mjs supabase/tests/admin_delete_account_test.sql`
-- (the runner replays every migration first).

begin;

create table public._rls_results (
  n serial primary key,
  test text not null,
  passed boolean not null,
  detail text
);

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

create function public._expect_ok(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, true, null);
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
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

grant all on public._rls_results to authenticated, anon;
grant usage on sequence public._rls_results_n_seq to authenticated, anon;
grant execute on function public._as_jwt(uuid, text, uuid, boolean), public._as_anon(), public._as_admin(),
  public._record(text, boolean, text), public._expect_state(text, text, text), public._expect_ok(text, text),
  public._expect_value(text, text, bigint), public._expect_text(text, text, text) to authenticated, anon;

-- Setup.
--   Owen : the admin (fresh session e1, an hour-old session e2).
--   Dee  : the person whose account is deleted. Made the group "Goa" with Hal. Has a private expense with a photo,
--          a Goa expense with a photo (stays), and a stray stored photo nothing points at.
--   Hal  : Dee's groupmate and the longest-standing other member, so he inherits Goa.
--   Kay  : deleted with her name kept.   Lou: only used to hit the daily limit.   Mia: deletes herself from the app.
insert into auth.users (id, email, raw_user_meta_data, last_sign_in_at) values
  ('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '{"full_name":"Owen"}', now()),
  ('00000000-0000-4000-8000-0000000000d3', 'dee@del.test',  '{"full_name":"Dee"}', now()),
  ('00000000-0000-4000-8000-0000000000e3', 'hal@del.test',  '{"full_name":"Hal"}', now()),
  ('00000000-0000-4000-8000-0000000000f3', 'kay@del.test',  '{"full_name":"Kay"}', now()),
  ('00000000-0000-4000-8000-000000000013', 'lou@del.test',  '{"full_name":"Lou"}', now()),
  ('00000000-0000-4000-8000-000000000023', 'mia@del.test',  '{"full_name":"Mia"}', now());
insert into auth.identities (user_id, provider, identity_data) values
  ('00000000-0000-4000-8000-0000000000a3', 'google', '{"email":"owen@del.test"}');
insert into private.admins (user_id, email) values ('00000000-0000-4000-8000-0000000000a3', 'owen@del.test');
insert into auth.sessions (id, user_id, created_at) values
  ('00000000-0000-4000-8000-0000000000b3', '00000000-0000-4000-8000-0000000000a3', now()),
  ('00000000-0000-4000-8000-0000000000c3', '00000000-0000-4000-8000-0000000000a3', now() - interval '1 hour');

insert into public.groups (id, name, created_by) values
  ('00000000-0000-4000-8000-0000000000aa', 'Goa', '00000000-0000-4000-8000-0000000000d3');
insert into public.group_members (group_id, profile_id, joined_at) values
  ('00000000-0000-4000-8000-0000000000aa', '00000000-0000-4000-8000-0000000000d3', now() - interval '10 days'),
  ('00000000-0000-4000-8000-0000000000aa', '00000000-0000-4000-8000-0000000000e3', now() - interval '5 days');
insert into public.transactions (id, owner_id, type, amount, category, group_id, receipt_storage_path) values
  ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-0000000000d3', 'expense', 10, 'Food', null,
   '00000000-0000-4000-8000-0000000000d3/00000000-0000-4000-8000-0000000000a1/own.webp'),
  ('00000000-0000-4000-8000-0000000000a2', '00000000-0000-4000-8000-0000000000d3', 'expense', 20, 'Food', '00000000-0000-4000-8000-0000000000aa',
   '00000000-0000-4000-8000-0000000000d3/00000000-0000-4000-8000-0000000000a2/shared.webp');
insert into storage.buckets (id, name, public) values ('receipts', 'receipts', false) on conflict (id) do nothing;
insert into storage.objects (bucket_id, name, metadata) values
  ('receipts', '00000000-0000-4000-8000-0000000000d3/00000000-0000-4000-8000-0000000000a1/own.webp', '{"size": 10}'),
  ('receipts', '00000000-0000-4000-8000-0000000000d3/00000000-0000-4000-8000-0000000000a2/shared.webp', '{"size": 10}'),
  ('receipts', '00000000-0000-4000-8000-0000000000d3/stray/stray.webp', '{"size": 10}');

-- 1. Signed out, and an ordinary person: refused.
select public._as_anon();
select public._expect_state('anon: admin_find_account is refused', $q$ select * from public.admin_find_account('dee@del.test') $q$, '42501');
select public._expect_state('anon: admin_delete_account is refused',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', 'dee@del.test') $q$, '42501');
select public._as_jwt('00000000-0000-4000-8000-0000000000e3', 'hal@del.test');
select public._expect_state('non-admin: admin_find_account raises 42501', $q$ select * from public.admin_find_account('dee@del.test') $q$, '42501');
select public._expect_state('non-admin: admin_delete_account raises 42501',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', 'dee@del.test') $q$, '42501');
select public._expect_state('non-admin: private.delete_account is out of reach',
  $q$ select private.delete_account('00000000-0000-4000-8000-0000000000d3', false) $q$, '42501');

-- 2. The preview.
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '00000000-0000-4000-8000-0000000000b3');
select public._expect_value('find: one row for Dee, any letter case', $q$ select count(*) from public.admin_find_account('  DEE@del.test ') $q$, 1);
select public._expect_value('find: nobody for an unknown email', $q$ select count(*) from public.admin_find_account('nobody@del.test') $q$, 0);
select public._expect_text('find: Dee is in 1 group, made 1, photos 2, and Hal inherits Goa',
  $q$ select groups || '/' || groups_created || '/' || photos || ' ' || (heirs -> 0 ->> 'group') || '>' || (heirs -> 0 ->> 'heir')
     from public.admin_find_account('dee@del.test') $q$, '1/1/2 Goa>Hal');

-- 3. Every guard.
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '00000000-0000-4000-8000-0000000000c3');
select public._expect_state('delete: a session older than 10 minutes is refused',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', 'dee@del.test') $q$, '42501');
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test');
select public._expect_state('delete: a token with no session is refused',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', 'dee@del.test') $q$, '42501');
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '00000000-0000-4000-8000-0000000000b3');
select public._expect_state('delete: the wrong typed email is refused',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', 'dede@del.test') $q$, '22023');
select public._expect_state('delete: an admin cannot be deleted',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000a3', 'owen@del.test') $q$, '22023');
select public._expect_state('delete: an unknown person is P0002',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-00000000dead', 'x@del.test') $q$, 'P0002');
select public._as_admin();
select public._expect_value('refused deletions changed nothing', $q$ select count(*) from public.transactions where owner_id = '00000000-0000-4000-8000-0000000000d3' $q$, 2);
select public._expect_value('and logged nothing', $q$ select count(*) from private.admin_audit $q$, 0);

-- 4. The deletion itself.
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '00000000-0000-4000-8000-0000000000b3');
select public._expect_value('delete: Dee is deleted; two photo files are left behind (her private one and the stray)',
  $q$ select cardinality(leftover_paths) from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', ' Dee@del.test ', false) $q$, 2);
select public._as_admin();
select public._expect_value('delete: the sign-in is gone', $q$ select count(*) from auth.users where id = '00000000-0000-4000-8000-0000000000d3' $q$, 0);
select public._expect_text('delete: the profile is a tombstone named Former member',
  $q$ select display_name from public.profiles where id = '00000000-0000-4000-8000-0000000000d3' $q$, 'Former member');
select public._expect_text('delete: Goa passes to Hal',
  $q$ select display_name from public.profiles where id = (select created_by from public.groups where id = '00000000-0000-4000-8000-0000000000aa') $q$, 'Hal');
select public._expect_value('delete: her private expense is gone, the Goa expense stays',
  $q$ select count(*) from public.transactions where owner_id = '00000000-0000-4000-8000-0000000000d3' and group_id is not null $q$, 1);
select public._expect_value('delete: exactly one log row', $q$ select count(*) from private.admin_audit where action = 'delete_account' $q$, 1);
select public._expect_value('delete: the log names the user id and carries no email anywhere',
  $q$ select count(*) from private.admin_audit where action = 'delete_account'
        and target_user = '00000000-0000-4000-8000-0000000000d3'
        and coalesce(target, '') not like '%@%' and coalesce(before::text, '') not like '%@%' and coalesce(after::text, '') not like '%@%' $q$, 1);
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '00000000-0000-4000-8000-0000000000b3');
select public._expect_value('find: a deleted account is no longer found by email',
  $q$ select count(*) from public.admin_find_account('dee@del.test') $q$, 0);
select public._expect_state('delete: deleting again is refused',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000d3', 'dee@del.test') $q$, 'P0002');

-- Keep my name.
select public._expect_ok('delete: Kay, keeping her name',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-0000000000f3', 'kay@del.test', true) $q$);
select public._as_admin();
select public._expect_text('delete: Kay is still called Kay',
  $q$ select display_name from public.profiles where id = '00000000-0000-4000-8000-0000000000f3' $q$, 'Kay');

-- The daily limit: five a day. Two are done; add three more log rows for today, then the next is refused.
insert into private.admin_audit (admin_id, admin_email, action) select '00000000-0000-4000-8000-0000000000a3', 'owen@del.test', 'delete_account' from generate_series(1, 3);
select public._as_jwt('00000000-0000-4000-8000-0000000000a3', 'owen@del.test', '00000000-0000-4000-8000-0000000000b3');
select public._expect_state('delete: the sixth deletion in an India day is refused',
  $q$ select * from public.admin_delete_account('00000000-0000-4000-8000-000000000013', 'lou@del.test') $q$, '54000');
select public._as_admin();
select public._expect_value('delete: Lou is untouched', $q$ select count(*) from auth.users where id = '00000000-0000-4000-8000-000000000013' $q$, 1);

-- 9. A person deleting themself from the app still works, through the wrapper.
select public._as_jwt('00000000-0000-4000-8000-000000000023', 'mia@del.test');
select public._expect_ok('delete_my_account: still works for a signed-in person', $q$ select public.delete_my_account(false) $q$);
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select public._expect_state('delete_my_account: a token with no user still raises 42501', $q$ select public.delete_my_account(false) $q$, '42501');
select public._as_admin();
select public._expect_text('delete_my_account: Mia is a tombstone',
  $q$ select display_name from public.profiles where id = '00000000-0000-4000-8000-000000000023' $q$, 'Former member');
select public._expect_value('delete_my_account: Mia is not in the log (only the admin path logs)',
  $q$ select count(*) from private.admin_audit where target_user = '00000000-0000-4000-8000-000000000023' $q$, 0);

select public._as_admin();
select n, test, passed, detail from public._rls_results order by n;

rollback;
