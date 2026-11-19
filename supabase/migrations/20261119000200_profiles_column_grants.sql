-- STAGED, NOT APPLIED (owner review: changes who may write what on a live table). Issue #297, audit S-04.
--
-- What it is for. Signed-in people could update every column of their own profile row through the API,
-- including `deleted_at`, `created_at` and `id`. The app only ever writes four columns, so only those
-- four stay writable: display_name, avatar_emoji, upi_id, hue (see AuthRepository.updateProfile,
-- updateUpiId, updateHue). Account deletion runs through `delete_my_account`, which is security
-- definer and is not affected.
--
-- What does not change. Row access (`profiles_update_own`: only your own row), reading profiles, the
-- hue and UPI format checks, the sign-up trigger and every security-definer function.
--
-- What it deliberately leaves alone: a minimum length for display_name. The name column is nullable and
-- sign-in can arrive without a name, so a check there could break sign-up; it needs its own design.
--
-- Not additive: it narrows a live grant. Behaviour for the shipped app (1.6 and later) is unchanged
-- because it writes only the four columns. Rollback: grant update on public.profiles to authenticated;

begin;

revoke update on public.profiles from authenticated, anon, public;

grant update (display_name, avatar_emoji, upi_id, hue) on public.profiles to authenticated;

commit;
