-- Checks for migration group_sync_columns (step 4, PR 2). Same conventions as rls_groups_test.sql:
-- one transaction ending in ROLLBACK, one result row per check, nothing left behind.

begin;

create table public._results (n serial primary key, test text not null, passed boolean not null, detail text);
create table public._ids (key text primary key, value text);

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

create function public._check(p_test text, p_sql text, p_expected bigint) returns void language plpgsql as $$
declare v bigint;
begin
  execute p_sql into v;
  insert into public._results (test, passed, detail) values (p_test, v is not distinct from p_expected, format('got %s, expected %s', v, p_expected));
exception when others then
  insert into public._results (test, passed, detail) values (p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

create function public._ok(p_test text, p_sql text) returns void language plpgsql as $$
begin
  execute p_sql;
  insert into public._results (test, passed) values (p_test, true);
exception when others then
  insert into public._results (test, passed, detail) values (p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

grant all on public._results, public._ids to authenticated;
grant usage on sequence public._results_n_seq to authenticated;
grant execute on function public._as(uuid), public._as_admin(), public._check(text, text, bigint), public._ok(text, text) to authenticated;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a1', 'alice@sync.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b1', 'bob@sync.test', '{"full_name":"Bob"}');

select public._as('00000000-0000-4000-8000-0000000000a1');
select public._ok('alice creates a group', $q$ select public.create_group('10000000-0000-4000-8000-000000000001', 'Goa trip') $q$);
select public._check('a new group gets the default description and colour',
  $q$ select count(*) from public.groups where id = '10000000-0000-4000-8000-000000000001' and description = '' and color_hex = '#FF7A3D' $q$, 1);
select public._ok('the creator sets description and colour',
  $q$ update public.groups set description = 'Monsoon villa', color_hex = '#C6F24C' where id = '10000000-0000-4000-8000-000000000001' $q$);
insert into public._ids select 'invite', code from public.create_group_invite('10000000-0000-4000-8000-000000000001');

-- The whole script is one transaction, so now() can't show a bump; a rewritten row gets a new ctid.
select public._as_admin();
insert into public._ids select 'ctid_before_join', ctid::text from public.groups where id = '10000000-0000-4000-8000-000000000001';

select public._as('00000000-0000-4000-8000-0000000000b1');
select public._ok('bob joins', format($q$ select public.join_group(%L) $q$, (select value from public._ids where key = 'invite')));
select public._check('joining bumped the group''s clock',
  $q$ select count(*) from public.groups where id = '10000000-0000-4000-8000-000000000001' and ctid::text <> (select value from public._ids where key = 'ctid_before_join') $q$, 1);

select public._as_admin();
insert into public._ids select 'ctid_before_leave', ctid::text from public.groups where id = '10000000-0000-4000-8000-000000000001';
select public._as('00000000-0000-4000-8000-0000000000b1');
select public._ok('bob leaves',
  $q$ delete from public.group_members where group_id = '10000000-0000-4000-8000-000000000001' and profile_id = '00000000-0000-4000-8000-0000000000b1' $q$);
select public._as_admin();
select public._check('leaving bumped the group''s clock',
  $q$ select count(*) from public.groups where id = '10000000-0000-4000-8000-000000000001' and ctid::text <> (select value from public._ids where key = 'ctid_before_leave') $q$, 1);

select public._check('Realtime publishes transactions, settlements and groups',
  $q$ select count(*) from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename in ('transactions', 'settlements', 'groups') $q$, 3);
select public._check('Realtime does not publish group_members, splits or split_shares',
  $q$ select count(*) from pg_publication_tables where pubname = 'supabase_realtime' and tablename in ('group_members', 'splits', 'split_shares') $q$, 0);
select public._check('the membership trigger function is executable by no client role',
  $q$ select (has_function_privilege('authenticated', 'private.touch_group_on_membership_change()', 'EXECUTE') or has_function_privilege('anon', 'private.touch_group_on_membership_change()', 'EXECUTE'))::int $q$, 0);

select public._as_admin();
select n, passed, test, detail from public._results order by n;

rollback;
