-- NOT APPLIED. Staged for owner review (plan T1.1, decision D1). NOT purely additive: it drops the
-- foreign key from profiles to auth.users (see below).
-- Tested against live inside a transaction that rolled back: supabase/tests/delete_my_account_test.sql.
--
-- What it is for. Play requires that people can delete their account from inside the app. Decision D1:
--
--   * Their private rows go outright: personal expenses and income, private splits and settlements
--     with friends who aren't on the app, invites they made.
--   * Shared history stays exactly as it was, so nobody else's balance moves. On it they appear under
--     the name they have now, or as "Former member", as they choose (Former member by default).
--   * The profile is kept as a tombstone: the name above and nothing else. Picture, emoji, colour and
--     UPI ID are cleared, and `deleted_at` is set.
--   * They leave every group. A group they created passes to the member who has been in it longest;
--     a group nobody is left in is deleted, with its expenses and payments.
--   * The auth.users row is deleted (with its identities and sessions), so nobody can sign in as them,
--     and their email goes with it: profiles never held it.
--   * Immediate, no grace period. The app refuses while it has changes it hasn't synced.
--
-- Why the foreign key goes. profiles.id referenced auth.users with ON DELETE CASCADE, so deleting the
-- sign-in also deleted the profile, which shared expenses, splits and settlements point at (NO ACTION):
-- the delete would fail, or, had those rows cascaded, take everyone's shared history with it. A
-- tombstone profile has to outlive its sign-in, so the reference is dropped. New sign-ups still get
-- their profile from handle_new_user, unchanged.
--
-- Who can call it: authenticated, for themselves only (auth.uid()). It runs as its owner because it
-- has to delete from auth.users and from rows RLS hides from the caller.
--
-- Rollback: drop functions public.delete_my_account(boolean) and public.former_member_names(uuid[]);
-- drop column profiles.deleted_at; and,
-- only if no tombstone exists, add back profiles_id_fkey (foreign key (id) references auth.users (id)
-- on delete cascade).

begin;

alter table public.profiles drop constraint profiles_id_fkey;

alter table public.profiles add column deleted_at timestamptz;

comment on column public.profiles.deleted_at is
  'Set when the account was deleted (delete_my_account). The row is a tombstone: a display name and nothing else.';

create or replace function public.delete_my_account(p_keep_name boolean default false)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_group record;
  v_heir uuid;
begin
  if v_me is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  if exists (select 1 from public.profiles where id = v_me and deleted_at is not null) then
    raise exception 'account already deleted' using errcode = 'P0002';
  end if;

  -- Private rows. Personal expenses carry their private splits with them (local_splits cascades).
  delete from public.local_settlements where owner_id = v_me;
  delete from public.local_splits where owner_id = v_me;
  delete from public.transactions where owner_id = v_me and group_id is null;
  delete from public.group_invites where created_by = v_me;

  -- Groups they made: to the longest-standing other member, or gone with their records if nobody's left.
  for v_group in select id from public.groups where created_by = v_me loop
    select profile_id into v_heir
    from public.group_members
    where group_id = v_group.id and profile_id <> v_me
    order by joined_at, profile_id
    limit 1;
    if v_heir is not null then
      update public.groups set created_by = v_heir where id = v_group.id;
    end if;
  end loop;

  delete from public.group_members where profile_id = v_me;

  -- Any group of theirs that's now empty, created by them or not.
  for v_group in
    select g.id from public.groups g
    where not exists (select 1 from public.group_members m where m.group_id = g.id)
      and (g.created_by = v_me
        or exists (select 1 from public.transactions t where t.group_id = g.id and t.owner_id = v_me)
        or exists (select 1 from public.settlements s where s.group_id = g.id and v_me in (s.from_profile_id, s.to_profile_id)))
  loop
    delete from public.settlements where group_id = v_group.id;
    delete from public.transactions where group_id = v_group.id;
    delete from public.groups where id = v_group.id;
  end loop;

  update public.profiles
  set display_name = case when p_keep_name then display_name else 'Former member' end,
      avatar_url = null,
      avatar_emoji = null,
      hue = null,
      upi_id = null,
      deleted_at = now()
  where id = v_me;

  delete from auth.users where id = v_me;
end;
$$;

revoke execute on function public.delete_my_account(boolean) from public, anon;
grant execute on function public.delete_my_account(boolean) to authenticated;

-- How "Former member" reaches phones that already knew the person by name. Profiles are readable only
-- between current groupmates (profiles_select_self_or_groupmate), and someone who deleted their
-- account is in no group, so their old groupmates' phones would keep the old name for good. This hands
-- back the tombstone names, and only for profiles that appear on a record in one of the caller's
-- groups. No policy changes: a tombstone stays unreadable through the table itself.
create or replace function public.former_member_names(p_ids uuid[])
returns table (id uuid, display_name text)
language sql
stable
security definer
set search_path = ''
as $$
  select p.id, p.display_name
  from public.profiles p
  where cardinality(p_ids) <= 500
    and p.id = any (p_ids)
    and p.deleted_at is not null
    and exists (
      select 1
      from public.group_members m
      where m.profile_id = auth.uid()
        and m.group_id in (
          select t.group_id from public.transactions t where t.owner_id = p.id
          union
          select t.group_id from public.transactions t join public.splits s on s.transaction_id = t.id where s.paid_by = p.id
          union
          select t.group_id from public.transactions t join public.splits s on s.transaction_id = t.id
            join public.split_shares ss on ss.split_id = s.id where ss.profile_id = p.id
          union
          select st.group_id from public.settlements st where p.id in (st.from_profile_id, st.to_profile_id)
        )
    );
$$;

revoke execute on function public.former_member_names(uuid[]) from public, anon;
grant execute on function public.former_member_names(uuid[]) to authenticated;

commit;
