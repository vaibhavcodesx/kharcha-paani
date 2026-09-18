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
- 🔎 **Found, not fixed** (correctness audit, priority 2):
  - Deleting a group leaves its transactions with a dangling `groupId`
    (no foreign key).
  - `deletePerson` removes shares but leaves `isSplit = true` on the
    transaction.
  - ~~REPLACE inserts could cascade-delete child rows~~: assessed and
    fixed, see below.
  - The widget duplicates the ledger balance maths instead of using the
    repository.
- ✅ **Two-device verification (2026-09-14, done by the user)**: phone
  and emulator on the same account, changes made on each appeared on the
  other. Sync works end to end.

#### REPLACE-insert cascade risk: assessment and fix (2026-09-14, before step 4)

- **The mechanism**: SQLite's `INSERT OR REPLACE` *deletes* the
  conflicting row before inserting. Room turns foreign keys on
  (`PRAGMA foreign_keys = ON` in the generated `AppDatabase_Impl`), so
  that delete fires `ON DELETE CASCADE`. The three affected parent tables
  are `groups` → `group_members` and `transactions` → `splits` →
  `split_shares`.
- **What triggers it**: a REPLACE insert into one of those tables with a
  primary key that already exists, or (for `transactions`) a `remoteId`
  that already exists.
- **Origin**:
  - All 8 REPLACE inserts in `Daos.kt` date from the AI Studio build.
  - Step 3 added no REPLACE on a parent table: its REPLACE inserts write
    to `pending_remote_deletes` and `sync_meta`, which have no children.
  - Step 3 *did* add a second trigger key, the unique `transactions.remoteId`
    index.
- **Reproduced in real SQLite** (3.45, the exact v3 table definitions,
  foreign keys on):
  - REPLACE with an existing transaction id silently removed its split
    and all 3 shares.
  - REPLACE with an existing `remoteId` swapped in a copy with no split.
  - REPLACE with an existing group id dropped its members from 3 to 0.
  - ABORT and a true `ON CONFLICT DO UPDATE` both left the children
    intact.
- **Could it have affected shipped data? No.** Every call site was
  traced:
  - All inserts build new entities (`id = 0`, auto-generated, with a fresh
    random `remoteId`).
  - Edits use `@Update`.
  - Sync pulls look rows up by `remoteId`, then insert with ABORT or run a
    conditional UPDATE.
- **Severity**:
  - *Before the fix*: latent, since no current code could trigger it.
    But the impact if triggered was high: splits, shares or members lost
    with no error.
  - *Likelihood rose sharply in step 4*: syncing groups, members, splits
    and shares writes rows whose ids already exist locally, and the loss
    would then have propagated to the server and friends' devices.
