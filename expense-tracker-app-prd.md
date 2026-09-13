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
- `splits` — id, transaction_id, total_amount, paid_by (profile id).
- `split_shares` — id, split_id, profile_id, share_amount.
- `settlements` — id, from_profile_id, to_profile_id, amount, date, note.
- **RLS policies, in plain terms**: a user can always read/write their
  own `transactions`; a user can read someone else's `transaction` only
  if it has a `group_id` they're a member of via `group_members`;
  `split_shares` and `settlements` are readable by either party
  referenced in the row.

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
  balances, minimized transfer count for known multi-person cases, and
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

## Skills to Use With Claude Code for This Pass

- **`chrisbanes/skills`** (Chris Banes, Android engineer at Google) —
  `compose-state-and-effects`, `compose-animations`, `compose-performance`.
  The strongest available signal for correct Compose state/recomposition
  patterns, which matters since Claude Code is editing existing Compose
  code, not generating from a blank slate.
- **`hamen/material-3-skill`** — keeps the UI/UX pass (Part B) consistent
  with Material 3 conventions rather than drifting into inconsistent
  custom styling.
- **`google-gemini-api`** (from `secondsky/claude-skills`) — current
  Gemini SDK/API patterns for the existing AI ingestion pipeline.
- **The official Supabase plugin** (`supabase@claude-plugins-official`,
  from Anthropic's own marketplace) — bundles an MCP server letting
  Claude Code directly inspect and run migrations against the real
  Supabase project, rather than writing SQL blind.

(Exact install commands and connection steps for all of these are in the
companion setup guide.)
