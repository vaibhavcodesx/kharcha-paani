-- Already applied to the live project on 2026-09-18; committed here after the fact.
--
-- It was applied through the MCP tool and never written to this folder, so the repository and the
-- live schema disagreed: anyone replaying supabase/migrations got a `profiles` table with no
-- `upi_id`, and the local RLS harness tested a schema production does not have. Reconstructed from
-- the live definitions rather than from memory.
--
-- The handle a groupmate scans to pay you. Visibility is inherited, not new:
-- profiles_select_self_or_groupmate already limits a profile to its owner and to people who share
-- a group with them, which is exactly the audience for a payment handle. No policy is touched.

begin;

alter table public.profiles
  add column if not exists upi_id text;

comment on column public.profiles.upi_id is
  'This account''s UPI handle, shown to groupmates settling up. Format-constrained because it is
   interpolated into a upi://pay deep link. Readable only via profiles_select_self_or_groupmate.';

commit;
