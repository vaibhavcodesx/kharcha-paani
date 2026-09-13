# PRD: Kharcha Paani — Cloud Migration, Robustness & Premium UI Pass

## Origin & Context

Kharcha Paani already exists as a complete, working native Android app —
**built in Kotlin + Jetpack Compose via Google AI Studio**, not built with
Claude Code yet. The project lives at
`Z:\V\.The_Future\Vibeeee\Kharcha Paani`. No work has been done on it with
Claude Code so far — this PRD is the starting brief for that first real
session, covering three things together: moving from local-only storage
to a cloud backend so data is safe and shareable with friends, hardening
the app's correctness and test coverage, and a deliberate pass on the
UI/UX so it reads as a genuinely premium, hand-crafted product rather than
an AI-generated MVP — since this may end up in a portfolio.

## What Already Exists (built via AI Studio — verified against the
## project's own architecture dossier, saved alongside this PRD as
## `docs/original-architecture-dossier.md`)

- **Architecture**: MVVM, offline-first repository layer, Kotlin
  Coroutines + Flow, StateFlow-driven reactive UI.
- **Persistence**: Room 2.6.1 over SQLite (WAL mode), 5 normalized
  entities (Group, Person, Transaction, Split, SplitShare, Settlement)
  with proper foreign keys and cascade rules.
- **Ledger correctness**: balances are derived reactively from
  transactions + splits + settlements, not stored as a mutable field —
  the right pattern, and something to preserve through the cloud
  migration below rather than regress.
- **Debt simplification**: a greedy min-cash-flow algorithm
  (`DebtSimplifier.kt`), already implemented and reasoned about
  correctly (O(n log n), reduces to at most n−1 settlement transfers).
- **AI ingestion**: a unified `ParsedTransactionResult` schema across
  three input paths — messy text/voice (Gemini 2.5 Flash NLP), receipt/
  screenshot OCR (Gemini multimodal vision), and PDF bank statement
  parsing (Gemini document vision) — plus a **local regex fallback** for
  standard Indian bank/UPI SMS formats that works fully offline. This
  offline fallback is a genuinely notable feature most competitor apps
  don't have.
- **Android system integration**: a home screen widget (balance +
  quick-add + voice deep link), notification channels for spending
  milestones and settlement reminders, and a share-intent receiver
  handling images, PDFs, *and* plain text (WhatsApp payment messages).
- **Design system** (already chosen, execution quality to be audited
  below): dark charcoal palette (`#121214` background) with warm amber
  (`#FF7A3D`) primary and chartreuse (`#CCFF00`) secondary accents,
  Outfit / Space Grotesk / Plus Jakarta Sans typography, spring-physics
  `bounceClick`, rolling-digit number animation, directional
  swipe-to-dismiss with color-coded backdrops.
- **Groups/trips concept**: a more capable model than a flat friend
  list — keep and build on this rather than simplifying it.
- **Data portability**: RFC-4180 CSV export via share intent.
- **Security posture**: no hardcoded secrets — the Gemini API key is
  injected via `BuildConfig` from a `.env` file, kept out of version
  control.

## Goals for This Pass

1. **Go cloud** — data safety (survives a lost/reset phone) and real
   multi-user sharing with friends, with live-syncing balances.
2. **Harden correctness** — close silent failure modes, add test
   coverage for the highest-value logic (`DebtSimplifier`, ledger math).
3. **Elevate the UI/UX to genuinely premium** — a deliberate audit and
   polish pass so the app doesn't read as "AI-generated," even though the
   underlying color/type choices are already good on paper.

## Prerequisite: Fix the Placeholder Package Name Before Anything Else

The project's actual `applicationId`/package is currently `com.example`
(visible throughout the real source: `MainActivity.kt`,
`AppDatabase.kt`, etc. all live under `com.example.*`). This is a
scaffolding placeholder, not something to ship or put in a portfolio, and
it needs to change **before** Google Sign-In is wired into the app code
— an Android-native OAuth client (mentioned in Part A) is registered
against the package name plus a signing fingerprint, so fixing this after
that registration means redoing it. Rename to something real (e.g.
`com.vaibhav.kharchapaani`) as the very first task of the actual build
session, before extending `supabase-kt` sign-in code.

---

## PART A — Cloud Migration & Multi-User Sharing

### Research Summary: Why Supabase, Not Firebase

Both were seriously evaluated.

