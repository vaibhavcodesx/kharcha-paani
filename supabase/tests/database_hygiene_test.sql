-- Checks for the migration 20261025000300_database_hygiene (plan T2.3; security audit M-4, L-8, L-9):
-- anon has no privileges in public, now or by default; a colour clash with a nameless groupmate is
-- caught; avatar_url is an https link.
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check. Run against live on 2026-10-27 inside one rolled-back transaction with the migration: all
-- pass. The first run caught that a new function stayed executable by anon (PUBLIC's default EXECUTE
-- is database-wide); the migration now revokes it there.

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
-- anon has nothing, now or later
-- ------------------------------------------------------------
select public._expect_value('anon can''t read, write or delete any table in public',
  $q$ select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relkind in ('r', 'p', 'v') and c.relname not like '\_%'
        and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('anon', c.oid, 'insert')
          or has_table_privilege('anon', c.oid, 'update') or has_table_privilege('anon', c.oid, 'delete')) $q$, 0);
select public._expect_value('anon can''t run any function in public',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname not like '\_%' and has_function_privilege('anon', p.oid, 'execute') $q$, 0);

create table public.probe_table (x int);
create function public.probe_fn() returns int language sql as $f$ select 1 $f$;
select public._expect_value('a table made later isn''t granted to anon',
  $q$ select has_table_privilege('anon', 'public.probe_table', 'select')::int $q$, 0);
select public._expect_value('a function made later isn''t executable by anon',
  $q$ select has_function_privilege('anon', 'public.probe_fn()', 'execute')::int $q$, 0);
select public._expect_value('but signed-in users still get what they always did',
  $q$ select has_table_privilege('authenticated', 'public.probe_table', 'select')::int $q$, 1);
create function private.probe_private_fn() returns int language sql as $f$ select 1 $f$;
select public._expect_value('and a function made later still runs for signed-in users',
  $q$ select has_function_privilege('authenticated', 'public.probe_fn()', 'execute')::int $q$, 1);
select public._expect_value('a private helper made later still runs for signed-in users (policies call these)',
  $q$ select has_function_privilege('authenticated', 'private.probe_private_fn()', 'execute')::int $q$, 1);

-- ------------------------------------------------------------
-- A colour clash with a groupmate who has no name
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a6', 'ana@hygiene.test', '{"full_name":"Ana"}'),
  ('00000000-0000-4000-8000-0000000000b6', 'ben@hygiene.test', '{"full_name":"Ben"}');
create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;
select public._as('00000000-0000-4000-8000-0000000000a6');
select public.create_group('10000000-0000-4000-8000-0000000000a6', 'Flat', '🏠');
insert into public._ids select 'g', code from public.create_group_invite('10000000-0000-4000-8000-0000000000a6');
select public._as('00000000-0000-4000-8000-0000000000b6');
select public.join_group((select value from public._ids where key = 'g'));
select public._as_admin();
update public.profiles set display_name = null, hue = 200 where id = '00000000-0000-4000-8000-0000000000b6';

select public._as('00000000-0000-4000-8000-0000000000a6');
select public._expect_state('a colour next to a nameless groupmate''s is refused',
  $q$ update public.profiles set hue = 205 where id = '00000000-0000-4000-8000-0000000000a6' $q$, '23514');
select public._expect_ok('a colour well away from it is fine',
  $q$ update public.profiles set hue = 260 where id = '00000000-0000-4000-8000-0000000000a6' $q$);

-- ------------------------------------------------------------
-- avatar_url
-- ------------------------------------------------------------
select public._expect_state('an avatar that isn''t an https link is refused',
  $q$ update public.profiles set avatar_url = 'http://example.com/a.png' where id = '00000000-0000-4000-8000-0000000000a6' $q$, '23514');
select public._expect_ok('an https one is fine',
  $q$ update public.profiles set avatar_url = 'https://lh3.googleusercontent.com/a/abc' where id = '00000000-0000-4000-8000-0000000000a6' $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
