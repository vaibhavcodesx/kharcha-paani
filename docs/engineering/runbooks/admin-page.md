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

## Day-to-Day Owner Guide

This guide is for managing the app day to day without technical jargon.

### How to Sign In
1. Open the admin URL on your phone or computer.
2. Tap **Sign in with Google** and pick your Google account.
3. **If sign-in fails:**
   - Confirm you are using the same Google email you added to the database.
   - The most common cause is a Google client secret mismatch: re-copy the Web Client secret from Google Cloud into Supabase under Auth > Providers > Google.
   - Check that your admin URL is listed in Supabase Auth > URL Configuration > Redirect URLs.

### What Each Tab Does
- **Today**: Instant overview of the app: active people this week, how much AI has been used today, cloud photo storage, and database size.
- **People**: Search users by name or email, view account details, and manage their plan.
- **Limits**: Daily usage ceilings and the emergency AI pause button.
- **Release**: Control which app versions are allowed to connect, prompting older versions to update.
- **Log**: A clear timeline record of every action taken by an admin (with dates, who did it, and what changed).
- **Delete account**: Permanently erase a user and all their stored receipts when requested.

### How to Pause AI
1. Go to **Limits** (or the prompt on **Today**).
2. Click **Pause AI**.
3. Type `PAUSE` in the box and confirm. AI parsing stops cleanly and the app switches to manual entry without errors. Tap **Resume AI** when ready to restore it.

### How to Change a Plan
1. Open the **People** tab and find the person by name or email.
2. Click their row to open details.
3. Select the new plan (Free, Friends, or Paid) and click **Save**.

### How to Raise the Minimum Version Safely
1. Open the **Release** tab.
2. Check the timeline track. **Safety rule:** Never raise the minimum version higher than the version code that contains the in-app update screen.
3. Enter the new version number and save. Older app builds will gently tell users to update on Google Play.

### How to Delete an Account
1. Open the **Delete account** tab.
2. Enter the person\'s email to look up their records.
3. Confirm their details, type `DELETE` in the confirmation box, and proceed. Their data, profile, and photos will be removed permanently.
