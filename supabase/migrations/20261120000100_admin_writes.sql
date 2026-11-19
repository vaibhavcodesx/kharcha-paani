-- STAGED, NOT APPLIED (owner review: new security-definer functions that change plans, limits and the minimum version).
-- Issues #355, #439. Design: docs/designs/admin-page.md. Step S2 of four. Apply after 20261120000000_admin_core.sql.
--
-- What it is for. The owner's admin page changes things through these functions only. Each one starts with
-- `private.assert_admin()`, spends from a rate limit (30 writes per admin per 10 minutes, error 54000), makes its
-- change and appends one row to the admin log in the same transaction.
--
--   admin_set_plan(user, plan)             free or friends for one person ('paid' is refused until it is decided)
--   admin_set_cap(plan, feature, per_day)  the per-person daily cap for a plan and kind
--   admin_set_ceiling(feature, per_day)    the project-wide daily ceiling for a kind (refused while AI is paused)
--   admin_pause_ai(reason)                 the kill switch: saves every ceiling, then sets them all to 0
--   admin_resume_ai()                      puts the saved ceilings back exactly (a kind that had none gets none)
--   admin_set_release_facts(a, b)          the first versionCode that has the update screen, and what Play serves
--   admin_set_app_versions(min, rec)       the minimum and recommended app version, behind two safety rules
--
-- Safety rules for the version switch (the page shows them; the server enforces them):
--   * The minimum can only be RAISED once the update screen is known (`update_screen_version_code` is set), because
--     a phone below the minimum stops syncing and, without the screen, would give no reason.
--   * Neither number can exceed `latest_published_version_code` (what Play serves), and with none recorded neither
--     can be above 0. Lowering is always allowed: it is the way out.
--
-- `take_ai_read` is not changed. A ceiling of 0 already refuses and refunds the person's read, so pausing is only
-- "set every ceiling to 0" with the old values saved in `private.admin_settings.ai_pause_snapshot`.
--
-- Additive only. Nothing runs until an admin calls a function. Rollback: drop function public.admin_set_plan(uuid, text),
--   public.admin_set_cap(text, text, integer), public.admin_set_ceiling(text, integer), public.admin_pause_ai(text),
--   public.admin_resume_ai(), public.admin_set_release_facts(integer, integer), public.admin_set_app_versions(integer, integer);
--   drop function private.admin_spend();

begin;

-- 30 writes per admin per 10-minute window. Raises 54000 on the 31st.
create function private.admin_spend()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_window timestamptz := date_bin(interval '10 minutes', now(), timestamptz '2026-01-01 00:00:00+00');
  v_writes integer;
begin
  insert into private.admin_rate (admin_id, window_start, writes)
  values (auth.uid(), v_window, 1)
  on conflict (admin_id, window_start) do update set writes = private.admin_rate.writes + 1
  returning writes into v_writes;
  if v_writes > 30 then
    raise exception 'too many admin changes in a few minutes' using errcode = '54000';
  end if;
  delete from private.admin_rate where window_start < now() - interval '1 day';
end;
$$;

revoke execute on function private.admin_spend() from public, anon;

