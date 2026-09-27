-- Checks for migration leave_rules_and_immutable_records (the step 4 audit's RLS gaps).
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check, nothing left behind. Safe to run on the live project as `postgres`.
--
-- Users: alice creates Goa trip and Flat; bob joins both; carol joins Goa trip; mallory is outside.

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
-- Setup: alice creates Goa trip (G1) and Flat (G2); bob joins both; carol joins G1.
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@leave.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@leave.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c1', 'carol@leave.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@leave.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️');
select public.create_group('10000000-0000-4000-8000-000000000002', 'Flat', '🏠');
insert into public._ids select 'alice_g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
insert into public._ids select 'alice_g2', code from public.create_group_invite('10000000-0000-4000-8000-000000000002');

select public._as('00000000-0000-4000-8000-0000000000b1');
select public.join_group((select value from public._ids where key = 'alice_g1'));
select public.join_group((select value from public._ids where key = 'alice_g2'));
insert into public._ids select 'bob_g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
insert into public._ids select 'bob_g2', code from public.create_group_invite('10000000-0000-4000-8000-000000000002');

select public._as('00000000-0000-4000-8000-0000000000c1');
select public.join_group((select value from public._ids where key = 'alice_g1'));
insert into public._ids select 'carol_g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');

-- carol's expense E1 in G1, split with bob; bob's expense E2 in G1; bob's settlement S1 to carol in G1.
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-000000000001","type":"expense","amount":90,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-09-14T10:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
  '{"id":"30000000-0000-4000-8000-000000000001","paid_by":"00000000-0000-4000-8000-0000000000c1","split_type":"equal","total_amount":90}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":45},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":45}]');
select public._as('00000000-0000-4000-8000-0000000000b1');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-000000000002","type":"expense","amount":40,"category":"Food","merchant":"Chai","payment_method":"UPI","date":"2026-09-14T11:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
  '{"id":"30000000-0000-4000-8000-000000000002","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":40}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":40}]');
insert into public.settlements (id, from_profile_id, to_profile_id, amount, date, note, group_id)
values ('40000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000b1', '00000000-0000-4000-8000-0000000000c1', 45, '2026-09-14T12:00:00Z', 'paid back', '10000000-0000-4000-8000-000000000001');
insert into public.transactions (id, owner_id, type, amount, category, merchant)
values ('20000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-0000000000b1', 'expense', 15, 'Food', 'Personal');

-- ------------------------------------------------------------
-- 5. Group names: 1 to 60 characters
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_rows('the creator (a member) renames the group',
  $q$ update public.groups set name = 'Goa 2026' where id = '10000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_state('an empty group name is refused',
  $q$ update public.groups set name = '' where id = '10000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('a blank (spaces only) group name is refused',
  $q$ update public.groups set name = '    ' where id = '10000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('a 61-character group name is refused',
  format($q$ update public.groups set name = %L where id = '10000000-0000-4000-8000-000000000001' $q$, repeat('x', 61)), '23514');
select public._expect_rows('a 60-character group name is accepted',
  format($q$ update public.groups set name = %L where id = '10000000-0000-4000-8000-000000000001' $q$, repeat('x', 60)), 1);
select public._expect_rows('a name padded with spaces counts its trimmed length',
  format($q$ update public.groups set name = %L where id = '10000000-0000-4000-8000-000000000001' $q$, '  ' || repeat('y', 60) || '  '), 1);
select public._expect_error('create_group still refuses a 61-character name',
  format($q$ select public.create_group('10000000-0000-4000-8000-000000000009', %L) $q$, repeat('x', 61)));
