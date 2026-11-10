-- Checks for the migration 20261110000000_bill_item_splits (docs/designs/split-by-items.md): a bill
-- split by items, started by a signed-in host and picked from by anyone holding its link.
--
-- Same conventions as rls_groups_test.sql: one transaction ending in ROLLBACK, one result row per
-- check. Run it with the migration applied inside the same rolled-back transaction.

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

-- Setup: Asha hosts, Bilal is another signed-in person.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a7', 'asha@bill.test', '{"full_name":"Asha"}'),
  ('00000000-0000-4000-8000-0000000000b7', 'bilal@bill.test', '{"full_name":"Bilal"}');

-- Asha's bill: three items, herself and Rohan (added by hand), Rohan already on the naan.
create function public._bill(p_id uuid) returns jsonb language sql as $f$
  select jsonb_build_object(
    'id', p_id, 'title', 'Toit', 'host_name', 'Asha', 'total_paise', 105000,
    'items', jsonb_build_array(
      jsonb_build_object('id', '20000000-0000-4000-8000-000000000001', 'name', 'Paneer tikka', 'quantity', 1, 'amount_paise', 42000),
      jsonb_build_object('id', '20000000-0000-4000-8000-000000000002', 'name', 'Butter naan', 'quantity', 3, 'amount_paise', 18000),
      jsonb_build_object('id', '20000000-0000-4000-8000-000000000003', 'name', 'Lime soda', 'quantity', 1, 'amount_paise', 40000)),
    'people', jsonb_build_array(
      jsonb_build_object('id', '30000000-0000-4000-8000-000000000001', 'name', 'Asha', 'is_host', true),
      jsonb_build_object('id', '30000000-0000-4000-8000-000000000002', 'name', 'Rohan', 'is_host', false)),
    'claims', jsonb_build_array(
      jsonb_build_object('item_id', '20000000-0000-4000-8000-000000000002', 'person_id', '30000000-0000-4000-8000-000000000002', 'weight', 2)));
$f$;
grant execute on function public._bill(uuid) to authenticated, anon;

-- ------------------------------------------------------------
-- Starting a split
-- ------------------------------------------------------------
select public._as_anon();
select public._expect_denied('signed out, nobody can start a split',
  $q$ select public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000a7')) $q$);

select public._as('00000000-0000-4000-8000-0000000000a7');
insert into public._ids
  select 't', public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000a7')) ->> 'token';
select public._expect_value('the link is 32 hex characters',
  $q$ select ((select value from public._ids where key = 't') ~ '^[0-9a-f]{32}$')::int $q$, 1);
select public._expect_value('starting it again (a retry) gives the same link',
  $q$ select (public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000a7')) ->> 'token'
              = (select value from public._ids where key = 't'))::int $q$, 1);
select public._expect_state('a split with no items is refused',
  $q$ select public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000c7') || '{"items":[]}'::jsonb) $q$, '22023');
select public._expect_state('a split with no host at the table is refused',
  $q$ select public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000c7')
       || '{"people":[{"id":"30000000-0000-4000-8000-000000000009","name":"Rohan","is_host":false}],"claims":[]}'::jsonb) $q$, '22023');
select public._expect_error('a pick for an item that isn''t on this bill is refused',
  $q$ select public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000c7')
       || '{"claims":[{"item_id":"20000000-0000-4000-8000-0000000000ff","person_id":"30000000-0000-4000-8000-000000000002","weight":1}]}'::jsonb) $q$);

