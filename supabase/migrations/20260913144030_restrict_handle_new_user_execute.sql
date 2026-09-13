-- handle_new_user is a trigger function only; triggers fire regardless of the
-- triggering role's EXECUTE grant, so it never needs to be directly callable
-- via the PostgREST RPC endpoint.
revoke execute on function public.handle_new_user() from public, anon, authenticated;
