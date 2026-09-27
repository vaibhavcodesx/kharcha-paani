-- Checks for the migration 20260927000000_profile_hue (design doc item 11): one colour per
-- person, refused when it's within 25 degrees of anyone you share a group with, or in the amber band.
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check. Applied to live 2026-09-27; safe to run there as `postgres` (it rolls back).
--
-- Users: alice, bob and carol share Goa trip; mallory shares nothing, then joins.

begin;

create table public._rls_results (
  n serial primary key,
  test text not null,
  passed boolean not null,
  detail text
);

create function public._as(p_user uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
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

-- The statement must raise an error (a policy, constraint or function refusing it).
create function public._expect_error(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have been refused');
exception when others then
  perform public._record(p_test, true, sqlstate || ': ' || sqlerrm);
end $$;

-- The statement must be refused with insufficient_privilege (42501) specifically. Used where another
-- error could make a check pass for the wrong reason (e.g. TRUNCATE failing on a foreign key).
create function public._expect_denied(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have been denied');
exception when insufficient_privilege then
  perform public._record(p_test, true, sqlstate || ': ' || sqlerrm);
when others then
  perform public._record(p_test, false, 'refused for another reason: ' || sqlstate || ': ' || sqlerrm);
end $$;

-- The statement must succeed.
create function public._expect_ok(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, true, null);
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

-- A query returning one integer must return p_expected (used for visibility counts).
create function public._expect_value(p_test text, p_sql text, p_expected bigint) returns void language plpgsql as $$
declare v bigint;
begin
  execute p_sql into v;
  perform public._record(p_test, v is not distinct from p_expected, format('got %s, expected %s', v, p_expected));
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

-- A write that RLS silently filters (UPDATE/DELETE) must affect p_expected rows.
create function public._expect_rows(p_test text, p_sql text, p_expected int) returns void language plpgsql as $$
declare v int;
begin
  execute p_sql;
  get diagnostics v = row_count;
  perform public._record(p_test, v = p_expected, format('%s row(s) affected, expected %s', v, p_expected));
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

grant all on public._rls_results to authenticated, anon;
grant usage on sequence public._rls_results_n_seq to authenticated, anon;
-- The statement must fail with exactly this SQLSTATE (e.g. 23514 from the new triggers).
create function public._expect_state(p_test text, p_sql text, p_state text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have failed with ' || p_state);
exception when others then
  perform public._record(p_test, sqlstate = p_state, sqlstate || ': ' || sqlerrm);
end $$;

grant execute on function public._as(uuid), public._as_anon(), public._as_admin(), public._record(text, boolean, text),
  public._expect_error(text, text), public._expect_denied(text, text), public._expect_ok(text, text), public._expect_value(text, text, bigint),
  public._expect_rows(text, text, int), public._expect_state(text, text, text) to authenticated, anon;

-- ------------------------------------------------------------
-- Setup: alice creates Goa trip; bob and carol join. Mallory is in no group with them.
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@hue.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@hue.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c1', 'carol@hue.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@hue.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', 'x');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._as('00000000-0000-4000-8000-0000000000b1');
select public.join_group((select value from public._ids where key = 'g1'));
select public._as('00000000-0000-4000-8000-0000000000c1');
select public.join_group((select value from public._ids where key = 'g1'));

-- ------------------------------------------------------------
-- Picking a colour
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_rows('alice picks blue (200)',
  $q$ update public.profiles set hue = 200 where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_rows('saving the same colour again is fine',
  $q$ update public.profiles set hue = 200 where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_state('the app''s amber accent is refused (43)',
  $q$ update public.profiles set hue = 43 where id = '00000000-0000-4000-8000-0000000000a1' $q$, '23514');
select public._expect_state('so is the edge of that band (28)',
  $q$ update public.profiles set hue = 28 where id = '00000000-0000-4000-8000-0000000000a1' $q$, '23514');
select public._expect_state('and a hue out of range (360)',
  $q$ update public.profiles set hue = 360 where id = '00000000-0000-4000-8000-0000000000a1' $q$, '23514');
select public._expect_value('alice is still blue',
  $q$ select hue from public.profiles where id = '00000000-0000-4000-8000-0000000000a1' $q$, 200);

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_state('bob, in her group, can''t take a hue 10 degrees from hers',
  $q$ update public.profiles set hue = 210 where id = '00000000-0000-4000-8000-0000000000b1' $q$, '23514');
select public._expect_state('nor one 24 degrees away',
  $q$ update public.profiles set hue = 176 where id = '00000000-0000-4000-8000-0000000000b1' $q$, '23514');
select public._expect_rows('25 degrees away is far enough',
  $q$ update public.profiles set hue = 225 where id = '00000000-0000-4000-8000-0000000000b1' $q$, 1);
select public._expect_rows('bob can''t change alice''s colour',
  $q$ update public.profiles set hue = 100 where id = '00000000-0000-4000-8000-0000000000a1' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_rows('alice moves to pink (5)',
  $q$ update public.profiles set hue = 5 where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_state('carol can''t take 350: the distance wraps round through 0',
  $q$ update public.profiles set hue = 350 where id = '00000000-0000-4000-8000-0000000000c1' $q$, '23514');
select public._expect_state('nor 230, 5 from bob''s',
  $q$ update public.profiles set hue = 230 where id = '00000000-0000-4000-8000-0000000000c1' $q$, '23514');
select public._expect_rows('alice''s old 200 is free now',
  $q$ update public.profiles set hue = 200 where id = '00000000-0000-4000-8000-0000000000c1' $q$, 1);

-- ------------------------------------------------------------
-- Only people you share a group with count
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_rows('mallory, in no group with them, can take carol''s exact colour',
  $q$ update public.profiles set hue = 200 where id = '00000000-0000-4000-8000-0000000000d1' $q$, 1);
select public._expect_ok('joining their group is never refused over colour',
  $q$ select public.join_group((select value from public._ids where key = 'g1')) $q$);
select public._expect_value('she keeps her colour; the app asks her to pick again',
  $q$ select hue from public.profiles where id = '00000000-0000-4000-8000-0000000000d1' $q$, 200);
select public._expect_state('but once she''s in, a new clash is refused',
  $q$ update public.profiles set hue = 10 where id = '00000000-0000-4000-8000-0000000000d1' $q$, '23514');
select public._expect_rows('and a free colour is accepted',
  $q$ update public.profiles set hue = 290 where id = '00000000-0000-4000-8000-0000000000d1' $q$, 1);
select public._expect_rows('clearing it (let the app choose) is always allowed',
  $q$ update public.profiles set hue = null where id = '00000000-0000-4000-8000-0000000000d1' $q$, 1);

-- ------------------------------------------------------------
-- Leaving a group frees its colours
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('bob leaves Goa trip',
  $q$ delete from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000b1' $q$, 1);
select public._expect_rows('and can now take a colour next to carol''s',
  $q$ update public.profiles set hue = 205 where id = '00000000-0000-4000-8000-0000000000b1' $q$, 1);

-- ------------------------------------------------------------
-- Everyone else
-- ------------------------------------------------------------
select public._as_anon();
select public._expect_rows('anon can''t set anyone''s colour',
  $q$ update public.profiles set hue = 100 $q$, 0);
select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_value('groupmates read each other''s colour',
  $q$ select count(*) from public.profiles where id in ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-0000000000c1') and hue is not null $q$, 2);
select public._as_admin();
select public._expect_rows('the service role can repair a colour without the rules',
  $q$ update public.profiles set hue = 206 where id = '00000000-0000-4000-8000-0000000000c1' $q$, 1);

select n, test, passed, detail from public._rls_results order by n;

rollback;