create function public.admin_set_plan(p_user uuid, p_plan text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_before text;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if p_plan = 'paid' then
    raise exception 'the paid plan is not available yet' using errcode = '22023';
  end if;
  if p_plan is null or p_plan not in ('free', 'friends') then
    raise exception 'unknown plan' using errcode = '22023';
  end if;
  if not exists (select 1 from public.profiles where id = p_user and deleted_at is null) then
    raise exception 'no such person' using errcode = 'P0002';
  end if;

  select plan into v_before from private.user_plans where user_id = p_user;
  v_before := coalesce(v_before, 'free');

  if p_plan = 'free' then
    delete from private.user_plans where user_id = p_user;
  else
    insert into private.user_plans (user_id, plan) values (p_user, p_plan)
    on conflict (user_id) do update set plan = excluded.plan, updated_at = now();
  end if;

  perform private.admin_log('set_plan', p_user, null,
    jsonb_build_object('plan', v_before), jsonb_build_object('plan', p_plan));
  return p_plan;
end;
$$;

create function public.admin_set_cap(p_plan text, p_feature text, p_per_day integer)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_before integer;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if p_plan is null or p_plan not in ('free', 'friends') then
    raise exception 'unknown plan' using errcode = '22023';
  end if;
  if p_per_day is null or p_per_day < 0 or p_per_day > 10000 then
    raise exception 'cap must be between 0 and 10000' using errcode = '22023';
  end if;
  if not exists (select 1 from private.ai_read_caps where feature = p_feature) then
    raise exception 'unknown feature' using errcode = '22023';
  end if;

  if p_plan = 'free' then
    select per_day into v_before from private.ai_read_caps where feature = p_feature;
    update private.ai_read_caps set per_day = p_per_day where feature = p_feature;
  else
    select per_day into v_before from private.ai_plan_caps where plan = 'friends' and feature = p_feature;
    insert into private.ai_plan_caps (plan, feature, per_day) values ('friends', p_feature, p_per_day)
    on conflict (plan, feature) do update set per_day = excluded.per_day;
  end if;

  perform private.admin_log('set_cap', null, p_plan || ':' || p_feature,
    jsonb_build_object('per_day', v_before), jsonb_build_object('per_day', p_per_day));
end;
$$;

create function public.admin_set_ceiling(p_feature text, p_per_day integer)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_before integer;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if exists (select 1 from private.admin_settings where ai_pause_snapshot is not null) then
    raise exception 'AI is paused; resume it first' using errcode = '22023';
  end if;
  if p_per_day is null or p_per_day < 0 or p_per_day > 100000 then
    raise exception 'ceiling must be between 0 and 100000' using errcode = '22023';
  end if;
  if not exists (select 1 from private.ai_read_caps where feature = p_feature) then
    raise exception 'unknown feature' using errcode = '22023';
  end if;

  select per_day into v_before from private.ai_project_ceilings where feature = p_feature;
  insert into private.ai_project_ceilings (feature, per_day) values (p_feature, p_per_day)
  on conflict (feature) do update set per_day = excluded.per_day;

  perform private.admin_log('set_ceiling', null, p_feature,
    jsonb_build_object('per_day', v_before), jsonb_build_object('per_day', p_per_day));
end;
$$;

-- The ceilings as one object, {feature: per_day or null}.
create function private.admin_ceilings_json()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_object_agg(c.feature, k.per_day order by c.feature), '{}'::jsonb)
  from private.ai_read_caps c
  left join private.ai_project_ceilings k on k.feature = c.feature;
$$;

revoke execute on function private.admin_ceilings_json() from public, anon;

