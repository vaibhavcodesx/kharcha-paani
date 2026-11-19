<!-- status: approved by the Lead 2026-11-19 (owner questions answered with the defaults in section 6) | owner: lead | reviewed: 2026-11-19 -->
# Admin page for the owner (#355)

One page where the owner runs the closed test (12 to 100 people, Supabase free plan) without the table editor.
Not part of the APK; it does not hold 1.8.0. The source lives at `site/admin/` in this repository. It is deployed to **Vercel** as its own project
(root directory `site/admin`; plain files, no build step, no server code of our own). Owner decision 2026-11-19: Vercel,
not GitHub Pages. The page holds no secret: it signs in with Google and calls database functions that
check the caller against `private.admins`. All SQL here is **staged**: the owner applies it by hand. Agents apply
nothing to the live project. Planned by an Opus agent, reviewed by the Lead.

## 1. What the page does

| Area | Shows | Controls |
|---|---|---|
| **Today** | People (total, active in 7 days, deleted), plan split, AI reads today per kind against the ceiling, receipt storage vs the free 1 GB, database size vs 500 MB, photos left by deleted accounts, current min/recommended version | none; tiles link to the tab that changes them |
| **People** | Search by email or name (server side, 50 per page). Row: name, email, plan, joined, last sign-in, AI reads today, photo count and size, groups. Deleted accounts greyed | Change plan (free / friends). Person sheet with **Delete account** |
| **Limits** | Caps grid (kind x plan), project ceiling per kind with today's use as a meter, pause state | Edit a cap, edit a ceiling, **Pause AI** / **Resume AI** |
| **Release** | `min_version_code`, `recommended_version_code`, two release facts, a plain sentence on who is affected | Edit facts; raise or lower min/recommended behind guards |
| **Delete** (inside People, plus "Delete by email") | Preview: groups it is in, groups it created and who inherits them, photo count | Type-to-confirm deletion, then cleanup of leftover photos |
| **Log** | Every admin action: when, who, what, target, before and after | Load more. Nothing editable |

