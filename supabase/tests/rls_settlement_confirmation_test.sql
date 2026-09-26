-- Checks for STAGED migration supabase/staged/20260926000000_settlement_confirmation.sql.
-- Not applied to live. Run with the staged file copied into a migrations directory (see
-- docs/local-friend-sync-v2.md, "Running the test locally").
--
-- Same conventions as the other suites: one transaction ending in ROLLBACK, one result row per
-- check, nothing left behind.
--
-- Users: alice and bob settle up with each other; carol is in their group but not a party; mallory
-- is outside everything.

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
  public._expect_state(text, text, text)
  to authenticated, anon;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-00000000a001', 'alice@local.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-00000000b001', 'bob@local.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-00000000c001', 'carol@local.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-00000000d001', 'mallory@local.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-00000000a001');
select public.create_group('10000000-0000-4000-8000-00000000a001', 'Flat', '🏠');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-00000000a001');
select public._as('00000000-0000-4000-8000-00000000b001');
select public.join_group((select value from public._ids where key = 'g1'));
select public._as('00000000-0000-4000-8000-00000000c001');
select public.join_group((select value from public._ids where key = 'g1'));

-- A settlement from before the migration: confirmed, nobody recorded as its author.
select public._as_admin();
insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id, status, recorded_by)
values ('50000000-0000-4000-8000-000000000000', '00000000-0000-4000-8000-00000000b001',
        '00000000-0000-4000-8000-00000000a001', 99, '10000000-0000-4000-8000-00000000a001', 'confirmed', null);

-- ------------------------------------------------------------
-- Recording: always pending, always by the caller
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000a001');

select public._expect_ok('alice records that she paid bob 500',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id)
      values ('50000000-0000-4000-8000-00000000a001', '00000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000b001', 500, '10000000-0000-4000-8000-00000000a001') $q$);

select public._expect_value('it is pending, recorded by alice, unanswered',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-00000000a001'
      and status = 'pending' and recorded_by = '00000000-0000-4000-8000-00000000a001'
      and responded_by is null and responded_at is null $q$, 1);

select public._expect_ok('alice records one claiming it is already confirmed and recorded by bob',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id, status, recorded_by, responded_by, responded_at)
      values ('50000000-0000-4000-8000-00000000a002', '00000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000b001', 50, '10000000-0000-4000-8000-00000000a001',
              'confirmed', '00000000-0000-4000-8000-00000000b001', '00000000-0000-4000-8000-00000000b001', now()) $q$);

select public._expect_value('the server overrules it: pending, recorded by alice',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-00000000a002'
      and status = 'pending' and recorded_by = '00000000-0000-4000-8000-00000000a001' and responded_by is null $q$, 1);

select public._expect_ok('re-sending the same row (the app''s upsert) is accepted',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id, deleted_at)
      values ('50000000-0000-4000-8000-00000000a001', '00000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000b001', 500, '10000000-0000-4000-8000-00000000a001', null)
      on conflict (id) do update set from_profile_id = excluded.from_profile_id, to_profile_id = excluded.to_profile_id,
        amount = excluded.amount, group_id = excluded.group_id, deleted_at = excluded.deleted_at $q$);