**Firebase (Firestore)** is the more mature choice for mobile-first,
offline-heavy, real-time apps — the most battle-tested offline
persistence of any BaaS. But it's a **document** database, and this app's
data is fundamentally relational (a settlement references two people, a
split references a transaction and multiple share rows, balances come
from joining across tables). Firestore would force denormalizing this
relational structure into nested documents, fighting the ledger pattern
already built and working. Firebase pricing is also usage-metered
(per read/write), harder to predict once friends start generating
activity in shared groups.

**Supabase (Postgres)** matches the existing data model almost exactly —
the current Room schema (people, transactions, splits, split_shares,
settlements, groups) maps close to 1:1 onto Postgres tables. It gives
real joins and transactions (no denormalization rewrite), **Row Level
Security** enforced at the database level (the core mechanism for
"friends see only groups they're in"), predictable flat-rate pricing,
and no hard vendor lock-in. Its real-time subscriptions and mobile
offline story are less mature than Firebase's — the honest tradeoff —
but at this app's scale (you plus a handful of friends), that gap doesn't
matter in practice.

**Decision: Supabase.** The relational-data-model fit outweighs
Firebase's edge in offline maturity at this scale.

### New Tech Stack (additions to what already exists)

- **Backend**: Supabase (Postgres + Auth + Realtime + Storage), cloud-
  hosted, free tier to start.
- **Client SDK**: `supabase-kt`, the official Kotlin client library —
  added alongside the existing Room/Kotlin/Compose stack, not replacing
  it.
- **Auth**: Supabase Auth using **Google Sign-In** — simplest onboarding
  for you and friends, no password to manage.
- **Local layer**: Room stays, but its role changes — from "the
  database" to "the local cache + offline write queue." The app always
  reads/writes Room first (instant, works offline); a sync layer pushes
  local changes to Supabase when online and pulls remote changes down.
- **Realtime**: Supabase Realtime (Postgres change subscriptions) for
  live balance updates when a friend in a shared group adds an expense.
- **Storage**: Supabase Storage for receipt photos — worth persisting
  the original image (currently likely processed in-memory only for
  OCR), both as an audit trail and so friends viewing a shared expense
  can see the original receipt.
- **Sync approach**: hand-rolled, not PowerSync. PowerSync is a
  legitimate, more robust option — a maintained sync engine built
  specifically for Supabase-plus-offline — but it's an additional paid
  service and additional architectural surface area. At this app's scale,
  a simpler hand-rolled queue is proportionate. Revisit PowerSync only if
  hand-rolled sync becomes a genuine, persistent time sink.

### New Data Model (Postgres, via Supabase)

Every table below replaces or extends the equivalent Room entity. Key
structural change: **every "person" in a split must be a real registered
user** (someone who has signed in with Google at least once), not a
free-text name — this is what makes real bidirectional sync possible,
since RLS needs an identity to know what someone's allowed to see.

- `profiles` — id (= Supabase Auth user id), display_name, avatar_url,
  created_at. Auto-created on first sign-in.
- `groups` — id, name, emoji, created_by (profile id), created_at.
- `group_members` — group_id, profile_id, joined_at. Junction table;
  **RLS: a user can only read a group's data if their profile_id exists
  in this table for that group_id.**
- `transactions` — id, owner_id (profile id), type (expense/income),
  amount, category, merchant, payment_method, date, note, group_id
  (nullable), receipt_storage_path (nullable), created_at.