create function public.admin_pause_ai(p_reason text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_snapshot jsonb;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if exists (select 1 from private.admin_settings where ai_pause_snapshot is not null) then
    raise exception 'AI is already paused' using errcode = '22023';
  end if;
  if p_reason is not null and char_length(p_reason) > 200 then
    raise exception 'reason too long' using errcode = '22023';
  end if;

  v_snapshot := private.admin_ceilings_json();
  update private.admin_settings set ai_pause_snapshot = v_snapshot, updated_at = now();

  insert into private.ai_project_ceilings (feature, per_day)
  select feature, 0 from private.ai_read_caps
  on conflict (feature) do update set per_day = 0;

  perform private.admin_log('pause_ai', null, p_reason, v_snapshot, private.admin_ceilings_json());
  return private.admin_ceilings_json();
end;
$$;

create function public.admin_resume_ai()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_snapshot jsonb;
  v_before jsonb;
  v_row record;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  select ai_pause_snapshot into v_snapshot from private.admin_settings;
  if v_snapshot is null then
    raise exception 'AI is not paused' using errcode = '22023';
  end if;
  v_before := private.admin_ceilings_json();

  for v_row in select key as feature, value as per_day from jsonb_each(v_snapshot) loop
    if jsonb_typeof(v_row.per_day) = 'null' then
      delete from private.ai_project_ceilings where feature = v_row.feature;
    else
      insert into private.ai_project_ceilings (feature, per_day) values (v_row.feature, (v_row.per_day #>> '{}')::integer)
      on conflict (feature) do update set per_day = excluded.per_day;
    end if;
  end loop;
  update private.admin_settings set ai_pause_snapshot = null, updated_at = now();

  perform private.admin_log('resume_ai', null, null, v_before, private.admin_ceilings_json());
  return private.admin_ceilings_json();
end;
$$;

-- The two numbers the owner types in. They only go up, except that a typo can be fixed within 10 minutes of the last change.
create function public.admin_set_release_facts(p_update_screen integer, p_latest_published integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_old private.admin_settings;
  v_recent boolean;
  v_before jsonb;
  v_after jsonb;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if p_update_screen is not null and p_update_screen < 0 or p_latest_published is not null and p_latest_published < 0 then
    raise exception 'version codes cannot be negative' using errcode = '22023';
  end if;

  select * into v_old from private.admin_settings;
  v_recent := exists (
    select 1 from private.admin_audit
    where action = 'set_release_facts' and at > now() - interval '10 minutes'
  );
  if not v_recent and (
       p_update_screen < v_old.update_screen_version_code
    or p_latest_published < v_old.latest_published_version_code
    or (p_update_screen is null and v_old.update_screen_version_code is not null)
    or (p_latest_published is null and v_old.latest_published_version_code is not null)
  ) then
    raise exception 'these numbers only go up' using errcode = '22023';
  end if;

  v_before := jsonb_build_object('update_screen', v_old.update_screen_version_code, 'latest_published', v_old.latest_published_version_code);
  update private.admin_settings
    set update_screen_version_code = p_update_screen, latest_published_version_code = p_latest_published, updated_at = now();
  v_after := jsonb_build_object('update_screen', p_update_screen, 'latest_published', p_latest_published);

  perform private.admin_log('set_release_facts', null, null, v_before, v_after);
  return v_after;
end;
$$;

create function public.admin_set_app_versions(p_min integer, p_recommended integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_cfg public.app_config;
  v_facts private.admin_settings;
  v_after jsonb;
begin
  perform private.assert_admin();
  perform private.admin_spend();
  if p_min is null or p_recommended is null or p_min < 0 or p_recommended < 0 then
    raise exception 'version codes must be numbers from 0 up' using errcode = '22023';
  end if;
  if p_recommended < p_min then
    raise exception 'recommended cannot be below the minimum' using errcode = '22023';
  end if;

  select * into v_cfg from public.app_config;
  select * into v_facts from private.admin_settings;

  if p_min > v_cfg.min_version_code and v_facts.update_screen_version_code is null then
    raise exception 'set the update screen version before raising the minimum' using errcode = '22023';
  end if;
  if p_min > coalesce(v_facts.latest_published_version_code, 0)
     or p_recommended > coalesce(v_facts.latest_published_version_code, 0) then
    raise exception 'a version above the latest published one would lock people out' using errcode = '22023';
  end if;

  update public.app_config
    set min_version_code = p_min, recommended_version_code = p_recommended, updated_at = now();
  v_after := jsonb_build_object('min', p_min, 'recommended', p_recommended);

  perform private.admin_log('set_app_versions', null, null,
    jsonb_build_object('min', v_cfg.min_version_code, 'recommended', v_cfg.recommended_version_code), v_after);
  return v_after;
end;
$$;

revoke execute on function public.admin_set_plan(uuid, text), public.admin_set_cap(text, text, integer),
  public.admin_set_ceiling(text, integer), public.admin_pause_ai(text), public.admin_resume_ai(),
  public.admin_set_release_facts(integer, integer), public.admin_set_app_versions(integer, integer)
  from public, anon;
grant execute on function public.admin_set_plan(uuid, text), public.admin_set_cap(text, text, integer),
  public.admin_set_ceiling(text, integer), public.admin_pause_ai(text), public.admin_resume_ai(),
  public.admin_set_release_facts(integer, integer), public.admin_set_app_versions(integer, integer)
  to authenticated;

commit;