select public._expect_rows('restore the name',
  $q$ update public.groups set name = 'Goa trip' where id = '10000000-0000-4000-8000-000000000001' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('a member who is not the creator still cannot rename',
  $q$ update public.groups set name = 'Bob''s trip' where id = '10000000-0000-4000-8000-000000000001' $q$, 0);

-- ------------------------------------------------------------
-- 4. Settlements: immutable except deleted_at
-- ------------------------------------------------------------
select public._expect_state('the recorder cannot change the amount',
  $q$ update public.settlements set amount = 1 where id = '40000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('the recorder cannot redirect it to someone else',
  $q$ update public.settlements set to_profile_id = '00000000-0000-4000-8000-0000000000a1' where id = '40000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('the recorder cannot move it to another group',
  $q$ update public.settlements set group_id = '10000000-0000-4000-8000-000000000002' where id = '40000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('the recorder cannot change the date',
  $q$ update public.settlements set date = now() where id = '40000000-0000-4000-8000-000000000001' $q$, '23514');
select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_state('the other party cannot change the note',
  $q$ update public.settlements set note = 'never happened' where id = '40000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('the other party cannot flip it into an IOU',
  $q$ update public.settlements set is_iou = true where id = '40000000-0000-4000-8000-000000000001' $q$, '23514');
-- Since settlement confirmation (20260926000000), a new settlement is pending and the other party
-- answers it rather than deleting it. Once confirmed, either party can still retract it.
select public._expect_ok('the other party confirms it',
  $q$ select public.respond_to_settlement('40000000-0000-4000-8000-000000000001', true) $q$);
select public._expect_rows('the other party can still retract it once confirmed',
  $q$ update public.settlements set deleted_at = now() where id = '40000000-0000-4000-8000-000000000001' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_rows('the recorder can revive it (a queued push after a retraction)',
  $q$ update public.settlements set deleted_at = null where id = '40000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_ok('re-sending the same settlement as the app''s upsert does is accepted',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, date, note, is_iou, group_id, created_at, deleted_at)
      select id, from_profile_id, to_profile_id, 45.00, date, note, is_iou, group_id, created_at, null
      from public.settlements where id = '40000000-0000-4000-8000-000000000001'
      on conflict (id) do update set
        from_profile_id = excluded.from_profile_id, to_profile_id = excluded.to_profile_id, amount = excluded.amount,
        date = excluded.date, note = excluded.note, is_iou = excluded.is_iou, group_id = excluded.group_id,
        created_at = excluded.created_at, deleted_at = excluded.deleted_at $q$);
select public._expect_state('the same upsert with a different amount is refused',
  $q$ insert into public.settlements (id, from_profile_id, to_profile_id, amount, date, note, is_iou, group_id, created_at, deleted_at)
      select id, from_profile_id, to_profile_id, 999, date, note, is_iou, group_id, created_at, null
      from public.settlements where id = '40000000-0000-4000-8000-000000000001'
      on conflict (id) do update set amount = excluded.amount $q$, '23514');
select public._expect_value('the settlement is unchanged',
  $q$ select count(*) from public.settlements where id = '40000000-0000-4000-8000-000000000001' and amount = 45 and note = 'paid back' and to_profile_id = '00000000-0000-4000-8000-0000000000c1' and deleted_at is null $q$, 1);

-- ------------------------------------------------------------
-- 3. A group expense stays in its group
-- ------------------------------------------------------------
select public._expect_state('the owner cannot take a group expense out of its group',
  $q$ update public.transactions set group_id = null where id = '20000000-0000-4000-8000-000000000002' $q$, '23514');
select public._expect_state('the owner cannot move it to another group they belong to',
  $q$ update public.transactions set group_id = '10000000-0000-4000-8000-000000000002' where id = '20000000-0000-4000-8000-000000000002' $q$, '23514');
select public._expect_state('save_group_expense cannot move it to another group either',
  $q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-000000000002","type":"expense","amount":40,"category":"Food","merchant":"Chai","payment_method":"UPI","date":"2026-09-14T11:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000002"}',
    '{"id":"30000000-0000-4000-8000-000000000002","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":40}',
    '[{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":40}]') $q$, '23514');
select public._expect_ok('re-saving it in the same group still works (the app''s edit path)',
  $q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-000000000002","type":"expense","amount":40,"category":"Food","merchant":"Chai edited","payment_method":"UPI","date":"2026-09-14T11:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
    '{"id":"30000000-0000-4000-8000-000000000002","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":40}',
    '[{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":40}]') $q$);
select public._expect_rows('the owner can still soft-delete it',
  $q$ update public.transactions set deleted_at = now() where id = '20000000-0000-4000-8000-000000000002' $q$, 1);
select public._expect_ok('a personal expense can still move into a group (the app''s path)',
  $q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-000000000009","type":"expense","amount":15,"category":"Food","merchant":"Personal","payment_method":"UPI","date":"2026-09-14T11:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
    '{"id":"30000000-0000-4000-8000-000000000009","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":15}',
    '[{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":15}]') $q$);
select public._expect_value('that expense is now in the group',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000009' and group_id = '10000000-0000-4000-8000-000000000001' $q$, 1);
select public._expect_state('the personal sync upsert (group_id null) cannot pull a group expense back out',
  $q$ insert into public.transactions (id, owner_id, type, amount, category, group_id)
      values ('20000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-0000000000b1', 'expense', 15, 'Food', null)
      on conflict (id) do update set group_id = excluded.group_id $q$, '23514');

-- ------------------------------------------------------------
-- 2. Invites stop working when their creator leaves; 1. creator powers need membership
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('a creator who is a member revokes someone else''s invite (G2)',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'bob_g2')));
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_error('a member who is neither inviter nor creator still cannot revoke',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'carol_g1')));

