-- Backlog item 6: an emoji avatar instead of a profile photo (owner, 2026-09-15), so groupmates see
-- the same face on their phones. Additive: one nullable column, no existing row changes.
--
-- No new policy: profiles_update_own already limits updates to your own row, and
-- profiles_select_self_or_groupmate limits who reads it. The app offers a fixed set of emoji; the
-- length check only stops arbitrary text being stored as an "avatar".

alter table public.profiles
  add column avatar_emoji text
    constraint profiles_avatar_emoji_length check (avatar_emoji is null or char_length(avatar_emoji) between 1 and 8);
