-- Checks for the migration 20261120000300_admin_leftover_photos (issues #355, #441): the two narrow storage policies, the
-- leftover list, the cleanup log and the overview totals.
--
-- Same conventions as admin_core_test.sql: one transaction ending in ROLLBACK, one result row per check.
-- Staged, not applied. Run: `node supabase/tests/local/run.mjs supabase/tests/admin_leftover_photos_test.sql`
-- (the runner replays every migration first).

begin;

create table public._rls_results (
  n serial primary key,
  test text not null,
  passed boolean not null,
  detail text
);

create function public._as_jwt(p_user uuid, p_email text, p_session uuid default null, p_anonymous boolean default false)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object(
    'sub', p_user, 'role', 'authenticated', 'email', p_email,
    'session_id', p_session, 'is_anonymous', p_anonymous)::text, true);
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

create function public._expect_state(p_test text, p_sql text, p_state text) returns void language plpgsql as $$
begin
  execute p_sql;
  perform public._record(p_test, false, 'succeeded, but should have failed with ' || p_state);
exception when others then
  perform public._record(p_test, sqlstate = p_state, sqlstate || ': ' || sqlerrm);
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

create function public._expect_text(p_test text, p_sql text, p_expected text) returns void language plpgsql as $$
declare v text;
begin
  execute p_sql into v;
  perform public._record(p_test, v is not distinct from p_expected, format('got %s, expected %s', v, p_expected));
exception when others then
  perform public._record(p_test, false, sqlstate || ': ' || sqlerrm);
end $$;

grant all on public._rls_results to authenticated, anon;
grant usage on sequence public._rls_results_n_seq to authenticated, anon;
grant execute on function public._as_jwt(uuid, text, uuid, boolean), public._as_anon(), public._as_admin(),
  public._record(text, boolean, text), public._expect_state(text, text, text), public._expect_ok(text, text),
  public._expect_value(text, text, bigint), public._expect_text(text, text, text) to authenticated, anon;

create function public._t_delete_rows(p_like text) returns bigint language plpgsql as $$
declare v bigint;
begin
  delete from storage.objects where bucket_id = 'receipts' and name like p_like;
  get diagnostics v = row_count;
  return v;
end $$;
grant execute on function public._t_delete_rows(text) to authenticated;

-- Setup.
--   Owen : the admin (fresh session).   Bob: an ordinary live person.
--   Dead : a deleted account (a tombstone profile, no sign-in) whose folder holds three files:
--            a.webp  nothing points at it            -> leftover
--            b.webp  a shared group expense still points at it -> not leftover
--            c.webp  the same: referenced          -> not leftover (a second one, to be sure)
--   Live : a live account with an unreferenced file in its folder -> never leftover while the account is live.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000a4', 'owen@photos.test', '{"full_name":"Owen"}'),
  ('00000000-0000-4000-8000-0000000000b4', 'bob@photos.test',  '{"full_name":"Bob"}'),
  ('00000000-0000-4000-8000-0000000000c4', 'live@photos.test', '{"full_name":"Live"}');
insert into auth.identities (user_id, provider, identity_data) values
  ('00000000-0000-4000-8000-0000000000a4', 'google', '{"email":"owen@photos.test"}');
insert into private.admins (user_id, email) values ('00000000-0000-4000-8000-0000000000a4', 'owen@photos.test');
insert into public.profiles (id, display_name, deleted_at) values ('00000000-0000-4000-8000-0000000000d4', 'Former member', now());
insert into public.groups (id, name, created_by) values ('00000000-0000-4000-8000-0000000000aa', 'Goa', '00000000-0000-4000-8000-0000000000b4');
insert into public.transactions (id, owner_id, type, amount, category, group_id, receipt_storage_path) values
  ('00000000-0000-4000-8000-0000000000e4', '00000000-0000-4000-8000-0000000000d4', 'expense', 20, 'Food', '00000000-0000-4000-8000-0000000000aa',
   '00000000-0000-4000-8000-0000000000d4/00000000-0000-4000-8000-0000000000e4/b.webp'),
  ('00000000-0000-4000-8000-0000000000f4', '00000000-0000-4000-8000-0000000000d4', 'expense', 30, 'Food', '00000000-0000-4000-8000-0000000000aa',
   '00000000-0000-4000-8000-0000000000d4/00000000-0000-4000-8000-0000000000f4/c.webp');
insert into storage.buckets (id, name, public) values ('receipts', 'receipts', false) on conflict (id) do nothing;
insert into storage.objects (bucket_id, name, metadata) values
  ('receipts', '00000000-0000-4000-8000-0000000000d4/00000000-0000-4000-8000-0000000000a1/a.webp', '{"size": 100}'),
  ('receipts', '00000000-0000-4000-8000-0000000000d4/00000000-0000-4000-8000-0000000000e4/b.webp', '{"size": 100}'),
  ('receipts', '00000000-0000-4000-8000-0000000000d4/00000000-0000-4000-8000-0000000000f4/c.webp', '{"size": 100}'),
  ('receipts', '00000000-0000-4000-8000-0000000000c4/00000000-0000-4000-8000-0000000000a2/live.webp', '{"size": 100}'),
  ('receipts', 'not-a-uuid/x/y.webp', '{"size": 100}');

