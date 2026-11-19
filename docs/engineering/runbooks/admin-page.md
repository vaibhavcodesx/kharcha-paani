<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: owner admin page (apply, verify, rollback)

Owner-only steps from [admin-page.md](../../designs/admin-page.md). Agents apply nothing to live.

## Apply
1. Take a fresh manual database dump before each file ([migration.md](migration.md)).
2. In Supabase SQL editor, apply staged migrations in order from `supabase/MIGRATIONS.md`.
3. Apply admin SQL files in order (S1, S2, S3, S4), running each test block to its expected error.
4. Add your admin record: `insert into private.admins (user_id, email) select id, lower(email) from auth.users where lower(email) = lower('<your Google email>');`.
5. In Supabase Auth > URL Configuration > Redirect URLs, add the Vercel admin project URL.
6. In Supabase Auth > Providers > Google, configure Web Client ID and secret; add Supabase callback URL in Google Cloud.
7. Turn on 2-Step Verification / passkey on Google account, and 2FA on Supabase.
8. In Vercel, import repository with root directory `site/admin` (Lead supplies `vercel.json`; owner deploys).
9. (Optional) Turn on Vercel Deployment Protection for a second access lock.

## Verify
10. Open the Vercel URL and sign in with your Google admin account.
11. Confirm the Today tab displays live metrics and does not show "Server functions not installed yet".
12. On Release tab, verify "Update screen from" and "Latest on Play" are configured before raising min version.

## Rollback & Break-Glass
13. To rollback database objects: run the DROP statements documented in the header of each admin SQL file.
14. To take down the frontend: delete or disable the Vercel project deployment.
15. **Break-glass immediate lockout:** run `delete from private.admins;` in Supabase SQL editor.
