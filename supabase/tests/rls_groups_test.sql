-- RLS and function tests for groups, invites and multi-user splits (step 4, PR 1).
--
-- Runs as one transaction that ends in ROLLBACK: it creates throwaway users, tables and helpers,
-- exercises the policies as each user, returns one row per check, and leaves nothing behind.
-- Safe to run against the live project as `postgres`, and against a local Postgres that has the
-- migrations plus a Supabase-style auth bootstrap.
--
-- Users: alice creates a group and invites bob; carol joins later; mallory is never invited.

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
grant execute on function public._as(uuid), public._as_anon(), public._as_admin(), public._record(text, boolean, text),
  public._expect_error(text, text), public._expect_denied(text, text), public._expect_ok(text, text), public._expect_value(text, text, bigint),
  public._expect_rows(text, text, int) to authenticated, anon;

-- ------------------------------------------------------------
-- Setup (admin): four users; profiles come from the on_auth_user_created trigger
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@rls.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@rls.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c1', 'carol@rls.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@rls.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

-- ------------------------------------------------------------
-- Creating a group
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');

select public._expect_ok('alice creates a group through create_group',
  $q$ select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️') $q$);
select public._expect_value('create_group made alice a member',
  $q$ select count(*) from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_ok('a retried create_group from the creator is a no-op',
  $q$ select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️') $q$);
select public._expect_value('the retry did not create a second membership',
  $q$ select count(*) from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_error('direct insert into groups is refused (no membership would be created)',
  $q$ insert into public.groups (id, name, created_by) values ('10000000-0000-4000-8000-000000000002', 'Sneaky', '00000000-0000-4000-8000-0000000000a1') $q$);
select public._expect_error('a blank group name is rejected',
  $q$ select public.create_group('10000000-0000-4000-8000-000000000003', '   ') $q$);

insert into public._ids select 'invite', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._expect_value('invite codes are 8 characters from the unambiguous alphabet',
  $q$ select count(*) from public._ids where key = 'invite' and value ~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$' $q$, 1);

-- A personal expense must keep working exactly as in step 3.
select public._expect_ok('alice still syncs a personal transaction (group_id null)',
  $q$ insert into public.transactions (id, owner_id, type, amount, category) values ('20000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000a1', 'expense', 50, 'Food') $q$);
select public._expect_ok('step 3 push still works: personal upsert with on conflict do update',
  $q$ insert into public.transactions (id, owner_id, type, amount, category, group_id, deleted_at) values ('20000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000a1', 'expense', 70, 'Food', null, null)
      on conflict (id) do update set amount = excluded.amount, deleted_at = excluded.deleted_at $q$);
select public._expect_ok('step 3 push still works: re-upserting the same personal row updates it',
  $q$ insert into public.transactions (id, owner_id, type, amount, category, group_id, deleted_at) values ('20000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000a1', 'expense', 75, 'Food', null, null)
      on conflict (id) do update set amount = excluded.amount, deleted_at = excluded.deleted_at $q$);
select public._expect_rows('step 3 push still works: soft-deleting a personal row',
  $q$ update public.transactions set deleted_at = now() where id = '20000000-0000-4000-8000-000000000002' $q$, 1);
select public._expect_value('step 3 pull still works: own personal rows, including soft-deleted ones',
  $q$ select count(*) from public.transactions where owner_id = '00000000-0000-4000-8000-0000000000a1' and group_id is null $q$, 2);

-- ------------------------------------------------------------
-- Gap 1: joining without an invite
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000d1');

select public._expect_value('mallory cannot see alice''s group',
  $q$ select count(*) from public.groups where id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_error('mallory cannot insert herself into a group by knowing its id',
  $q$ insert into public.group_members (group_id, profile_id) values ('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000d1') $q$);
select public._expect_error('join_group rejects a made-up code',
  $q$ select public.join_group('ABCDEFGH') $q$);
select public._expect_error('mallory cannot claim alice''s group id through create_group',
  $q$ select public.create_group('10000000-0000-4000-8000-000000000001', 'Mine now') $q$);
select public._expect_value('mallory cannot list the group''s invites',
  $q$ select count(*) from public.group_invites where group_id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_error('mallory cannot create an invite for a group she is not in',
  $q$ select * from public.create_group_invite('10000000-0000-4000-8000-000000000001') $q$);

-- ------------------------------------------------------------
-- Joining with an invite
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');

select public._expect_value('bob previews the invite: group name and inviter only',
  format($q$ select count(*) from public.preview_invite(%L) where group_name = 'Goa trip' and inviter_name = 'Alice' and member_count = 1 $q$,
    (select value from public._ids where key = 'invite')), 1);
select public._expect_value('the preview accepts lower-case, padded input',
  format($q$ select count(*) from public.preview_invite(%L) $q$, '  ' || lower((select value from public._ids where key = 'invite')) || ' '), 1);
select public._expect_ok('bob joins with the code',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite')));
select public._expect_ok('joining twice is a no-op',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite')));
select public._expect_value('bob is a member exactly once',
  $q$ select count(*) from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000b1' $q$, 1);
select public._expect_value('bob can now see the group',
  $q$ select count(*) from public.groups where id = '10000000-0000-4000-8000-000000000001' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_ok('carol joins with the same code (codes are multi-use until expiry)',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite')));

-- Expired and revoked codes (admin ages one invite, alice revokes another).
select public._as_admin();
update public.group_invites set expires_at = now() - interval '1 minute' where code = (select value from public._ids where key = 'invite');
select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_error('an expired code cannot be used',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite')));
select public._expect_value('an expired code previews as nothing',
  format($q$ select count(*) from public.preview_invite(%L) $q$, (select value from public._ids where key = 'invite')), 0);

select public._as('00000000-0000-4000-8000-0000000000a1');
insert into public._ids select 'invite2', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_error('mallory cannot revoke an invite',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'invite2')));
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('the group creator revokes the second invite',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'invite2')));
select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_error('a revoked code cannot be used',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite2')));

-- ------------------------------------------------------------
-- Gap 2: profile visibility
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('bob sees his groupmates'' profiles and his own (alice, bob, carol)',
  $q$ select count(*) from public.profiles where id in ('00000000-0000-4000-8000-0000000000a1','00000000-0000-4000-8000-0000000000b1','00000000-0000-4000-8000-0000000000c1','00000000-0000-4000-8000-0000000000d1') $q$, 3);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('mallory sees only her own profile',
  $q$ select count(*) from public.profiles where id in ('00000000-0000-4000-8000-0000000000a1','00000000-0000-4000-8000-0000000000b1','00000000-0000-4000-8000-0000000000c1','00000000-0000-4000-8000-0000000000d1') $q$, 1);

-- ------------------------------------------------------------
-- Gap 3: group expenses and share visibility
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');

select public._expect_ok('alice saves a 100.00 expense split alice 33.34 / bob 33.33 / carol 33.33',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000001","type":"expense","amount":100.00,"category":"Food","merchant":"Beach shack","payment_method":"UPI","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000001","total_amount":100.00,"paid_by":"00000000-0000-4000-8000-0000000000a1","split_type":"equal"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":33.34},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":33.33},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":33.33}]') $q$);

select public._expect_error('shares that do not add up to the amount are rejected (33.33 x 3)',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000002","type":"expense","amount":100.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000002","total_amount":100.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":33.33},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":33.33},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":33.33}]') $q$);

select public._expect_error('a share for a non-member (mallory) is rejected',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000003","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000003","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":5.00},{"profile_id":"00000000-0000-4000-8000-0000000000d1","share_amount":5.00}]') $q$);

select public._expect_error('a non-member payer is rejected',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000004","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000004","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000d1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":10.00}]') $q$);

select public._expect_ok('re-saving the expense with new shares replaces them (edit: alice 50 / bob 50)',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000001","type":"expense","amount":100.00,"category":"Food","merchant":"Beach shack","payment_method":"UPI","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000001","total_amount":100.00,"paid_by":"00000000-0000-4000-8000-0000000000a1","split_type":"exact"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":50.00},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":50.00}]') $q$);

select public._expect_value('the edit left exactly one split',
  $q$ select count(*) from public.splits where transaction_id = '30000000-0000-4000-8000-000000000001' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_value('carol (member, no longer in the split) still sees the expense',
  $q$ select count(*) from public.transactions where id = '30000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_value('carol sees every active share of the split, not just her own (gap 3)',
  $q$ select count(*) from public.split_shares ss join public.splits s on s.id = ss.split_id where s.transaction_id = '30000000-0000-4000-8000-000000000001' and ss.deleted_at is null $q$, 2);
select public._expect_value('the edit soft-deleted carol''s removed share instead of deleting it',
  $q$ select count(*) from public.split_shares ss join public.splits s on s.id = ss.split_id where s.transaction_id = '30000000-0000-4000-8000-000000000001' and ss.profile_id = '00000000-0000-4000-8000-0000000000c1' and ss.deleted_at is not null $q$, 1);
select public._expect_value('carol cannot see alice''s personal transaction',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000001' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('bob cannot edit alice''s group expense (decision 2)',
  $q$ update public.transactions set note = 'bob was here' where id = '30000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_denied('bob cannot hard-delete alice''s group expense',
  $q$ delete from public.transactions where id = '30000000-0000-4000-8000-000000000001' $q$);
select public._expect_error('bob cannot overwrite alice''s expense through save_group_expense',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000001","type":"expense","amount":1.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000001","total_amount":1.00,"paid_by":"00000000-0000-4000-8000-0000000000b1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":1.00}]') $q$);
select public._expect_rows('bob cannot rewrite a share of alice''s split',
  $q$ update public.split_shares set share_amount = 0 where profile_id = '00000000-0000-4000-8000-0000000000b1' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('mallory sees none of the group''s transactions',
  $q$ select count(*) from public.transactions where group_id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_value('mallory sees none of its splits',
  $q$ select count(*) from public.splits where transaction_id = '30000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_value('mallory sees none of its shares',
  $q$ select count(*) from public.split_shares where profile_id in ('00000000-0000-4000-8000-0000000000a1','00000000-0000-4000-8000-0000000000b1') $q$, 0);

-- ------------------------------------------------------------
-- Gap 4: attaching transactions to other people's groups
-- ------------------------------------------------------------
select public._expect_error('mallory cannot add her own transaction to alice''s group',
  $q$ insert into public.transactions (id, owner_id, type, amount, category, group_id) values ('20000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-0000000000d1', 'expense', 999, 'Spam', '10000000-0000-4000-8000-000000000001') $q$);
select public._expect_ok('mallory creates her own group',
  $q$ select public.create_group('10000000-0000-4000-8000-000000000009', 'Mallory''s group') $q$);

select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_error('alice cannot move her personal transaction into mallory''s group',
  $q$ update public.transactions set group_id = '10000000-0000-4000-8000-000000000009' where id = '20000000-0000-4000-8000-000000000001' $q$);
select public._expect_error('a split on a personal transaction is refused (sharing only inside groups)',
  $q$ insert into public.splits (id, transaction_id, total_amount, paid_by) values ('40000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000001', 50, '00000000-0000-4000-8000-0000000000a1') $q$);

-- ------------------------------------------------------------
-- Settlements
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_ok('bob records paying alice 50 in the group',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id) values ('50000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000b1', '00000000-0000-4000-8000-0000000000a1', 50, '10000000-0000-4000-8000-000000000001') $q$);
select public._expect_error('bob cannot record a settlement between alice and carol',
  $q$ insert into public.settlements (from_profile_id, to_profile_id, amount, group_id) values ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-0000000000c1', 10, '10000000-0000-4000-8000-000000000001') $q$);
select public._expect_error('a settlement without a group is refused',
  $q$ insert into public.settlements (from_profile_id, to_profile_id, amount) values ('00000000-0000-4000-8000-0000000000b1', '00000000-0000-4000-8000-0000000000a1', 10) $q$);
select public._expect_error('a settlement with yourself is refused',
  $q$ insert into public.settlements (from_profile_id, to_profile_id, amount, group_id) values ('00000000-0000-4000-8000-0000000000b1', '00000000-0000-4000-8000-0000000000b1', 10, '10000000-0000-4000-8000-000000000001') $q$);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_value('carol (member, not a party) sees the group settlement',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-000000000001' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('mallory does not see the settlement',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_error('mallory cannot record a settlement with alice in alice''s group',
  $q$ insert into public.settlements (from_profile_id, to_profile_id, amount, group_id) values ('00000000-0000-4000-8000-0000000000d1', '00000000-0000-4000-8000-0000000000a1', 10, '10000000-0000-4000-8000-000000000001') $q$);

-- ------------------------------------------------------------
-- No hard deletes: only soft deletes, which reach other devices
-- ------------------------------------------------------------
select public._as_admin();
select public._expect_value('no client role holds DELETE or TRUNCATE on any app table except group_members',
  $q$ select count(*) from unnest(array['public.transactions', 'public.splits', 'public.split_shares', 'public.settlements', 'public.groups', 'public.group_invites', 'public.profiles']::regclass[]) t
      cross join unnest(array['anon', 'authenticated']) r
      where has_table_privilege(r, t, 'DELETE') or has_table_privilege(r, t, 'TRUNCATE') $q$, 0);
select public._expect_value('group_members: authenticated keeps DELETE (leaving) but not TRUNCATE; anon has neither',
  $q$ select (has_table_privilege('authenticated', 'public.group_members', 'DELETE'))::int
          + (not has_table_privilege('authenticated', 'public.group_members', 'TRUNCATE'))::int
          + (not has_table_privilege('anon', 'public.group_members', 'TRUNCATE'))::int $q$, 3);

select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_denied('alice cannot hard-delete her own personal transaction',
  $q$ delete from public.transactions where id = '20000000-0000-4000-8000-000000000001' $q$);
select public._expect_denied('alice cannot hard-delete her own group expense',
  $q$ delete from public.transactions where id = '30000000-0000-4000-8000-000000000001' $q$);
select public._expect_denied('alice cannot hard-delete the expense''s split',
  $q$ delete from public.splits where transaction_id = '30000000-0000-4000-8000-000000000001' $q$);
select public._expect_denied('alice cannot hard-delete a share of her own split',
  $q$ delete from public.split_shares where split_id = '40000000-0000-4000-8000-000000000001' $q$);
select public._expect_denied('alice cannot truncate settlements (truncate ignores RLS)',
  $q$ truncate public.settlements $q$);
select public._expect_value('after the refused deletes, both transactions, the split and its shares all still exist',
  $q$ select (select count(*) from public.transactions where id in ('20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001'))
          + (select count(*) from public.splits where transaction_id = '30000000-0000-4000-8000-000000000001')
          + (select count(*) from public.split_shares where split_id = '40000000-0000-4000-8000-000000000001' and deleted_at is null) $q$, 5);

-- A direct share edit (not through save_group_expense) must still bump the parent transaction, or
-- members' pulls would never see it. The whole script is one transaction, so now() can't show the
-- bump; the row's ctid changes whenever the row is rewritten.
insert into public._ids select 'ctid', ctid::text from public.transactions where id = '30000000-0000-4000-8000-000000000001';
select public._expect_rows('alice edits a share directly',
  $q$ update public.split_shares set percentage = 50 where split_id = '40000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000a1' and deleted_at is null $q$, 1);
select public._expect_value('the direct share edit bumped the parent transaction',
  $q$ select count(*) from public.transactions where id = '30000000-0000-4000-8000-000000000001' and ctid::text <> (select value from public._ids where key = 'ctid') $q$, 1);

select public._expect_ok('alice adds a second expense shared with carol',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000006","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000006","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":5.00},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":5.00}]') $q$);
select public._expect_error('the same person twice in one split is rejected',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000008","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000008","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":5.00},{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":5.00}]') $q$);
select public._expect_ok('alice adds a third expense to delete',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000007","type":"expense","amount":20.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000007","total_amount":20.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":10.00},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":10.00}]') $q$);
select public._expect_rows('alice soft-deletes her group expense',
  $q$ update public.transactions set deleted_at = now() where id = '30000000-0000-4000-8000-000000000007' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('bob pulls the soft-deleted expense and sees it is deleted',
  $q$ select count(*) from public.transactions where id = '30000000-0000-4000-8000-000000000007' and deleted_at is not null $q$, 1);
select public._expect_denied('bob cannot hard-delete the settlement he recorded',
  $q$ delete from public.settlements where id = '50000000-0000-4000-8000-000000000001' $q$);
select public._expect_rows('bob retracts his settlement by soft delete',
  $q$ update public.settlements set deleted_at = now() where id = '50000000-0000-4000-8000-000000000001' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_value('carol (member) sees the settlement as retracted',
  $q$ select count(*) from public.settlements where id = '50000000-0000-4000-8000-000000000001' and deleted_at is not null $q$, 1);
select public._expect_rows('carol (not a party) cannot retract bob''s settlement',
  $q$ update public.settlements set deleted_at = null where id = '50000000-0000-4000-8000-000000000001' $q$, 0);

-- ------------------------------------------------------------
-- Leaving
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('bob cannot remove alice from the group',
  $q$ delete from public.group_members where profile_id = '00000000-0000-4000-8000-0000000000a1' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_rows('carol leaves the group',
  $q$ delete from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000c1' $q$, 1);
select public._expect_value('after leaving, carol no longer sees the group''s expenses',
  $q$ select count(*) from public.transactions where group_id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_value('after leaving, carol no longer sees alice''s profile',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a1' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice removes carol (now a former member) from an old expense',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000006","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000006","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":10.00}]') $q$);
select public._expect_value('carol''s share on that expense is soft-deleted, alice''s is now 10.00',
  $q$ select count(*) from public.split_shares where split_id = '40000000-0000-4000-8000-000000000006'
      and ((profile_id = '00000000-0000-4000-8000-0000000000c1' and deleted_at is not null) or (profile_id = '00000000-0000-4000-8000-0000000000a1' and deleted_at is null and share_amount = 10.00)) $q$, 2);
select public._expect_error('a former member cannot be added back into a split',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000006","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000006","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":5.00},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":5.00}]') $q$);

-- ------------------------------------------------------------
-- Archiving
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('bob (not the creator) cannot archive the group',
  $q$ update public.groups set archived_at = now() where id = '10000000-0000-4000-8000-000000000001' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000a1');
insert into public._ids select 'invite3', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._expect_denied('alice cannot hard-delete the group',
  $q$ delete from public.groups where id = '10000000-0000-4000-8000-000000000001' $q$);
select public._expect_rows('alice archives the group',
  $q$ update public.groups set archived_at = now() where id = '10000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_error('no new expenses in an archived group',
  $q$ select public.save_group_expense(
        '{"id":"30000000-0000-4000-8000-000000000005","type":"expense","amount":10.00,"category":"Food","date":"2026-09-14T12:00:00Z","group_id":"10000000-0000-4000-8000-000000000001"}',
        '{"id":"40000000-0000-4000-8000-000000000005","total_amount":10.00,"paid_by":"00000000-0000-4000-8000-0000000000a1"}',
        '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":10.00}]') $q$);
select public._expect_error('no new invites for an archived group',
  $q$ select * from public.create_group_invite('10000000-0000-4000-8000-000000000001') $q$);

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('bob still reads the archived group''s history (3 expenses, one soft-deleted)',
  $q$ select count(*) from public.transactions where group_id = '10000000-0000-4000-8000-000000000001' $q$, 3);
select public._expect_error('no settlements in an archived group',
  $q$ insert into public.settlements (from_profile_id, to_profile_id, amount, group_id) values ('00000000-0000-4000-8000-0000000000b1', '00000000-0000-4000-8000-0000000000a1', 5, '10000000-0000-4000-8000-000000000001') $q$);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_error('an unexpired code for an archived group cannot be used',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite3')));

-- ------------------------------------------------------------
-- Anonymous callers
-- ------------------------------------------------------------
select public._as_anon();
select public._expect_error('anon cannot call create_group',
  $q$ select public.create_group('10000000-0000-4000-8000-00000000000a', 'Anon') $q$);
select public._expect_error('anon cannot call join_group',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite3')));
select public._expect_error('anon cannot call preview_invite',
  format($q$ select * from public.preview_invite(%L) $q$, (select value from public._ids where key = 'invite3')));
select public._expect_value('anon sees no profiles',
  $q$ select count(*) from public.profiles $q$, 0);

-- The calls above can also be refused by the functions' own auth.uid() checks; verify the
-- grants themselves so a re-granted EXECUTE can't hide behind that second layer.
select public._as_admin();
select public._expect_value('anon holds EXECUTE on none of the new functions',
  $q$ select count(*) from unnest(array['public.create_group(uuid,text,text)', 'public.create_group_invite(uuid)', 'public.revoke_group_invite(text)', 'public.preview_invite(text)', 'public.join_group(text)', 'public.save_group_expense(jsonb,jsonb,jsonb)']::regprocedure[]) f where has_function_privilege('anon', f, 'EXECUTE') $q$, 0);
select public._expect_value('authenticated holds EXECUTE on all six new functions',
  $q$ select count(*) from unnest(array['public.create_group(uuid,text,text)', 'public.create_group_invite(uuid)', 'public.revoke_group_invite(text)', 'public.preview_invite(text)', 'public.join_group(text)', 'public.save_group_expense(jsonb,jsonb,jsonb)']::regprocedure[]) f where has_function_privilege('authenticated', f, 'EXECUTE') $q$, 6);

-- ------------------------------------------------------------
select public._as_admin();
select n, passed, test, detail from public._rls_results order by n;

rollback;
