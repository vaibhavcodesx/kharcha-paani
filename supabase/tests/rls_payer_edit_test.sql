-- Checks for migration payer_can_edit_group_expenses (backlog item 11, reverses step 4 decision 2).
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check, nothing left behind. Safe to run on the live project as `postgres`.
--
-- Users: alice adds expenses; bob paid for them; carol and dave are members; mallory is outside.

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
-- Setup: alice creates Goa trip (G1) and Lonavala (G2); bob, carol and dave join G1; bob joins G2.
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@payer.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@payer.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c1', 'carol@payer.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000e1', 'dave@payer.test', '{"full_name":"Dave"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@payer.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️');
select public.create_group('10000000-0000-4000-8000-000000000002', 'Lonavala', '⛰️');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
insert into public._ids select 'g2', code from public.create_group_invite('10000000-0000-4000-8000-000000000002');

select public._as('00000000-0000-4000-8000-0000000000b1');
select public.join_group((select value from public._ids where key = 'g1'));
select public.join_group((select value from public._ids where key = 'g2'));
select public._as('00000000-0000-4000-8000-0000000000c1');
select public.join_group((select value from public._ids where key = 'g1'));
select public._as('00000000-0000-4000-8000-0000000000e1');
select public.join_group((select value from public._ids where key = 'g1'));

-- E1: alice adds dinner in G1 that BOB paid, split between alice, bob and carol.
select public._as('00000000-0000-4000-8000-0000000000a1');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-000000000001","type":"expense","amount":900,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-09-14T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
  '{"id":"30000000-0000-4000-8000-000000000001","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":900}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":300},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":300},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":300}]');
-- E2: alice adds a cab in G2 that bob paid; G2 is archived below.
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-000000000002","type":"expense","amount":400,"category":"Transport","merchant":"Cab","payment_method":"UPI","date":"2026-09-14T09:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000002"}',
  '{"id":"30000000-0000-4000-8000-000000000002","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":400}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":200},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":200}]');
-- P1: alice's personal expense (no group).
insert into public.transactions (id, owner_id, type, amount, category, merchant, payment_method, date, note)
values ('20000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-0000000000a1', 'expense', 50, 'Food', 'Chai', 'Cash', now(), '');

select public._as_admin();
-- The save as bob would send it: same payer, a new amount and merchant, carol taken out.
create function public._bob_edit_sql(p_amount int, p_paid_by text) returns text language sql as $f$
  select format($q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-000000000001","type":"expense","amount":%1$s,"category":"Food","merchant":"Shack (fixed)","payment_method":"Card","date":"2026-09-14T20:00:00Z","note":"bob fixed the bill","group_id":"10000000-0000-4000-8000-000000000001"}',
    '{"id":"30000000-0000-4000-8000-000000000001","paid_by":"%2$s","split_type":"equal","total_amount":%1$s}',
    '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":%3$s},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":%3$s}]') $q$,
    p_amount, p_paid_by, p_amount / 2);
$f$;
grant execute on function public._bob_edit_sql(int, text) to authenticated;

-- ------------------------------------------------------------
-- The payer can edit and delete an expense someone else added
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');

select public._expect_ok('the payer can save an edit to an expense someone else added',
  public._bob_edit_sql(800, '00000000-0000-4000-8000-0000000000b1'));
