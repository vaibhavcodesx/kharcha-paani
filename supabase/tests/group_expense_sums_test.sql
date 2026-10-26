-- Checks for the migration 20261025000200_group_expense_sums (plan T2.1, security audit H-2): a group
-- expense's shares and split total must add up to its amount, whoever writes them.
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check. The triggers are deferred, so each check sets constraints immediate to make them fire as a
-- commit would. Run against live 2026-10-25 with the migration applied inside the same rolled-back
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

-- At-commit checks: the triggers are deferred, so each check makes them fire now, the way a commit would.
create function public._expect_ok_at_commit(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  set constraints all immediate;
  set constraints all deferred;
  perform public._record(p_test, true, null);
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

create function public._expect_state_at_commit(p_test text, p_sql text, p_state text) returns void language plpgsql as $$
begin
  execute p_sql;
  set constraints all immediate;
  set constraints all deferred;
  perform public._record(p_test, false, 'committed, but should have failed with ' || p_state);
exception when others then
  perform public._record(p_test, sqlstate = p_state, sqlstate || ': ' || sqlerrm);
end $$;

grant execute on function public._expect_ok_at_commit(text, text), public._expect_state_at_commit(text, text, text) to authenticated, anon;

-- Setup: Asha makes Goa trip and Bilal joins. Asha pays ₹900 for dinner, split ₹450 each.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a5', 'asha@sums.test', '{"full_name":"Asha"}'),
  ('00000000-0000-4000-8000-0000000000b5', 'bilal@sums.test', '{"full_name":"Bilal"}');
create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a5');
select public.create_group('10000000-0000-4000-8000-0000000000a5', 'Goa trip', '🏖️');
insert into public._ids select 'g', code from public.create_group_invite('10000000-0000-4000-8000-0000000000a5');
select public._as('00000000-0000-4000-8000-0000000000b5');
select public.join_group((select value from public._ids where key = 'g'));
select public._as('00000000-0000-4000-8000-0000000000a5');
select public._expect_ok_at_commit('save_group_expense still saves an expense that adds up',
  $q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-0000000000a5","type":"expense","amount":900,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-10-14T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-0000000000a5"}',
    '{"id":"30000000-0000-4000-8000-0000000000a5","paid_by":"00000000-0000-4000-8000-0000000000a5","split_type":"equal","total_amount":900}',
    '[{"profile_id":"00000000-0000-4000-8000-0000000000a5","share_amount":450},{"profile_id":"00000000-0000-4000-8000-0000000000b5","share_amount":450}]') $q$);

-- ------------------------------------------------------------
-- Direct writes that would break the sums are refused
-- ------------------------------------------------------------
select public._expect_state_at_commit('the payer can''t change one share on its own',
  $q$ update public.split_shares set share_amount = 50 where split_id = '30000000-0000-4000-8000-0000000000a5' and profile_id = '00000000-0000-4000-8000-0000000000b5' $q$, '23514');
select public._expect_state_at_commit('the adder can''t change the amount without the shares',
  $q$ update public.transactions set amount = 1900 where id = '20000000-0000-4000-8000-0000000000a5' $q$, '23514');
select public._expect_state_at_commit('nor the split''s total',
  $q$ update public.splits set total_amount = 1000 where id = '30000000-0000-4000-8000-0000000000a5' $q$, '23514');
select public._expect_state_at_commit('nor drop a share and leave the rest short',
  $q$ update public.split_shares set deleted_at = now() where split_id = '30000000-0000-4000-8000-0000000000a5' and profile_id = '00000000-0000-4000-8000-0000000000b5' $q$, '23514');

-- ------------------------------------------------------------
-- What still works
-- ------------------------------------------------------------
select public._expect_ok_at_commit('saving it again with new amounts that add up',
  $q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-0000000000a5","type":"expense","amount":1200,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-10-14T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-0000000000a5"}',
    '{"id":"30000000-0000-4000-8000-0000000000a5","paid_by":"00000000-0000-4000-8000-0000000000a5","split_type":"exact","total_amount":1200}',
    '[{"profile_id":"00000000-0000-4000-8000-0000000000a5","share_amount":400},{"profile_id":"00000000-0000-4000-8000-0000000000b5","share_amount":800}]') $q$);
select public._expect_ok_at_commit('deleting it (soft delete)',
  $q$ update public.transactions set deleted_at = now() where id = '20000000-0000-4000-8000-0000000000a5' $q$);
select public._expect_ok_at_commit('an expense with no split, in a group',
  $q$ insert into public.transactions (id, owner_id, type, amount, category, merchant, payment_method, date, note, group_id)
     values ('20000000-0000-4000-8000-0000000000f9', '00000000-0000-4000-8000-0000000000a5', 'expense', 60, 'Food', 'Chai', 'Cash', now(), '', '10000000-0000-4000-8000-0000000000a5') $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
