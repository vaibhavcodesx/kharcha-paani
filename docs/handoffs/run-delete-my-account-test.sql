-- Kharcha Paani: run the account-deletion migration (#103) and its test on live, then ROLL BACK.
-- Nothing persists: everything below is one transaction that ends in ROLLBACK.
-- Paste the whole file into Supabase > SQL editor and press Run. The last result table lists every
-- check with passed = true/false. Send Claude the rows where passed is false (or "all passed").

begin;


alter table public.profiles drop constraint profiles_id_fkey;

alter table public.profiles add column deleted_at timestamptz;

comment on column public.profiles.deleted_at is
  'Set when the account was deleted (delete_my_account). The row is a tombstone: a display name and nothing else.';

create or replace function public.delete_my_account(p_keep_name boolean default false)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_group record;
  v_heir uuid;
begin
  if v_me is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  if exists (select 1 from public.profiles where id = v_me and deleted_at is not null) then
    raise exception 'account already deleted' using errcode = 'P0002';
  end if;

  -- Private rows. Personal expenses carry their private splits with them (local_splits cascades).
  delete from public.local_settlements where owner_id = v_me;
  delete from public.local_splits where owner_id = v_me;
  delete from public.transactions where owner_id = v_me and group_id is null;
  delete from public.group_invites where created_by = v_me;

  -- Groups they made: to the longest-standing other member, or gone with their records if nobody's left.
  for v_group in select id from public.groups where created_by = v_me loop
    select profile_id into v_heir
    from public.group_members
    where group_id = v_group.id and profile_id <> v_me
    order by joined_at, profile_id
    limit 1;
    if v_heir is not null then
      update public.groups set created_by = v_heir where id = v_group.id;
    end if;
  end loop;

  delete from public.group_members where profile_id = v_me;

  -- Any group of theirs that's now empty, created by them or not.
  for v_group in
    select g.id from public.groups g
    where not exists (select 1 from public.group_members m where m.group_id = g.id)
      and (g.created_by = v_me
        or exists (select 1 from public.transactions t where t.group_id = g.id and t.owner_id = v_me)
        or exists (select 1 from public.settlements s where s.group_id = g.id and v_me in (s.from_profile_id, s.to_profile_id)))
  loop
    delete from public.settlements where group_id = v_group.id;
    delete from public.transactions where group_id = v_group.id;
    delete from public.groups where id = v_group.id;
  end loop;

  update public.profiles
  set display_name = case when p_keep_name then display_name else 'Former member' end,
      avatar_url = null,
      avatar_emoji = null,
      hue = null,
      upi_id = null,
      deleted_at = now()
  where id = v_me;

  delete from auth.users where id = v_me;
end;
$$;

revoke execute on function public.delete_my_account(boolean) from public, anon;
grant execute on function public.delete_my_account(boolean) to authenticated;

-- How "Former member" reaches phones that already knew the person by name. Profiles are readable only
-- between current groupmates (profiles_select_self_or_groupmate), and someone who deleted their
-- account is in no group, so their old groupmates' phones would keep the old name for good. This hands
-- back the tombstone names, and only for profiles that appear on a record in one of the caller's
-- groups. No policy changes: a tombstone stays unreadable through the table itself.
create or replace function public.former_member_names(p_ids uuid[])
returns table (id uuid, display_name text)
language sql
stable
security definer
set search_path = ''
as $$
  select p.id, p.display_name
  from public.profiles p
  where cardinality(p_ids) <= 500
    and p.id = any (p_ids)
    and p.deleted_at is not null
    and exists (
      select 1
      from public.group_members m
      where m.profile_id = auth.uid()
        and m.group_id in (
          select t.group_id from public.transactions t where t.owner_id = p.id
          union
          select t.group_id from public.transactions t join public.splits s on s.transaction_id = t.id where s.paid_by = p.id
          union
          select t.group_id from public.transactions t join public.splits s on s.transaction_id = t.id
            join public.split_shares ss on ss.split_id = s.id where ss.profile_id = p.id
          union
          select st.group_id from public.settlements st where p.id in (st.from_profile_id, st.to_profile_id)
        )
    );
$$;

revoke execute on function public.former_member_names(uuid[]) from public, anon;
grant execute on function public.former_member_names(uuid[]) to authenticated;


-- The test


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

-- Setup. Alice makes Goa trip (Bob joins, then Carol) and Solo (nobody joins). Dan makes Dan's
-- group and Alice joins it, but nothing there involves her.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a3', 'alice@delete.test', '{"full_name":"Alice"}'),
  ('00000000-0000-4000-8000-0000000000b3', 'bob@delete.test', '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c3', 'carol@delete.test', '{"full_name":"Carol"}'),
  ('00000000-0000-4000-8000-0000000000d3', 'dan@delete.test', '{"full_name":"Dan"}');

create table public._ids (key text primary key, value text);
grant all on public._ids to authenticated, anon;

select public._as('00000000-0000-4000-8000-0000000000a3');
update public.profiles set upi_id = 'alice@okaxis', avatar_emoji = '🙂' where id = '00000000-0000-4000-8000-0000000000a3';
select public.create_group('10000000-0000-4000-8000-0000000000a1', 'Goa trip', '🏖️');
select public.create_group('10000000-0000-4000-8000-0000000000a2', 'Solo', '🧘');
insert into public._ids select 'g1', code from public.create_group_invite('10000000-0000-4000-8000-0000000000a1');
select public._as('00000000-0000-4000-8000-0000000000b3');
select public.join_group((select value from public._ids where key = 'g1'));
select public._as('00000000-0000-4000-8000-0000000000c3');
select public.join_group((select value from public._ids where key = 'g1'));
select public._as('00000000-0000-4000-8000-0000000000d3');
select public.create_group('10000000-0000-4000-8000-0000000000a4', 'Dan''s group', '🏠');
insert into public._ids select 'g4', code from public.create_group_invite('10000000-0000-4000-8000-0000000000a4');
select public._as('00000000-0000-4000-8000-0000000000a3');
select public.join_group((select value from public._ids where key = 'g4'));

-- E1: Alice paid 900 in Goa, split three ways. E3: Alice's tea in Solo. P1: her personal chai.
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-0000000000e1","type":"expense","amount":900,"category":"Food","merchant":"Shack","payment_method":"UPI","date":"2026-10-14T20:00:00Z","note":"","group_id":"10000000-0000-4000-8000-0000000000a1"}',
  '{"id":"30000000-0000-4000-8000-0000000000e1","paid_by":"00000000-0000-4000-8000-0000000000a3","split_type":"equal","total_amount":900}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000a3","share_amount":300},{"profile_id":"00000000-0000-4000-8000-0000000000b3","share_amount":300},{"profile_id":"00000000-0000-4000-8000-0000000000c3","share_amount":300}]');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-0000000000e3","type":"expense","amount":100,"category":"Food","merchant":"Tea","payment_method":"Cash","date":"2026-10-14T08:00:00Z","note":"","group_id":"10000000-0000-4000-8000-0000000000a2"}',
  '{"id":"30000000-0000-4000-8000-0000000000e3","paid_by":"00000000-0000-4000-8000-0000000000a3","split_type":"exact","total_amount":100}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000a3","share_amount":100}]');
