-- STAGED, applied by hand like the others. When the owner pauses AI from the admin page, the app only found out
-- AFTER the person had spoken, photographed a bill or picked a statement. This read-only function lets the app ask
-- first, when the button is tapped.
--
-- public.ai_available(p_feature) is false only when AI is paused for that kind: the project's ceiling for it is 0,
-- which is exactly what the owner's pause sets (every ceiling to 0). A person using up their own daily cap is NOT
-- reported here; that still shows when the read is refused. It spends nothing and writes nothing, and
-- take_ai_read stays the only thing that actually decides.
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
  v_ceiling integer;
begin
  if auth.uid() is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  if not exists (select 1 from private.ai_read_caps where feature = p_feature) then
    raise exception 'unknown feature %', p_feature using errcode = '22023';
  end if;

  select per_day into v_ceiling from private.ai_project_ceilings where feature = p_feature;
  return v_ceiling is null or v_ceiling > 0;
end;
$$;

revoke execute on function public.ai_available(text) from public, anon;
grant execute on function public.ai_available(text) to authenticated;

commit;
