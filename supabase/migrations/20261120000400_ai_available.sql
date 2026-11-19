-- STAGED, applied by hand like the others. The app only learns that AI is paused, or that a daily cap is used up,
-- AFTER the person has spoken, photographed a bill or picked a statement. This read-only function lets the app ask
-- first, when the button is tapped.
--
-- public.ai_available(p_feature) is true when a take_ai_read of that kind would be allowed right now: the person's
-- cap for the day is not used up and the project's ceiling is not used up (a cap or ceiling of 0, which is how the
-- owner's pause works, counts as used up). It spends nothing and writes nothing. Two phones racing can make it
-- wrong by one read; take_ai_read stays the only thing that actually decides.
--
-- Additive: one new function. Rollback: drop function public.ai_available(text);

begin;

create function public.ai_available(p_feature text)
returns boolean
language plpgsql
stable
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

  select per_day into v_cap from private.ai_plan_caps where plan = v_plan and feature = p_feature;
  if v_cap is null then
    select per_day into v_cap from private.ai_read_caps where feature = p_feature;
  end if;
  if v_cap is null then
    raise exception 'unknown feature %', p_feature using errcode = '22023';
  end if;

  select used into v_used from private.ai_reads where user_id = v_user and day = v_day and feature = p_feature;
  if coalesce(v_used, 0) >= v_cap then
    return false;
  end if;

  select per_day into v_ceiling from private.ai_project_ceilings where feature = p_feature;
  if v_ceiling is not null then
    select used into v_project_used from private.ai_project_reads where day = v_day and feature = p_feature;
    if coalesce(v_project_used, 0) >= v_ceiling then
      return false;
    end if;
  end if;

  return true;
end;
$$;

revoke execute on function public.ai_available(text) from public, anon;
grant execute on function public.ai_available(text) to authenticated;

commit;