- ✅ **Fix (PR #6)**:
  - All 8 app-data inserts switched from REPLACE to ABORT, with a
    header comment explaining why.
  - `SyncDao`'s two REPLACE inserts stay, documented as safe: those tables
    have no children, and overwriting is the intent.
  - No schema change.
  - `DuplicateInsertSafetyTest` checks that re-inserting an existing
    transaction, split or group id throws and leaves children intact,
    that a duplicate `remoteId` is rejected, and that a batch import
    containing an existing id rolls back as a whole.
- **Step 4 design input**: Room's `@Upsert` falls back to an update by
  *primary key*, so it isn't a safe way to write synced rows keyed by
  `remoteId`. Sync writes keep the explicit "find by remoteId, then
  conditional update" pattern.

#### Migration Plan step 4: groups, invites, multi-user splits

Design approved on 2026-09-14:
1. Sharing happens only inside groups; a 1:1 split means a two-person
   group.
2. Only the person who added a group expense can edit or delete it.
   *Amended by backlog item 11 (owner, 2026-09-15; migration
   `payer_can_edit_group_expenses`, PR #31): the person who paid can
   edit and delete it too. Only the adder can change who paid or move it
   out of its group, and it always stays owned by the adder.*
3. Either party can record a settlement.
4. Groups are archived rather than deleted; members can leave.
5. Invites are 8-character codes that expire after 7 days, shared as
   `kharchapaani://join/CODE`.
6. Equal splits round to 2 decimals, with the leftover paisa going to the
   payer.
7. Existing local groups and friends stay local-only.

A fourth RLS gap turned up while re-reading the live policies:
transactions could be attached to any group.

- ✅ **PR 1: RLS policies and database functions** (PR #7,
  `supabase/migrations/20260914033124_groups_invites_rls.sql`). Reviewed
  and approved by the user in two rounds, then **applied to live
  2026-09-14**.
  - Fixes all four gaps: joining only via invite code, profiles visible
    only to groupmates, shares visible to group members, group writes
    require membership.
  - Also: members can leave, groups are archived instead of deleted,
    settlements are group-scoped, and splits, shares and settlements may
    only reference members.
  - New `group_invites` table, plus `create_group`, `create_group_invite`,
    `revoke_group_invite`, `preview_invite`, `join_group` and the atomic
    `save_group_expense` (runs as the caller, so RLS still applies).
  - **Review round 1 (2026-09-14): hard deletes closed at the database
    level.**
    - Hard deletes never reach other devices, so step 3's soft-delete
      design was only guaranteed while the app behaved.
    - Found while fixing: `anon` and `authenticated` held DELETE *and*
      TRUNCATE on every public table (Supabase default privileges), and
      TRUNCATE ignores RLS entirely.
    - The fix:
      - Delete policies dropped on transactions, splits, shares and
        settlements.
      - DELETE and TRUNCATE revoked from client roles on all app tables,
        so deletes now fail loudly with `42501`. The only real delete
        left is leaving a group.
      - Shares are soft-deleted instead: `save_group_expense` now
        soft-deletes removed shares, updates kept ones and inserts new
        ones, with the unique index made partial.
      - `settlements` gained `updated_at` / `deleted_at` so they can be
        retracted.
      - A trigger bumps the parent transaction on any split or share
        write, so direct child edits still reach members.
  - **`supabase/tests/rls_groups_test.sql`**: 100 checks with alice, bob,
    carol and mallory. It runs as a single transaction that ends in
    `ROLLBACK`.
    - Run locally on PGlite (PostgreSQL 18.3; live is 17.6), with a
      Supabase-style bootstrap that mirrors the live roles, `auth.uid()`
      and default privileges: **100/100 pass**, and it rolled back
      cleanly.
    - Every expected refusal was checked to fail for the intended reason.
      Hard-delete and truncate checks accept only `42501`.
    - Mutation-checked (16 mutations), each caught:
      - each original gap
      - anon EXECUTE
      - DELETE restored on each of five tables
      - DELETE privilege alone
      - TRUNCATE on transactions and on settlements
      - no parent-bump trigger
      - a strict membership check on removed shares
      - no unique index on active shares
    - The mutations caught two checks that passed for the wrong reason:
      anon EXECUTE was masked by the function's own check, and a truncate
      of `transactions` was blocked by a foreign key rather than the
      privilege. Both checks were rewritten.
    - Includes the exact writes step 3's personal sync sends (upsert,
      soft delete, pull), so the 3 live personal transactions keep syncing.
  - **Live verification**:
    - Applied as version `20260914033124`. Supabase's stored statement
      md5 `d7241d26…` is byte-identical to the reviewed file.
    - `rls_groups_test.sql` run on live (PostgreSQL 17.6): **100/100 pass**
      with the same refusal reasons as locally.
    - Afterwards: 0 test users, profiles, helper tables or functions
      left; the 3 real personal transactions are untouched.
    - Security advisor: only the anticipated warning (`authenticated` can
      execute the 5 definer RPCs) and the existing, irrelevant
      leaked-password warning (Google-only sign-in).
    - Performance advisor: only "unused index" notices (no data yet).
- ✅ **PR 2: group sync columns and the Realtime publication** (PR #8,
  `supabase/migrations/20260914034325_group_sync_columns.sql`, fast-track:
  purely additive).
  - `groups` gained `updated_at` (trigger), `description` and `color_hex`.
    Joining or leaving bumps the group's clock, so membership changes
    reach members' devices.
  - Partial pull indexes on group transactions and settlements.
  - Realtime publishes `transactions`, `settlements` and `groups` only.
    Members, splits and shares are covered by their parent's clock.
  - `supabase/tests/group_sync_columns_test.sql`: 10 checks, rolled back.
  - Live: applied and re-checked (3 columns, 2 triggers, the 3 published
    tables; still 0 groups, and the 3 real transactions untouched). CI:
    66 tests, 0 failures.
- ✅ **PR 3: shared-group data layer and sync engine** (PR #9,
  fast-track: no server changes; the Room migration is purely additive).
  - **Room v4** (`MIGRATION_3_4`, `4.json`, `Migration3To4Test`):
    - people carry a Supabase `profileId`;
    - groups, splits and settlements get backfilled unique `remoteId`s,
      and groups and settlements get sync state;
    - transactions record `ownerProfileId` for groupmates' expenses;
    - tombstones get an `entityType`.
    - Every existing row stays `LOCAL_ONLY` (decision 7).
  - **`SplitRounding`**: shares round to paisa with the leftover on the
    payer (decision 6), so they pass `save_group_expense`'s exact-sum
    check. Shares that don't add up are kept as entered and the expense
    fails to sync, rather than silently changing what someone owes.
  - **`GroupBalances`**: net positions for every member, including debts
    between two groupmates, plus `DebtSimplifier.simplifyPositions`.
    Shared groups' "your balance" now includes settlements.
  - **`SupabaseGroupRemote`**:
    - calls `create_group` and `save_group_expense`;
    - pulls groups with embedded members, and expenses with embedded
      splits (object, array or null all decode);
    - pages settlements.
    - A **403 on a group write is final** (the group is archived or the
      writer left) instead of being retried forever.
  - **`LedgerRepository` rules**:
    - only the adder edits or deletes a group expense;
    - only the two parties retract a settlement;
    - only the creator renames, archives or un-archives a group;
    - shared groups and groupmates can't be deleted locally;
    - membership only comes from the server.
    - Moving an expense out of a shared group retracts it and gives it a
      new remote id, because members only pull their group's rows.
    - Analytics and the streak ignore groupmates' expenses I have no share
      in.
  - **`GroupSyncEngine`**, run by `SyncWorker` after personal sync:
    - Push: groups, then expenses, then settlements, then retractions.
    - Pull: all visible groups with members, removing any this account has
      left; then expenses and settlements by cursor.
    - A newly visible group resets those cursors, so history from before
      joining arrives.
    - Same conflict rules as step 3.
    - "Last synced" is written only after both engines finish.
  - Status counts and Retry now include groups, group expenses and
    settlements.
  - Tests:
    - JVM, run locally: `SplitRoundingTest` (8), `GroupBalancesTest` (3),
      `RemoteGroupsDecodingTest` (3).
    - Robolectric with real Room: `GroupSyncEngineTest` (11) against a fake
      server that enforces the schema's rules, and
      `SharedGroupRepositoryTest` (10).
    - The first CI run had 1 failure, in the migration test's own
      assertion: SQLite returns the REAL 300.0 as "300". It now compares
      numerically.
  - Logged for the correctness audit:
    - `DebtSimplifier` truncates rather than rounds paisa;
    - a pulled expense replaces its local split on every pull, even when
      unchanged;
    - leaving a group discards changes still queued in it.
- ✅ **PR 4: shared group, invite and join UI** (PR #10, fast-track: no
  RLS, auth or server changes).
  - **Create:** "Share with others" in the create dialog. A shared group
    starts with only the creator (names up to 60 characters).
  - **Invite:** from the group sheet, as an 8-character code plus a
    `kharchapaani://join/CODE` link, via Android's share sheet. The invite
    can be turned off. Groups that haven't reached the server yet can't be
    invited to, and the sheet says so.
  - **Join:** by tapped link or the new Join entry on the Splits screen.
    The group is always **previewed first** (name, inviter, member count)
    and nothing is joined until confirmed. One message covers unknown,
    expired and revoked codes.
  - **Archive / restore** (creator only), and **leave**, with a
    confirmation. Leaving removes the group and its history from the
    device.
  - **Expense entry in shared groups:**
    - only members can be picked;
    - equal splits round to paisa with the leftover on the payer;
    - percentage splits become exact proportional amounts (33.3% × 3
      still adds up);
    - groupmates' expenses read "Added by X" and don't open the editor;
    - archived groups take no new expenses;
    - shared groups have no delete button.
  - The group sheet now follows live data, so a sync updates it while
    it's open.
  - `SharedGroupsController` holds the state and uses no Android types.
    `SupabaseGroupMembershipRemote` calls the PR 1 RPCs. `InviteLink`
    parses links and codes.
  - Also fixed:
    - The group sheet's "Add Expense" button had both a `bounceClick` and
      an `onClick` handler, so one tap could add twice.
    - **Compose version skew:** `lifecycle-viewmodel-compose` 2.10, pulled
      in through other AndroidX libraries, needs Compose UI 1.9.0 at
      runtime. So the device and test runtimes ran Compose 1.9.0 while
      BOM 2024.09.00 compiled against 1.7.0.
      - Calls to APIs that 1.9 changed, like `FlowRow`, throw
        `NoSuchMethodError`. The existing Create Group member picker has
        the same call.
      - CI caught this in the new panel's UI tests (2 failures). The BOM
        is now 2025.08.00: Compose 1.9.0, Material3 1.3.2.
  - Tests:
    - JVM: `InviteLinkTest` (3), plus 2 new rounding cases.
    - CI: `SharedGroupsControllerTest` (7) and `SharedGroupUiTest` (4).
- ✅ **PR 5: Realtime doorbell** (PR #11, fast-track: no server changes;
  Realtime delivers only RLS-visible rows from the 3 tables PR 2
  published).
  - While in the foreground and signed in, changes to `transactions`,
    `settlements` or `groups` (or a reconnect that may have missed some)
    schedule a normal sync after a 2-second quiet period.
  - Events carry no data; rows still arrive only through pulls, so a lost
    or duplicated event can't corrupt anything. Failed subscriptions
    retry with backoff (1 s doubling, capped at 60 s).
  - This device's own push echoes back as one extra pull-only pass, which
    writes nothing and can't loop.
  - `SyncDoorbell` (debounce and retry, `SyncDoorbellTest` on the JVM with
    virtual time) and `SupabaseLedgerChanges` (the channel), run with
    `repeatOnLifecycle(STARTED)`. New dependency: `realtime-kt` 3.6.0.
- 🔎 **Step 4 two-device test (2026-09-14)** found two bugs; the follow-up
  audit found more of the same two kinds.
  - ✅ **Settle Up / Record IOU / group-less splits didn't sync** (PR #13).
    - Not a missing sync flag. Both dialogs saved settlements with no
      group, and the server only accepts settlements (and
      `save_group_expense` only accepts splits) inside an active shared
      group containing everyone involved (decision 1).
    - Fix, as the user chose:
      - with a groupmate, the dialogs ask for the shared group
        (preselected when there's one, required when there are several),
        or offer to create one;
      - saving a split with groupmates outside a shared group asks to add
        it to a group, create one, or keep it on this phone;
      - friends without an account stay local as before.
  - ✅ **Archived groups still accepted new expenses in the app** (PR
    #12).
    - The database already refused every write into an archived group:
      4 checks in `rls_groups_test.sql`, and reconfirmed by an audit
      probe.
    - The app now hides archived groups from the picker and refuses writes
      to them (`ArchivedGroupException`).
  - ✅ **Found in the audit: a refused delete stalled sync forever** (PR
    #12). Deleting or retracting in a group archived before the change
    synced got a 403 that wasn't handled. The worker retried endlessly,
    so neither personal nor group sync completed again. Refused deletes
    are now isolated from their batch, dropped, and the row re-pulled.
  - Audit of local writes: every write goes through `LedgerRepository`,
    and each save/edit/delete path matches what the sync engines push.
  - Audit of app-only rules vs RLS (rolled-back probe on the local
    harness). Enforced by the database:
    - editing or deleting others' expenses;
    - writes into archived groups;
    - an ex-member's invites and edits;
    - edits to a split you were removed from.

    **Gaps:**
    - a creator who left could still rename, archive or un-archive the
      group and revoke its invites;
    - invites made by someone who left still admitted people;
    - any owner could null a group expense's `group_id`.

    **Judgement calls, approved by the user:**
    - either party could rewrite a settlement;
    - group names were only length-checked in `create_group`.
- ✅ **RLS fix for the audit gaps** (PR #14,
  `supabase/migrations/20260914142135_leave_rules_and_immutable_records.sql`).
  Reviewed and approved, then applied to live and verified (below).
  - `groups_update_creator` and `revoke_group_invite` require current
    membership.
  - `preview_invite` / `join_group` only accept codes whose creator is
    still a member.
  - Trigger `transactions_keep_group`: a group expense can't change
    `group_id`, except the foreign key's set-null when the service role
    deletes the group.
  - Trigger `settlements_lock_columns`: only `deleted_at` can change after
    insert, with the same group-deletion exception.
  - Constraint `groups_name_length`: 1–60 characters after trimming. The
    one live group name was checked and passes.
  - Both triggers raise `23514` (HTTP 400) rather than `42501`: the app
    marks a 400 as a failed row, while personal sync retries a 403.
  - `supabase/tests/rls_leave_and_immutability_test.sql`: **51/51** on
    PGlite, rolled back cleanly.
    - Every refusal checked for its exact SQLSTATE or message.
    - Existing scripts still pass: `rls_groups_test.sql` 100/100,
      `group_sync_columns_test.sql` 10/10.
    - 11 mutations (each part undone), each caught.
  - **Live verification (2026-09-14)**:
    - Applied as version `20260914142135`. Supabase's stored statement
      md5 `e1ff4c71…` is byte-identical to the reviewed file, which was
      renamed to match.
    - On live (PostgreSQL 17.6), both run as rolled-back transactions:
      - `rls_leave_and_immutability_test.sql` **51/51**, with the same
        refusal codes as locally;
      - `rls_groups_test.sql` **100/100**.
    - Afterwards: 0 test users, helper tables or functions left. Real data
      untouched (2 users, 1 group, 7 transactions of which 4 are group
      expenses); both triggers and the constraint are present.
    - Security advisor unchanged: only the anticipated warning
      (`authenticated` can execute the 5 definer RPCs) and the irrelevant
      leaked-password notice.
    - Performance advisor: only "unused index" notices.
- ✅ **Step 4 complete (2026-09-14).** The two-device retest after PRs
  #12–#14 passed end to end: settle up and IOU in a group, the split
  prompt, archived groups read-only on both phones, and leaving.
- ✅ **Correctness backlog batch** (PRs #15–#17, fast-track, 155 tests on
  CI):
  - **Settlements and IOUs missing from Recent Activity** (reported
    after the retest).
    - Home now merges them with transactions (`ActivityFeed`), newest
      first, under All and Splits, and searchable.
    - Rows read "Bob paid you" / "You lent Bob".
    - Either party can retract one from its menu.
  - **Duplicate click handlers** (the "IOU double-tap" item).
    - Several buttons had their own `onClick` plus `bounceClick`'s
      clickable.
    - Correction to PR #10: Compose sends a tap to only one of two nested
      clickables, so actions never ran twice. The real effects were a
      second button announced by screen readers and a press animation
      separate from the click.
    - `pressBounce(interactionSource)` keeps one handler per control.
  - **Paisa truncation.**
    - `(x * 100).toLong() / 100.0` turned 33.33 into 33.32 and 0.29
      into 0.28, and rounded negative balances toward zero.
    - Balances, group totals, analytics, statement totals and the debt
      simplifier now use `roundPaisa` (half up).
    - The simplifier no longer drops exact one-paisa debts.
  - **Deleting a local group** now detaches its expenses and settlements,
    with their sync state unchanged, instead of leaving a dangling
    `groupId`.
  - **Deleting a friend** collapses splits that no longer involve anyone
    else, keeping my spend the same: my full payment, or the share I had
    when they paid. If none of it was mine, it's removed. Splits another
    friend paid are kept.
  - **The widget** reads `LedgerRepository`'s analytics and balances
    instead of its own copy of the math.
  - **Unchanged pulls:** a pulled group expense or settlement that matches
    the local copy is no longer rewritten. The cursor overlap had been
    replacing splits and shares on every sync.
  - **Leaving a group** now warns how many changes there haven't synced,
    with Sync now / Leave anyway, instead of silently dropping them.
  - Also fixed: a race in `SharedGroupsControllerTest` (the leave callback
    ran after the "left" message).
  - One CI run failed before building (the runner timed out downloading
    Gradle) and passed on re-run.
- ✅ **Step 5 closed (2026-09-14):** no local data to migrate. The
  "convert a local group to shared" feature was not added.
- 🔄 **Part B UI/UX pass started (2026-09-14).**
  - **Audit:** `docs/part-b-ui-audit.md`.
  - **Owner decisions:**
    - dark mode only;
    - Liquid Glass via Haze;
    - cut the Insights table (a "filter by category" feature is wanted
      later);
    - custom-drawn icons.
  - ✅ **Foundations (PR #19, 165 tests on CI):**
    - squircle shapes, spacing scale, type scale, contrast fix;
    - glass material and brand gradient;
    - `Money.format`: paisa shown, Indian grouping;
    - odometer balance;
    - read-only rows can't be swiped.
    - Found by the new test: one swipe deleted the same expense twice
      (`confirmValueChange` runs more than once), now fixed.
  - ✅ **App shell (PR #20):**
    - floating glass dock with a separate gradient add button (replaces
      the NavigationBar and the FAB that covered the last row);
    - custom duotone nav and category icons;
    - directional tab motion;
    - shared screen header.
    - A Robolectric render capture saves `screens/dock.png` in the CI
      test results for visual review.
    - M3 audit of the shell:
      - text contrast 6.3–14:1;
      - tabs are `Role.Tab` in a selectable group with touch targets of
        at least 50dp;
      - the dock is capped at 560dp wide for tablets.
    - Still open: content lists also stretch on wide screens (Part B
      later).
  - ✅ **Home and quick-add (PR #21, 180 tests on CI):**
    - **Home header:** the balance is the header, in the brand gradient,
      replacing the title row, tagline, streak badge and IOU icon.
    - **Month strip:** this month's spend and income.
    - **Capture:** one input (type or mic) with Scan bill / Statement /
      IOU underneath. The demo preset chips are gone.
    - **Day timeline:**
      - sticky day headers with each day's spend (my share);
      - rows joined into one panel per day;
      - the ⋮ menu that duplicated swipe is replaced by TalkBack
        Edit/Delete actions.
    - **Quick-add sheet:**
      - amount first;
      - details as rows that open their choices in place;
      - a floating Liquid Glass save bar that says "Add an amount" when
        disabled;
      - loading rows instead of a spinner.
    - `ChoiceChip` and `SegmentedControl` are shared for the remaining
      sheets.
    - **Checked on the CI renders** (`home.png`, `confirm_sheet.png`,
      `dock.png`). Real blur still needs a look on an Android 12+ phone.
  - ✅ **Polish from render review (PR #22):**
    - every sheet has a visible grab bar (3.3:1, was 1.3:1) with
      Material's TalkBack label, in place of a close X (owner request);
    - Home's month figures are no longer a card;
    - settlement rows open their menu on tap, so amounts align;
    - the quick-add header stops repeating "expense".
  - ✅ **Splits & Trips (PR #23, 190 tests on CI):**
    - **Splits screen:**
      - "who owes whom" summary;
      - Friends | Groups control;
      - compact friend rows with one Settle button;
      - trip-ticket group cards.
    - **Settle up, IOU, add friend and new group are sheets** on shared
      sheet components (`KpModalSheet`, `SheetScaffold`,
      `GlassActionBar`), with disabled actions that say what's missing.
    - **Bug fixed:** typing a decimal point in an exact split share
      dropped it, so ₹12.50 was saved as ₹125. It was proven by a test
      that failed on CI first (the field held "125"), then fixed.
    - **IOU** no longer preselects the first friend when there's a choice.
  - 🔎 **Deferred (triaged, no decision needed):**
    - the group and person detail sheets aren't restyled;
    - the delete-friend and simplify-debts dialogs aren't sheets yet;
    - a field scrolled into view by TalkBack or the keyboard can stop
      behind a floating action bar (states batch);
    - content stretches on wide screens.
  - 🔎 **Machine note (2026-09-15):** a local Gradle build exhausted RAM
    and crashed the laptop mid-session; no work was lost.
    `~/.gradle/gradle.properties` now caps the heap at 2 GB, uses 2
    workers and runs no daemon, and every build is checked for leftover
    Java processes.
  - ✅ **Insights and widget (PR #24, 198 tests on CI):**
    - Insights is one page:
      - spent vs came in;
      - ranked category bars in one amber hue (a donut can't compare
        close values; per-category pastels only repeated the label);
      - two stat tiles;
      - the AI review on request.
    - The Notion table is cut.
    - **Bug fixed:** a category's entry list showed every month's
      transactions under a this-month chart.
    - **Bug fixed:** the widget's Speak button opened the app without
      starting voice capture.
    - **Widget rebuilt:** app mark and name instead of "⚡ SplitFlow",
      theme colours, the app's own icons, 48dp buttons. Placed widgets
      keep working (same provider class).
    - Figures shown side by side now step down in size together (on
      Insights and Splits).
  - ✅ **States, messages and remaining sheets (PRs #25–#26, 212 tests
    on CI). Part B is complete.**
    - **Bug fixed:** a statement that failed to parse showed 8 fake
      sample transactions; it now says it couldn't read the file.
    - Toasts replaced by glass snackbars above the dock; the avatar
      carries a sync badge (failed / syncing / waiting).
    - Group detail, person history, join group, statement import,
      delete friend and simplify debts rebuilt on the shared sheets.
    - **Bug fixed:** deleting a groupmate was a silent no-op; the row
      no longer offers it.
    - Fields scrolled into view stop clear of a sheet's action bar; tab
      content caps at a readable width on wide screens.
- 📌 **Backlog (owner, 2026-09-14): "filter by category".** The
  Notion-style Insights table was cut in Part B, but the owner wants a
  way to see entries for one category later. Not scheduled.

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

## Feature & Fix Backlog (owner, 2026-09-15)

Recorded here as the permanent record before work starts. It is
sequenced after Part B's last batch.

**Cross-cutting rule (not optional):**
- nothing merges that breaks an existing screen, existing sync or
  existing tests;
- every page has to work end to end, not just look right in a screenshot;
- anything else worth fixing found along the way is brought to the
  owner, not silently scoped out.

**Reporting:** checkpoints at meaningful points, not per item.

### Group A — straightforward fixes (fast-track, no design proposal)

1. **Swipe-to-delete triggers accidentally while scrolling, with no undo.**
   - Raise the swipe distance threshold.
   - Add Undo (a snackbar after delete). Say so if a confirm dialog is
     actually better.
2. **The quick-add sheet dismisses too easily.** A small downward scroll
   closes it and loses the entry. Ask before discarding unsaved input.
3. **CSV export only seems to cover the last 10–20 expenses.** Let the
   owner pick a month or a custom date range, and export everything in it.
4. **Verify that Insights/spending views filter correctly by month.**
   - Seed transactions across several months.
   - Confirm the month picker shows the right data for each, with tests,
     not by eyeballing.
5. **Group activity doesn't show who paid when someone else added the
   expense.** The payer data already exists (balances use it).
   - Show both when they differ, e.g. "Added by Priya · Paid by Rohan".
6. **Profile entry point.**
   - An avatar/name area opens a profile screen, with settings reachable
     from there.
   - Edit display name and profile photo.
   - The photo needs Supabase Storage: check whether the existing setup
     can be reused or needs extending.
   - Storage access rules are RLS on `storage.objects`, so any policy
     change is a hard stop for review.
   - This screen is also the home for items 7 and 9 and the settings
     parts of items 2 and 3.

### Group B — short design proposal first (like the sync/RLS designs)

7. **Editable expense categories and payment methods**, managed in
   settings: defaults plus your own.
   - Propose local-only vs synced, with the real tradeoff.
   - A custom category on a group expense has to display on groupmates'
     devices.
8. **Subscriptions:** recurring fixed-amount expenses (name, amount,
   frequency, payment method) that count into monthly spending without
   re-entry.
   - Propose: materialise a real transaction each period (background
     job), or compute projected entries on the fly?
   - What happens to past months when one is added retroactively or
     cancelled?
   - **Scope note (owner, 2026-09-15):** the earlier "no recurring
     payments" decision was scoped to **irregular person-to-person
     payments between teammates** (settlements, IOUs, splits). It does
     **not** cover **fixed-schedule subscriptions** (a fixed amount on a
     fixed cadence, e.g. Netflix monthly). They are a different concept,
     so there is no conflict: person-to-person payments stay one-off;
     subscriptions are allowed.
9. **UPI ID and QR code in settings**, so a friend settling up can scan
   or copy it.
   - Use the real deep-link format
     (`upi://pay?pa=…&pn=…&cu=INR`) so GPay/PhonePe/Paytm open it.
10. **Better group screen.**
    - The owner's example: a pie of each member's share of group
      spending.
    - Audit what else earns its place day to day (trends, biggest
      spender, per-group categories, …).
    - Bring a short list of proposals before building.

### Group C — hard stop: RLS change, owner review before live

11. **Let the payer of a group expense edit and delete it too**, not only
    the person who added it.
    - This reverses step 4's design decision 2 ("Only the person who
      added a group expense can edit or delete it").
    - Write the RLS change, test it locally, and bring the PR for review
      before applying to live, as with every RLS change.

### Owner decisions (2026-09-15)

- **6. Profile:** display name editing now (fast-track). No profile
  photo: an emoji avatar instead, which needs no Storage bucket or
  `storage.objects` policies.
- **7. Categories and payment methods:** approved as proposed. The
  category name is stored on the expense itself; lists are not synced
  per person. The statement importer's mismatched categories
  (Groceries, Investment, Income (Salary)) are fixed in the same change.
- **8. Subscriptions:** approved as proposed.
  - Real entries are created by a background job or on app open, never
    future-dated.
  - Backdating asks first.
  - Cancelling stops future entries and keeps past ones.
  - See the scope note above: no conflict with "no recurring payments".
- **9. UPI ID and QR:** approved, local-only, no sync.
- **10. Group screen:** all six approved:
  - donut of member shares;
  - who owes whom;
  - category spending;
  - group CSV export;
  - per-row settle-up;
  - archived banner.
- **11. Payer edit/delete:** go ahead; RLS PR for review before live.
- **Audit (FileProvider paths):** narrow to what exports need, in its
  own small PR.

### Backlog status

- ✅ **5. Who added vs who paid** (PR #26): "Added by Priya · Paid by
  Rohan" on group expenses when they differ.
- ✅ **1. Swipe-to-delete while scrolling** (PR #27, 232 tests on CI).
  Root cause: M3's swipe commits after 56dp *or any flick over
  125dp/s*; a row now needs ~45% of its width. Undo snackbar instead of
  a confirm dialog (frequent, reversible action); the ledger delete
  waits for the snackbar to go. **Bug found and fixed:** a row brought
  back by Undo was deleted a second time (saved swipe state); both
  proven by tests red on CI first.
- ✅ **2. Quick-add sheet discard prompt** (PR #27): swipe, tap outside
  or Back with unsaved input asks Discard / Keep editing.
- ✅ **4. Insights by month** (PR #28, 251 tests on CI). There was no
  month picker; figures were hard-wired to the current month. Added a
  month switcher; analytics extracted to `MonthAnalytics.of(month)` and
  tested with entries across months (midnight edges, splits) in pure
  JVM, real Room and Compose UI tests.
- ✅ **3. CSV export by range** (PR #29). Root cause of "last 10-20":
  the CSV was shared as text, which share targets truncate; now a .csv
  file. Also fixed: junk banner rows, `1.0E7` amounts, missing quoting,
  formula injection, no BOM. Month / dates / everything picker.
- ✅ **6. Profile** (PR #32, 283 tests on CI).
  - Display name editing.
  - Emoji avatar instead of a photo (additive `profiles.avatar_emoji`,
    applied to live 2026-09-15 as `20260915184031`); groupmates see it
    via group sync (Room v5).
  - Settings: "Ask before discarding an entry" (item 2) and export
    (item 3).
- ✅ **9. UPI ID and QR** (PR #33, 297 tests on CI). Local-only, per
  account. Real `upi://pay` link; the QR is tested by decoding it back.
  Settle up shows a code for the amount when a friend owes you.
- ✅ **7. Categories and payment methods** (PRs #34, #35). One list for
  the pickers, AI prompts and statement importer (Groceries, Investment,
  Income (Salary) are now ordinary defaults, with icons). Hide defaults,
  add your own, per account on this phone; the category name travels on
  the expense, so groupmates see it.
- ✅ **11. Payer edit/delete** (PR #31, 319 tests on CI). Owner approved
  2026-09-18; migration applied to live as `20260918073251`.
  - RLS via `private.can_edit_group_expense`, plus a trigger locking
    `owner_id`. The payer can't name a different payer.
  - Local PGlite: payer 28/28, groups 100/100, leave 51/51, sync 10/10.
  - **Bug found:** the soft delete filtered on `owner_id`, which would
    have silently left a payer's delete on the server.
- ✅ **Audit fix: FileProvider** (PR #30): narrowed to `cache/exports/`.
- ✅ **10. Group screen** (PR #37, 329 tests on CI). All six approved
  parts: a donut of member shares (colour per person), who owes whom
  after the group's settlements, Settle up on any row that includes you,
  the group's spending by category, export this group, and an archived
  banner.
- ✅ **8. Subscriptions** (PR #38, 346 tests on CI). A subscription is a
  rule, stored in Room v6; each charge becomes a real expense when its
  date arrives (on app open), never future-dated, and never twice.
  Backdating asks first; cancelling stops future charges and keeps past
  ones. Monthly on the 31st clamps to short months.
- 🔎 **Audit (not changed):** the server doesn't bound `display_name`
  length (the app caps it at 40). A check constraint would also need
  `handle_new_user` to truncate long Google names, which is auth config.
- 🔎 **Audit (not changed):** the security advisor lists five
  SECURITY DEFINER RPCs callable by signed-in users. They are the
  intended group functions from step 4. Leaked-password protection is
  off, but sign-in is Google-only.

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