- `splits` — id, transaction_id, total_amount, paid_by (profile id),
  **split_type (equal/exact/percentage)** — carried over from the
  existing Room `SplitEntity`; the first Postgres migration dropped this,
  which would have been a real feature regression (exact/percentage
  splits already work today and shouldn't be lost in the cloud version).
- `split_shares` — id, split_id, profile_id, share_amount,
  **percentage (nullable)** — same reasoning, matches the existing
  `SplitShareEntity`.
- `settlements` — id, from_profile_id, to_profile_id, amount, date, note,
  **is_iou (boolean)**, **group_id (nullable)** — both exist in the
  current Room `SettlementEntity` and should carry over: `is_iou`
  distinguishes an informal IOU from an actual settlement payment (a
  named feature in this PRD's own user stories), and `group_id` lets a
  settlement be scoped to "everything from the Goa trip" rather than
  always being a flat two-person balance.
- **RLS policies, in plain terms**: a user can always read/write their
  own `transactions`; a user can read someone else's `transaction` only
  if it has a `group_id` they're a member of via `group_members`;
  `split_shares` are readable by either party referenced in the row.
  **`settlements` read access, decided here** (flagged by Claude Code as
  a real design question during implementation, not something to guess
  at): if a settlement has a `group_id` set, any member of that group can
  read it — group expense apps are more useful when settling-up activity
  is visible to the group ("Rohan settled with Priya" showing in shared
  history), so this is intentional transparency, not a leak. A settlement
  with no `group_id` (an informal 1:1 IOU) stays private to just the two
  parties involved. Both parties can always write/update their own
  settlement regardless of group visibility.
- **Schema changes should be tracked as versioned migration files in
  git** (a `supabase/migrations/` folder, one SQL file per change), not
  just applied live against the project with no local record. The
  MCP-driven approach is fine for authoring and testing a migration
  quickly, but the resulting SQL should be saved into the repo afterward
  — this is what makes the schema reproducible, reviewable in a PR, and
  rollback-able, and it's also a good portfolio signal (a visible
  migration history is a concrete sign of process, not just a working
  app).

**Note on the "must be registered" decision**: earlier drafts (and most
competitor apps) allow adding a friend as just a name, no account
required — simpler, but the balance then only exists in *your* app, not
synced anywhere. Since the explicit goal now is sharing the app with live
balances, requiring a one-time Google sign-in is the right tradeoff — but
it is a real behavior change from typical Splitwise-style apps, worth
being conscious of: a friend who won't install the app can no longer be
split with.

### Sharing & Onboarding Flow

1. A user signs in with Google on first launch — a `profiles` row is
   created automatically.
2. To split with a friend, the user creates or opens a **group** and
   generates an **invite link or short code**.
3. The friend installs the app, signs in with Google, and enters the
   code (or opens the invite link, which deep-links into the app) to
   join `group_members` for that group.
4. Both users now see the same group, same transactions, and
   live-updating balances via Supabase Realtime.
5. A personal (non-split) expense is unaffected — just `owner_id = me`,
   `group_id = null`.

### Sync Architecture

- **Writes**: every local write is written to Room immediately (instant
  UI update, works offline) and marked `pending_sync`. A background
  `WorkManager` job periodically pushes pending rows to Supabase via
  `supabase-kt`, clearing the flag on success.
- **Reads / incoming changes**: a Supabase Realtime subscription listens
  for changes on tables the current user has RLS access to, and writes
  incoming changes into Room — the existing reactive Flow-based UI layer
  just receives new data with no separate refresh logic needed.
- **Conflict handling**: last-write-wins by timestamp, explicitly. True
  conflict resolution is a hard distributed-systems problem; at this
  app's scale (personal finance data, low write frequency, small friend
  groups) such conflicts will be rare, and last-write-wins is an
  acceptable, honest simplification rather than something worth
  architecting deeply around.
- **Stays purely local, never synced**: the streak counter, notification
  scheduling, UI preferences — per-device by nature.

### Status Log (living record — update as work progresses)

- ✅ Supabase project created (`kharcha-paani`, `ap-northeast-1`,
  Postgres 17).
- ✅ Google Sign-In provider enabled on Supabase, Web OAuth Client ID
  configured in Google Cloud Console.
- ✅ Initial schema migration applied via the Supabase MCP connection:
  all 7 tables created, RLS enabled on every one, `SECURITY DEFINER`
  helper functions used to avoid self-referencing RLS recursion on
  `groups`/`group_members`, `auth.uid()` wrapped per Postgres
  performance best practice, and a real security-advisor finding fixed
  (the `profiles`-creation trigger function was callable directly via
  REST RPC — access revoked).
- ✅ **Package rename** (branch `foundation/finish-schema-and-package`):
  `namespace` `com.example` → `com.vaibhav.kharchapaani`. The
  `applicationId` was actually the AI Studio-generated
  `com.aistudio.splitflow.wzvq`, not `com.example`; it is now
  `com.vaibhav.kharchapaani` too. This installs as a **separate app** from
  any existing AI Studio build on a phone, so that build's local Room data
  is not visible to the renamed app. Keep the old install (or a CSV export)
  until Migration Plan step 5.
- ✅ **Migrations versioned in git**: both already-applied migrations are
  saved under `supabase/migrations/`, byte-verified against
  `supabase_migrations.schema_migrations` by md5.
- ✅ **Follow-up migration applied** (`20260913154813`):
  `splits.split_type` (text, `equal`/`exact`/`percentage`, default
  `equal`), `split_shares.percentage` (nullable unscaled numeric, 0–100),
  `settlements.is_iou` (boolean, default false), and `settlements.group_id`
  (nullable FK → `groups`, `on delete set null`, indexed). Settlements RLS
  is intentionally unchanged. Security advisor: clean.
- ✅ **`SECURITY DEFINER` helper bodies checked**: read the live
  `pg_get_functiondef` output for `private.is_group_member`,
  `can_access_transaction`, and `owns_transaction`. All three already use
  `(select auth.uid())`, and all have `search_path = ''`. EXECUTE is granted
  to `authenticated` only. No change needed.