insert into public.transactions (id, owner_id, type, amount, category, merchant, payment_method, date, note)
values ('20000000-0000-4000-8000-0000000000f1', '00000000-0000-4000-8000-0000000000a3', 'expense', 50, 'Food', 'Chai', 'Cash', now(), '');

-- E2: Bob paid 600 in Goa, split with Alice; Bob says he paid Alice 250.
select public._as('00000000-0000-4000-8000-0000000000b3');
select public.save_group_expense(
  '{"id":"20000000-0000-4000-8000-0000000000e2","type":"expense","amount":600,"category":"Transport","merchant":"Cab","payment_method":"UPI","date":"2026-10-15T09:00:00Z","note":"","group_id":"10000000-0000-4000-8000-0000000000a1"}',
  '{"id":"30000000-0000-4000-8000-0000000000e2","paid_by":"00000000-0000-4000-8000-0000000000b3","split_type":"equal","total_amount":600}',
  '[{"profile_id":"00000000-0000-4000-8000-0000000000a3","share_amount":300},{"profile_id":"00000000-0000-4000-8000-0000000000b3","share_amount":300}]');
insert into public.settlements (id, from_profile_id, to_profile_id, amount, group_id)
values ('50000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-0000000000b3', '00000000-0000-4000-8000-0000000000a3', 250, '10000000-0000-4000-8000-0000000000a1');

-- What Goa's balances are made of, before.
select public._as_admin();
insert into public._ids select 'goa_before', (
  select count(*) || '/' || sum(ss.share_amount) || '/' || (select count(*) || ':' || sum(amount) from public.settlements where group_id = '10000000-0000-4000-8000-0000000000a1')
  from public.transactions t join public.splits s on s.transaction_id = t.id join public.split_shares ss on ss.split_id = s.id
  where t.group_id = '10000000-0000-4000-8000-0000000000a1' and t.deleted_at is null);