select public._expect_value('and leaves it pending',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-00000000a001' and status = 'pending' $q$, 1);

-- ------------------------------------------------------------
-- Answering: only the other party
-- ------------------------------------------------------------
select public._expect_state('alice cannot confirm her own settlement',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a001', true) $q$, '42501');

select public._expect_state('nor by writing the status directly',
  $q$ update public.settlements set status = 'confirmed' where id = '50000000-0000-4000-8000-00000000a001' $q$, '42501');

select public._as('00000000-0000-4000-8000-00000000c001');
select public._expect_state('carol, in the group but not a party, cannot confirm it',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a001', true) $q$, 'P0002');

select public._as('00000000-0000-4000-8000-00000000d001');
select public._expect_state('mallory cannot either, and cannot tell that it exists',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a001', true) $q$, 'P0002');

select public._as('00000000-0000-4000-8000-00000000b001');
select public._expect_state('while pending, bob cannot make it disappear: he answers instead',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-00000000a001' $q$, '42501');

select public._expect_ok('bob confirms it',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a001', true) $q$);

select public._expect_value('it is confirmed, answered by bob, with a time',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-00000000a001'
      and status = 'confirmed' and responded_by = '00000000-0000-4000-8000-00000000b001' and responded_at is not null $q$, 1);

select public._expect_state('bob cannot change his mind afterwards',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a001', false) $q$, '23514');

select public._expect_ok('a later re-send of the row leaves it confirmed',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id, deleted_at)
      values ('50000000-0000-4000-8000-00000000a001', '00000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000b001', 500, '10000000-0000-4000-8000-00000000a001', null)
      on conflict (id) do update set deleted_at = excluded.deleted_at $q$);

select public._expect_value('still confirmed',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-00000000a001' and status = 'confirmed' $q$, 1);

select public._expect_ok('a confirmed settlement can still be retracted by either party, as before',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-00000000a001' $q$);

-- ------------------------------------------------------------
-- Rejecting, with a reason
-- ------------------------------------------------------------
select public._expect_state('a reason longer than 200 characters is refused',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a002', false, repeat('x', 201)) $q$, '23514');

select public._expect_ok('bob rejects the second one, saying why',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a002', false, '  Never got it  ') $q$);

select public._expect_value('it is rejected, with the note trimmed',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-00000000a002'
      and status = 'rejected' and response_note = 'Never got it' and responded_by = '00000000-0000-4000-8000-00000000b001' $q$, 1);

select public._expect_state('bob cannot clear away a rejection: it stays for alice to see',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-00000000a002' $q$, '42501');

select public._as('00000000-0000-4000-8000-00000000a001');
select public._expect_ok('alice, who recorded it, can clear the rejected one away',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-00000000a002' $q$);

-- ------------------------------------------------------------
-- Withdrawing while pending
-- ------------------------------------------------------------
select public._expect_ok('alice records a third, then withdraws it before bob answers',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id)
      values ('50000000-0000-4000-8000-00000000a003', '00000000-0000-4000-8000-00000000a001',
              '00000000-0000-4000-8000-00000000b001', 75, '10000000-0000-4000-8000-00000000a001') $q$);
select public._expect_ok('withdrawing is a soft delete by the recorder',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-00000000a003' $q$);

select public._as('00000000-0000-4000-8000-00000000b001');
select public._expect_state('bob cannot answer a withdrawn settlement',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a003', true) $q$, '23514');

-- ------------------------------------------------------------
-- The facts stay locked, including who recorded it
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000a001');
select public._expect_state('the recorder cannot be rewritten',
  $q$ update public.settlements set recorded_by = '00000000-0000-4000-8000-00000000b001' where id = '50000000-0000-4000-8000-00000000a003' $q$, '23514');
select public._expect_state('nor the amount (unchanged rule)',
  $q$ update public.settlements set amount = 1 where id = '50000000-0000-4000-8000-00000000a003' $q$, '23514');

-- ------------------------------------------------------------
-- Settlements from before the migration behave as they always did
-- ------------------------------------------------------------
select public._expect_value('the old settlement reads as confirmed, with no recorder',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-000000000000' and status = 'confirmed' and recorded_by is null $q$, 1);
select public._expect_state('it cannot be re-opened for answering',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-000000000000', false) $q$, '23514');
select public._expect_ok('and either party can still retract it',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-000000000000' $q$);

-- ------------------------------------------------------------
-- Everyone in the group sees the state, nobody outside does
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-00000000c001');
select public._expect_value('carol sees every settlement in her group, status included',
  $q$ select count(*) from public.settlements where group_id = '10000000-0000-4000-8000-00000000a001' and status is not null $q$, 4);

select public._as('00000000-0000-4000-8000-00000000d001');
select public._expect_value('mallory sees none',
  $q$ select count(*) from public.settlements $q$, 0);

-- ------------------------------------------------------------
-- Privileges
-- ------------------------------------------------------------
set local role anon;
select public._expect_state('anon cannot call the answering function',
  $q$ select public.respond_to_settlement('50000000-0000-4000-8000-00000000a002', true) $q$, '42501');
reset role;

select public._as('00000000-0000-4000-8000-00000000a001');
select public._expect_state('settlements still cannot be truncated by a signed-in user',
  $q$ truncate public.settlements $q$, '42501');
select public._expect_state('or hard-deleted',
  $q$ delete from public.settlements where id = '50000000-0000-4000-8000-00000000a003' $q$, '42501');

select n, test, passed, detail from public._rls_results order by n;

rollback;