- ✅ **Build verified after the rename**: `:app:compileDebugKotlin` and
  `:app:testDebugUnitTest` pass (5 tests, 0 failures, 0 skipped, all under
  `com.vaibhav.kharchapaani`). The only compiler warnings are pre-existing
  deprecations: `Icons.Filled.*` → `AutoMirrored` variants, and
  `fallbackToDestructiveMigration()`. The first attempt had failed only
  because drive C: was full.
- 🔎 **Found while doing the above; not fixed, belongs to later steps**:
  - *Step 3 (sync)*: no table has `updated_at` or a soft-delete column,
    so last-write-wins and delete propagation have nothing to compare
    against. Room stores money as `Double`, while Postgres uses
    `numeric(12,2)`, so shares like 100/3 will round on every round-trip.
    `AppDatabase` uses `fallbackToDestructiveMigration()` with
    `exportSchema = false`, so the first Room schema change for sync
    (e.g. adding `pending_sync`) **wipes all local data** unless real Room
    migrations replace it. Its `onCreate` callback also seeds sample
    people (Rahul/Priya/Arjun), which step 5's upload must not push to the
    cloud as real data.
  - *Step 4 (groups/RLS)*:
    - `group_members_insert_self` lets any signed-in user join any group
      whose UUID they know. There is no invite-code check yet.
    - `profiles` is readable by every signed-in user.
    - `split_shares` is readable only by the share-holder and the payer.
      Other members of a 3+ person group can't see all shares, so debt
      simplification across the group can't run for them.
    - Split and settlement rows can reference any profile, including
      non-members.
    - There is no policy that lets a member leave a group.
  - *Tooling*: the repo has no `gradlew`/wrapper JAR (needed for CI in
    Part C). ~~`debug.keystore` is gitignored and absent~~ generated
    locally in step 2 (still gitignored; see below).
  - *UI (Part B)*: the widget still reads "⚡ SplitFlow", and classes and
    resources keep the `SplitFlow*` name. The real palette is
    `CharcoalBg #0E0D0C` and chartreuse `#C6F24C`, not the `#121214` /
    `#CCFF00` this PRD lists.

#### Migration Plan step 2: supabase-kt + Google Sign-In (branch `feature/supabase-google-sign-in`)

- ✅ **Dependencies**: `supabase-kt` 3.6.0 (auth + postgrest), Ktor
  OkHttp engine, `androidx.credentials` 1.6.0, `googleid` 1.2.0.
  - 3.6.0 is the newest supabase-kt this project's Kotlin 2.2.10 can
    use; 3.7.0+ is built with Kotlin 2.4. Moving to it means bumping
    Kotlin first, as its own change (Room/Moshi KSP need re-checking).
  - OkHttp and logging-interceptor moved 4.10 → 5.3.2, because the
    Ktor engine requires OkHttp 5. Gemini's Retrofit client now runs on
    OkHttp 5 too; it compiles, but hasn't been exercised at runtime yet.
- ✅ **Auth flow**: Credential Manager → Google ID token with a
  SHA-256-hashed nonce → `supabase.auth.signInWith(IDToken)` with the raw
  nonce. If the device has no Google account, it falls back to
  `GetSignInWithGoogleOption`. Every failure maps to a specific message;
  a dismissed picker shows nothing.
- ✅ **Offline-first session handling**: supabase-kt reports
  `RefreshFailure` when a token expires with no network, and it's mapped
  to *still signed in*. Being offline never locks the user out. Sign-out
  clears the local session even when the logout call can't reach the
  server.
- ✅ **Session storage**: a dedicated `supabase_auth` SharedPreferences
  file, excluded from Auto Backup and from Android 12+ cloud backup and
  device transfer.
- ✅ **UI**:
  - Sign-in gate in `MainActivity`: blank frame while the stored session
    loads, then `SignInScreen` or the app.
  - Account button in the Home header, opening an account sheet. The
    sheet shows whether the `profiles` row loaded, says the ledger is
    still local-only, and offers sign-out.
  - Material 3 audit run on all three surfaces. Contrast was measured
    for every pairing and one real failure fixed (sign-out outline 1.28:1
    → 3.17:1), plus a double-clickable account button, a tablet max
    width, and heading semantics.
- ✅ **Tests**: 19 new unit tests (nonce, `SessionStatus` → `AuthState`
  mapping, client-ID validation, UI state, `SignInScreen` via
  Robolectric). All 24 pass and `assembleDebug` passes. The offline
  mapping test was mutation-checked: breaking the mapping makes it fail.
