-- Checks for the migration 20261120000100_admin_writes (issues #355, #439): the owner's write functions for plans,
-- caps, ceilings, the AI pause, and the app version switch, each logged and rate limited.
--
-- Same conventions as admin_core_test.sql: one transaction ending in ROLLBACK, one result row per check.
-- Staged, not applied. Run: `node supabase/tests/local/run.mjs supabase/tests/admin_writes_test.sql`
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

create function public._t_one_write() returns bigint language plpgsql as $$
declare v_before bigint;
begin
  select count(*) into v_before from public.admin_list_audit(200);
  perform public.admin_set_cap('free', 'insights', 21);
  return (select count(*) from public.admin_list_audit(200)) - v_before;
end $$;

grant all on public._rls_results to authenticated, anon;
grant usage on sequence public._rls_results_n_seq to authenticated, anon;
grant execute on function public._as_jwt(uuid, text, uuid, boolean), public._as_anon(), public._as_admin(),
  public._record(text, boolean, text), public._expect_state(text, text, text), public._expect_ok(text, text),
  public._expect_value(text, text, bigint), public._expect_text(text, text, text), public._t_one_write() to authenticated, anon;

-- Setup. Owen is the admin. Bob is an ordinary person. Asha is the person whose plan changes. Gone is deleted.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test', '{"full_name":"Owen"}'),
  ('00000000-0000-4000-8000-0000000000b2', 'bob@writes.test',  '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c2', 'asha@writes.test', '{"full_name":"Asha"}');
insert into auth.identities (user_id, provider, identity_data) values
  ('00000000-0000-4000-8000-0000000000a2', 'google', '{"email":"owen@writes.test"}'),
  ('00000000-0000-4000-8000-0000000000b2', 'google', '{"email":"bob@writes.test"}'),
  ('00000000-0000-4000-8000-0000000000c2', 'google', '{"email":"asha@writes.test"}');
insert into private.admins (user_id, email) values ('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
insert into public.profiles (id, display_name, deleted_at) values ('00000000-0000-4000-8000-0000000000f2', 'Former member', now());

-- 1. Signed out and not-an-admin: every write is refused.
select public._as_anon();
select public._expect_state('anon: admin_set_plan is refused', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000c2', 'friends') $q$, '42501');
select public._expect_state('anon: admin_set_cap is refused', $q$ select public.admin_set_cap('free', 'bill', 5) $q$, '42501');
select public._expect_state('anon: admin_pause_ai is refused', $q$ select public.admin_pause_ai('x') $q$, '42501');
select public._expect_state('anon: admin_set_app_versions is refused', $q$ select public.admin_set_app_versions(0, 0) $q$, '42501');
select public._as_jwt('00000000-0000-4000-8000-0000000000b2', 'bob@writes.test');
select public._expect_state('non-admin: admin_set_plan raises 42501', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000c2', 'friends') $q$, '42501');
select public._expect_state('non-admin: admin_set_cap raises 42501', $q$ select public.admin_set_cap('free', 'bill', 5) $q$, '42501');
select public._expect_state('non-admin: admin_set_ceiling raises 42501', $q$ select public.admin_set_ceiling('bill', 5) $q$, '42501');
select public._expect_state('non-admin: admin_pause_ai raises 42501', $q$ select public.admin_pause_ai('x') $q$, '42501');
select public._expect_state('non-admin: admin_resume_ai raises 42501', $q$ select public.admin_resume_ai() $q$, '42501');
select public._expect_state('non-admin: admin_set_release_facts raises 42501', $q$ select public.admin_set_release_facts(1, 1) $q$, '42501');
select public._expect_state('non-admin: admin_set_app_versions raises 42501', $q$ select public.admin_set_app_versions(0, 0) $q$, '42501');
select public._as_admin();
select public._expect_value('a refused write changes nothing and logs nothing', $q$ select count(*) from private.admin_audit $q$, 0);

-- 4. Plans. Asha is free (cap 50 typed lines a day). As a friend she gets 150.
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_text('set_plan: friends is returned', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000c2', 'friends') $q$, 'friends');
select public._as_jwt('00000000-0000-4000-8000-0000000000c2', 'asha@writes.test');
select public._expect_value('friends plan: a real take_ai_read goes past the free 50 (51 reads)',
  $q$ select sum(public.take_ai_read('quick_add')::int) from generate_series(1, 51) $q$, 51);
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_text('set_plan: free is returned', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000c2', 'free') $q$, 'free');
select public._as_admin();
select public._expect_value('set_plan free removes the plan row', $q$ select count(*) from private.user_plans where user_id = '00000000-0000-4000-8000-0000000000c2' $q$, 0);
select public._as_jwt('00000000-0000-4000-8000-0000000000c2', 'asha@writes.test');
select public._expect_value('free again: she has used 51, so the 52nd is refused',
  $q$ select public.take_ai_read('quick_add')::int $q$, 0);
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_state('set_plan: paid is refused', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000c2', 'paid') $q$, '22023');
select public._expect_state('set_plan: an unknown plan is refused', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000c2', 'gold') $q$, '22023');
select public._expect_state('set_plan: an unknown person is P0002', $q$ select public.admin_set_plan('00000000-0000-4000-8000-00000000dead', 'friends') $q$, 'P0002');
select public._expect_state('set_plan: a deleted account is P0002', $q$ select public.admin_set_plan('00000000-0000-4000-8000-0000000000f2', 'friends') $q$, 'P0002');
select public._as_admin();
select public._expect_text('audit: set_plan records before and after',
  $q$ select (before ->> 'plan') || '>' || (after ->> 'plan') from private.admin_audit where action = 'set_plan' order by id limit 1 $q$, 'free>friends');

-- 5. Caps: bounds, the right table, exact before and after.
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_state('set_cap: below 0 is refused', $q$ select public.admin_set_cap('free', 'bill', -1) $q$, '22023');
select public._expect_state('set_cap: above 10000 is refused', $q$ select public.admin_set_cap('free', 'bill', 10001) $q$, '22023');
select public._expect_state('set_cap: an unknown kind is refused', $q$ select public.admin_set_cap('free', 'dance', 5) $q$, '22023');
select public._expect_state('set_cap: paid is refused', $q$ select public.admin_set_cap('paid', 'bill', 5) $q$, '22023');
select public._expect_ok('set_cap: free bill to 12', $q$ select public.admin_set_cap('free', 'bill', 12) $q$);
select public._expect_ok('set_cap: friends bill to 31', $q$ select public.admin_set_cap('friends', 'bill', 31) $q$);
select public._as_admin();
select public._expect_value('set_cap free writes ai_read_caps', $q$ select per_day from private.ai_read_caps where feature = 'bill' $q$, 12);
select public._expect_value('set_cap friends writes ai_plan_caps', $q$ select per_day from private.ai_plan_caps where plan = 'friends' and feature = 'bill' $q$, 31);
select public._expect_text('audit: set_cap free is exact (10 to 12)',
  $q$ select target || ' ' || (before ->> 'per_day') || '>' || (after ->> 'per_day') from private.admin_audit where action = 'set_cap' order by id limit 1 $q$, 'free:bill 10>12');
select public._expect_text('audit: set_cap friends is exact (30 to 31)',
  $q$ select target || ' ' || (before ->> 'per_day') || '>' || (after ->> 'per_day') from private.admin_audit where action = 'set_cap' order by id desc limit 1 $q$, 'friends:bill 30>31');

-- 6. Ceilings: a real take_ai_read stops at the ceiling; the pause and resume round trip restores everything.
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_state('set_ceiling: above 100000 is refused', $q$ select public.admin_set_ceiling('bill', 100001) $q$, '22023');
select public._expect_ok('set_ceiling: statement to 3', $q$ select public.admin_set_ceiling('statement', 3) $q$);
select public._as_jwt('00000000-0000-4000-8000-0000000000b2', 'bob@writes.test');
select public._expect_value('a real take_ai_read stops at the ceiling of 3', $q$ select sum(public.take_ai_read('statement')::int) from generate_series(1, 5) $q$, 3);
select public._as_admin();
-- Make one kind have no ceiling at all, to prove the resume leaves it without one.
delete from private.ai_project_ceilings where feature = 'insights';
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_ok('pause_ai works', $q$ select public.admin_pause_ai('test pause') $q$);
select public._expect_state('pausing twice is refused', $q$ select public.admin_pause_ai('again') $q$, '22023');
select public._expect_state('set_ceiling is refused while paused', $q$ select public.admin_set_ceiling('bill', 9) $q$, '22023');
select public._as_admin();
select public._expect_value('paused: every ceiling is 0 (5 kinds including insights)',
  $q$ select count(*) from private.ai_project_ceilings where per_day = 0 $q$, 4);
select public._expect_value('paused: the overview says so', $q$ select ((select ai_pause_snapshot is not null from private.admin_settings))::int $q$, 1);
select public._as_jwt('00000000-0000-4000-8000-0000000000b2', 'bob@writes.test');
select public._expect_value('paused: a fresh kind is refused (bill)', $q$ select public.take_ai_read('bill')::int $q$, 0);
select public._as_admin();
select public._expect_value('paused: the refused read was not charged to the person',
  $q$ select count(*) from private.ai_reads where user_id = '00000000-0000-4000-8000-0000000000b2' and feature = 'bill' $q$, 0);
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_ok('resume_ai works', $q$ select public.admin_resume_ai() $q$);
select public._expect_state('resuming when not paused is refused', $q$ select public.admin_resume_ai() $q$, '22023');
select public._as_admin();
select public._expect_value('resumed: statement is back to 3', $q$ select per_day from private.ai_project_ceilings where feature = 'statement' $q$, 3);
select public._expect_value('resumed: bill is back to its seeded 400', $q$ select per_day from private.ai_project_ceilings where feature = 'bill' $q$, 400);
select public._expect_value('resumed: insights, which had no ceiling, has none again', $q$ select count(*) from private.ai_project_ceilings where feature = 'insights' $q$, 0);
select public._expect_value('resumed: the snapshot is cleared', $q$ select (ai_pause_snapshot is null)::int from private.admin_settings $q$, 1);

-- 7. The version switch.
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_state('versions: raising the minimum before the update screen is known is refused',
  $q$ select public.admin_set_app_versions(5, 5) $q$, '22023');
select public._expect_ok('release facts: update screen 10, latest published 12', $q$ select public.admin_set_release_facts(10, 12) $q$);
select public._expect_state('versions: a minimum above the latest published is refused', $q$ select public.admin_set_app_versions(13, 13) $q$, '22023');
select public._expect_state('versions: recommended below the minimum is refused', $q$ select public.admin_set_app_versions(5, 3) $q$, '22023');
select public._expect_ok('versions: raise to min 5, recommended 12', $q$ select public.admin_set_app_versions(5, 12) $q$);
select public._as_admin();
select public._expect_value('versions: the live row changed', $q$ select min_version_code * 100 + recommended_version_code from public.app_config $q$, 512);
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_ok('versions: lowering always works', $q$ select public.admin_set_app_versions(0, 0) $q$);
select public._expect_ok('release facts: a typo may be fixed within 10 minutes (down to 9 and 11)', $q$ select public.admin_set_release_facts(9, 11) $q$);
select public._as_admin();
update private.admin_audit set at = now() - interval '1 hour' where action = 'set_release_facts';
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_state('release facts: after 10 minutes the numbers only go up', $q$ select public.admin_set_release_facts(8, 11) $q$, '22023');
select public._expect_ok('release facts: going up is fine', $q$ select public.admin_set_release_facts(9, 14) $q$);
select public._as_admin();
select public._expect_text('audit: the version change records before and after',
  $q$ select (before ->> 'min') || '/' || (before ->> 'recommended') || ' > ' || (after ->> 'min') || '/' || (after ->> 'recommended')
     from private.admin_audit where action = 'set_app_versions' order by id limit 1 $q$, '0/0 > 5/12');

-- 12. Every write adds exactly one audit row.
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
select public._expect_value('one write, one audit row', $q$ select public._t_one_write() $q$, 1);

-- 11. Rate limit: 30 writes in a window are fine, the 31st raises 54000.
select public._as_admin();
delete from private.admin_rate;
select public._as_jwt('00000000-0000-4000-8000-0000000000a2', 'owen@writes.test');
do $$ begin for i in 1..30 loop perform public.admin_set_cap('free', 'insights', 20 + (i % 2)); end loop; end $$;
select public._expect_state('the 31st write in a window raises 54000', $q$ select public.admin_set_cap('free', 'insights', 22) $q$, '54000');
select public._as_admin();
select public._expect_value('a refused write is not counted twice', $q$ select writes from private.admin_rate $q$, 30);

select public._as_admin();
select n, test, passed, detail from public._rls_results order by n;

rollback;
