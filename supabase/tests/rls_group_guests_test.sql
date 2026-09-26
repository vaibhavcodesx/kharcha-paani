-- Checks for the STAGED migration 20260926000100_group_expense_guests (design doc item 9): a group
-- expense can carry shares for friends without an account ("guests" of the member who added it).
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check, nothing left behind. Run on the local harness with the staged file copied in after the
-- applied migrations; never against live until the migration is approved.
--
-- Users: alice adds expenses and hosts guests; bob paid one of them; carol is a member; mallory is outside.


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
-- Setup: alice creates Goa trip; bob and carol join.
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@guests.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@guests.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c1', 'carol@guests.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@guests.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._as('00000000-0000-4000-8000-0000000000b1');
select public.join_group((select value from public._ids where key = 'g1'));
select public._as('00000000-0000-4000-8000-0000000000c1');
select public.join_group((select value from public._ids where key = 'g1'));

select public._as_admin();
-- A save of an expense in Goa trip with the given shares JSON: E1 (dinner) by default.
create function public._save_sql(p_shares text, p_paid_by text default '00000000-0000-4000-8000-0000000000a1',
  p_amount int default 900, p_n int default 1) returns text language sql as $f$
  select format($q$ select public.save_group_expense(
    '{"id":"20000000-0000-4000-8000-00000000000%4$s","type":"expense","amount":%1$s,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-09-14T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
    '{"id":"30000000-0000-4000-8000-00000000000%4$s","paid_by":"%2$s","split_type":"equal","total_amount":%1$s}',
    '%3$s') $q$, p_amount, p_paid_by, p_shares, p_n);
$f$;
grant execute on function public._save_sql(text, text, int, int) to authenticated;

-- ------------------------------------------------------------
-- The host saves an expense with a guest
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice saves dinner split with bob, carol and her friend Ravi (no account)',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":225},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":225},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":225},{"local_name":"Ravi","share_amount":225}]'));
select public._expect_value('four live shares',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null $q$, 4);
select public._expect_value('Ravi''s share is stored by name with no profile',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null and local_name = 'Ravi' and profile_id is null and share_amount = 225 $q$, 1);

select public._expect_error('a share naming both a member and a guest is refused',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","local_name":"Ravi","share_amount":450},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":450}]'));
select public._expect_error('a share naming neither is refused',
  public._save_sql('[{"share_amount":450},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":450}]'));
select public._expect_error('guest shares still have to add up to the amount',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":450},{"local_name":"Ravi","share_amount":400}]'));

-- ------------------------------------------------------------
-- Groupmates see the guest share; outsiders see nothing
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_value('carol, a member, sees Ravi''s share',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null and local_name = 'Ravi' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('mallory sees no shares at all',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' $q$, 0);

-- ------------------------------------------------------------
-- The host changes a guest: the old row is retired, a new one written
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice changes Ravi''s share to 300 and hers to 150',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":150},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":225},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":225},{"local_name":"Ravi","share_amount":300}]'));
select public._expect_value('one live Ravi share, at 300',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null and local_name = 'Ravi' and share_amount = 300 $q$, 1);
select public._expect_value('the old 225 row is soft-deleted, not rewritten',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is not null and local_name = 'Ravi' and share_amount = 225 $q$, 1);
select public._expect_ok('re-sending the same save (name in another case, with a space) is accepted',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":150},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":225},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":225},{"local_name":"ravi ","share_amount":300}]'));
select public._expect_value('and still leaves one live guest share',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null and local_name is not null $q$, 1);

-- ------------------------------------------------------------
-- Only the host changes guests
-- ------------------------------------------------------------
select public._expect_ok('alice adds a cab that bob paid, with Ravi in it',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":100},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":100},{"local_name":"Ravi","share_amount":100}]',
    '00000000-0000-4000-8000-0000000000b1', 300, 2));

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_ok('bob, the payer, can edit it when he sends Ravi back unchanged',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":150},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":50},{"local_name":"Ravi","share_amount":100}]',
    '00000000-0000-4000-8000-0000000000b1', 300, 2));
select public._expect_denied('but not change Ravi''s share',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":150},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":100},{"local_name":"Ravi","share_amount":50}]',
    '00000000-0000-4000-8000-0000000000b1', 300, 2));
select public._expect_denied('nor drop Ravi',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":150},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":150}]',
    '00000000-0000-4000-8000-0000000000b1', 300, 2));
select public._expect_value('Ravi''s cab share is untouched',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000002' and deleted_at is null and local_name = 'Ravi' and share_amount = 100 $q$, 1);
select public._expect_rows('bob can''t soft-delete the guest share directly either',
  $q$ update public.split_shares set deleted_at = now() where split_id = '30000000-0000-4000-8000-000000000002' and local_name = 'Ravi' $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000c1');
select public._expect_error('carol, who neither added nor paid, can''t save over dinner at all',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":450},{"profile_id":"00000000-0000-4000-8000-0000000000c1","share_amount":450}]'));

-- ------------------------------------------------------------
-- The latent fault: removing a member when a guest is in the payload
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice takes carol out of dinner, Ravi still in',
  public._save_sql('[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":300},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":300},{"local_name":"Ravi","share_amount":300}]'));
select public._expect_value('carol''s share is soft-deleted (the old body left it live)',
  $q$ select count(*) from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null and profile_id = '00000000-0000-4000-8000-0000000000c1' $q$, 0);
select public._expect_value('the three live shares add up to 900',
  $q$ select sum(share_amount)::int from public.split_shares where split_id = '30000000-0000-4000-8000-000000000001' and deleted_at is null $q$, 900);

select n, test, passed, detail from public._rls_results order by n;

rollback;
