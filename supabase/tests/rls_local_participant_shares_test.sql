-- Checks for migration 20260920000000_local_participant_shares, applied to live on 2026-09-20.
--
-- Ran 12/12 here and 12/12 against the live project, which rolled back leaving nothing behind.
--
-- Same conventions as rls_payer_edit_test.sql: one transaction ending in ROLLBACK, one result row
-- per check, nothing left behind. Safe to run on the live project as `postgres`.
--
-- Users: alice owns an expense and has a friend with no account; bob is a groupmate; mallory is
-- outside everything.

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

create function public._as_admin() returns void language plpgsql as $$
begin
  reset role;
  perform set_config('request.jwt.claims', '', true);
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
grant execute on function public._as(uuid), public._as_admin(), public._record(text, boolean, text),
  public._expect_error(text, text), public._expect_ok(text, text), public._expect_value(text, text, bigint),
  public._expect_state(text, text, text) to authenticated, anon;

-- ------------------------------------------------------------
-- Setup: alice creates a group, bob joins. Alice adds an expense she paid for.
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-00000000a001', 'alice@local.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-00000000b001', 'bob@local.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-00000000d001', 'mallory@local.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-00000000a001');
select public.create_group('10000000-0000-4000-8000-00000000a001', 'Flat', '🏠');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-00000000a001');

select public._as('00000000-0000-4000-8000-00000000b001');
select public.join_group((select value from public._ids where key = 'g1'));

select public._as('00000000-0000-4000-8000-00000000a001');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-00000000a001","type":"expense","amount":600,"category":"Food","merchant":"Dinner","payment_method":"UPI","date":"2026-09-20T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-00000000a001"}',
  '{"id":"30000000-0000-4000-8000-00000000a001","paid_by":"00000000-0000-4000-8000-00000000a001","split_type":"equal","total_amount":600}',
  '[{"profile_id":"00000000-0000-4000-8000-00000000a001","share_amount":300},{"profile_id":"00000000-0000-4000-8000-00000000b001","share_amount":300}]');

-- ------------------------------------------------------------
-- The point of the migration: a share for someone with no account
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000a001');

select public._expect_ok('the owner can record a share for a friend with no account',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000a001', '30000000-0000-4000-8000-00000000a001', null, 'Rohan', 200) $q$);

select public._expect_value('it is stored as a local name, with no profile',
  $q$ select count(*) from public.split_shares
      where id = '40000000-0000-4000-8000-00000000a001' and profile_id is null and local_name = 'Rohan' $q$, 1);

-- ------------------------------------------------------------
-- The constraint: exactly one of the two, never both and never neither
-- ------------------------------------------------------------
select public._expect_state('a share cannot name both a profile and a local person',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000a002', '30000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000b001', 'Rohan', 100) $q$, '23514');

-- Refused, but by the policy rather than the constraint: with both columns null neither branch of
-- the with-check matches, so RLS rejects it (42501) before the constraint (23514) is reached. Two
-- independent refusals, which is what belt and braces looks like; the test asserts that it cannot
-- be written, not which layer says no first.
select public._expect_error('a share cannot be for nobody at all',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000a003', '30000000-0000-4000-8000-00000000a001', null, null, 100) $q$);

select public._expect_state('a blank local name is not a name',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000a004', '30000000-0000-4000-8000-00000000a001', null, '   ', 100) $q$, '23514');

select public._expect_error('the same local person cannot take two shares of one split',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000a005', '30000000-0000-4000-8000-00000000a001', null, ' rohan ', 100) $q$);

-- ------------------------------------------------------------
-- The half that matters for review: who may write a local name
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000b001');

select public._expect_error('a groupmate cannot add a local participant to an expense they do not own',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000b001', '30000000-0000-4000-8000-00000000a001', null, 'Bob''s mate', 100) $q$);

select public._as('00000000-0000-4000-8000-00000000d001');

select public._expect_error('someone outside the group cannot add a local participant either',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000d001', '30000000-0000-4000-8000-00000000a001', null, 'Mallory''s mate', 100) $q$);

select public._expect_value('and still cannot read the split at all',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-00000000a001' $q$, 0);

-- ------------------------------------------------------------
-- Nothing else widened: reads are exactly as before
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000b001');

select public._expect_value('a groupmate still sees the split, local participant included',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-00000000a001' and deleted_at is null $q$, 3);

select public._as('00000000-0000-4000-8000-00000000a001');

select public._expect_value('a share for a real profile still requires them to be in the group',
  $q$ select count(*) from public.split_shares where local_name is null and deleted_at is null $q$, 2);

select public._expect_error('a share for a profile outside the group is still refused',
  $q$ insert into public.split_shares (id, split_id, profile_id, local_name, share_amount)
      values ('40000000-0000-4000-8000-00000000a006', '30000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000d001', null, 100) $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
