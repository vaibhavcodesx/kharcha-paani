-- Checks for the migration 20261120000400_ai_available: the read-only check the app makes when a button is tapped, so a paused AI or a used-up cap shows at once. Same conventions as ai_plans_and_ceiling_test.sql.
--
-- Same conventions as ai_daily_reads_test.sql: one transaction ending in ROLLBACK, one result row per check. Staged, not applied: run with the migration body inside the same rolled-back transaction (nothing persisted).

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

-- Setup: Asha is on the free plan (no row), Bilal is a friend.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a4', 'asha@avail.test', '{"full_name":"Asha"}'),
  ('00000000-0000-4000-8000-0000000000b4', 'bilal@avail.test', '{"full_name":"Bilal"}');
insert into private.user_plans (user_id, plan) values ('00000000-0000-4000-8000-0000000000b4', 'friends');

select public._as('00000000-0000-4000-8000-0000000000a4');
select public._expect_value('available at the start', $q$ select public.ai_available('statement')::int $q$, 1);
select public._expect_value('asking spends nothing: ten asks, then all three statements still go through',
  $q$ select (select count(*) from generate_series(1, 10) where public.ai_available('statement')) + public.take_ai_read('statement')::int + public.take_ai_read('statement')::int + public.take_ai_read('statement')::int $q$, 13);
select public._expect_value('not available once the free cap of 3 is used', $q$ select public.ai_available('statement')::int $q$, 0);
select public._expect_value('another kind is unaffected', $q$ select public.ai_available('bill')::int $q$, 1);

-- The project's ceiling (how the owner's pause works: every ceiling set to 0).
select public._as_admin();
insert into private.ai_project_ceilings (feature, per_day) values ('bill', 0)
  on conflict (feature) do update set per_day = 0;
select public._as('00000000-0000-4000-8000-0000000000b4');
select public._expect_value('a ceiling of 0 (paused) is not available, for everyone', $q$ select public.ai_available('bill')::int $q$, 0);
select public._expect_value('and take_ai_read agrees', $q$ select public.take_ai_read('bill')::int $q$, 0);
select public._as_admin();
update private.ai_project_ceilings set per_day = 1 where feature = 'bill';
select public._as('00000000-0000-4000-8000-0000000000b4');
select public._expect_value('ceiling of 1: available until it is used', $q$ select public.ai_available('bill')::int $q$, 1);
select public._expect_value('one read is taken', $q$ select public.take_ai_read('bill')::int $q$, 1);
select public._expect_value('then it is not available', $q$ select public.ai_available('bill')::int $q$, 0);

-- Edges.
select public._expect_state('an unknown kind is refused', $q$ select public.ai_available('nope') $q$, '22023');
select public._as_anon();
select public._expect_denied('signed out cannot call it', $q$ select public.ai_available('bill') $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
