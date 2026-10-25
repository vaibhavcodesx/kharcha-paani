-- Checks for the migration 20261025000000_ai_daily_reads (plan T1.3, decision D2): per-person daily
-- AI reads by kind, spent only through public.take_ai_read, with the caps as data.
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check. Run against live 2026-10-25 with the migration applied inside the same rolled-back
-- transaction (nothing persisted); see the PR for the result.

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

-- Setup: two people, Asha and Bilal.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a2', 'asha@ai.test', '{"full_name":"Asha"}'),
  ('00000000-0000-4000-8000-0000000000b2', 'bilal@ai.test', '{"full_name":"Bilal"}');

-- ------------------------------------------------------------
-- Spending reads
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a2');
select public._expect_value('the first quick add of the day goes through',
  $q$ select public.take_ai_read('quick_add')::int $q$, 1);
select public._expect_value('statements: the 2nd and 3rd still go through',
  $q$ select (public.take_ai_read('statement')::int + public.take_ai_read('statement')::int + public.take_ai_read('statement')::int) $q$, 3);
select public._expect_value('the 4th statement of the day is refused',
  $q$ select public.take_ai_read('statement')::int $q$, 0);
select public._expect_value('one kind running out leaves the others alone',
  $q$ select public.take_ai_read('bill')::int $q$, 1);
select public._expect_error('an unknown kind is refused',
  $q$ select public.take_ai_read('poetry') $q$);

-- Someone else's count is their own.
select public._as('00000000-0000-4000-8000-0000000000b2');
select public._expect_value('another person still has all their statements',
  $q$ select public.take_ai_read('statement')::int $q$, 1);

-- ------------------------------------------------------------
-- Nobody reads or writes the tables directly
-- ------------------------------------------------------------
select public._expect_denied('a signed-in user can''t read the counts',
  $q$ select count(*) from private.ai_reads $q$);
select public._expect_denied('a signed-in user can''t raise their cap',
  $q$ update private.ai_read_caps set per_day = 1000 $q$);
select public._as_anon();
select public._expect_error('signed out, there''s nothing to spend',
  $q$ select public.take_ai_read('quick_add') $q$);

-- ------------------------------------------------------------
-- The caps are data: changing one applies at once, and 0 turns a kind off
-- ------------------------------------------------------------
select public._as_admin();
update private.ai_read_caps set per_day = 0 where feature = 'insights';
select public._as('00000000-0000-4000-8000-0000000000a2');
select public._expect_value('a cap of 0 refuses the first read',
  $q$ select public.take_ai_read('insights')::int $q$, 0);
select public._as_admin();
select public._expect_value('and leaves no count behind',
  $q$ select count(*) from private.ai_reads where feature = 'insights' $q$, 0);
select public._expect_value('the day is India''s: counts are stored under today in IST',
  $q$ select count(*) from private.ai_reads where day = (now() at time zone 'Asia/Kolkata')::date and user_id = '00000000-0000-4000-8000-0000000000a2' $q$, 3);

select n, test, passed, detail from public._rls_results order by n;

rollback;