select public._expect_value('the edit took: new amount and merchant',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000001' and amount = 800 and merchant = 'Shack (fixed)' $q$, 1);
select public._expect_value('the edit took: carol''s share is removed, two remain at 400',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null and share_amount = 400 $q$, 2);
select public._expect_value('the expense still belongs to alice, who added it',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000001' and owner_id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);

select public._expect_state('the payer can''t hand ownership to themselves',
  $q$ update public.transactions set owner_id = '00000000-0000-4000-8000-0000000000b1' where id = '20000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_value('alice still owns it after that attempt',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000001' and owner_id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_error('the payer can''t make someone else the payer through a save',
  public._bob_edit_sql(800, '00000000-0000-4000-8000-0000000000c1'));
select public._expect_error('the payer can''t make someone else the payer directly',
  $q$ update public.splits set paid_by = '00000000-0000-4000-8000-0000000000c1' where id = '30000000-0000-4000-8000-000000000001' $q$);
select public._expect_value('bob is still the payer',
  $q$ select count(*) from public.splits where id = '30000000-0000-4000-8000-000000000001' and paid_by = '00000000-0000-4000-8000-0000000000b1' $q$, 1);

select public._expect_rows('the payer can soft-delete it',
  $q$ update public.transactions set deleted_at = now() where id = '20000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_ok('and bring it back by saving it again',
  public._bob_edit_sql(800, '00000000-0000-4000-8000-0000000000b1'));
select public._expect_value('it is back',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000001' and deleted_at is null $q$, 1);

select public._expect_rows('the payer can''t touch a personal expense of the adder''s',
  $q$ update public.transactions set amount = 1 where id = '20000000-0000-4000-8000-000000000009' $q$, 0);

-- ------------------------------------------------------------
-- Nobody else gains anything
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_rows('a member with a share who didn''t pay still can''t edit it',
  $q$ update public.transactions set amount = 1 where id = '20000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_error('nor save over it',
  public._bob_edit_sql(10, '00000000-0000-4000-8000-0000000000b1'));
select public._expect_rows('nor change a share',
  $q$ update public.split_shares set share_amount = 1 where split_id = '30000000-0000-4000-8000-000000000001' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000e1');
select public._expect_rows('a member with no part in it can''t delete it',
  $q$ update public.transactions set deleted_at = now() where id = '20000000-0000-4000-8000-000000000001' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_error('an outsider can''t save over it',
  public._bob_edit_sql(10, '00000000-0000-4000-8000-0000000000b1'));
select public._expect_rows('nor edit its split',
  $q$ update public.splits set total_amount = 1 where id = '30000000-0000-4000-8000-000000000001' $q$, 0);

-- ------------------------------------------------------------
-- The adder keeps full control; the payer's rights follow the payer
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('the adder can still make carol the payer',
  public._bob_edit_sql(800, '00000000-0000-4000-8000-0000000000c1'));

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('once carol is the payer, bob can no longer edit it',
  $q$ update public.transactions set amount = 1 where id = '20000000-0000-4000-8000-000000000001' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_rows('and carol now can',
  $q$ update public.transactions set note = 'carol paid' where id = '20000000-0000-4000-8000-000000000001' $q$, 1);

-- ------------------------------------------------------------
-- Archived groups stay read-only; leaving ends the right
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
update public.groups set archived_at = now() where id = '10000000-0000-4000-8000-000000000002';
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_error('the payer can''t edit an expense in an archived group',
  $q$ update public.transactions set amount = 1 where id = '20000000-0000-4000-8000-000000000002' $q$);

select public._as('00000000-0000-4000-8000-0000000000c1');
delete from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000c1';
select public._expect_rows('a payer who has left the group can''t edit it',
  $q$ update public.transactions set amount = 1 where id = '20000000-0000-4000-8000-000000000001' $q$, 0);

-- ------------------------------------------------------------
-- The adder still can't hand away ownership either
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_error('the adder can''t give the expense to someone else',
  $q$ update public.transactions set owner_id = '00000000-0000-4000-8000-0000000000b1' where id = '20000000-0000-4000-8000-000000000001' $q$);

select public._expect_value('the owner-lock trigger function is executable by no client role',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'private' and p.proname = 'lock_transaction_owner'
        and (has_function_privilege('authenticated', p.oid, 'EXECUTE') or has_function_privilege('anon', p.oid, 'EXECUTE')) $q$, 0);
select public._expect_value('the policy helper is executable by signed-in users only (policies run as them)',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'private' and p.proname = 'can_edit_group_expense'
        and has_function_privilege('authenticated', p.oid, 'EXECUTE') and not has_function_privilege('anon', p.oid, 'EXECUTE') $q$, 1);
select public._expect_value('both helpers exist',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'private' and p.proname in ('can_edit_group_expense', 'lock_transaction_owner') $q$, 2);

select public._as_admin();
select n, passed, test, detail from public._rls_results order by n;

rollback;
