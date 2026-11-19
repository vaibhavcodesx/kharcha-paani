-- Checks for the migration 20261119000100_ai_plans_and_ceiling (issues #354, #296): a plan per person, caps by plan, and a project-wide daily ceiling, all spent through public.take_ai_read.
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
  ('00000000-0000-4000-8000-0000000000a3', 'asha@plans.test', '{"full_name":"Asha"}'),
  ('00000000-0000-4000-8000-0000000000b3', 'bilal@plans.test', '{"full_name":"Bilal"}');
insert into private.user_plans (user_id, plan) values ('00000000-0000-4000-8000-0000000000b3', 'friends');

-- Nothing has changed for a free person: the free caps still apply.
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_value('free: three statements go through',
  $q$ select (public.take_ai_read('statement')::int + public.take_ai_read('statement')::int + public.take_ai_read('statement')::int) $q$, 3);
select public._expect_value('free: the 4th statement is refused',
  $q$ select public.take_ai_read('statement')::int $q$, 0);

-- A friend has the friends caps: 8 statements, and more than the free 50 typed lines.
select public._as('00000000-0000-4000-8000-0000000000b3');
select public._expect_value('friends: eight statements go through',
  $q$ select sum(public.take_ai_read('statement')::int) from generate_series(1, 8) $q$, 8);
select public._expect_value('friends: the 9th statement is refused',
  $q$ select public.take_ai_read('statement')::int $q$, 0);
select public._expect_value('friends: 60 typed lines go through (free would stop at 50)',
  $q$ select sum(public.take_ai_read('quick_add')::int) from generate_series(1, 60) $q$, 60);

-- A kind a plan has no row for uses the free cap (bill: 10).
select public._as_admin();
delete from private.ai_plan_caps where plan = 'friends' and feature = 'bill';
select public._as('00000000-0000-4000-8000-0000000000b3');
select public._expect_value('friends with no bill row: the free cap of 10 applies',
  $q$ select sum(public.take_ai_read('bill')::int) from generate_series(1, 11) $q$, 10);

-- Changing a plan applies at once.
select public._as_admin();
update private.user_plans set plan = 'free' where user_id = '00000000-0000-4000-8000-0000000000b3';
select public._as('00000000-0000-4000-8000-0000000000b3');
select public._expect_value('moved back to free: the quick-add count is over the free cap, so refused',
  $q$ select public.take_ai_read('quick_add')::int $q$, 0);

-- ------------------------------------------------------------
-- The project-wide ceiling
-- ------------------------------------------------------------
select public._as_admin();
delete from private.ai_reads where user_id in ('00000000-0000-4000-8000-0000000000a3', '00000000-0000-4000-8000-0000000000b3');
delete from private.ai_project_reads;
update private.ai_project_ceilings set per_day = 2 where feature = 'insights';
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_value('ceiling 2: the first insights read goes through',
  $q$ select public.take_ai_read('insights')::int $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000b3');
select public._expect_value('ceiling 2: a different person takes the second',
  $q$ select public.take_ai_read('insights')::int $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_value('ceiling 2: the third, by anybody, is refused',
  $q$ select public.take_ai_read('insights')::int $q$, 0);
select public._as_admin();
select public._expect_value('a refusal by the ceiling is not charged to the person',
  $q$ select used from private.ai_reads where user_id = '00000000-0000-4000-8000-0000000000a3' and feature = 'insights' $q$, 1);
select public._expect_value('the project counter stops at the ceiling',
  $q$ select used from private.ai_project_reads where feature = 'insights' and day = (now() at time zone 'Asia/Kolkata')::date $q$, 2);
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_value('other kinds are unaffected by one kind''s ceiling: quick_add still goes through',
  $q$ select public.take_ai_read('quick_add')::int $q$, 1);

-- A ceiling of 0 turns a kind off for everyone and leaves nothing behind.
select public._as_admin();
update private.ai_project_ceilings set per_day = 0 where feature = 'statement';
delete from private.ai_reads where feature = 'statement' and user_id in ('00000000-0000-4000-8000-0000000000a3', '00000000-0000-4000-8000-0000000000b3');
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_value('ceiling 0: the first statement read is refused',
  $q$ select public.take_ai_read('statement')::int $q$, 0);
select public._as_admin();
select public._expect_value('ceiling 0: no project row is left behind',
  $q$ select count(*) from private.ai_project_reads where feature = 'statement' $q$, 0);
select public._expect_value('ceiling 0: no person count is left behind',
  $q$ select count(*) from private.ai_reads where feature = 'statement' $q$, 0);

-- No ceiling row means only the person's own cap applies.
delete from private.ai_project_ceilings where feature = 'bill';
delete from private.ai_reads where feature = 'bill' and user_id in ('00000000-0000-4000-8000-0000000000a3', '00000000-0000-4000-8000-0000000000b3');
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_value('no ceiling row: the person''s own cap (10 bills) still applies',
  $q$ select sum(public.take_ai_read('bill')::int) from generate_series(1, 12) $q$, 10);

-- ------------------------------------------------------------
-- Old behaviour is kept
-- ------------------------------------------------------------
select public._expect_error('an unknown kind is refused',
  $q$ select public.take_ai_read('poetry') $q$);
select public._as_anon();
select public._expect_error('signed out, there is nothing to spend',
  $q$ select public.take_ai_read('quick_add') $q$);

-- ------------------------------------------------------------
-- Nobody reads or writes the new tables directly
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_denied('a signed-in user can''t read plans',
  $q$ select count(*) from private.user_plans $q$);
select public._expect_denied('a signed-in user can''t make themselves a friend',
  $q$ insert into private.user_plans (user_id, plan) values ('00000000-0000-4000-8000-0000000000a3', 'friends') $q$);
select public._expect_denied('a signed-in user can''t raise a plan cap',
  $q$ update private.ai_plan_caps set per_day = 100000 $q$);
select public._expect_denied('a signed-in user can''t lift the ceiling',
  $q$ update private.ai_project_ceilings set per_day = 100000 $q$);
select public._expect_denied('a signed-in user can''t read the project counter',
  $q$ select count(*) from private.ai_project_reads $q$);
select public._as_anon();
select public._expect_denied('signed out can''t read plans',
  $q$ select count(*) from private.user_plans $q$);

-- Constraints
select public._as_admin();
select public._expect_error('an unknown plan name is refused',
  $q$ insert into private.user_plans (user_id, plan) values ('00000000-0000-4000-8000-0000000000a3', 'vip') $q$);
select public._expect_error('a plan cap can''t be negative',
  $q$ update private.ai_plan_caps set per_day = -1 $q$);
select public._expect_error('the free plan has no cap rows of its own (the free cap is ai_read_caps)',
  $q$ insert into private.ai_plan_caps (plan, feature, per_day) values ('free', 'bill', 5) $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