- ✅ **Config**:
  - `.env.example` documents `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`
    and `GOOGLE_WEB_CLIENT_ID`.
  - Found: an **empty** value in `.env` breaks `BuildConfig.java`
    generation. Documented, and the placeholder ID is rejected at
    runtime.
  - The local `.env` `SUPABASE_URL` had a stray `/rest/v1/` suffix, now
    fixed.
  - A local `debug.keystore` was generated. SHA-1
    `1C:B2:42:8A:71:4F:9A:F7:6A:24:9D:32:37:D3:6C:EB:C0:14:28:1B` is
    verified on the built APK.
- ✅ **Verified end-to-end on a real phone (2026-09-13)**:
  - Setup: Android OAuth client registered (package + SHA-1 above), and
    the Web client ID placed in `.env`.
  - Google sign-in succeeded, and the account sheet showed "Cloud
    profile ready".
  - A Gemini expense entry also worked, so the OkHttp 4 → 5 bump holds
    at runtime.
  - Checked via the Supabase MCP connection: exactly one `auth.users`
    row (provider `google`, one `google` identity) and a matching
    `profiles` row with `display_name` and `avatar_url` populated. The
    row was created by `on_auth_user_created` in the same transaction.
- 🔎 **Carried forward**:
  - ~~*Before step 3*: replace `fallbackToDestructiveMigration()`~~,
    done (see step 3 prerequisites below).
  - ~~*Step 3*: tie the local ledger to the account~~, done (see below).
  - *Step 5*: exclude the seeded Rahul/Priya/Arjun/Alex rows and the
    "Lonavala 2026" sample group from the upload.
  - *Part B*: the existing Home IOU button has the same
    IconButton + `bounceClick` double-clickable issue fixed on the new
    account button.

#### Migration Plan step 3: prerequisites (PR #4, branch `feature/sync-personal-transactions`)

- ✅ **Room migrations**:
  - `fallbackToDestructiveMigration()` removed. A missing migration now
    fails to open instead of wiping the ledger.
  - The Room Gradle plugin exports a JSON schema per version to
    `app/schemas` (v2 baseline committed), and migrations register in
    `AppDatabase.ALL_MIGRATIONS`.
  - `AppDatabaseMigrationSafetyTest` was mutation-checked: re-adding the
    fallback makes it fail.
- ✅ **Per-account ledgers** (design and all three decisions approved):
  - One `ledger-<userId>.db` per account.
  - The first account to sign in takes over the pre-accounts
    `splitflow.db`, with the WAL checkpointed before the rename.
  - New accounts start empty (seeding removed). Sign-out keeps the file.
  - `AccountSessionHolder` gives each account its own `MainViewModel`,
    cleared before that account's database closes.
  - The widget shows only the signed-in account, and "Sign in" when
    signed out.
  - Fixed along the way: rotation no longer re-parses a shared screenshot.
  - Accepted limit: separation is app-enforced, not encrypted.
- ✅ **Gradle wrapper + Android CI** (pulled forward from Part C):
  - Gradle 9.3.1 wrapper pinned to the official SHA-256.
  - `android-ci.yml` runs `:app:testDebugUnitTest` on every PR (JDK 25)
    and uploads the results.
  - Reason: Windows Application Control blocks Robolectric's native SQLite
    DLL on the dev machine, so database tests are verified on CI. Smart
    App Control was deliberately left on.
- ✅ **Verified on CI (Linux)**: 36 tests, 0 failures, 0 skipped,
  including all 8 `LedgerDatabasesTest` and both
  `AccountSessionHolderTest` cases.
- **Process change (2026-09-13, refined 2026-09-14)**: PRs merge once CI
  is green and the results have been checked. Still reviewed first:
  anything touching RLS or auth/security config, and **any database
  migration that isn't purely additive** (drops, type changes, or
  changes to existing data). Requested design discussions also still
  happen first.

#### Migration Plan step 3: sync loop for personal transactions (PR #5, branch `feature/sync-loop-personal-transactions`)

Design approved on 2026-09-14:
- **Conflicts:** last write to reach the server wins, and a still-queued
  local change beats a pulled one.
- **Pre-sync history** isn't uploaded; step 5 decides. In practice
  there's no legacy data to migrate: the old install's data is gone and a
  CSV backup exists.
- **Realtime** is deferred to step 4. Pulls only for now.