-- ------------------------------------------------------------
-- Who can't
-- ------------------------------------------------------------
select public._as_anon();
select public._expect_denied('signed out, nobody can delete an account',
  $q$ select public.delete_my_account() $q$);

-- ------------------------------------------------------------
-- Alice deletes hers, choosing Former member
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000a3');
select public._expect_ok('Alice deletes her account', $q$ select public.delete_my_account(false) $q$);

select public._as_admin();
select public._expect_value('she can''t sign in any more: her auth user is gone',
  $q$ select count(*) from auth.users where id = '00000000-0000-4000-8000-0000000000a3' $q$, 0);
select public._expect_value('her profile is a tombstone: "Former member" and nothing else',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a3' and display_name = 'Former member' and upi_id is null and avatar_emoji is null and avatar_url is null and hue is null and deleted_at is not null $q$, 1);
select public._expect_value('her personal expense is gone',
  $q$ select count(*) from public.transactions where id = '20000000-0000-4000-8000-0000000000f1' $q$, 0);
select public._expect_value('Goa''s expenses, shares and payments are exactly as they were',
  $q$ select ((select count(*) || '/' || sum(ss.share_amount) || '/' || (select count(*) || ':' || sum(amount) from public.settlements where group_id = '10000000-0000-4000-8000-0000000000a1')
       from public.transactions t join public.splits s on s.transaction_id = t.id join public.split_shares ss on ss.split_id = s.id
       where t.group_id = '10000000-0000-4000-8000-0000000000a1' and t.deleted_at is null) = (select value from public._ids where key = 'goa_before'))::int $q$, 1);
select public._expect_value('Goa passes to Bob, who joined before Carol',
  $q$ select count(*) from public.groups where id = '10000000-0000-4000-8000-0000000000a1' and created_by = '00000000-0000-4000-8000-0000000000b3' $q$, 1);
select public._expect_value('Solo, with nobody left, is gone with its expense',
  $q$ select (select count(*) from public.groups where id = '10000000-0000-4000-8000-0000000000a2') + (select count(*) from public.transactions where id = '20000000-0000-4000-8000-0000000000e3') $q$, 0);
select public._expect_value('she''s in no group',
  $q$ select count(*) from public.group_members where profile_id = '00000000-0000-4000-8000-0000000000a3' $q$, 0);
select public._expect_value('Dan''s group, where she did nothing, is still there for Dan',
  $q$ select count(*) from public.group_members where group_id = '10000000-0000-4000-8000-0000000000a4' and profile_id = '00000000-0000-4000-8000-0000000000d3' $q$, 1);

-- ------------------------------------------------------------
-- What her old groupmates see
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000b3');
select public._expect_value('Bob can''t read her profile through the table',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a3' $q$, 0);
select public._expect_value('but Bob learns she is now "Former member"',
  $q$ select count(*) from public.former_member_names(array['00000000-0000-4000-8000-0000000000a3']::uuid[]) where display_name = 'Former member' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000d3');
select public._expect_value('Dan, with no record of hers in his groups, learns nothing',
  $q$ select count(*) from public.former_member_names(array['00000000-0000-4000-8000-0000000000a3']::uuid[]) $q$, 0);
select public._expect_value('a live account isn''t a former member',
  $q$ select count(*) from public.former_member_names(array['00000000-0000-4000-8000-0000000000b3']::uuid[]) $q$, 0);

-- ------------------------------------------------------------
-- Carol deletes hers, keeping her name
-- ------------------------------------------------------------
select public._as('00000000-0000-4000-8000-0000000000c3');
select public._expect_ok('Carol deletes hers and keeps her name', $q$ select public.delete_my_account(true) $q$);
select public._as('00000000-0000-4000-8000-0000000000b3');
select public._expect_value('Bob sees "Carol" on old expenses',
  $q$ select count(*) from public.former_member_names(array['00000000-0000-4000-8000-0000000000c3']::uuid[]) where display_name = 'Carol' $q$, 1);
select public._as('00000000-0000-4000-8000-0000000000c3');
select public._expect_error('a deleted account can''t delete again',
  $q$ select public.delete_my_account(false) $q$);

select n, test, passed, detail from public._rls_results order by n;

rollback;