**Safe-raise rule.** Phones only obey `min_version_code` from the build with `AppVersionGate`. `private.admin_settings`
holds `update_screen_version_code` (first versionCode with the update screen, #393) and `latest_published_version_code`
(what Play serves). The server refuses to **raise** min while `update_screen_version_code` is unset, and refuses min or
recommended above `latest_published_version_code`. Lowering is always allowed. The page shows a non-dismissable warning
before a raise and asks to type the new number.

**AI kill switch.** `admin_pause_ai` saves the current ceilings (including kinds with no row) in
`admin_settings.ai_pause_snapshot`, then sets every ceiling to 0; `take_ai_read` then refuses and refunds the read, and
the app shows its daily-limit message. `admin_resume_ai` restores the snapshot exactly. A harder stop (undeploying
`gemini-proxy`) stays a dashboard step.

**Account deletion and photos.** SQL cannot remove Storage files. Flow: `admin_find_account(email)` previews;
`admin_delete_account(user, typed_email, keep_name)` runs the same body as `delete_my_account` (moved to
`private.delete_account(uuid, boolean)`, with `delete_my_account` a one-line wrapper, same signature and grants) and
returns the account's **leftover** photo paths (in that account's folder, not pointed at by a surviving expense); the
page calls `storage.from('receipts').remove(paths)` with the owner's session; two new storage policies allow an admin to
select and delete **only leftover objects of deleted accounts**; `admin_record_photo_cleanup` recounts and logs. If the
owner declines the policies, this becomes a runbook step (the page lists the paths). The leftover view also covers
accounts deleted in the app (issue #420 fixes that on the app side).

**Cut from 1.8, with reasons.** AI use history chart (today's meter is enough); group and invite stats (no action
follows); sync-failure counts (the server never sees them; needs app telemetry); maintenance banner (needs an app
release); egress and MAU (not readable from SQL; link to the dashboard); the `paid` plan (refused until decided);
several admins and roles (one owner; the table allows more rows).

## 2. Security design

The page is untrusted code anyone can read. The database is the boundary. The page holds the publishable key, already
public in `split.html` and the APK. Every admin function is `security definer`, `set search_path = ''`, and starts with
`perform private.assert_admin()`. Tables stay in `private` (not exposed). No service key anywhere.

**Tables (`private`, `revoke all from public, anon, authenticated`):**
- `admins(user_id uuid pk references auth.users on delete cascade, email text not null, added_at timestamptz)`. The
  owner inserts the row by hand; it is never in a migration, so no email enters the repo.
- `admin_audit(id bigserial pk, at timestamptz default now(), admin_id uuid, admin_email text, session_id uuid,
  action text, target_user uuid null, target text null, before jsonb, after jsonb)`. Append-only. Deletions log the user
  id, never the email.
- `admin_settings` (single row, `id boolean pk check (id)`): `update_screen_version_code int`,
  `latest_published_version_code int`, `ai_pause_snapshot jsonb`, `updated_at`.
- `admin_rate(admin_id uuid, window_start timestamptz, writes int, pk (admin_id, window_start))`.

**`private.assert_admin(p_fresh_minutes int default null) returns uuid`.** All must hold or it raises 42501 "not an
admin": (1) `auth.uid()` not null and JWT `is_anonymous` false; (2) a row in `private.admins` for that uid; (3) the
row's email equals the JWT email (lower-cased) and an `auth.identities` row with `provider = 'google'` has the same
email (uid and Google identity, not email alone); (4) when `p_fresh_minutes` is given (deletion: 10), the JWT's
`session_id` session was created within that many minutes. `private.is_admin() returns boolean` runs 1 to 3 without
raising, granted to `authenticated` for storage policies, called as `(select private.is_admin())`.

**Functions** (public, `revoke execute from public, anon`, `grant execute to authenticated`; each writes one audit row
in the same transaction):

| Function | Returns / notes |
|---|---|
| `admin_whoami()` | boolean, never raises |
| `admin_overview()` | jsonb: as_of, people, plans, ai_today[], ai_paused, storage, db bytes, versions |
| `admin_list_users(p_query, p_limit, p_offset)` | page of users with plan, AI today, photos, groups, total_count; query at most 100 chars, `%` and `_` escaped; limit clamped 1 to 100 |
| `admin_set_plan(p_user, p_plan)` | `free` deletes the row, `friends` upserts, `paid` refused, unknown user P0002 |
| `admin_get_limits()` | caps, ceilings with used_today, paused |
| `admin_set_cap(p_plan, p_feature, p_per_day)` | range 0 to 10,000; `free` updates `ai_read_caps`, `friends` upserts `ai_plan_caps` |
| `admin_set_ceiling(p_feature, p_per_day)` | range 0 to 100,000; refused while paused |
| `admin_pause_ai(p_reason)` / `admin_resume_ai()` | double pause or resume when not paused: 22023 |
| `admin_set_release_facts(p_update_screen, p_latest_published)` | numbers only go up (typo fix within 10 minutes) |
| `admin_set_app_versions(p_min, p_recommended)` | guards as above; recommended >= min |
| `admin_find_account(p_email)` | preview row, exact case-insensitive match |
| `admin_delete_account(p_user, p_confirm_email, p_keep_name)` | `assert_admin(10)`; typed email must match; refuses admins and deleted accounts; max 5 per India day; returns leftover paths |
| `admin_leftover_photos(p_user, p_limit)` | deleted accounts only |
| `admin_record_photo_cleanup(p_user, p_removed)` | recount and log |
| `admin_list_audit(p_limit, p_before)` | keyset paging |

**Storage policies** (separate migration, so the owner can decline): `receipts_admin_select_leftover` and
`receipts_admin_delete_leftover` for `authenticated`, using `bucket_id = 'receipts' and (select private.is_admin()) and
private.is_leftover_receipt(name)`; the helper is true only when the folder's account is a tombstone or gone and no
undeleted transaction points at the path.

**Rate limits.** 30 writes per admin per 10 minutes (error 54000); 5 deletions per India day; reads clamped.

**Destructive actions.** Delete, Pause AI and raising min version open a confirm sheet with one plain sentence; the
owner types the target (email, `PAUSE`, or the number). The server re-checks the typed email. Deletion needs a fresh
session.

**XSS, CSRF, CSP.** No cookies (bearer token), so no CSRF. All text through `textContent` or `createElement`; no
`innerHTML`, `insertAdjacentHTML`, `eval` or inline handlers; a check script greps for them. Vercel sets real response headers from `site/admin/vercel.json`: the CSP below, `Strict-Transport-Security`,
`X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex`, and
`frame-ancestors 'none'` (a meta tag cannot set it, so the clickjacking caveat of Pages disappears). Keep the same CSP in a
meta tag as a fallback. CSP:
`default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self' data:; connect-src
https://<project>.supabase.co; base-uri 'none'; form-action 'none'; require-trusted-types-for 'script'`.
`referrer no-referrer`, `robots noindex`, hides itself when framed. One library: `@supabase/supabase-js` 2.49.4 (same
as `gemini-proxy`), committed under `site/admin/vendor/` with an SRI hash; no CDN, no build step.

**Session.** `sessionStorage`, signed out after 20 idle minutes; PKCE Google sign-in; anyone else sees "This page is for
the owner" and is signed out.

**If the owner's Google account is compromised.** The attacker gets this page's powers only: no expenses, balances or
photos; at most 5 deletions a day, with a fresh sign-in, all logged. Break-glass in the Supabase dashboard (separate
login): `delete from private.admins;` and sign out all sessions. Owner steps: 2-Step Verification or a passkey on the
Google account, 2FA on Supabase.

**Rollback-only SQL tests** (`supabase/tests/admin_*_test.sql`, each ending in a raised error with results):
1 anon refused everywhere and `app_config` still readable; 2 non-admin raises 42501, `whoami` false; 3 right uid wrong
JWT email, right email wrong uid, no Google identity, anonymous: all refused; 4 `set_plan` friends gives the 51st
`take_ai_read`, free removes the row, paid refused, unknown user P0002; 5 `set_cap` bounds, correct table, exact audit
before/after; 6 ceilings stop `take_ai_read`, pause refuses and does not charge, resume restores every value including
kinds with no ceiling, pause twice refused; 7 version guards; 8 delete: wrong email, stale session, admin target, the
same assertions as `delete_my_account_test.sql`, audit row has no email, 6th deletion a day refused; 9 existing
`delete_my_account` test unchanged after the wrapper; 10 leftover photos: unreferenced listed and deletable,
surviving-expense photo not listed, live account's folder 0 rows, non-admin 0 rows; 11 31st write raises 54000;
12 every write adds exactly one audit row, `authenticated` cannot select `private.*`; 13 overview numbers equal
fixtures, search for `a%` does not match everything.

## 3. UI and UX

**Direction.** The app's evening ledger in a browser: warm charcoal, one saffron accent, numbers in their own
typeface, plain sentences. Five tabs, one hero figure each, nothing decorative that carries no meaning. Used on a
phone at night.

**Navigation.** Tabs **Today, People, Limits, Release, Log**; Delete lives in People. Phones: a floating glass dock at
the bottom (the app's dock). From 900 px: segmented row in a sticky header, two columns, max 72 rem. The URL hash holds
the tab.

**Layouts (phone, 360 to 430 px).**
- Today: eyebrow "KHARCHA PAANI · ADMIN", hero "37 people" (the one gradient use), "21 active this week · 2 gone", AI
  today meters per kind ("quick_add 412 / 2,000"), storage and database meters, "12 leftover photos · Clean", versions
  row.
- People: search field, "Delete by email" button, stacked rows (name, plan chip, email, AI and photo counts), deleted
  rows greyed with leftover photo count; tapping opens a bottom sheet with plan segmented control, today's usage,
  groups, and a coral "Delete account..." button.
- Limits: AI on/off with "Pause AI"; caps grid (free, friends) with inline edit; ceilings with used count and edit.
  Edit is inline with a stepper and Save/Cancel; nothing saves on blur; after saving the row shows "40 (was 30)" for
  five seconds.
- Release: min, recommended, update screen from, latest on Play, a plain sentence on who is affected, "Change
  versions..." button.
- Delete sheet: "Delete <email>'s account. Leaves N groups, 'Trip' passes to Ravi. N photos." name choice (Former
  member / keep name), type the email, "Delete permanently" disabled until it matches.
- Log: time, action, target, before to after, "Load older".

**Design tokens** (from `Color.kt`, `Type.kt`, `Shapes.kt`, `Spacing.kt`, `Motion.kt`, plus the site's light set), in
`site/admin/admin.css` on `:root`; dark follows `prefers-color-scheme`, a toggle overrides it (kept in `localStorage`
inside try/catch).

| Token | Dark | Light |
|---|---|---|
| `--bg` | `#0E0D0C` | `#FBF9F6` |
| `--surface` | `#181615` | `#F6F2EC` |
| `--card` | `#1E1C1A` | `#FFFFFF` |
| `--raised` | `#22201D` | `#F3EFE8` |
| `--line` | `#2E2B27` | `#E6DFD4` |
| `--outline` (controls, 3:1) | `#726D65` | `#9A9186` |
| `--track` | `#423E37` | `#E2DACD` |
| `--fg` | `#F4F0E8` | `#1F1D1A` |
| `--muted` | `#A09A8F` | `#6B645B` |
| `--faint` | `#8D887E` | `#756D63` |
| `--accent` | `#FF7A3D` | `#C4501C` |
| `--accent-text` | `#FF9B6A` | `#B04716` |
| `--accent-soft` | `#FF7A3D33` | `#FBE9DF` |
| `--on-accent` | `#1A0D06` | `#FFFFFF` |
| `--ok` | `#C6F24C` | `#2F7D4F` |
| `--danger` | `#FF6F61` | `#B83A2E` |
| `--gold` | `#FFB547` | `#A86A00` |

Brand gradient amber, gold, lime, used once (Today hero).
- **Type.** Fonts copied from `app/src/main/res/font` to `site/admin/fonts/`, `font-display: swap`, `system-ui`
  fallback. Outfit 600 for titles (40/32/24/20, tracking -1 to -0.2); Plus Jakarta Sans for body 16/24 and labels 14/12
  (nothing under 12 px); Space Grotesk with tabular numbers for every number, Indian grouping (`en-IN`). Eyebrow:
  12 px, 600, `letter-spacing .1em`, uppercase, `--faint`.
- **Shapes.** Radii 12, 18, 28; sheet `32px 32px 0 0`; primary action uses the app's leaf `22px 8px 22px 8px`;
  `corner-shape: squircle` as progressive enhancement; no ticket or perforation (those mean money changing hands).
- **Spacing.** 4 px scale `2 4 8 12 16 24 32 48`; gutter 20 px (16 below 360).
- **Glass.** Only the dock and the sticky header: `backdrop-filter: blur(32px)`, neutral tint (no amber), 7% rim, top
  hairline. Cards are solid.
- **Motion.** Enter `cubic-bezier(.05,.7,.1,1)` 320 ms; leave `cubic-bezier(.3,0,.8,.15)` 180 ms; tab change slides
  10% with a cross-fade; sheet rises 24 px; meters fill once. No bounce, no looping animation, no animated logo (the
  logo is not final). `prefers-reduced-motion: reduce` makes all changes instant, opacity only.
- **States.** Loading: skeleton rows shaped like real rows. Empty: plain sentences. Error: inline card with a "Try
  again" button; 42501 shows "Signed out or not the owner"; 54000 "Too many changes in a few minutes". Saving: busy and
  disabled button, never optimistic. Confirm sheets; a toast names the log entry. Signed out: calm card with "Sign in
  with Google" and "Owner only." Not admin: "This page is for the owner."
- **Accessibility.** AA contrast in both themes; 44 px targets; `:focus-visible` 2 px accent; ARIA tablist with arrow
  keys; sheets are `<dialog>`; meters have text and are never colour-only ("Near", "At ceiling", "Paused"); live
  regions for toasts; labelled fields; 200% zoom without horizontal scroll.
- **Components.** Vanilla ES modules: `auth.js`, `rpc.js` (maps error codes to plain messages), `router.js`, `ui.js`
  (`el()` builder, toast, sheet, confirm-by-typing, meter, skeleton, segmented control, stepper), one module per tab.
  Pure functions where possible so `scripts/check_admin_page.mjs` tests them in Node like `check_split_page.mjs`.
- **Avoid.** Left sidebar with many icons; a grid of identical KPI cards with sparklines and "+12%" chips; "Welcome
  back"; Inter; purple or blue SaaS gradients; gradient text beyond the hero; glass on content; nested cards;
  centred-everything layout; emoji icons; grey text on colour; bounce easing; decorative charts; tables that scroll
  sideways on a phone (rows stack).

## 4. PRs, in order

Lead = Claude, Builder = Codex, Worker = Antigravity. Server SQL is Lead only. Each PR adds `changes/355.md` and ends
with "Check on the phone".

| # | PR | Owner | Risk | Files | Done when |
|---|---|---|---|---|---|
| S1 | Identity, audit, read functions | Lead | red | `supabase/migrations/20261120000000_admin_core.sql`, `supabase/tests/admin_core_test.sql` | tables, `assert_admin`, `is_admin`, `whoami`, `overview`, `list_users`, `get_limits`, `list_audit`; tests 1-3, 12, 13 |
| S2 | Write functions | Lead | red | `...000100_admin_writes.sql`, test | plan, caps, ceilings, pause/resume, release facts, versions, rate limit; tests 4-7, 11, 12 |
| S3 | Delete by email | Lead | red | `...000200_admin_delete_account.sql`, test | `private.delete_account`, wrapper, find, delete; tests 8, 9 |
| S4 | Leftover photos | Lead | red | `...000300_admin_leftover_photos.sql`, test | helper, two policies, leftover and cleanup functions; test 10 |
| P1 | Page shell and sign-in | Builder | red | `site/admin/index.html`, `admin.css`, `js/{auth,rpc,router,ui}.js`, `vendor/`, `fonts/`, `scripts/check_admin_page.mjs` | sign-in works, non-admin refused, tabs show "Coming soon", both themes, no `innerHTML`, no CSP violations |
| P2 | Today | Builder | yellow | `js/today.js` | numbers match the dashboard |
| P3 | People and plan | Builder | yellow | `js/people.js` | search, paging, plan change logged |
| P4 | Limits and pause | Builder | yellow | `js/limits.js` | edit cap and ceiling; pause and resume round trip |
| P5 | Release | Worker (complete brief) | yellow | `js/release.js` | guards visible; raise confirm shows who is affected |
| P6 | Delete and photo cleanup | Builder | red | `js/delete.js` | preview, type-to-confirm, re-sign-in if stale, leftover removal, manual list fallback |
| P7 | Log | Worker | yellow | `js/log.js` | paged log with before and after |
| P8 | Docs | Worker | green | `docs/engineering/runbooks/admin-page.md`, `delete-account.md`, `site/README.md` | apply, verify, break-glass |
| CI | Run `check_admin_page.mjs` in CI | Lead | red (`.github`) | `.github/workflows/` | runs beside `check_split_page` |

Page PRs may merge before the SQL is applied: the page shows "Server functions not installed yet" when `admin_whoami`
returns 404. S3 and S4 stay `needs-owner` until the owner has read them.

## 5. Owner steps

Apply the staged files from `supabase/MIGRATIONS.md` first, in file order and honouring each header; then the admin
files in order (S1, S2, S3, S4). A dump before each file; run each file's test script (it must end with the "all
passed" error and change nothing). Add yourself once: `insert into private.admins (user_id, email) select id, email
from auth.users where email = '<your Google email>';`. Add the Vercel URL of the admin project
to Auth > URL Configuration > Redirect URLs; set the Google **web** client ID and secret in Auth > Providers and add
the Supabase callback URL in Google Cloud. Turn on 2-Step Verification or a passkey on the Google account and 2FA on
Supabase. Create a Vercel project from this repository with root directory `site/admin` (the owner's account; the Lead
prepares `vercel.json` and does not deploy). Add its URL to Supabase Auth Redirect URLs. Optionally turn on Vercel
Deployment Protection as a second lock. Before raising the minimum version, set "Update screen from"
and "Latest on Play" on the Release tab. Rollback: each file's header has drops; delete the Vercel project;
break-glass `delete from private.admins;`.

## 6. Questions for the owner (answered with the safe default; file a `needs-owner` issue to change)

1. Who is admin? Only the owner, by Google email and user id; no second admin.
2. May the page delete leftover photos itself (S4)? Yes; otherwise cleanup is a manual dashboard step.
3. Deletion limits: 5 per day, sign-in within the last 10 minutes.
4. Audit log kept forever (rows are tiny; deletions record no email).
5. Pause message: the existing daily-limit message while paused (a dedicated message needs an app release).