-- 1. Signed out and ordinary people.
select public._as_anon();
select public._expect_state('anon: admin_leftover_photos is refused', $q$ select * from public.admin_leftover_photos() $q$, '42501');
select public._expect_state('anon: admin_record_photo_cleanup is refused', $q$ select public.admin_record_photo_cleanup(null, 1) $q$, '42501');
select public._as_jwt('00000000-0000-4000-8000-0000000000b4', 'bob@photos.test');
select public._expect_state('non-admin: admin_leftover_photos raises 42501', $q$ select * from public.admin_leftover_photos() $q$, '42501');
select public._expect_state('non-admin: admin_record_photo_cleanup raises 42501', $q$ select public.admin_record_photo_cleanup('00000000-0000-4000-8000-0000000000d4', 1) $q$, '42501');
select public._expect_state('non-admin: the private helpers are out of reach', $q$ select private.is_leftover_receipt('x/y/z.webp') $q$, '42501');
select public._expect_value('non-admin: cannot see the leftover file through the table',
  $q$ select count(*) from storage.objects where name like '%/a.webp' $q$, 0);
select public._as_admin();

-- 10. The admin sees and deletes only leftover files.
select public._as_jwt('00000000-0000-4000-8000-0000000000a4', 'owen@photos.test');
select public._expect_value('admin: sees exactly the one leftover file in the table',
  $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 1);
select public._expect_value('admin: the list shows that one file, 100 bytes', $q$ select count(*) * 1000 + sum(bytes) from public.admin_leftover_photos() $q$, 1100);
select public._expect_value('admin: the list can be narrowed to an account',
  $q$ select count(*) from public.admin_leftover_photos('00000000-0000-4000-8000-0000000000d4') $q$, 1);
select public._expect_value('admin: a live account has no leftover files',
  $q$ select count(*) from public.admin_leftover_photos('00000000-0000-4000-8000-0000000000c4') $q$, 0);
select public._expect_value('admin: the overview counts one leftover file of 100 bytes',
  $q$ select (public.admin_overview() -> 'storage' ->> 'leftover_objects')::int * 1000 + (public.admin_overview() -> 'storage' ->> 'leftover_bytes')::int $q$, 1100);
select public._expect_value('admin: the overview still counts all five files',
  $q$ select (public.admin_overview() -> 'storage' ->> 'objects')::int $q$, 5);

-- Deleting: the live person's file, the referenced files, a file with a bad folder name: none are touched.
select public._as_jwt('00000000-0000-4000-8000-0000000000a4', 'owen@photos.test');

select public._expect_value('admin delete: a live account''s file is out of reach', $q$ select public._t_delete_rows('%/live.webp') $q$, 0);
select public._expect_value('admin delete: a file an expense still points at is out of reach (b)', $q$ select public._t_delete_rows('%/b.webp') $q$, 0);
select public._expect_value('admin delete: a file an expense still points at is out of reach (c)', $q$ select public._t_delete_rows('%/c.webp') $q$, 0);
select public._expect_value('admin delete: a file with a folder that is not an account id is out of reach', $q$ select public._t_delete_rows('not-a-uuid/%') $q$, 0);
select public._expect_value('admin delete: the one leftover file is deleted', $q$ select public._t_delete_rows('%/a.webp') $q$, 1);
select public._as_admin();
select public._expect_value('the four other files are all still there', $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 4);

-- The cleanup call recounts and logs.
select public._as_jwt('00000000-0000-4000-8000-0000000000a4', 'owen@photos.test');
select public._expect_value('cleanup: nothing is left for that account', $q$ select public.admin_record_photo_cleanup('00000000-0000-4000-8000-0000000000d4', 1) $q$, 0);
select public._expect_state('cleanup: a negative count is refused', $q$ select public.admin_record_photo_cleanup('00000000-0000-4000-8000-0000000000d4', -1) $q$, '22023');
select public._as_admin();
select public._expect_text('cleanup: one log row with the counts',
  $q$ select (after ->> 'removed') || '/' || (after ->> 'still_left') from private.admin_audit where action = 'photo_cleanup' $q$, '1/0');

-- Once a referenced expense goes (the group's expense removed), its photo becomes leftover.
delete from public.transactions where id = '00000000-0000-4000-8000-0000000000e4';
select public._as_jwt('00000000-0000-4000-8000-0000000000a4', 'owen@photos.test');
select public._expect_value('after the expense row is gone, b.webp is offered', $q$ select count(*) from public.admin_leftover_photos() $q$, 1);

-- A person who is a non-admin signed in with the same uid but another email gets nothing.
select public._as_jwt('00000000-0000-4000-8000-0000000000a4', 'someone@photos.test');
select public._expect_value('wrong token email: the table shows no leftover file', $q$ select count(*) from storage.objects where bucket_id = 'receipts' $q$, 0);

select public._as_admin();
select n, test, passed, detail from public._rls_results order by n;

rollback;
