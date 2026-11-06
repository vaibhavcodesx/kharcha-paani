-- Checks for 20261106000000_bill_photo_storage (docs/designs/bill-photo-sync.md): the receipts bucket,
-- writes only into your own folder, reads through an expense you can see, sharing on and off.
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per check.

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
-- ------------------------------------------------------------
-- Setup: alice makes a group and bob joins; mallory is in no group with them.
-- E1 is alice's group expense, P1 her personal one.
-- ------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@photo.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@photo.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000d1', 'mallory@photo.test', '{"full_name":"Mallory"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip', '🏖️');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');
select public._as('00000000-0000-4000-8000-0000000000b1');
select public.join_group((select value from public._ids where key = 'g1'));

select public._as('00000000-0000-4000-8000-0000000000a1');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-000000000001","type":"expense","amount":900,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-11-03T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-000000000001"}',
  '{"id":"30000000-0000-4000-8000-000000000001","paid_by":"00000000-0000-4000-8000-0000000000a1","split_type":"equal","total_amount":900}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000a1","share_amount":450},{"profile_id":"00000000-0000-4000-8000-0000000000b1","share_amount":450}]');
insert into public.transactions (id, owner_id, type, amount, category, merchant, payment_method, date, note)
values ('20000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-0000000000a1', 'expense', 50, 'Food', 'Chai', 'Cash', now(), '');

-- ------------------------------------------------------------
-- The bucket
-- ------------------------------------------------------------
select public._as_admin();
select public._expect_value('the receipts bucket is private, 1 MB a file, images only',
  $q$ select count(*) from storage.buckets where id = 'receipts' and not public and file_size_limit = 1048576
      and allowed_mime_types @> array['image/webp','image/jpeg'] and cardinality(allowed_mime_types) = 2 $q$, 1);

-- ------------------------------------------------------------
-- Writes: only into your own folder
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice can add a photo for her group expense in her folder',
  $q$ insert into storage.objects (bucket_id, name) values ('receipts', '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000001/aaaaaaaa-0000-4000-8000-000000000001.webp') $q$);
select public._expect_ok('alice can add a photo for her personal expense',
  $q$ insert into storage.objects (bucket_id, name) values ('receipts', '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000009/aaaaaaaa-0000-4000-8000-000000000009.webp') $q$);
select public._expect_denied('alice can''t write into bob''s folder',
  $q$ insert into storage.objects (bucket_id, name) values ('receipts', '00000000-0000-4000-8000-0000000000b1/20000000-0000-4000-8000-000000000001/x.webp') $q$);
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_denied('bob can''t write into alice''s folder',
  $q$ insert into storage.objects (bucket_id, name) values ('receipts', '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000001/x.webp') $q$);
select public._expect_rows('bob can''t rename alice''s photo',
  $q$ update storage.objects set name = name || '.x' where bucket_id = 'receipts' and name like '00000000-0000-4000-8000-0000000000a1/%' $q$, 0);

-- ------------------------------------------------------------
-- Reads before sharing: nobody but alice
-- ------------------------------------------------------------
select public._expect_value('before alice shares it, bob sees none of her photos',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 0);
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_value('alice always sees her own folder (her backup)',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 2);

-- ------------------------------------------------------------
-- The path on a transaction: only its owner's folder, under its own id
-- ------------------------------------------------------------
select public._expect_ok('alice shares the group photo by setting the path',
  $q$ update public.transactions set receipt_storage_path = '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000001/aaaaaaaa-0000-4000-8000-000000000001.webp'
      where id = '20000000-0000-4000-8000-000000000001' $q$);
select public._expect_ok('alice backs up her personal photo by setting its path',
  $q$ update public.transactions set receipt_storage_path = '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000009/aaaaaaaa-0000-4000-8000-000000000009.webp'
      where id = '20000000-0000-4000-8000-000000000009' $q$);
select public._expect_state('the group expense can''t point at her personal photo',
  $q$ update public.transactions set receipt_storage_path = '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000009/aaaaaaaa-0000-4000-8000-000000000009.webp'
      where id = '20000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('a path in someone else''s folder is refused',
  $q$ update public.transactions set receipt_storage_path = '00000000-0000-4000-8000-0000000000b1/20000000-0000-4000-8000-000000000001/x.webp'
      where id = '20000000-0000-4000-8000-000000000001' $q$, '23514');
select public._expect_state('a path that escapes its folder is refused',
  $q$ update public.transactions set receipt_storage_path = '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000001/../x.webp'
      where id = '20000000-0000-4000-8000-000000000001' $q$, '23514');

-- ------------------------------------------------------------
-- Reads after sharing
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('bob, in the group, now sees the shared group photo and nothing else',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts'
      and name = '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000001/aaaaaaaa-0000-4000-8000-000000000001.webp' $q$, 1);
select public._expect_value('bob never sees alice''s personal photo',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000d1');
select public._expect_value('mallory, outside the group, sees nothing',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 0);
select public._as_anon();
select public._expect_value('signed out, nothing',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 0);

-- ------------------------------------------------------------
-- Sharing off, and deleted expenses
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice turns sharing off: the path is cleared',
  $q$ update public.transactions set receipt_storage_path = null where id = '20000000-0000-4000-8000-000000000001' $q$);
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('with sharing off bob sees nothing, even knowing the name',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 0);
select public._as('00000000-0000-4000-8000-0000000000a1');
select public._expect_ok('alice shares it again',
  $q$ update public.transactions set receipt_storage_path = '00000000-0000-4000-8000-0000000000a1/20000000-0000-4000-8000-000000000001/aaaaaaaa-0000-4000-8000-000000000001.webp'
      where id = '20000000-0000-4000-8000-000000000001' $q$);
select public._as_admin();
update public.transactions set deleted_at = now() where id = '20000000-0000-4000-8000-000000000001';
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._expect_value('a deleted expense shares its photo with nobody',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 0);

-- ------------------------------------------------------------
-- The helper
-- ------------------------------------------------------------
select public._as_admin();
select public._expect_value('the read helper runs for signed-in users only',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'private' and p.proname = 'can_read_receipt'
        and has_function_privilege('authenticated', p.oid, 'EXECUTE') and not has_function_privilege('anon', p.oid, 'EXECUTE') $q$, 1);
select public._expect_value('four receipt policies on storage.objects',
  $q$ select count(*) from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname like 'receipts_%' $q$, 4);

select public._as_admin();
select n, passed, test, detail from public._rls_results order by n;

rollback;