- ✅ **Supabase migration `20260913184544`** (purely additive, 0 rows,
  applied under the fast-track rule):
  - `transactions.updated_at` (server-owned via a
    `BEFORE INSERT OR UPDATE` trigger) and `deleted_at` for soft deletes.
  - An index on `(owner_id, updated_at)`.
  - Checked under RLS as an authenticated user in a rolled-back
    transaction: the trigger overrides a client-sent `updated_at`, and
    the soft delete works. RLS is unchanged.
- ✅ **Room v3** with a real `MIGRATION_2_3`:
  - `remoteId` (a device-generated UUID that is also the server id),
    `updatedAt` and `syncState`.
  - New `pending_remote_deletes` and `sync_meta` tables.
  - Existing rows get unique v4 UUIDs and stay `LOCAL_ONLY`.
- ✅ **Repository writes queue sync atomically**:
  - Personal expenses become `PENDING`; split and group expenses stay
    `LOCAL_ONLY`.
  - Edits keep `remoteId` and `createdAt`. This also fixes an existing
    bug: editing used to reset `createdAt`.
  - Converting a synced expense into a split queues a server delete.
- ✅ **`TransactionSyncEngine`**:
  - Batched idempotent upserts, marked synced only if the row is
    unchanged since read.
  - Soft deletes, then an overlapped, paged pull.
  - A row the server rejects is marked `FAILED` instead of blocking the
    queue.
- ✅ **WorkManager**:
  - Debounced sync after each write and on app start/foreground.
  - Periodic sync every 15 minutes while signed in; cancelled on
    sign-out.
  - Only the signed-in account's queue runs.
- ✅ **Account sheet sync status**: synced / waiting / syncing /
  couldn't sync, with Retry.
- **Known limits (accepted for step 3)**:
  - An offline edit that syncs later can overwrite a newer edit from
    another device, or revive a row another device deleted. This follows
    from the approved conflict rule.
  - `SyncWorker.doWork()` can't run under Robolectric (supabase-kt auth
    init never completes there), so its decision logic is tested directly.
  - Multi-device behaviour hasn't been tried by hand on two real devices
    yet.
- 🔎 **Found, not fixed** (correctness audit, priority 2):
  - Deleting a group leaves its transactions with a dangling `groupId`
    (no foreign key).
  - `deletePerson` removes shares but leaves `isSplit = true` on the
    transaction.
  - Several `@Insert(REPLACE)` DAOs would cascade-delete child rows if
    they ever replaced an existing parent.
  - The widget duplicates the ledger balance maths instead of using the
    repository.
- ⏳ **Not yet started**: groups, invites and multi-user splits (step 4:
  RLS changes need review), and Realtime.

### Web-Verified Notes (re-checked against current Supabase/Google docs)

- **The `SECURITY DEFINER` + wrapped-`auth.uid()` pattern used in the
  migration is current, correct Supabase practice** — confirmed against
  Supabase's own RLS performance documentation, unchanged in substance.
- **One specific, easy-to-miss gap worth explicitly checking**: Supabase's
  own linter that flags unwrapped `auth.uid()` calls
  (`auth_rls_initplan`) only inspects the RLS policy expression itself —
  it does **not** look inside the body of any `SECURITY DEFINER` helper
  function the policy calls. That means if `private.is_group_member()`,
  `can_access_transaction()`, or `owns_transaction()` internally use a
  bare `auth.uid()` rather than `(select auth.uid())` inside their own
  function bodies, the security advisor would report "clean" while the
  per-row performance cost still exists silently. Worth explicitly
  asking Claude Code to check the *bodies* of these three functions
  specifically, not just re-run the advisor and trust a clean result.
- **The native Android Google Sign-In approach discussed (Credential
  Manager → ID token → `supabase.auth.signInWithIdToken()`) is confirmed
  as Supabase's own current, officially documented path** — `supabase-kt`
  has a dedicated "Kotlin (Android)" version of this exact flow, not a
  workaround. No changes needed to the plan.

### Migration Plan

1. Stand up the Supabase project and tables before touching app code.
2. Add `supabase-kt` and Google Sign-In without removing Room — get auth
   working and a `profiles` row created on sign-in, as an isolated first
   step.
3. Migrate transaction/split/settlement sync next, starting with plain
   personal transactions (no group/split complexity) to prove the sync
   loop end-to-end before adding multi-user group logic.
4. Add groups, invites, and multi-user splits last, once single-user
   sync is solid.
5. One-time "upload my existing local data to my new account" migration
   step on first login, so the AI-Studio-era history isn't lost.

---

## PART B — UI/UX: Making It Genuinely Premium, Not AI-Slop

