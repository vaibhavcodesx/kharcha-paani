-- APPLIED to live on 2026-09-27 as version 20260927073400, after owner review and approval. Re-verified
-- against live in rolled-back transactions the same day (its suite passed in full).
--
-- Tested on the local harness: supabase/tests/rls_settlement_confirmation_test.sql.
-- Design and reasoning: docs/design-v1.4-groups-settlements-colours.md, item 10.
--
-- What it is for. Recording a settle-up or an IOU between two members is one-sided and final the
-- moment it is tapped: either party writes it, and it moves both people's balances at once. Now a
-- settlement is recorded as *pending*, and only the other party can confirm or reject it.
--
-- The rules, all enforced here rather than trusted to the app:
--
--   * Whoever records it is `recorded_by`, stamped by the server from the caller's session. A new
--     row is always `pending`, whatever the client sent.
--   * Only the other party can answer it, once: pending -> confirmed or pending -> rejected (with an
--     optional note). `responded_by` and `responded_at` are stamped by the server.
--   * While it is pending, only the recorder can withdraw it (soft delete). The other party answers
--     instead of deleting, so a disputed payment leaves a visible "rejected", not a disappearance.
--   * A rejected settlement can be cleared away by its recorder only.
--   * A confirmed one can still be retracted by either party, as today.
--   * Every existing row predates confirmation: it becomes `confirmed` with no recorder, and none of
--     the answering rules apply to it.
--
-- Unchanged: who can read a settlement, who can record one (settlements_select / _insert), and
-- that amounts, parties and dates can never be edited after recording (settlements_lock_columns).
--
-- Rollback: drop the trigger stamp_new_settlement and the function respond_to_settlement, restore
-- private.lock_settlement_columns from 20260914142135, drop the five columns and two constraints.
-- Rows written after the migration lose their status, and every settlement is final again.

begin;

-- ------------------------------------------------------------
-- Columns. Existing rows take 'confirmed' through the default at the moment the column is added;
-- the default then becomes 'pending' for everything written afterwards.
-- ------------------------------------------------------------

alter table public.settlements
  add column recorded_by uuid references public.profiles (id),
  add column status text not null default 'confirmed',
  add column responded_by uuid references public.profiles (id),
  add column responded_at timestamptz,
  add column response_note text;

alter table public.settlements alter column status set default 'pending';

alter table public.settlements
  add constraint settlements_status_valid check (status in ('pending', 'confirmed', 'rejected'));

-- An answer is always from the other party, and a pending row has no answer yet. A confirmed row
-- with no recorder is a legacy row from before this migration.
alter table public.settlements
  add constraint settlements_answer_consistent check (
    (status = 'pending' and responded_by is null and responded_at is null and response_note is null)
    or (status = 'confirmed' and recorded_by is null and responded_by is null)
    or (status in ('confirmed', 'rejected')
        and recorded_by is not null
        and responded_by is not null
        and responded_at is not null
        and responded_by <> recorded_by
        and responded_by in (from_profile_id, to_profile_id))
  );

alter table public.settlements
  add constraint settlements_response_note_length check (response_note is null or char_length(response_note) <= 200);

comment on column public.settlements.status is
  'pending until the party who did not record it confirms or rejects it. Server-owned.';

-- ------------------------------------------------------------
-- New rows: recorded by the caller, pending, unanswered. Server-owned like updated_at.
-- ------------------------------------------------------------

create or replace function private.stamp_new_settlement()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Only for a signed-in caller. The service role (no auth.uid()) keeps whatever it wrote, which
  -- is how an operator could backfill or repair rows.
  if (select auth.uid()) is not null then
    new.recorded_by := (select auth.uid());
    new.status := 'pending';
    new.responded_by := null;
    new.responded_at := null;
    new.response_note := null;
  end if;
  return new;
end;
$$;

revoke execute on function private.stamp_new_settlement() from public, anon, authenticated;

create trigger settlements_stamp_new
  before insert on public.settlements
  for each row execute function private.stamp_new_settlement();

-- ------------------------------------------------------------
-- Updates: the existing immutability, plus who may answer and who may withdraw.
-- ------------------------------------------------------------

create or replace function private.lock_settlement_columns()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := (select auth.uid());
begin
  -- Unchanged from 20260914142135: a settlement's facts never change after it is recorded.
  if new.id is distinct from old.id
     or new.from_profile_id is distinct from old.from_profile_id
     or new.to_profile_id is distinct from old.to_profile_id
     or new.amount is distinct from old.amount
     or new.date is distinct from old.date
     or new.note is distinct from old.note
     or new.is_iou is distinct from old.is_iou
     or new.created_at is distinct from old.created_at
     or new.recorded_by is distinct from old.recorded_by
     or (new.group_id is distinct from old.group_id
         and (new.group_id is not null or exists (select 1 from public.groups where id = old.group_id))) then
    raise exception 'a settlement cannot be changed after it is recorded'
      using errcode = '23514', hint = 'Retract it and record a new one.';
  end if;

  -- The service role repairs data without these rules.
  if v_me is null then
    return new;
  end if;

  -- Answering: only a pending, live settlement, only by the other party, only once.
  if new.status is distinct from old.status
     or new.responded_by is distinct from old.responded_by
     or new.responded_at is distinct from old.responded_at
     or new.response_note is distinct from old.response_note then
    if old.status <> 'pending' or new.status not in ('confirmed', 'rejected') then
      raise exception 'this settlement has already been answered'
        using errcode = '23514';
    end if;
    if old.deleted_at is not null or new.deleted_at is not null then
      raise exception 'a withdrawn settlement cannot be answered'
        using errcode = '23514';
    end if;
    if old.recorded_by is null or v_me = old.recorded_by or v_me not in (old.from_profile_id, old.to_profile_id) then
      raise exception 'only the other person can confirm or reject this settlement'
        using errcode = '42501';
    end if;
    new.responded_by := v_me;
    new.responded_at := now();
    new.response_note := nullif(btrim(new.response_note), '');
  end if;

  -- Withdrawing or reviving (deleted_at). Pending and rejected rows belong to their recorder;
  -- confirmed and legacy rows can be retracted by either party, as before.
  if new.deleted_at is distinct from old.deleted_at
     and old.status in ('pending', 'rejected')
     and v_me is distinct from old.recorded_by then
    raise exception 'only the person who recorded this settlement can withdraw it'
      using errcode = '42501', hint = 'Reject it instead.';
  end if;

  return new;
end;
$$;

revoke execute on function private.lock_settlement_columns() from public, anon, authenticated;

-- ------------------------------------------------------------
-- The one call the app makes to answer. SECURITY INVOKER: settlements_update and the trigger above
-- decide, exactly as for a direct update.
-- ------------------------------------------------------------

create or replace function public.respond_to_settlement(p_settlement_id uuid, p_confirm boolean, p_note text default null)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.settlements
  set status = case when p_confirm then 'confirmed' else 'rejected' end,
      response_note = p_note
  where id = p_settlement_id;
  -- Not a party (settlements_update's USING hides the row) or no such settlement: one answer, so
  -- the call can't be used to probe which ids exist.
  if not found then
    raise exception 'settlement not found' using errcode = 'P0002';
  end if;
end;
$$;

revoke execute on function public.respond_to_settlement(uuid, boolean, text) from public, anon;
grant execute on function public.respond_to_settlement(uuid, boolean, text) to authenticated;

commit;
