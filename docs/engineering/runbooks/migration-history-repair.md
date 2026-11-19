<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: repair the migration history, and add a staging project (owner only)

**Why.** Two migrations were applied by hand in the SQL editor, so the live history does not list them, and a tool
that trusts the history (`supabase db push`) would try to apply them again. The repo's migration files now carry the
exact versions the live history uses (renamed in the same PR as this page); `supabase/MIGRATIONS.md` says which
files are applied and which are staged.

## 1. Record the two applied-by-hand migrations (about 2 minutes)

Take a database dump first (`docs/engineering/runbooks/migration.md`, "Owner" step 1). Then, in the SQL editor:

```sql
select version, name from supabase_migrations.schema_migrations order by version desc limit 3;  -- expect 20261106130938 on top

insert into supabase_migrations.schema_migrations (version, name) values
  ('20261025000100', 'delete_my_account'),
  ('20261110000000', 'bill_item_splits')
on conflict do nothing;

select count(*) from supabase_migrations.schema_migrations;   -- 23
```

This only records facts; it changes no table the app uses. Tell the Lead so `supabase/MIGRATIONS.md` is updated.

## 2. A staging project (once, about 15 minutes; the free plan allows a second project)

An agent cannot create projects. You do:
1. supabase.com > New project, name `kharcha-paani-staging`, the same region (Tokyo). Save its password in your password manager.
2. In its SQL editor run every file in `supabase/migrations/` in file-name order (applied and staged: staging should be
   the newest state), one at a time; stop at the first error and tell the Lead.
3. Authentication > Providers: leave Google off in staging (nothing signs in there; tests impersonate users in SQL).
4. Give the Lead the staging project id (not a password). From then on every staged migration is applied to
   staging and its `supabase/tests/*.sql` run there first; production comes second, after a dump.

Until staging exists, "rollback-only tests" run on production (a single script that always ends in a raised error);
the Lead also runs every test on a local Postgres first (`docs/engineering/testing.md`).
