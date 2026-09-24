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

### Status log

Moved out on 2026-09-23. What changed and when is in [`CHANGELOG.md`](CHANGELOG.md); the full
engineering record of the first week (what was verified and how) is archived verbatim in
[`docs/history/prd-status-log-2026-09.md`](docs/history/prd-status-log-2026-09.md). New work
updates the changelog, and this document only when a spec or decision changes.

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
4. From 2026-09-18 this workstream's live queue is
   `design-and-feature-backlog.md` (see "Design & Feature Backlog
   (owner, 2026-09-18) — companion doc" below), with the audit claims in
   its §3 verified against code and the running app first.

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
- **Superseded 2026-09-18:** the Home hero metric decision from batch 3
  (net debt balance in the header) is reversed — see decision 12 in
  "Design & Feature Backlog (owner, 2026-09-18)" below.
- **Audit (FileProvider paths):** narrow to what exports need, in its
  own small PR.

---

## Design & Feature Backlog (owner, 2026-09-18) — companion doc

A second owner document now sits alongside this PRD:
**`design-and-feature-backlog.md`** in the project root. It is a
**companion to this PRD, not a replacement** — this PRD stays the
governing brief; the backlog is the current working queue for the
design/feature work that follows Part B.

It combines three sources: an external screen-by-screen design audit
(8 app screenshots), the owner's own feedback after real device use,
and six competitor screenshots with specific features to borrow. It
marks each item ✅ DECIDED / 🐛 BUG / 💡 SUGGESTION, and its §6 carries
the merged priority order. Read it in full before starting any item in
it. Its reference images are committed at `docs/design-reference/`.

**Standing caveat, restated by the owner:** §3 of that document (the
design audit) was written from screenshots alone. Every claim in it is
to be cross-checked against the actual code and the running app before
anything is built on it. The results of that cross-check are recorded
in `docs/design-audit-verification.md`; where verification contradicts
the audit, **verification wins** and the backlog item is re-scoped
rather than built as written.

Two further documents came out of this batch:
`docs/ai-prompt-set-design.md` (the §4.3 design, and the decisions taken
on it) and `docs/splitting-with-people-not-on-the-app.md`.

### Owner decisions (2026-09-18)

These two are decided, not proposals. They come before the rest of the
design work in that document.

- **12. Home hero metric is "Spent this month", not "You owe".**
  This **reverses the batch-3 decision** that made the net debt balance
  the Home header metric. Rationale: spending is relevant daily; debt is
  occasional and already has a dedicated home in Splits. Owed / owed-to-
  you demotes to the secondary stat row, where "Came in" currently sits.
  Applies to `ui/home/BalanceHeader.kt` (`BalanceHeader` + `MonthStrip`).

- **13. The bottom dock's glass needs re-examination, not polish.**
  On the real device the owner's words were "looks ugly". This is
  **not filed as a polish item**: the current implementation is to be
  treated as a suspected regression and re-derived, not tweaked. What
  actually renders is a hairline edge-light stroke that runs white at the
  top into amber at the bottom of a 5dp bezel, which on charcoal reads as
  a dashed rust-coloured outline rather than as glass — and the same
  treatment is on the sheet action bars and the snackbar, so the fix is
  to `liquidGlass`/`GlassPanel` in `ui/theme/Glass.kt`, not to the dock
  alone. Re-check it against the Liquid Glass principles already stated
  in Part B (real depth hierarchy, tint that adapts to content, a
  restrained specular highlight) and against how it looked in batch 2, to
  isolate when and why it changed. The ticket-stub perforation motif is
  to be extended to other receipt-like surfaces but **explicitly never to
  the dock**.


### Owner decisions (2026-09-19)

- **14. UPI ID is shared with groupmates, reversing item 9's
  "local-only, no sync".** The bug: "I added my UPI ID, the other person
  settling can't see it." Correct — local-only meant the one person who
  needed it was the only one who could not see it, and settle-up showed a
  QR only when *they* paid *me*.

  The handle rides on `profiles`. **Visibility is inherited, not new:**
  `profiles_select_self_or_groupmate` already limits a profile to its
  owner and to people who share a group with them, which is exactly the
  audience for a payment handle, so no policy was added.

  Two QR codes: the exact amount, and "Any amount" for a part payment or
  a round number. Scanning the wrong one silently pays the wrong sum, so
  the choice is explicit.

  **Security.** A handle now arrives from someone else's phone and is
  interpolated into a `upi://pay` link a payment app acts on — the only
  synced string in this app that could move money. Checked twice:
  `profiles_upi_id_format` server-side, `UpiLink.cleanId` on every read,
  and `buildOrNull` refuses to build from an unvalidated string. The
  payee name and note are percent-encoded, being free text.

  **Applied to live:** `add_upi_id_to_profiles`,
  `fix_upi_id_format_constraint` (the first mirrored the app's
  `{2,256}`, which Postgres rejects — its regex caps repetition at 255;
  both sides are 64 now). Room v7 adds the handle to `people`.

- **15. AI cap: 20 answers a day.** A backstop against a loop or a stuck
  retry rather than a budget; the key is free at this volume.

- **16. Splitting with people who aren't on the app** —
  `docs/splitting-with-people-not-on-the-app.md`. The model was already
  right and was never explained. A local friend needs no account and
  supports splits, IOUs, settling up and local groups; only a *shared*
  group requires every member to have an account, which the app already
  enforces rather than failing halfway. The one real gap — merging a
  local friend into their account once they join — is deliberately not
  built, because it means rewriting every share and settlement naming
  either of them, and getting that wrong silently corrupts a ledger.

## Design & Bug Backlog Round 2 (owner, 2026-09-19) — companion doc

`design-and-feature-backlog-round2.md`, with the source screenshots under
`docs/design-reference/2026-09-1 11;16 AM Post_Changes SS/`. Scores the
previous round and reports what the build actually did on a real phone.

Its two headline items were both **regressions or non-functioning features
reported as fixed**, which is the thing to take from this round: the previous
session verified the UPI work with a synthetic RLS test and the AI work with
unit tests, and both passed while the features did not work. Neither test was
wrong; both tested the wrong layer. See `docs/round-2-investigation.md` for
what each cause turned out to be.

### Decisions taken (2026-09-19, round 2)

- **17. Every UPI delivery method, not one.** Alongside the two QR codes, the
  same `upi://pay` link is a button that hands off to whichever UPI app the
  payer has. A QR needs a second device or a screenshot; the phone holding the
  debt can just open the app. Decided independently of the bug fix, and worth
  it on its own.

- **18. A person's colour is a property of the set, not of the row.** Colours
  were assigned once, at insert, against nobody, so two people could hold the
  same one for ever. They are now respaced across everyone on the phone —
  first holder keeps it, a duplicate moves — which is what makes "the same
  colour everywhere a person appears" a guarantee rather than a hope.

- **19. Each AI question owns its own result.** Three independent requests
  shared one answer and one error flag. `AskBoard` keys both by question,
  which also means an answer already fetched is shown again for free instead
  of costing another of the day's asks.

- **20. Payment method is an icon, not a word.** Reference-led (§8), matched
  on what a name contains rather than a fixed list, because categories and
  payment methods are user-extendable.

- **21. Contrast is measured, not judged.** `SurfaceContrastTest` (containers,
  in L\*) and `TextContrastTest` (text, in WCAG ratio). The Ghost tier had
  been invisible on cards since it was written, and no amount of looking at
  screenshots had caught it.

### Examined and deliberately not changed

- **§10.3, bottom clipping.** What reproduces is the floating dock passing
  over content mid-scroll, which is what a floating dock does. Every list's
  `contentPadding` already clears the dock at the end of the list, which is
  the case that matters.
- **§10.4, the bolt.** The Bills icon and the owner's chosen ⚡ avatar really
  do look alike on the Insights screen. Both available fixes cost more than
  the problem: dropping ⚡ from the avatar list silently changes an avatar
  already chosen (`cleanEmoji` would reject it and fall back to initials), and
  a bolt is the clearest glyph available for a utility bill. **Owner's call
  which to keep** — this is a preference, not a defect.
- **§10.9, the wordmark.** Already shipped, at the top of the account sheet,
  above where the v3 screenshot started.

### Backlog: move Gemini behind a Supabase Edge Function (2026-09-19)

**Not built. Logged here rather than done, because it is too large to bundle
into a distribution-prep pass.**

The Gemini key is compiled into `BuildConfig` and therefore into every APK that
carries it. Anyone with the file can extract it. The distribution pass added the
two mitigations that are available client-side — an Android application
restriction on the key (package + signing SHA-1, with `AppIdentity` sending the
`X-Android-Package` / `X-Android-Cert` headers that make it enforceable) and a
quota ceiling — plus `-PomitAiKey`, which ships a build with no key at all.

None of those stop a determined person: Google's own documentation calls
bypassing the app restriction "straightforward", because the headers are just
headers. The only fix that actually holds is for the key never to reach the
client.

**Shape of the work:** a Supabase Edge Function holding the key, called by the
app with its existing Supabase session as auth; per-user rate limiting there
(which would also replace `AI_DAILY_CAP`, currently enforced only on-device and
so trivially resettable); and rewriting the five call sites in
`GeminiRepository` to point at it. Statement scanning is the awkward one — it
posts a PDF of up to 14 MB, so either the function streams it or the app
uploads to Storage first and passes a reference.

**Do it when** the app goes past people you know, or the key gets abused,
whichever is first.

## BACKLOG — in progress, blocked on review

### Local-friend splits and IOUs in the cloud

**Status (2026-09-23): client half started; the server half needs a new migration, which is
staged and waiting for review.** Full case in
[`docs/local-friend-sync-v2.md`](docs/local-friend-sync-v2.md).

The 2026-09-20 migration (`local_name` on `split_shares`) turned out to cover only a *group*
expense: `splits_insert` requires an active group and `paid_by` a member, so the common case,
a personal expense split with a friend who has no account, still has nowhere to go on the
server. `supabase/staged/20260923000000_owner_private_local_ledger.sql` adds owner-private
`local_splits` and `local_settlements` tables and an atomic `save_local_split` function
(28/28 on the local harness). Nothing is applied.

**Guardrail (owner, 2026-09-23):** only splits and IOUs recorded after the feature is switched
on sync. History already on a phone stays local unless the owner opts in to a separate,
explicit upload, designed in the doc above but not built.

### Still open

- **Filter by category** (owner, 2026-09-14). The Notion-style Insights table was cut in
  Part B, but the owner wants a way to see one category's entries. Not scheduled.
- **`display_name` has no server-side length bound** (the app caps it at 40). A check
  constraint also needs `handle_new_user` to truncate long Google names, which is auth config.

- **Merging a local friend into a joined account** — its own design pass,
  migration and balance-equality tests.
- **`minSdk` 24 → 26**, which would give Android 7.x the bundled fonts'
  weight axis. A product call.
- **§10.5, repeated food icons** and **§10.6, the month switcher's pattern** —
  both real, both cosmetic, neither started.
- **The ⚡ collision above**, pending the owner's preference.


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
5. **Design & feature backlog** (`design-and-feature-backlog.md`) — the
   2026-09-15 backlog is complete; its 2026-09-18 successor runs in that
   document's §6 order, bugs first, then the two owner decisions, then
   the audit quick wins, then the Insights makeover.
6. **Documentation** (Part D) — last, once there's something finished to
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
