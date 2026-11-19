-- Checks for the migration 20261119000200_profiles_column_grants (issue #297, S-04): people can write only display_name, avatar_emoji, upi_id and hue on their own profile.
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

-- Setup: two people. The sign-up trigger creates their profile rows.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a4', 'asha@profiles.test', '{"full_name":"Asha"}'),
  ('00000000-0000-4000-8000-0000000000b4', 'bilal@profiles.test', '{"full_name":"Bilal"}');

select public._as_admin();
select public._expect_value('both profile rows exist',
  $q$ select count(*) from public.profiles where id in ('00000000-0000-4000-8000-0000000000a4', '00000000-0000-4000-8000-0000000000b4') $q$, 2);

-- The four columns the app writes still work, on your own row.
select public._as('00000000-0000-4000-8000-0000000000a4');
select public._expect_rows('your own name can be changed',
  $q$ update public.profiles set display_name = 'Asha K' where id = '00000000-0000-4000-8000-0000000000a4' $q$, 1);
select public._expect_rows('your own emoji can be changed',
  $q$ update public.profiles set avatar_emoji = 'x' where id = '00000000-0000-4000-8000-0000000000a4' $q$, 1);
select public._expect_rows('your own UPI id can be set',
  $q$ update public.profiles set upi_id = 'asha@okbank' where id = '00000000-0000-4000-8000-0000000000a4' $q$, 1);
select public._expect_rows('your own UPI id can be cleared',
  $q$ update public.profiles set upi_id = null where id = '00000000-0000-4000-8000-0000000000a4' $q$, 1);
select public._expect_rows('your own colour can be set',
  $q$ update public.profiles set hue = 150 where id = '00000000-0000-4000-8000-0000000000a4' $q$, 1);

-- Everything else is refused with a privilege error.
select public._expect_denied('deleted_at can''t be written',
  $q$ update public.profiles set deleted_at = now() where id = '00000000-0000-4000-8000-0000000000a4' $q$);
select public._expect_denied('created_at can''t be written',
  $q$ update public.profiles set created_at = now() where id = '00000000-0000-4000-8000-0000000000a4' $q$);
select public._expect_denied('id can''t be written',
  $q$ update public.profiles set id = '00000000-0000-4000-8000-0000000000c4' where id = '00000000-0000-4000-8000-0000000000a4' $q$);
select public._expect_denied('avatar_url can''t be written (the app never writes it)',
  $q$ update public.profiles set avatar_url = 'https://example.test/x.png' where id = '00000000-0000-4000-8000-0000000000a4' $q$);

-- Someone else's row is still out of reach.
select public._expect_rows('another person''s name can''t be changed (0 rows)',
  $q$ update public.profiles set display_name = 'Hacked' where id = '00000000-0000-4000-8000-0000000000b4' $q$, 0);

-- Signed out can't write at all.
select public._as_anon();
select public._expect_denied('signed out can''t update profiles',
  $q$ update public.profiles set display_name = 'x' where id = '00000000-0000-4000-8000-0000000000a4' $q$);

-- Reading profiles is unchanged, and account deletion still works for a normal account.
select public._as('00000000-0000-4000-8000-0000000000a4');
select public._expect_value('a signed-in person can still read their own profile (others are visible only to groupmates)',
  $q$ select count(*) from public.profiles where id in ('00000000-0000-4000-8000-0000000000a4', '00000000-0000-4000-8000-0000000000b4') $q$, 1);
select public._expect_ok('delete_my_account still works for a normal account',
  $q$ select public.delete_my_account() $q$);
select public._as_admin();
select public._expect_value('and it did tombstone the profile (deleted_at set by the function, not the client)',
  $q$ select count(*) from public.profiles where id = '00000000-0000-4000-8000-0000000000a4' and deleted_at is not null $q$, 1);

select n, test, passed, detail from public._rls_results order by n;

rollback;
