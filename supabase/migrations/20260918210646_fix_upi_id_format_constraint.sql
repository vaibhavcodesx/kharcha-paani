-- Already applied to the live project on 2026-09-18; committed here after the fact, with
-- 20260918210534_add_upi_id_to_profiles.
--
-- The constraint as first written used {2,256}, which Postgres rejects: its regex engine caps a
-- repetition count at 255 ("invalid repetition count(s)"). Both sides are 64 now, which is well
-- past any real VPA — NPCI handles run to about 50 — and is the same bound `UpiLink.cleanId` uses
-- on the device, so exactly one rule governs what can be stored and what can be built into a link.
--
-- What it excludes is the point: '&', '=', '?' and whitespace can never appear, so a handle that
-- arrives from someone else's phone cannot smuggle a second parameter into a upi://pay link.

begin;

alter table public.profiles
  drop constraint if exists profiles_upi_id_format;

alter table public.profiles
  add constraint profiles_upi_id_format check (
    upi_id is null
    or upi_id ~ '^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9.-]{1,64}$'
  );

commit;