This is a deliberate, standalone workstream — **do not assume the
existing design system is already good just because the token choices
(charcoal + amber + chartreuse + Outfit/Space Grotesk) are reasonable on
paper.** AI-generated UI code frequently picks fine tokens but executes
them generically. Claude Code should audit the actual current
implementation against the checklist below and fix what it finds, not
just confirm the theme file looks correct.

### Concrete "AI slop" tells to specifically check for and eliminate

- **Generic empty states**: a centered icon + bold headline + gray
  subtext, all default-Material-spaced, with no personality or specific
  next action. If this pattern exists anywhere (first-launch, zero
  transactions, empty group), redesign it with a real illustration or
  distinctive layout and a concrete call-to-action, not a placeholder.
- **Uniform, rhythm-less spacing** — everything on the same 8dp/16dp grid
  with no visual hierarchy between related and unrelated content. Check
  actual spacing values used across screens, not just whether a spacing
  system exists in theory.
- **Default Material ripple/elevation on every card** — if every
  surface uses the same flat elevation and stock ripple with no
  distinction between primary and secondary surfaces, that reads as
  unstyled Material, not a designed product.
- **Stock Material icon pack used everywhere** — check whether category
  icons, navigation icons, and action icons are the default
  `Icons.Filled.*` set uniformly, versus a curated/custom icon treatment
  that fits the amber/chartreuse-on-charcoal identity.
- **Placeholder-feeling copy** — generic labels like "Transaction added
  successfully" or "No data available" instead of copy with the app's
  actual voice. Small, but cumulative.
- **A single accent color slapped onto otherwise-default components**,
  rather than the accent genuinely informing the whole visual system
  (button shapes, focus states, chart styling, loading indicators).
- **No distinction between loading, empty, and error states** — if all
  three collapse into the same generic spinner or blank screen, that's
  worth fixing; each deserves its own considered state.

### What "premium and modern" means concretely here

- **Screenshot-worthy moments, specifically**: the quick-add
  confirmation sheet, the balance screen with the rolling-digit
  animation, and the widget — these are the three most likely to appear
  in a portfolio demo GIF, so they deserve the most scrutiny.
- **Motion should feel considered, not decorative** — verify the
  existing `bounceClick` and rolling-digit animation actually feel
  intentional (right spring stiffness/damping, not too bouncy or too
  stiff) rather than just "there's an animation."
- **Typography hierarchy should be real** — Outfit for headings, Space
  Grotesk for numbers, Plus Jakarta Sans for body is a good pairing on
  paper; confirm the actual type scale (sizes, weights, line heights)
  creates real visual hierarchy rather than everything sitting at similar
  weight.
- **Dark-mode contrast check**: amber-on-charcoal and chartreuse-on-
  charcoal need an actual contrast ratio check, not an eyeball check —
  accent-on-dark combinations are an easy place to fail accessibility
  contrast without it being visually obvious.
- **Consistency across the newly-added cloud UI**: the sign-in screen,
  invite/join-group flow, and any "syncing..." indicators are net-new
  surfaces from Part A — make sure they're designed with the same care
  as the rest of the app, not bolted on as bare functional screens.

### Process for this workstream

1. Claude Code should first walk through the existing app (or its
   Compose source) screen by screen and produce a short written audit
   against the checklist above, before changing anything.
2. Prioritize fixes to the three "screenshot-worthy moments" first, then
   empty/loading/error states, then the smaller consistency items.
3. Treat this as ongoing craftsmanship, not a one-time task — re-run a
   quick version of this audit after Part A's new screens (sign-in,
   invite flow) are built, since those are the newest, least-polished
   surfaces by definition.

---

## PART C — Testing & Quality

- **Unit tests for `DebtSimplifier.kt`** — verify convergence to zero
  balances, verify the transfer count never exceeds n−1 for n people
  (correction from an earlier draft of this PRD: the greedy algorithm
  guarantees this bound, it does **not** guarantee the true minimum
  number of transfers — finding the actual minimum is NP-hard in the
  general case, so tests should assert the bound, not "minimized"), and
  the zero-debt / single-debtor-single-creditor edge cases.
- **Unit tests for ledger balance calculation** in the repository layer —
  the split/settlement netting math is exactly the kind of logic that
  silently breaks during refactors without tests catching it.
- **Sync-specific tests**, added because of Part A: verify the offline
  write queue actually drains once connectivity returns, and that RLS
  denies a read it should deny (test with two distinct test accounts).
- **A basic CI workflow** (GitHub Actions) running `./gradlew test` and
  `./gradlew lint` on push.
- Instrumented/Compose UI tests remain explicitly **not** a priority —
  good ROI stops at unit-testing business logic for a project this size.

