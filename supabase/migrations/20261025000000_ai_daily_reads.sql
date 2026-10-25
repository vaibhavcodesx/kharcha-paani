-- NOT APPLIED. Staged for owner review with the gemini-proxy Edge Function (plan T1.3, decision D2).
-- Tested against live inside a transaction that rolled back: supabase/tests/ai_daily_reads_test.sql.
--
-- What it is for. Gemini moves behind an Edge Function so the key leaves the app. Before forwarding
-- anything, the function asks this database whether the caller has an AI read left today, by kind.
-- The caps live in a table, so they can change without an app update or a redeploy:
--
--   quick_add 50 (typed or spoken lines) · bill 10 (photos, screenshots) · statement 3 · insights 20
--   (Insights questions and the trip read)
--
-- A day is a calendar day in India (Asia/Kolkata): counts reset at midnight IST.
--
-- Who can do what:
--   * Nobody reads or writes the tables directly: they're in `private`, which isn't exposed.
--   * `public.take_ai_read(feature)` is the one way in. It runs as its owner (security definer) so it
--     can write the count, but only ever for auth.uid(), the caller. Calling it by hand can only use
--     up your own reads.
--
-- Additive only. Rollback: drop function public.take_ai_read(text); drop table private.ai_reads,
-- private.ai_read_caps.

begin;

create table private.ai_read_caps (
  feature text primary key check (feature in ('quick_add', 'bill', 'statement', 'insights')),
  per_day integer not null check (per_day >= 0)
);

insert into private.ai_read_caps (feature, per_day) values
  ('quick_add', 50),
  ('bill', 10),
  ('statement', 3),
  ('insights', 20);

create table private.ai_reads (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null,
  feature text not null references private.ai_read_caps (feature),
  used integer not null default 0 check (used >= 0),
  primary key (user_id, day, feature)
);

revoke all on private.ai_read_caps, private.ai_reads from public, anon, authenticated;

-- Spends one of today's reads of [p_feature] for the caller. True if it was spent (go ahead), false
-- if today's are used up. Atomic: two calls at once can't both take the last one.
create or replace function public.take_ai_read(p_feature text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_day date := (now() at time zone 'Asia/Kolkata')::date;
  v_cap integer;
  v_used integer;
begin
  if v_user is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  select per_day into v_cap from private.ai_read_caps where feature = p_feature;
  if v_cap is null then
    raise exception 'unknown feature %', p_feature using errcode = '22023';
  end if;

  insert into private.ai_reads (user_id, day, feature, used)
  values (v_user, v_day, p_feature, 1)
  on conflict (user_id, day, feature) do update
    set used = private.ai_reads.used + 1
    where private.ai_reads.used < v_cap
  returning used into v_used;

  -- No row back: the update's condition failed, so the cap was already reached. A cap of 0 turns a
  -- feature off; the first insert then has to be undone.
  if v_used is null then
    return false;
  end if;
  if v_used > v_cap then
    delete from private.ai_reads where user_id = v_user and day = v_day and feature = p_feature;
    return false;
  end if;
  return true;
end;
$$;

revoke execute on function public.take_ai_read(text) from public, anon;
grant execute on function public.take_ai_read(text) to authenticated;

commit;
