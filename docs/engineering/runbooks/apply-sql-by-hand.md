<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: applying staged SQL by hand (owner guide)

This runbook guides you through applying a staged database migration to Supabase using the web dashboard.

Agents stage database migrations in pull requests, but agents never touch the live database. You apply them manually.

---

## Before you begin: take a backup

Never apply a migration without a backup. The free Supabase plan has no point-in-time recovery. Before opening the SQL editor:
1. Take a manual database dump with `pg_dump` using your project connection string (see [migration.md](migration.md)).
2. Confirm the dump file exists on your computer.

---

## How to apply a migration (exact clicks)

1. **Open Supabase:**
   - Go to [supabase.com/dashboard](https://supabase.com/dashboard) and sign in.
   - Click on your project.

2. **Open the SQL Editor:**
   - In the left-hand navigation sidebar, click **SQL Editor** (the terminal/code icon `>_`).
   - Click the green **New query** button at the top (or click **+**).

3. **Paste the migration code:**
   - Open the staged `.sql` file in GitHub or your local repository (inside `supabase/migrations/`).
   - Copy the entire contents of the file.
   - Click into the large query text box in Supabase.
   - Press **Ctrl+A** (or **Cmd+A** on Mac) to select any placeholder text.
   - Press **Ctrl+V** (or **Cmd+V** on Mac) to paste the complete migration text.

4. **Press Run:**
   - Click the green **Run** button at the bottom right of the query editor (or press **Ctrl+Enter**).
   - **Important:** Do NOT just click "Save". Clicking "Save" only saves a draft query snippet to your personal sidebar—it does **not** execute or apply anything to the database.

5. **Wait for success:**
   - Wait for the query to finish. Do not close or refresh the tab.
   - When finished, the results panel at the bottom will display:
     `Success. No rows returned`
     (or a table of results if the script returned rows).

6. **Report back:**
   - Tell the Lead agent that the migration ran successfully. The Lead will update `supabase/MIGRATIONS.md` and the changelog.

---

## What to do if an error occurs

If the query fails and shows red error text at the bottom:

1. **Stop immediately. Do NOT click Run again.**
2. **Copy the error text:** Highlight the entire error message at the bottom and copy it.
3. **Why you must not re-run:**
   - A migration script usually contains multiple SQL statements.
   - Supabase executes statements in sequence. Earlier statements (like creating a table or adding a column) may have already succeeded before the error happened.
   - If you click Run again, the script will immediately crash on the first statement (e.g. `relation "..." already exists`), obscuring the real error.
4. **Send the error to the Lead:**
   - Paste the exact error text into a message or issue for the Lead agent.
   - The Lead will inspect what partially succeeded and prepare a tailored script to finish the remaining steps safely.

---

## How to check what is live

To verify what is currently applied in your database, open a **New query** in the SQL Editor and run these verification queries:

### 1. Check recorded migrations
```sql
select version, name from supabase_migrations.schema_migrations order by version desc;
```
This lists all migrations recorded by migration tools. The newest version is at the top.

### 2. Check custom functions
```sql
select proname from pg_proc where pronamespace = 'public'::regnamespace order by proname;
```
*(To also check private internal functions: `select proname from pg_proc where pronamespace = 'private'::regnamespace order by proname;`)*

When a migration was applied by hand rather than via automated tools, checking `pg_proc` confirms whether the specific functions and database logic from the migration are actually live.

---

## Common mistakes to avoid

- **Pasting without clicking Run:** Saving the query or pasting it into the window does nothing until you click the green **Run** button.
- **Running a text selection only:** If any portion of the text is highlighted when you click **Run**, Supabase runs **only** the highlighted text instead of the whole file. Ensure nothing is selected before clicking Run.
- **Closing the browser tab:** Do not close the tab or switch pages while the query is running. Wait for the green success message.
- **Re-running after an error:** Re-running a failed script without a fix will fail on duplicate names (tables, columns, types) created by the earlier statements in the same script. Stop at the first error.
