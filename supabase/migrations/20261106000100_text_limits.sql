-- Plan T2.2, abuse limits part 1: no field takes unbounded text (SEC M-5). Additive only: every
-- existing row fits (longest on 2026-11-06: merchant 51, note 97, category 15, payment 13, name 21).
-- The app caps typing at the same lengths (domain/TextLimits.kt), so these only refuse a client
-- that ignores them. Invite-guess limits and write rates (part 2) change security-definer
-- functions and wait for the owner's review.

alter table public.transactions
  add constraint transactions_merchant_length check (char_length(merchant) <= 120),
  add constraint transactions_note_length check (char_length(note) <= 1000),
  add constraint transactions_category_length check (char_length(category) <= 60),
  add constraint transactions_payment_method_length check (char_length(payment_method) <= 60);

alter table public.profiles
  add constraint profiles_display_name_length check (display_name is null or char_length(display_name) <= 60);

alter table public.groups
  add constraint groups_description_length check (description is null or char_length(description) <= 500);

alter table public.settlements
  add constraint settlements_note_length check (note is null or char_length(note) <= 500);