## PART D — Data Safety, Documentation

- Local **CSV/JSON export stays**, even though Supabase now serves as the
  real backup — useful as an account-independent export option.
- **README rewrite** aimed at someone evaluating the project: feature
  summary, screenshots/GIF of the quick-add flow, the architecture
  diagram (the existing dossier already has a good one — reuse it,
  updated for the Supabase addition), and a short "why these decisions"
  section (offline-first-plus-cloud, the ledger pattern, Supabase over
  Firebase, registered-users-only sharing) that shows engineering
  reasoning, not just a feature list.
- **Lightweight ADRs** for the decisions worth defending in an interview:
  Supabase vs. Firebase, the ledger/derived-balance pattern, and the
  registered-users-only sharing model.

## Out of Scope (this pass)

- Self-hosting Supabase — use the managed Supabase Cloud offering.
- Adopting PowerSync now — revisit only if hand-rolled sync proves
  persistently painful.
- Non-Google auth providers.
- Supporting friends who won't install the app — every split participant
  must be a registered user.
- Real payment settlement/money movement — the app tracks who owes what,
  it does not move money.
- Full instrumented/UI test suite.
- Any iOS work.

## Suggested Priority Order

1. **Cloud migration foundation** (Part A: auth, schema, sync loop,
   groups/sharing) — this reshapes the data model, so it comes first.
2. **Correctness audit**, re-run after migration since sync introduces
   new failure modes (stuck offline queue, partial sync, unexpected RLS
   denial) beyond whatever existed before.
3. **Testing** (Part C), extended to cover sync/RLS specifically.
4. **UI/UX premium pass** (Part B) — can start in parallel once core
   screens stabilize, but give it real, dedicated attention rather than
   treating it as a quick pass at the end; it's explicitly one of this
   PRD's three named goals, not an afterthought.
5. **Documentation** (Part D) — last, once there's something finished to
   screenshot and explain.

## Skills Installed (current, verified via `/plugin list --enabled`)

- **`material-3`** (`hamen/material-3-skill`) — Material 3 reference for
  Part B's UI polish work; has a built-in `/material-3 audit [path]`
  command, useful directly during that phase.
- **`supabase`** (`supabase@claude-plugins-official`) — the official MCP
  plugin; already proven useful, having driven the actual schema
  creation in Part A's status log above.
- **`chrisbanes-skills`** (`chrisbanes/skills`) — a bundle covering
  Compose state/effects, animations, performance, component design,
  focus navigation, UI testing patterns, and Kotlin
  concurrency/Flow/control-flow/API-design.
- **`android-skills`** (`rcosteira79/android-skills`) — a second bundle
  (21 skills) with real overlap against `chrisbanes-skills` on Compose,
  coroutines/Flow, and Material 3/UX. **Project convention, decided
  here**: where both could apply, `chrisbanes-skills` wins for Compose
  and Kotlin specifically (it's the more narrowly-focused, higher-
  credibility source for that). `android-skills` is used for what it
  uniquely covers: `android-testing` (Part C), `android-gradle-logic` and
  `gradle-build-performance` (build setup, relevant now while adding
  `supabase-kt`), `android-data-layer` (the sync layer's repository
  work), and `kmp-ktor` (supabase-kt runs on Ktor under the hood).
- **`mobile-offline-support`**, **`design-review`**, **`mobile-first-design`**
  (`secondsky/claude-skills`) — sync/offline patterns and Part B's UI
  audit workstream respectively.
- **`oauth-implementation`**, **`session-management`**,
  **`api-authentication`** (same marketplace) — for the sign-in/session
  code in Migration Plan step 2.
- **`test-quality-analysis`** (same marketplace) — for Part C.

## Skills to Add Before Starting the Sign-In/Sync Code

- **`oauth-implementation`**, **`session-management`**, and
  **`api-authentication`** (all from the already-added
  `secondsky/claude-skills` marketplace — no new marketplace needed,
  just `/plugin install <name>@claude-skills`). Genuinely the right time
  for these now, specifically: you're about to write the actual Google
  Sign-In + Supabase session code, which is exactly what these cover.
- **`test-quality-analysis`** (same marketplace) — for Part C's testing
  work (`DebtSimplifier`, ledger math, sync tests) once that phase
  starts; not urgent this session, but worth adding before Part C
  begins.
- **Not worth chasing**: a Gemini-API-specific skill was searched for
  earlier and doesn't exist as a named plugin in the installed
  marketplaces. Not a blocker — the Gemini integration already works
  (built in AI Studio), and Claude Code doesn't need a special skill to
  read and extend code that's already there.
