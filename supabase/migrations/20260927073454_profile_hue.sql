-- APPLIED to live on 2026-09-27 as version 20260927073454, after owner review and approval. Re-verified
-- against live in rolled-back transactions the same day (its suite passed in full).
--
-- Tested on the local harness: supabase/tests/rls_profile_hue_test.sql.
-- Design: docs/design-v1.4-groups-settlements-colours.md, item 11.
--
-- What it is for. Each person has ONE colour, chosen by them and the same on every phone and every
-- screen (owner decision, 2026-09-27: one persistent colour per person, not per group). Today a
-- groupmate's colour is picked separately on each phone, so Priya is green on one and blue on
-- another.
--
-- The colour is stored as a hue (0-359, OKLCH degrees). Lightness and chroma are fixed by the app,
-- so every pick reads on the dark background with the same contrast.
--
-- The rules, enforced here rather than trusted to the app:
--
--   * A hue within 25 degrees of the hue of anyone you currently share a group with is refused
--     (23514). Two colours closer than that look alike on a donut or a member chip.
--   * Hues 28-58 are refused: that band is the brand amber (43), the app's own accent for buttons,
--     highlights and warnings, so no person's colour reads as part of the interface.
--   * The check takes a lock per shared group, so two groupmates picking at the same moment can't
--     both get the same colour.
--   * Only the hue changing triggers the check. Clearing it (null: "let the app choose") is always
--     allowed. Joining a group is never refused over colour: if the joiner's hue is close to a
--     member's, the app asks the joiner to pick again (see the design doc).
--
-- Who can write it is unchanged: profiles_update_own already limits updates to your own row.
--
-- Rollback: drop trigger profiles_check_hue, drop function private.check_profile_hue, drop the
-- column. Colours go back to being chosen per phone.

begin;

alter table public.profiles
  add column hue smallint
    constraint profiles_hue_range check (hue is null or hue between 0 and 359);

comment on column public.profiles.hue is
  'The person''s colour as an OKLCH hue, the same on every phone. Null: the app derives one.';

create or replace function private.hue_distance(a integer, b integer)
returns integer
language sql
immutable
set search_path = ''
as $$
  select least(abs(a - b), 360 - abs(a - b));
$$;

create or replace function private.check_profile_hue()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_group uuid;
  v_clash text;
begin
  if new.hue is null or new.hue is not distinct from old.hue then
    return new;
  end if;
  -- The service role repairs data without these rules.
  if (select auth.uid()) is null then
    return new;
  end if;

  if new.hue between 28 and 58 then
    raise exception 'that colour is the app''s own accent' using errcode = '23514';
  end if;

  -- One lock per shared group, in a fixed order so two pickers can't deadlock.
  for v_group in
    select gm.group_id from public.group_members gm where gm.profile_id = new.id order by gm.group_id
  loop
    perform pg_advisory_xact_lock(hashtextextended('profile_hue:' || v_group::text, 0));
  end loop;

  select p.display_name into v_clash
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

  if v_clash is not null then
    raise exception 'too close to the colour of someone in your groups' using errcode = '23514',
      hint = 'Pick a colour further from ' || v_clash || '''s.';
  end if;
  return new;
end;
$$;

revoke execute on function private.check_profile_hue() from public, anon, authenticated;
revoke execute on function private.hue_distance(integer, integer) from public, anon;

create trigger profiles_check_hue
  before update of hue on public.profiles
  for each row execute function private.check_profile_hue();

commit;
