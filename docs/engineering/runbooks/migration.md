<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: a Supabase migration

The free plan has no backups, so a bad migration cannot be undone from the dashboard. Agents stage; the owner applies.

**Agent (staging a migration in a PR)**
1. Additive only unless the PR is marked red with the owner's review: new table, nullable or defaulted column, index,
   new function. No drop, no type change, no data mutation, no loosened RLS policy.
2. File `supabase/migrations/<timestamp>_<name>.sql` starting with a comment: "STAGED, NOT APPLIED", what it is for,
   who can do what, and the rollback statement.
3. File `supabase/tests/<name>_test.sql` that ends in a raised error carrying results (always rolls back).
4. A migration that changes what clients may send needs the minimum-version switch first (issue #299).
5. PR is `risk:red`: say plainly what the owner must apply, and in what order with any function deploy.

**Owner (applying)**
1. Take a dump first: Supabase dashboard > Database > Backups > or `pg_dump` with the connection string. Keep it off the laptop.
2. Apply to the staging project once it exists (issue #298), run the test script, look at the result.
3. Apply to live in the SQL editor. Run the test script again (rolled back). Check one real screen on the phone.
4. Tell the Lead; the Lead edits the migration's header comment to "Applied <date>" and the changelog line.