select public._as('00000000-0000-4000-8000-0000000000b7');
select public._expect_denied('someone else can''t take over a split by its id',
  $q$ select public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000a7')) $q$);

-- ------------------------------------------------------------
-- The link, signed out
-- ------------------------------------------------------------
select public._as_anon();
select public._expect_value('the link shows the bill''s three items',
  $q$ select jsonb_array_length(public.bill_split_view((select value from public._ids where key = 't')) -> 'items') $q$, 3);
select public._expect_value('and who is at the table',
  $q$ select jsonb_array_length(public.bill_split_view((select value from public._ids where key = 't')) -> 'people') $q$, 2);
select public._expect_value('and nothing about the host but a first name',
  $q$ select (public.bill_split_view((select value from public._ids where key = 't')) ?| array['host_id', 'token', 'email'])::int $q$, 0);
select public._expect_value('a wrong link shows nothing',
  $q$ select (public.bill_split_view('00000000000000000000000000000000') is null)::int $q$, 1);
select public._expect_denied('the tables can''t be read directly',
  $q$ select count(*) from private.bill_splits $q$);
select public._expect_denied('or written',
  $q$ update private.bill_split_items set amount_paise = 1 $q$);

-- Joining
insert into public._ids
  select 'priya', public.bill_split_join((select value from public._ids where key = 't'), '  Priya  ') ->> 'person_id';
select public._expect_value('Priya joins with her name tidied',
  $q$ select count(*) from jsonb_array_elements(public.bill_split_view((select value from public._ids where key = 't')) -> 'people') p
      where p ->> 'name' = 'Priya' $q$, 1);
select public._expect_value('typing the same name again is the same person',
  $q$ select (public.bill_split_join((select value from public._ids where key = 't'), 'priya') ->> 'person_id'
              = (select value from public._ids where key = 'priya'))::int $q$, 1);
select public._expect_value('so the table still has three people',
  $q$ select jsonb_array_length(public.bill_split_view((select value from public._ids where key = 't')) -> 'people') $q$, 3);
select public._expect_state('the host''s name can''t be taken',
  $q$ select public.bill_split_join((select value from public._ids where key = 't'), 'asha') $q$, 'P0001');
select public._expect_state('a blank name is refused',
  $q$ select public.bill_split_join((select value from public._ids where key = 't'), '   ') $q$, 'P0001');
select public._expect_state('a wrong link can''t be joined',
  $q$ select public.bill_split_join('00000000000000000000000000000000', 'Mallory') $q$, 'P0001');

-- Picking
select public._expect_value('Priya picks the paneer',
  $q$ select jsonb_array_length(public.bill_split_claim((select value from public._ids where key = 't'),
        (select value::uuid from public._ids where key = 'priya'), '20000000-0000-4000-8000-000000000001', 1) -> 'claims') $q$, 2);
select public._expect_value('picking it again with two parts changes the pick, not adds one',
  $q$ select (select (c ->> 'weight')::int from jsonb_array_elements(public.bill_split_claim((select value from public._ids where key = 't'),
        (select value::uuid from public._ids where key = 'priya'), '20000000-0000-4000-8000-000000000001', 2) -> 'claims') c
        where c ->> 'person_id' = (select value from public._ids where key = 'priya')) $q$, 2);
select public._expect_value('anyone at the table can tick for someone else (Rohan''s soda)',
  $q$ select jsonb_array_length(public.bill_split_claim((select value from public._ids where key = 't'),
        '30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000003', 1) -> 'claims') $q$, 3);
select public._expect_value('weight 0 takes a pick back',
  $q$ select jsonb_array_length(public.bill_split_claim((select value from public._ids where key = 't'),
        '30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000003', 0) -> 'claims') $q$, 2);
select public._expect_state('a weight past 20 is refused',
  $q$ select public.bill_split_claim((select value from public._ids where key = 't'),
        (select value::uuid from public._ids where key = 'priya'), '20000000-0000-4000-8000-000000000001', 21) $q$, '22023');
select public._expect_state('an item that isn''t on this bill is refused',
  $q$ select public.bill_split_claim((select value from public._ids where key = 't'),
        (select value::uuid from public._ids where key = 'priya'), '20000000-0000-4000-8000-0000000000ff', 1) $q$, '22023');
select public._expect_denied('the host''s picks can''t be made from the link alone',
  $q$ select public.bill_split_claim((select value from public._ids where key = 't'),
        '30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 1) $q$);
select public._expect_denied('nor the host marked as done',
  $q$ select public.bill_split_done((select value from public._ids where key = 't'), '30000000-0000-4000-8000-000000000001', true) $q$);
select public._expect_value('Priya says she''s done',
  $q$ select count(*) from jsonb_array_elements(public.bill_split_done((select value from public._ids where key = 't'),
        (select value::uuid from public._ids where key = 'priya'), true) -> 'people') p where (p ->> 'done')::boolean $q$, 1);

-- The host-only functions
select public._expect_denied('signed out, the bill can''t be changed',
  $q$ select public.update_bill_split('10000000-0000-4000-8000-0000000000a7', '{"items":[]}'::jsonb) $q$);
select public._expect_denied('or anyone removed',
  $q$ select public.remove_bill_split_person('10000000-0000-4000-8000-0000000000a7', '30000000-0000-4000-8000-000000000002') $q$);
select public._expect_denied('or the split finished',
  $q$ select public.close_bill_split('10000000-0000-4000-8000-0000000000a7') $q$);

-- ------------------------------------------------------------
-- Signed in, but not the host
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b7');
select public._expect_denied('another signed-in person can''t pick for the host',
  $q$ select public.bill_split_claim((select value from public._ids where key = 't'),
        '30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 1) $q$);
select public._expect_denied('or change the bill',
  $q$ select public.update_bill_split('10000000-0000-4000-8000-0000000000a7', public._bill('10000000-0000-4000-8000-0000000000a7')) $q$);
select public._expect_denied('or remove someone',
  $q$ select public.remove_bill_split_person('10000000-0000-4000-8000-0000000000a7', '30000000-0000-4000-8000-000000000002') $q$);
select public.close_bill_split('10000000-0000-4000-8000-0000000000a7');
select public._expect_value('and their "finish" leaves Asha''s split open',
  $q$ select (public.bill_split_view((select value from public._ids where key = 't')) is not null)::int $q$, 1);

-- ------------------------------------------------------------
-- The host
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a7');
select public._expect_value('the host picks for herself',
  $q$ select jsonb_array_length(public.bill_split_claim((select value from public._ids where key = 't'),
        '30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000003', 1) -> 'claims') $q$, 3);
select public._expect_value('the host adds Kabir with an id her phone chose',
  $q$ select (public.bill_split_join((select value from public._ids where key = 't'), 'Kabir', '30000000-0000-4000-8000-000000000004')
              ->> 'person_id' = '30000000-0000-4000-8000-000000000004')::int $q$, 1);
-- She corrects the bill: the soda was never ordered, the paneer was 450.
select public._expect_value('correcting the bill drops the soda',
  $q$ select jsonb_array_length(public.update_bill_split('10000000-0000-4000-8000-0000000000a7', jsonb_build_object(
        'title', 'Toit', 'total_paise', 66150,
        'items', jsonb_build_array(
          jsonb_build_object('id', '20000000-0000-4000-8000-000000000001', 'name', 'Paneer tikka', 'quantity', 1, 'amount_paise', 45000),
          jsonb_build_object('id', '20000000-0000-4000-8000-000000000002', 'name', 'Butter naan', 'quantity', 3, 'amount_paise', 18000)))) -> 'items') $q$, 2);
select public._expect_value('and the pick on it goes too, while the others stay',
  $q$ select jsonb_array_length(public.bill_split_view((select value from public._ids where key = 't')) -> 'claims') $q$, 2);
select public._expect_value('the new price and total are what the table sees',
  $q$ select ((v -> 'items' -> 0 ->> 'amount_paise')::int + (v ->> 'total_paise')::int)
      from public.bill_split_view((select value from public._ids where key = 't')) v $q$, 111150);
select public._expect_value('removing Priya takes her picks with her',
  $q$ select jsonb_array_length(public.remove_bill_split_person('10000000-0000-4000-8000-0000000000a7',
        (select value::uuid from public._ids where key = 'priya')) -> 'claims') $q$, 1);
select public._expect_value('the host herself can''t be removed',
  $q$ select count(*) from jsonb_array_elements(public.remove_bill_split_person('10000000-0000-4000-8000-0000000000a7',
        '30000000-0000-4000-8000-000000000001') -> 'people') p where (p ->> 'is_host')::boolean $q$, 1);

-- ------------------------------------------------------------
-- Limits
-- ------------------------------------------------------------
select public._as_anon();
select public._expect_ok('the table fills to 30 people',
  $q$ select public.bill_split_join((select value from public._ids where key = 't'), 'Guest ' || g) from generate_series(1, 27) g $q$);
select public._expect_state('and the 31st is refused',
  $q$ select public.bill_split_join((select value from public._ids where key = 't'), 'One too many') $q$, 'P0001');

select public._as('00000000-0000-4000-8000-0000000000b7');
select public._expect_ok('a host can have 10 splits open',
  $q$ select public.create_bill_split(public._bill(('40000000-0000-4000-8000-0000000000' || lpad(g::text, 2, '0'))::uuid)
        || jsonb_build_object(
          'items', jsonb_build_array(jsonb_build_object('id', gen_random_uuid(), 'name', 'Chai', 'amount_paise', 2000)),
          'people', jsonb_build_array(jsonb_build_object('id', gen_random_uuid(), 'name', 'Bilal', 'is_host', true)),
          'claims', '[]'::jsonb))
      from generate_series(1, 10) g $q$);
select public._expect_state('and the 11th is refused',
  $q$ select public.create_bill_split(public._bill('40000000-0000-4000-8000-000000000011')
        || jsonb_build_object(
          'items', jsonb_build_array(jsonb_build_object('id', gen_random_uuid(), 'name', 'Chai', 'amount_paise', 2000)),
          'people', jsonb_build_array(jsonb_build_object('id', gen_random_uuid(), 'name', 'Bilal', 'is_host', true)),
          'claims', '[]'::jsonb)) $q$, 'P0001');

-- ------------------------------------------------------------
-- Ending: finished, expired, account deleted
-- ------------------------------------------------------------
select public._as_admin();
update private.bill_splits set expires_at = now() - interval '1 minute' where id = '40000000-0000-4000-8000-000000000001';
insert into public._ids select 'old', token from private.bill_splits where id = '40000000-0000-4000-8000-000000000001';
select public._as_anon();
select public._expect_value('after 24 hours the link shows nothing',
  $q$ select (public.bill_split_view((select value from public._ids where key = 'old')) is null)::int $q$, 1);
select public._expect_state('and can''t be joined',
  $q$ select public.bill_split_join((select value from public._ids where key = 'old'), 'Late') $q$, 'P0001');

select public._as('00000000-0000-4000-8000-0000000000a7');
select public.close_bill_split('10000000-0000-4000-8000-0000000000a7');
select public._as_anon();
select public._expect_value('once the host finishes, the link shows nothing',
  $q$ select (public.bill_split_view((select value from public._ids where key = 't')) is null)::int $q$, 1);
select public._as_admin();
select public._expect_value('and everything typed into it is gone',
  $q$ select (select count(*) from private.bill_split_people where split_id = '10000000-0000-4000-8000-0000000000a7')
           + (select count(*) from private.bill_split_items where split_id = '10000000-0000-4000-8000-0000000000a7')
           + (select count(*) from private.bill_split_claims where split_id = '10000000-0000-4000-8000-0000000000a7') $q$, 0);

select public._as('00000000-0000-4000-8000-0000000000a7');
select public._expect_ok('the next split anyone starts clears out expired ones',
  $q$ select public.create_bill_split(public._bill('10000000-0000-4000-8000-0000000000d7')) $q$);
select public._as_admin();
select public._expect_value('so the expired one is deleted',
  $q$ select count(*) from private.bill_splits where id = '40000000-0000-4000-8000-000000000001' $q$, 0);

delete from auth.users where id = '00000000-0000-4000-8000-0000000000b7';
select public._expect_value('deleting an account deletes its splits',
  $q$ select count(*) from private.bill_splits where host_id = '00000000-0000-4000-8000-0000000000b7' $q$, 0);

-- ------------------------------------------------------------
-- What anon can run is exactly the four that take a link
-- ------------------------------------------------------------
select public._expect_value('anon can run four functions in public',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname not like '\_%' and has_function_privilege('anon', p.oid, 'execute') $q$, 4);
select public._expect_value('and they are the link ones',
  $q$ select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and has_function_privilege('anon', p.oid, 'execute')
        and p.proname in ('bill_split_view', 'bill_split_join', 'bill_split_claim', 'bill_split_done') $q$, 4);
select public._expect_value('anon still can''t touch any table in public',
  $q$ select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relkind in ('r', 'p', 'v') and c.relname not like '\_%'
        and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('anon', c.oid, 'insert')
          or has_table_privilege('anon', c.oid, 'update') or has_table_privilege('anon', c.oid, 'delete')) $q$, 0);

select n, test, passed, detail from public._rls_results order by n;

rollback;
