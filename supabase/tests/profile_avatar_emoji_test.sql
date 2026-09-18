-- Checks for migration profile_avatar_emoji (backlog item 6).
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check, nothing left behind. Safe to run on the live project as `postgres`.

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
-- Setup: alice creates Goa trip; bob joins; mallory is outside.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@emoji.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@emoji.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@emoji.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._as('00000000-0000-4000-8000-0000000000b1');
select public.join_group((select value from public._ids where key = 'g1'));

select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_value('a new profile has no emoji',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a1' and avatar_emoji is null $q$, 1);
select public._expect_rows('you can set your own emoji and name',
  $q$ update public.profiles set avatar_emoji = '🦊', display_name = 'Alice K' where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_rows('an emoji with a skin tone or joiner fits',
  $q$ update public.profiles set avatar_emoji = '🧑🏽‍🍳' where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_state('text isn''t an emoji avatar',
  $q$ update public.profiles set avatar_emoji = 'hello world' where id = '00000000-0000-4000-8000-0000000000a1' $q$, '23514');
select public._expect_state('an empty emoji isn''t stored',
  $q$ update public.profiles set avatar_emoji = '' where id = '00000000-0000-4000-8000-0000000000a1' $q$, '23514');
select public._expect_rows('clearing it goes back to initials',
  $q$ update public.profiles set avatar_emoji = null where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_rows('set it again for the groupmate checks',
  $q$ update public.profiles set avatar_emoji = '🦊' where id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_rows('you can''t set someone else''s',
  $q$ update public.profiles set avatar_emoji = '💩' where id = '00000000-0000-4000-8000-0000000000b1' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('a groupmate sees your emoji',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a1' and avatar_emoji = '🦊' $q$, 1);
select public._expect_rows('a groupmate can''t change it',
  $q$ update public.profiles set avatar_emoji = '💩' where id = '00000000-0000-4000-8000-0000000000a1' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('someone outside your groups can''t see your profile at all',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a1' $q$, 0);

select public._as_anon();
select public._expect_value('signed-out callers see no profiles',
  $q$ select count(*) from public.profiles $q$, 0);

select public._as_admin();
select n, passed, test, detail from public._rls_results order by n;

rollback;
