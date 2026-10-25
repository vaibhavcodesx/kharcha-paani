-- NOT APPLIED. Staged for owner review (plan T2.3; security audit M-4, L-8, L-9). Tested against live
-- inside a transaction that rolled back: supabase/tests/database_hygiene_test.sql.
--
-- 1. `anon` gets nothing (M-4). Supabase grants every new table, sequence and function in `public` to
--    `anon` by default, and `anon` held INSERT/SELECT/UPDATE on almost every table here. Only RLS
--    stood between a signed-out caller and the data: one table created without a policy would have
--    been open to anyone with the publishable key. Nothing in the app is used signed out (no policy
--    names `anon` or `public`, and anon executes no function), so it all goes, now and by default for
--    what postgres creates later. Functions also stop being executable by PUBLIC by default: every
--    migration here already grants execute to `authenticated` explicitly.
--    Objects created from the dashboard are owned by supabase_admin, whose defaults a migration can't
--    change; turn those off under Database > Roles if you create tables there.
--
-- 2. A colour clash with someone who has no display name went unnoticed (L-9): the trigger looked up
--    the clashing person's name and tested that for null. It now tests whether there is a clash.
--
-- 3. avatar_url must be an https link of a sane length (L-8). It comes from Google sign-in and is
--    shown to groupmates; every existing one already fits.
--
-- Additive in effect; the revokes take away only what nothing uses. Rollback: grant the privileges
-- back to anon (see the audit), restore private.check_profile_hue from 20260927000000, drop the
-- constraint profiles_avatar_url_https.

begin;

-- 1. anon
revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;
revoke execute on all functions in schema public from anon;

alter default privileges for role postgres in schema public revoke all on tables from anon;
alter default privileges for role postgres in schema public revoke all on sequences from anon;
alter default privileges for role postgres in schema public revoke execute on functions from anon, public;

-- 2. Colour clash with a nameless groupmate
create or replace function private.check_profile_hue()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_group uuid;
  v_clash_id uuid;
  v_clash_name text;
begin
  if new.hue is null or new.hue is not distinct from old.hue then
    return new;
  end if;
  if (select auth.uid()) is null then
    return new;
  end if;

  if new.hue between 28 and 58 then
    raise exception 'that colour is the app''s own accent' using errcode = '23514';
  end if;

  for v_group in
    select gm.group_id from public.group_members gm where gm.profile_id = new.id order by gm.group_id
  loop
    perform pg_advisory_xact_lock(hashtextextended('profile_hue:' || v_group::text, 0));
  end loop;

  -- The clash is found by id: a groupmate with no display name clashes just the same.
  select p.id, p.display_name into v_clash_id, v_clash_name
  from public.profiles p
  where p.id <> new.id
    and p.hue is not null
    and private.hue_distance(p.hue, new.hue) < 25
    and exists (
      select 1
      from public.group_members mine
      join public.group_members theirs on theirs.group_id = mine.group_id
      where mine.profile_id = new.id and theirs.profile_id = p.id
    )
  limit 1;

  if v_clash_id is not null then
    raise exception 'too close to the colour of someone in your groups' using errcode = '23514',
      hint = case
        when nullif(trim(v_clash_name), '') is null then 'Pick a colour further from theirs.'
        else 'Pick a colour further from ' || v_clash_name || '''s.'
      end;
  end if;
  return new;
end;
$$;

-- 3. avatar_url
alter table public.profiles
  add constraint profiles_avatar_url_https check (avatar_url is null or (avatar_url like 'https://%' and char_length(avatar_url) <= 1000));

commit;
