-- STAGED, NOT APPLIED (owner review: replaces a live function). Issues #354 and #296 (steps 4 and the plan).
--
-- What it is for. Two changes to the daily AI read limits, both changeable without an app release:
--
--   1. A plan per person (free, friends, later paid). The owner and friends are set to `friends` once and
--      keep better limits. This replaces the idea of a second app. A person with no row is `free`.
--   2. A project-wide daily ceiling per kind, shared by everyone. The free AI tiers count requests per
--      project, not per person, so a ceiling stops the day's allowance being used up by a few accounts.
--
-- It changes nothing until a plan or a ceiling is set: every person is `free`, and the free caps are still
-- `private.ai_read_caps`. The seeded ceilings are generous PLACEHOLDERS (about 20 times today's use); set
-- them from the provider's real quota page.
--
-- Who can do what:
--   * Nobody reads or writes these tables through the API: they are in `private`, which is not exposed.
--   * `public.take_ai_read(feature)` stays the one way in. Same signature, same grants, same result type.
--     It now reads the caller's plan, then the caller's cap, then the project ceiling.
--   * The owner changes plans and limits in the Supabase table editor or SQL editor (service role), for example:
--       insert into private.user_plans (user_id, plan) values ('<uuid>', 'friends')
--         on conflict (user_id) do update set plan = excluded.plan, updated_at = now();
--     An admin page will do the same through functions that check an admin list.
--
-- Additive except one thing, which the owner should read: `create or replace function public.take_ai_read`
-- replaces the live function body. Behaviour is identical for everyone until a plan or a ceiling is set.
-- Rollback: re-run the function body from 20261025000000_ai_daily_reads.sql, then
--   drop table private.ai_project_reads, private.ai_project_ceilings, private.ai_plan_caps, private.user_plans;

begin;

create table private.user_plans (
  user_id uuid primary key references auth.users (id) on delete cascade,
  plan text not null check (plan in ('free', 'friends', 'paid')),
  updated_at timestamptz not null default now()
);

-- Caps for a plan other than free. A (plan, kind) with no row here uses the free cap.
create table private.ai_plan_caps (
  plan text not null check (plan in ('friends', 'paid')),
  feature text not null references private.ai_read_caps (feature),
  per_day integer not null check (per_day >= 0),
  primary key (plan, feature)
);

insert into private.ai_plan_caps (plan, feature, per_day) values
  ('friends', 'quick_add', 150),
  ('friends', 'bill', 30),
  ('friends', 'statement', 8),
  ('friends', 'insights', 60);

-- One row per kind: how many reads, across everybody, the project allows in an India day. No row = no ceiling.
create table private.ai_project_ceilings (
  feature text primary key references private.ai_read_caps (feature),
  per_day integer not null check (per_day >= 0)
);

insert into private.ai_project_ceilings (feature, per_day) values
  ('quick_add', 2000),
  ('bill', 400),
  ('statement', 80),
  ('insights', 800);

create table private.ai_project_reads (
  day date not null,
  feature text not null references private.ai_read_caps (feature),
  used integer not null default 0 check (used >= 0),
  primary key (day, feature)
);

revoke all on private.user_plans, private.ai_plan_caps, private.ai_project_ceilings, private.ai_project_reads
  from public, anon, authenticated;

-- Spends one of today's reads of [p_feature] for the caller. True if it was spent (go ahead), false if
-- the person's cap or the project's ceiling for today is used up. Atomic per counter.
create or replace function public.take_ai_read(p_feature text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_day date := (now() at time zone 'Asia/Kolkata')::date;
  v_plan text;
  v_cap integer;
  v_used integer;
  v_ceiling integer;
  v_project_used integer;
begin
  if v_user is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;

  select plan into v_plan from private.user_plans where user_id = v_user;
  v_plan := coalesce(v_plan, 'free');

  -- The person's cap: their plan's row if it has one, otherwise the free cap.
  select per_day into v_cap from private.ai_plan_caps where plan = v_plan and feature = p_feature;
  if v_cap is null then
    select per_day into v_cap from private.ai_read_caps where feature = p_feature;
  end if;
  if v_cap is null then
    raise exception 'unknown feature %', p_feature using errcode = '22023';
  end if;

  insert into private.ai_reads (user_id, day, feature, used)
  values (v_user, v_day, p_feature, 1)
  on conflict (user_id, day, feature) do update
    set used = private.ai_reads.used + 1
    where private.ai_reads.used < v_cap
  returning used into v_used;

  -- No row back: the cap was already reached. A cap of 0 turns a kind off; the first insert is undone.
  if v_used is null then
    return false;
  end if;
  if v_used > v_cap then
    delete from private.ai_reads where user_id = v_user and day = v_day and feature = p_feature;
    return false;
  end if;

  -- The project's ceiling, after the person's own cap so a person over their cap costs the project nothing.
  select per_day into v_ceiling from private.ai_project_ceilings where feature = p_feature;
  if v_ceiling is not null then
    insert into private.ai_project_reads (day, feature, used)
    values (v_day, p_feature, 1)
    on conflict (day, feature) do update
      set used = private.ai_project_reads.used + 1
      where private.ai_project_reads.used < v_ceiling
    returning used into v_project_used;

    if v_project_used is null or v_project_used > v_ceiling then
      if v_project_used is not null then
        delete from private.ai_project_reads where day = v_day and feature = p_feature;
      end if;
      -- The project is out: give this person's read back, so a refusal is never charged to them.
      update private.ai_reads set used = used - 1
        where user_id = v_user and day = v_day and feature = p_feature;
      delete from private.ai_reads
        where user_id = v_user and day = v_day and feature = p_feature and used <= 0;
      return false;
    end if;
  end if;

  return true;
end;
$$;

revoke execute on function public.take_ai_read(text) from public, anon;
grant execute on function public.take_ai_read(text) to authenticated;

commit;
