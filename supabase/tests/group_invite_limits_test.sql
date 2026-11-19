-- Checks for the migration 20261119000300_group_invite_limits_and_member_removal (issue #312, S-03): invite use cap, failed-attempt limit, creator-only member removal, reset link.
-- Same conventions as ai_daily_reads_test.sql: one transaction ending in ROLLBACK, one result row per check. Staged, not applied.

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

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

-- Setup: Alice creates the group; Bob, Carol, Dave and Mallory are other people.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a5', 'alice@invites.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b5', 'bob@invites.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c5', 'carol@invites.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000d5', 'dave@invites.test', '{"full_name":"Dave"}'),
  ('00000000-0000-4000-8000-0000000000e5', 'mallory@invites.test', '{"full_name":"Mallory"}');

select public._as('00000000-0000-4000-8000-0000000000a5');
select public.create_group('00000000-0000-4000-8000-0000000000f5', 'Goa trip', 'x');
insert into public._ids (key, value) select 'code', code from public.create_group_invite('00000000-0000-4000-8000-0000000000f5');

-- ------------------------------------------------------------
-- Use cap
-- ------------------------------------------------------------
select public._as_admin();
select public._expect_value('a new code has the default cap of 20 and no uses',
  $q$ select count(*) from public.group_invites where code = (select value from public._ids where key = 'code') and max_uses = 20 and used_count = 0 $q$, 1);
select public._expect_error('a cap above 20 is refused',
  $q$ update public.group_invites set max_uses = 21 $q$);
select public._expect_error('a cap below 1 is refused',
  $q$ update public.group_invites set max_uses = 0 $q$);

select public._as('00000000-0000-4000-8000-0000000000b5');
select public._expect_value('Bob joins with the code and gets the group id',
  format($q$ select (public.join_group(%L) = '00000000-0000-4000-8000-0000000000f5')::int $q$, (select value from public._ids where key = 'code')), 1);
select public._as_admin();
select public._expect_value('that spent one use',
  $q$ select used_count from public.group_invites where code = (select value from public._ids where key = 'code') $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000b5');
select public._expect_value('joining twice is a no-op',
  format($q$ select (public.join_group(%L) = '00000000-0000-4000-8000-0000000000f5')::int $q$, (select value from public._ids where key = 'code')), 1);
select public._as_admin();
select public._expect_value('and spends no second use',
  $q$ select used_count from public.group_invites where code = (select value from public._ids where key = 'code') $q$, 1);

update public.group_invites set max_uses = 2 where code = (select value from public._ids where key = 'code');
select public._as('00000000-0000-4000-8000-0000000000c5');
select public._expect_value('Carol takes the last use',
  format($q$ select (public.join_group(%L) = '00000000-0000-4000-8000-0000000000f5')::int $q$, (select value from public._ids where key = 'code')), 1);
select public._as('00000000-0000-4000-8000-0000000000d5');
select public._expect_value('a used-up code previews as nothing',
  format($q$ select count(*) from public.preview_invite(%L) $q$, (select value from public._ids where key = 'code')), 0);
select public._expect_value('a used-up code returns null, not a membership',
  format($q$ select (public.join_group(%L) is null)::int $q$, (select value from public._ids where key = 'code')), 1);
select public._as_admin();
select public._expect_value('Dave is not a member',
  $q$ select count(*) from public.group_members where profile_id = '00000000-0000-4000-8000-0000000000d5' $q$, 0);

-- ------------------------------------------------------------
-- Attempt limiting: 10 failed tries an hour, shared by preview and join
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000e5');
select public._expect_value('Mallory''s first ten wrong guesses all just return null',
  $q$ select sum((public.join_group('ZZZZZZZZ') is null)::int) from generate_series(1, 10) $q$, 10);
select public._expect_state('the eleventh try is refused with 54000',
  $q$ select public.join_group('ZZZZZZZZ') $q$, '54000');
select public._expect_state('and so is a preview',
  $q$ select * from public.preview_invite('ZZZZZZZZ') $q$, '54000');
select public._as('00000000-0000-4000-8000-0000000000d5');
select public._expect_value('somebody else''s tries are not affected',
  $q$ select (public.join_group('YYYYYYYY') is null)::int $q$, 1);

select public._as_admin();
update private.invite_attempts set hour = hour - interval '1 hour';
select public._as('00000000-0000-4000-8000-0000000000e5');
select public._expect_value('an hour later Mallory can try again',
  $q$ select (public.join_group('ZZZZZZZZ') is null)::int $q$, 1);

-- A preview that finds a live code does not count as a failure.
select public._as_admin();
delete from private.invite_attempts;
update public.group_invites set max_uses = 20 where code = (select value from public._ids where key = 'code');
select public._as('00000000-0000-4000-8000-0000000000d5');
select public._expect_value('a good preview finds the group',
  format($q$ select count(*) from public.preview_invite(%L) $q$, (select value from public._ids where key = 'code')), 1);
select public._as_admin();
select public._expect_value('and costs no failed try',
  $q$ select count(*) from private.invite_attempts $q$, 0);

-- ------------------------------------------------------------
-- Removing a member
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000c5');
select public._expect_state('Carol is a member but not the creator: she can''t remove Bob',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000b5') $q$, '42501');
select public._as('00000000-0000-4000-8000-0000000000e5');
select public._expect_state('Mallory, outside the group, can''t either',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000b5') $q$, '42501');
select public._as_anon();
select public._expect_denied('signed out can''t call it',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000b5') $q$);

select public._as('00000000-0000-4000-8000-0000000000a5');
select public._expect_state('the creator can''t remove themselves',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000a5') $q$, '22023');
select public._expect_state('removing someone who is not a member is P0002',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000d5') $q$, 'P0002');
select public._expect_ok('the creator removes Bob',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000b5') $q$);
select public._as_admin();
select public._expect_value('Bob is out and Carol and Alice remain',
  $q$ select count(*) from public.group_members where group_id = '00000000-0000-4000-8000-0000000000f5' $q$, 2);

update public.groups set archived_at = now() where id = '00000000-0000-4000-8000-0000000000f5';
select public._as('00000000-0000-4000-8000-0000000000a5');
select public._expect_state('in an archived group even the creator can''t remove anyone',
  $q$ select public.remove_group_member('00000000-0000-4000-8000-0000000000f5', '00000000-0000-4000-8000-0000000000c5') $q$, '42501');
select public._as_admin();
update public.groups set archived_at = null where id = '00000000-0000-4000-8000-0000000000f5';

-- ------------------------------------------------------------
-- Reset link
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000c5');
insert into public._ids (key, value) select 'newcode', code from public.reset_group_invites('00000000-0000-4000-8000-0000000000f5');
select public._expect_value('any member can reset: a new code came back',
  $q$ select count(*) from public._ids where key = 'newcode' and value ~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000d5');
select public._expect_value('the old code is dead',
  format($q$ select count(*) from public.preview_invite(%L) $q$, (select value from public._ids where key = 'code')), 0);
select public._expect_value('the new code works',
  format($q$ select (public.join_group(%L) = '00000000-0000-4000-8000-0000000000f5')::int $q$, (select value from public._ids where key = 'newcode')), 1);
select public._as('00000000-0000-4000-8000-0000000000e5');
select public._as_admin();
delete from private.invite_attempts;
select public._as('00000000-0000-4000-8000-0000000000e5');
select public._expect_state('a non-member can''t reset a group''s links',
  $q$ select * from public.reset_group_invites('00000000-0000-4000-8000-0000000000f5') $q$, '42501');

-- ------------------------------------------------------------
-- The new table is private
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000e5');
select public._expect_denied('a signed-in user can''t read the attempt counts',
  $q$ select count(*) from private.invite_attempts $q$);
select public._expect_denied('or wipe them',
  $q$ delete from private.invite_attempts $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
