-- Checks for migration 20260923000000_owner_private_local_ledger, applied to live on 2026-09-24.
-- 33/33 on the local harness and 33/33 against live (run so that it could only roll back: the
-- final statement raises with the results), leaving nothing behind.
--
-- Same conventions as the other suites: one transaction ending in ROLLBACK, one result row per
-- check, nothing left behind.
--
-- Users: alice has a friend with no account (Rohan); bob is in a group with her; mallory is outside
-- everything.

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

create function public._record(p_test text, p_passed boolean, p_detail text default null) returns void
language sql as $$
  insert into public._rls_results (test, passed, detail) values (p_test, p_passed, p_detail);
$$;

create function public._expect_error(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have been refused');
exception when others then
  perform public._record(p_test, true, sqlstate || ': ' || sqlerrm);
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

create function public._expect_state(p_test text, p_sql text, p_state text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have failed with ' || p_state);
exception when others then
  perform public._record(p_test, sqlstate = p_state, sqlstate || ': ' || sqlerrm);
end $$;

grant all on public._rls_results to authenticated, anon;
grant usage on sequence public._rls_results_n_seq to authenticated, anon;
grant execute on function public._as(uuid), public._record(text, boolean, text),
  public._expect_error(text, text), public._expect_ok(text, text), public._expect_value(text, text, bigint),
  public._expect_state(text, text, text)
  to authenticated, anon;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-00000000a001', 'alice@local.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-00000000b001', 'bob@local.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-00000000d001', 'mallory@local.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

-- Alice and Bob share a group, so "a groupmate cannot see it" is tested against someone who can see
-- plenty of Alice's other data.
select public._as('00000000-0000-4000-8000-00000000a001');
select public.create_group('10000000-0000-4000-8000-00000000a001', 'Flat', '🏠');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-00000000a001');
select public._as('00000000-0000-4000-8000-00000000b001');
select public.join_group((select value from public._ids where key = 'g1'));

-- ------------------------------------------------------------
-- The point: a personal expense split with a friend who has no account
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000a001');

select public._expect_ok('alice saves a dinner she split with Rohan, expense and split together',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a001","type":"expense","amount":600,"category":"Food","merchant":"Dinner","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":"","created_at":"2026-09-20T20:05:00Z"}',
        '{"id":"30000000-0000-4000-8000-00000000a001","split_type":"equal","total_amount":600,"paid_by_local_name":null,
          "shares":[{"local_name":null,"share_amount":300},{"local_name":"Rohan","share_amount":300}]}') $q$);

select public._expect_value('the expense is stored as hers, personal, with its original dates',
  $q$ select count(*) from public.transactions
      where id = '20000000-0000-4000-8000-00000000a001' and owner_id = '00000000-0000-4000-8000-00000000a001'
        and group_id is null and date = '2026-09-20T20:00:00Z' and created_at = '2026-09-20T20:05:00Z' $q$, 1);

select public._expect_value('and the split is stored with Rohan''s share',
  $q$ select count(*) from public.local_splits
      where transaction_id = '20000000-0000-4000-8000-00000000a001'
        and shares @> '[{"local_name":"Rohan","share_amount":300}]' $q$, 1);

select public._expect_ok('saving it again (an edit) updates rather than duplicates',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a001","type":"expense","amount":900,"category":"Food","merchant":"Dinner","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000a001","split_type":"exact","total_amount":900,"paid_by_local_name":"Rohan",
          "shares":[{"local_name":null,"share_amount":400},{"local_name":"Rohan","share_amount":500}]}') $q$);

select public._expect_value('one split, now Rohan paid and the amounts moved',
  $q$ select count(*) from public.local_splits
      where transaction_id = '20000000-0000-4000-8000-00000000a001'
        and paid_by_local_name = 'Rohan' and total_amount = 900 $q$, 1);

-- ------------------------------------------------------------
-- A split that does not add up is not a split
-- ------------------------------------------------------------
select public._expect_error('shares that do not add up to the amount are refused',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a002","type":"expense","amount":600,"category":"Food","merchant":"x","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000a002","total_amount":600,"shares":[{"local_name":null,"share_amount":300},{"local_name":"Rohan","share_amount":200}]}') $q$);

select public._expect_value('and nothing of that expense was left behind half-written',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-00000000a002' $q$, 0);

select public._expect_error('the same friend twice in one split is refused',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a003","type":"expense","amount":600,"category":"Food","merchant":"x","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000a003","total_amount":600,"shares":[{"local_name":"Rohan","share_amount":300},{"local_name":" rohan ","share_amount":300}]}') $q$);

select public._expect_error('a blank name is refused',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a004","type":"expense","amount":600,"category":"Food","merchant":"x","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000a004","total_amount":600,"shares":[{"local_name":null,"share_amount":300},{"local_name":"  ","share_amount":300}]}') $q$);

select public._expect_error('a negative share is refused',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a005","type":"expense","amount":100,"category":"Food","merchant":"x","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000a005","total_amount":100,"shares":[{"local_name":null,"share_amount":150},{"local_name":"Rohan","share_amount":-50}]}') $q$);

select public._expect_error('a share with no amount at all is refused',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a008","type":"expense","amount":100,"category":"Food","merchant":"x","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000a008","total_amount":100,"shares":[{"local_name":"Rohan","share_amount":100},{"local_name":"Meera"}]}') $q$);

select public._expect_error('a local split cannot be put on a group expense',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a006","type":"expense","amount":100,"category":"Food","merchant":"x","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-00000000a001"}',
        '{"id":"30000000-0000-4000-8000-00000000a006","total_amount":100,"shares":[{"local_name":null,"share_amount":50},{"local_name":"Rohan","share_amount":50}]}') $q$);

-- The policy itself, not just the function's own check: a direct insert onto a group expense.
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-00000000a007","type":"expense","amount":100,"category":"Food","merchant":"Chai","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-00000000a001"}',
  '{"id":"30000000-0000-4000-8000-00000000a007","paid_by":"00000000-0000-4000-8000-00000000a001","split_type":"equal","total_amount":100}',
  '[{"profile_id":"00000000-0000-4000-8000-00000000a001","share_amount":100}]');

select public._expect_error('even inserted directly, a local split cannot hang off a group expense',
  $q$ insert into public.local_splits (transaction_id, total_amount, shares)
      values ('20000000-0000-4000-8000-00000000a007', 100, '[{"local_name":null,"share_amount":50},{"local_name":"Rohan","share_amount":50}]') $q$);

-- ------------------------------------------------------------
-- An IOU with Rohan
-- ------------------------------------------------------------
select public._expect_ok('alice records that she lent Rohan 777',
  $q$ insert into public.local_settlements (id, from_local_name, to_local_name, amount, is_iou, date)
      values ('40000000-0000-4000-8000-00000000a001', null, 'Rohan', 777, true, '2026-09-20T03:58:11Z') $q$);

select public._expect_error('a settlement with nobody but herself is refused',
  $q$ insert into public.local_settlements (from_local_name, to_local_name, amount) values (null, null, 10) $q$);

select public._expect_error('a settlement from Rohan to Rohan is refused',
  $q$ insert into public.local_settlements (from_local_name, to_local_name, amount) values ('Rohan', ' rohan', 10) $q$);

select public._expect_ok('she can retract it (soft delete)',
  $q$ update public.local_settlements set deleted_at = now() where id = '40000000-0000-4000-8000-00000000a001' $q$);

-- ------------------------------------------------------------
-- The half that matters for review: nobody else sees or touches any of it
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000b001');

select public._expect_value('a groupmate cannot read her local splits',
  $q$ select count(*) from public.local_splits $q$, 0);
select public._expect_value('or her local settlements',
  $q$ select count(*) from public.local_settlements $q$, 0);
select public._expect_value('or the personal expense they hang off',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-00000000a001' $q$, 0);

select public._expect_error('a groupmate cannot attach a split to her expense',
  $q$ insert into public.local_splits (owner_id, transaction_id, total_amount, shares)
      values ('00000000-0000-4000-8000-00000000b001', '20000000-0000-4000-8000-00000000a001', 900,
              '[{"local_name":null,"share_amount":900}]') $q$);

select public._expect_error('or write one in her name',
  $q$ insert into public.local_splits (owner_id, transaction_id, total_amount, shares)
      values ('00000000-0000-4000-8000-00000000a001', '20000000-0000-4000-8000-00000000a001', 900,
              '[{"local_name":null,"share_amount":900}]') $q$);

select public._expect_error('or a settlement in her name',
  $q$ insert into public.local_settlements (owner_id, from_local_name, amount)
      values ('00000000-0000-4000-8000-00000000a001', 'Rohan', 5) $q$);

select public._expect_value('an update aimed at her split changes nothing',
  $q$ with u as (update public.local_splits set deleted_at = now() returning 1) select count(*) from u $q$, 0);

select public._expect_error('the save function cannot overwrite her expense either',
  $q$ select public.save_local_split(
        '{"id":"20000000-0000-4000-8000-00000000a001","type":"expense","amount":1,"category":"Food","merchant":"pwned","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":""}',
        '{"id":"30000000-0000-4000-8000-00000000b009","total_amount":1,"shares":[{"local_name":null,"share_amount":1}]}') $q$);

select public._as('00000000-0000-4000-8000-00000000d001');

select public._expect_value('a stranger sees nothing',
  $q$ select (select count(*) from public.local_splits) + (select count(*) from public.local_settlements) $q$, 0);

-- TRUNCATE ignores RLS entirely, and this project's default privileges grant it on every new table
-- to anon and authenticated (see 20260914033124). Either would wipe every account's private
-- ledger at once. DELETE too: there's no delete policy, but the privilege shouldn't exist either.
select public._expect_state('a signed-in user cannot truncate everyone''s local splits',
  $q$ truncate public.local_splits $q$, '42501');
select public._expect_state('or everyone''s local settlements',
  $q$ truncate public.local_settlements $q$, '42501');
select public._expect_state('nor hard-delete a local split',
  $q$ delete from public.local_splits $q$, '42501');
select public._expect_state('nor hard-delete a local settlement',
  $q$ delete from public.local_settlements $q$, '42501');

set local role anon;
select public._expect_error('and anon cannot even ask',
  $q$ select count(*) from public.local_splits $q$);
reset role;

-- ------------------------------------------------------------
-- Alice still sees all of hers, and the clock moved for other devices
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000a001');

select public._expect_value('alice sees her split and her (retracted) IOU',
  $q$ select (select count(*) from public.local_splits) + (select count(*) from public.local_settlements) $q$, 2);

select public._expect_value('her merchant was not overwritten by bob''s attempt',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-00000000a001' and merchant = 'Dinner' $q$, 1);

select n, test, passed, detail from public._rls_results order by n;

rollback;