-- alice (creator of G1, inviter of alice_g1) leaves G1.
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_rows('alice leaves Goa trip',
  $q$ delete from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000a1' $q$, 1);
select public._expect_rows('the creator who LEFT cannot rename the group',
  $q$ update public.groups set name = 'Renamed by ex-member' where id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._expect_rows('the creator who LEFT cannot archive the group',
  $q$ update public.groups set archived_at = now() where id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._as_admin();
update public.groups set archived_at = now() where id = '10000000-0000-4000-8000-000000000001';
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_rows('the creator who LEFT cannot un-archive the group',
  $q$ update public.groups set archived_at = null where id = '10000000-0000-4000-8000-000000000001' $q$, 0);
select public._as_admin();
update public.groups set archived_at = null where id = '10000000-0000-4000-8000-000000000001';
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_error('the creator who LEFT cannot revoke a member''s invite',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'bob_g1')));
select public._expect_error('the creator who LEFT cannot revoke their own old invite',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'alice_g1')));
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_rows('a creator who is still a member can archive (G2)',
  $q$ update public.groups set archived_at = now() where id = '10000000-0000-4000-8000-000000000002' $q$, 1);
select public._expect_rows('and un-archive it',
  $q$ update public.groups set archived_at = null where id = '10000000-0000-4000-8000-000000000002' $q$, 1);

select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('an invite from someone who LEFT previews as nothing',
  format($q$ select count(*) from public.preview_invite(%L) $q$, (select value from public._ids where key = 'alice_g1')), 0);
select public._expect_error('an invite from someone who LEFT cannot be used to join',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'alice_g1')));
select public._expect_value('mallory did not get in',
  $q$ select count(*) from public.group_members where profile_id = '00000000-0000-4000-8000-0000000000d1' $q$, 0);
select public._expect_value('an invite from a current member still previews',
  format($q$ select count(*) from public.preview_invite(%L) $q$, (select value from public._ids where key = 'bob_g1')), 1);
select public._expect_ok('an invite from a current member still lets people join',
  format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'bob_g1')));
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_ok('an inviter who is still a member revokes their own invite',
  format($q$ select public.revoke_group_invite(%L) $q$, (select value from public._ids where key = 'bob_g1')));

-- carol leaves G1 but still owns E1.
select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_rows('carol leaves Goa trip',
  $q$ delete from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000c1' $q$, 1);
select public._expect_state('an ex-member cannot pull their old expense out of the group',
  $q$ update public.transactions set group_id = null where id = '20000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_value('the expense is still in the group for the members who remain',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000001' and group_id = '10000000-0000-4000-8000-000000000001' $q$, 1);

-- ------------------------------------------------------------
-- Service-role deletion still works (approved: permanent deletion is service-role only)
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-000000000003","type":"expense","amount":20,"category":"Food","merchant":"Rent","payment_method":"UPI","date":"2026-09-14T11:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000002"}',
  '{"id":"30000000-0000-4000-8000-000000000003","paid_by":"00000000-0000-4000-8000-0000000000b1","split_type":"equal","total_amount":20}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":20}]');
insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id)
values ('40000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000b1', '00000000-0000-4000-8000-0000000000a1', 10, '10000000-0000-4000-8000-000000000002');
select public._as_admin();
select public._expect_rows('the service role can still delete a group',
  $q$ delete from public.groups where id = '10000000-0000-4000-8000-000000000002' $q$, 1);
select public._expect_value('its expense lost the group through the foreign key',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-000000000003' and group_id is null $q$, 1);
select public._expect_value('its settlement lost the group through the foreign key',
  $q$ select count(*) from public.settlements where id = '40000000-0000-4000-8000-000000000002' and group_id is null $q$, 1);

select public._expect_value('the trigger functions are executable by no client role',
  $q$ select count(*) from unnest(array['private.keep_group_expense_in_group()', 'private.lock_settlement_columns()']::regprocedure[]) f
      where has_function_privilege('authenticated', f, 'EXECUTE') or has_function_privilege('anon', f, 'EXECUTE') $q$, 0);

select public._as_admin();
select n, passed, test, detail from public._rls_results order by n;

rollback;
