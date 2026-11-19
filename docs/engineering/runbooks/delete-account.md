<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: delete an account when the person has no app (owner only)

The in-app path (Settings, Delete my account) is the normal one. This is for the email request on
`site/delete-account.html`: someone who uninstalled the app. An agent never runs this; it writes the steps, the
owner runs them. The owner's email inbox is the request channel; answer within a few days (the privacy policy
says what is promised).

**1. Check the request is theirs.** The email must come from the Google account they signed in with. If it
comes from another address, reply asking them to write from the sign-in account. Never delete on a guess.

**2. Ask (once) the one choice:** may their name stay on past group expenses ("keep my name"), or show as
"Former member" (the default if they do not answer).

**3. Find the account.** Supabase dashboard > SQL editor:

```sql
select id, email, created_at from auth.users where email = '<their sign-in email>';
```

Exactly one row. Copy the id. Also look at `select * from public.profiles where id = '<id>'` and confirm
`deleted_at` is null (a deleted account has nothing left to delete).

**4. Take a database dump** if you have not taken one today (`docs/engineering/runbooks/migration.md`, step 1 of
"Owner"). Deletion is permanent.

**5. Delete their bill photos first.** The deletion function does not remove files. Supabase dashboard > Storage > `receipts` > open the folder named with their id > select all > Delete. Do not delete files with SQL (it leaves the files behind). Once their sign-in is gone you can no longer tell whose folder it was.

**6. Run the deletion as that person**, in one SQL editor run (it is a single transaction):

```sql
begin;
select set_config('request.jwt.claims', json_build_object('sub', '<id>', 'role', 'authenticated')::text, true);
set local role authenticated;
select public.delete_my_account(false);   -- false = "Former member"; true = keep their name
commit;
```

The function deletes their private rows, tombstones the profile (name only), removes them from groups (a group
they created passes to the longest-standing member; an empty group is deleted) and deletes the sign-in. It is the
same code the app runs; the photos are the one thing it does not remove, which is why step 5 exists.

**7. Check.**

```sql
select count(*) from auth.users where id = '<id>';                       -- 0
select display_name, deleted_at from public.profiles where id = '<id>';  -- the tombstone
```

In Storage, the `receipts` folder with their id is gone.

**8. Reply** to the person: done, what stays (shared group history under "Former member" or their name), and that
they can sign up again as a new account.

If the function raises an error, stop and do not edit rows by hand. Copy the error to a `needs-owner` issue without
the person's email or id (this repository is public).
