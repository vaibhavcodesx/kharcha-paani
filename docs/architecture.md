# Kharcha Paani: Architecture Map

Written 2026-11-03 for Antigravity onboarding (Item O3). This document supersedes the historical [original architecture dossier](original-architecture-dossier.md), reflecting the current production architecture across persistence, synchronization, security, AI proxying, and session lifecycles.

---

## 1. High-Level System Architecture

Kharcha Paani is an offline-first native Android application for personal finance and shared group expense tracking in India. It pairs an offline SQLite relational database (Room v10) with Supabase cloud infrastructure (Auth, PostgREST, Realtime, Edge Functions) and Gemini multimodal AI models routed via a secure backend proxy.

```mermaid
flowchart TD
    subgraph Client ["Android Client (com.vaibhav.kharchapaani)"]
        UI["Presentation Layer\n(Jetpack Compose + Material 3)"]
        VM["MainViewModel & Scoped ViewModels"]
        Session["AccountSessionHolder\n(Per-Account Session Lifecycle)"]
        Repo["LedgerRepository / AuthRepository / GeminiRepository"]
        Room["Local Room Database (v10)\n(databases/ledger-uid.db)"]
        SyncEngines["Sync Engines\n(Transaction, Group, LocalLedger)"]
        Worker["WorkManager SyncWorker"]
        Widgets["App Widgets & Notification Helpers"]
    end

    subgraph SupabaseCloud ["Supabase Backend"]
        Auth["Supabase GoTrue Auth\n(Google ID Token Exchange)"]
        Postgres["PostgreSQL Database\n(RLS, Transactions, Groups, Splits)"]
        Realtime["Realtime Broadcast & Publication"]
        Proxy["gemini-proxy Edge Function\n(Secret Rotation & Quota Enforcement)"]
    end

    subgraph GoogleAI ["Google Cloud / Gemini API"]
        Gemini["Gemini 2.5 Flash / 3.8 Flash\n(NLP, Vision OCR, Document Parsing)"]
    end

    UI --> VM
    VM --> Session
    Session --> Room
    VM --> Repo
    Repo --> Room
    Repo --> SyncEngines
    Worker --> SyncEngines
    SyncEngines <-->|PostgREST TLS| Postgres
    Realtime -.->|Doorbell Ring| SyncEngines
    Repo -->|Credential Manager| Auth
    Repo -->|Authenticated Request| Proxy
    Proxy -->|API Secret| Gemini
    Widgets --> Room
```

---

## 2. Presentation Layer & UI Architecture

### 2.1 Technology & Patterns
- **Jetpack Compose (Material 3):** 100% declarative Compose UI. No legacy XML views for screens.
- **Unidirectional Data Flow (UDF):** ViewModels expose state via Kotlin `StateFlow` and `SharedFlow`. UI emits intents back to ViewModel functions.
- **Theme & Design Tokens (`ui/theme/`):**
  - **Color:** Dark-mode exclusive palette: `CharcoalBg` (`#121214`), `CharcoalSurface` (`#18181B`), `CharcoalCard` (`#1E1E22`), `CharcoalElevated` (`#242429`), `WarmAmberPrimary` (`#FF7A3D`), `MoneyIn` (chartreuse/lime), `MoneyOut` (coral), and `OffWhiteText`.
  - **Typography:** Bespoke pairings (`Outfit` for hero titles, `Space Grotesk` for tabular currency figures, `Plus Jakarta Sans` for body copy and chips).
  - **Shapes:** Squircle corners (`KpShapes.small/medium/large/sheet/ticket`).
  - **Indian Currency Formatting:** Rendered using `Money.format()` with Indian numeral grouping (₹1,00,000) and paisa precision. Amounts are never ellipsized; when digits exceed screen width, they wrap or auto-scale cleanly.

### 2.2 Navigation & Screens
A floating glass dock (`KpDock`) coordinates top-level destinations:
- **Home / Activity (`ui/home/`):** Monthly spend hero header, quick-add input bar, day-grouped timeline feed (`DayGroupedFeed`) showing personal and group transactions.
- **Splits (`ui/splits/`):** Peer balance dashboard ("Who owes whom"), friend rows with debt directions, group cards with member color dots, settlement status inbox ("Waiting for you"), and friend history sheets.
- **Insights (`ui/insights/`):** Monthly cash-flow metrics, category breakdown rings, daily spend averages, and month-by-month historical comparison (`MonthPickerSheet`).
- **Sheets & Modals (`ui/components/Sheets.kt`):** Confirmation sheets, manual expense editors, settle-up flows, ask-to-pay message creators, bill photo zoom viewers, group detail inspectors, and statement import reviewers.

---

## 3. Account Session & State Architecture (`ui/session/`)

Kharcha Paani scopes all user data strictly per Google account to prevent data cross-contamination:

```mermaid
sequenceDiagram
    participant OS as Android System
    participant Holder as AccountSessionHolder
    participant Auth as AuthRepository
    participant Leftovers as AccountLeftovers
    participant DB as LedgerDatabases (Room)
    participant VM as MainViewModel

    OS->>Holder: onAccountChanged(userId)
    alt Signed Out (userId == null)
        Holder->>Leftovers: clear(context) (cancel notifications, wipe export/ask caches)
        Holder->>DB: close() (close ledger-uid.db)
        Holder->>VM: clear ViewModelStore
        Holder-->>OS: State = AccountSessionState.None (Render Sign-In)
    else Signed In (userId != null)
        Holder->>DB: open(context, userId) -> opens ledger-uid.db
        Holder->>OS: Schedule periodic SyncWorker
        Holder->>VM: Instantiate account-scoped ViewModels
        Holder-->>OS: State = AccountSessionState.Ready
    end
```

- **`AccountSessionHolder`:** Activity-scoped `AndroidViewModel` observing Supabase/Google authentication state.
- **`AccountSession`:** Contains the open `AppDatabase` connection for `ledger-<uid>.db` and a dedicated `ViewModelStore`. Ending a session disposes of ViewModels before the database connection closes.
- **`AccountLeftovers`:** Clears state external to the Room database on sign-out or account switch:
  - Cancels all active notification tray entries via `NotificationManagerCompat.cancelAll()`.
  - Recursively removes cached CSV export files (`cache/exports`) and generated Ask-to-Pay QR images (`cache/asks`).
- **Data Retention on Sign-Out:** The local SQLite database file `ledger-<uid>.db` remains intact on device storage for offline capability and seamless re-login. It is destroyed only upon an explicit Account Deletion operation (`AccountDeletion.kt`).

---

## 4. Local Relational Persistence (Room v10)

The persistence layer runs SQLite via Room (schema version 10) in Write-Ahead Logging (WAL) mode. Destructive schema fallbacks are prohibited; all schema migrations are versioned with migration tests.

```mermaid
erDiagram
    GroupEntity ||--o{ GroupMemberEntity : "has members"
    GroupEntity ||--o{ TransactionEntity : "contains"
    PersonEntity ||--o{ GroupMemberEntity : "joined as"
    TransactionEntity ||--o| SplitEntity : "split details"
    SplitEntity ||--o{ SplitShareEntity : "has shares"
    PersonEntity ||--o{ SplitShareEntity : "owes / owed"
    PersonEntity ||--o{ SettlementEntity : "from / to"
    TransactionEntity ||--o{ ReceiptPhotoEntity : "bill photo"

    TransactionEntity {
        Long id PK
        String type
        Double amount
        String category
        String paymentMethod
        String merchant
        Long date
        String note
        Long groupId FK
        Boolean isSplit
        SyncState syncState
        String remoteId
        Long updatedAt
        String ownerProfileId
    }

    SplitEntity {
        Long id PK
        Long transactionId FK
        Double totalAmount
        Long paidByPersonId
        String splitType
        String remoteId
    }

    SplitShareEntity {
        Long id PK
        Long splitId FK
        Long personId FK
        Double shareAmount
        Double percentage
        Boolean isGuest
        Long guestPersonId
        String guestName
    }

    SettlementEntity {
        Long id PK
        Long fromPersonId FK
        Long toPersonId FK
        Double amount
        Long date
        String note
        Boolean isIou
        SettlementStatus status
        String remoteId
        Long groupId FK
    }

    GroupEntity {
        Long id PK
        String name
        String description
        String emoji
        String colorHex
        String remoteId
        SyncState syncState
        Long archivedAt
        String createdByProfileId
    }
```

### 4.1 Schema Entities Overview
1. **`TransactionEntity`:** Primary ledger entries (incomes and expenses), carrying categorical metadata, merchant names, local timestamps, `groupId`, `syncState`, and `remoteId`.
2. **`SplitEntity`:** 1:1 split header associated with a transaction, designating `paidByPersonId` (`-1L` = "You", $>0$ = friend) and split calculation mode (`equal`, `exact`, `share`).
3. **`SplitShareEntity`:** 1:N breakdown of participant shares. Supports non-registered friends in shared groups via guest flags (`isGuest`, `guestPersonId`, `guestName`).
4. **`PersonEntity`:** Local contacts and groupmates (name, avatar URI, assigned color hex).
5. **`GroupEntity`:** Local and shared expense groups (name, emoji, color hex, archival timestamp, sync status).
6. **`GroupMemberEntity`:** Join table linking groups with participants.
7. **`SettlementEntity`:** Double-entry record for direct debt settle-ups and IOUs (`PENDING`, `CONFIRMED`, `REJECTED`).
8. **`SubscriptionEntity`:** Recurring fixed expenses evaluated on calendar dates.
9. **`ReceiptPhotoEntity`:** Local file paths of compressed bill photos linked to transactions.
10. **`PaymentAskEntity`:** Audit log of generated and dispatched Ask-to-Pay requests.
11. **`PendingRemoteDeleteEntity`:** Queue of soft-deletes awaiting remote server synchronization.

---

## 5. Synchronization & Offline-First Engine (`data/sync/`)

Kharcha Paani operates offline-first: all transactions are written to the local SQLite database first, then synchronized asynchronously when network connectivity is available.

```mermaid
flowchart LR
    LocalWrite["Local Mutation\n(Room DB)"] -->|onLocalTransactionChange| Sched["SyncScheduler\n(WorkManager)"]
    RealtimeMsg["Supabase Realtime\n(Publication)"] -->|SyncDoorbell| Sched
    Sched --> Worker["SyncWorker"]

    subgraph Passes ["Sync Passes"]
        T_Sync["TransactionSyncEngine\n(Personal Transactions)"]
        G_Sync["GroupSyncEngine\n(Shared Groups & Splits)"]
        L_Sync["LocalLedgerSyncEngine\n(Private Cloud Backup)"]
    end

    Worker --> T_Sync
    Worker --> G_Sync
    Worker --> L_Sync

    T_Sync <-->|PostgREST| SupaDB[(Supabase Postgres)]
    G_Sync <-->|PostgREST| SupaDB
    L_Sync <-->|PostgREST| SupaDB
```

### 5.1 Push-Then-Pull Cycle
1. **Push Phase:**
   - Evaluates rows in state `SyncState.PENDING`.
   - Sends batch upserts to Supabase via PostgREST.
   - Successfully uploaded rows are marked `SyncState.SYNCED`.
   - Permanently rejected rows (e.g. check constraint failures) are isolated and marked `SyncState.FAILED` so they do not block subsequent sync queues.
2. **Pull Phase:**
   - Queries remote changes updated after `lastPulledAt`.
   - Matches rows by immutable `remoteId`.
   - **Conflict Policy:** Local unsynced modifications (`PENDING` / `FAILED`) always take precedence over incoming remote changes.
3. **Delete Propagation:**
   - Deleted items are staged in `pending_remote_deletes`. The sync worker executes remote soft-deletes (`deleted_at = now()`) and clears the local tombstone upon server confirmation.

---

## 6. Authentication & Identity Pipeline (`data/auth/`)

```mermaid
sequenceDiagram
    participant User
    participant App as Android App (AuthRepository)
    participant CM as Google Credential Manager
    participant Supa as Supabase GoTrue

    User->>App: Tap Google Sign-In
    App->>CM: getCredential(GetGoogleIdOption)
    CM-->>App: Google ID Token (Nonce-protected)
    App->>Supa: signInWith(IdToken)
    Supa-->>App: Supabase Session (JWT + Refresh Token)
    App->>App: Cache Session Securely
    App-->>User: Navigate to Ledger
```

- **Google Credential Manager:** Replaces legacy Google Sign-In with modern `CredentialManager` API, protected by cryptographic nonces.
- **Supabase Authentication:** The Google ID token is exchanged for a Supabase session. Offline access is preserved by cached credentials; token refreshes execute silently in the background.
- **Headless Background Authentication (`BackgroundAuth`):** Provides authenticated access to workers, widgets, and broadcast receivers without risking main-thread or UI deadlocks.

---

## 7. Multimodal AI Ingestion Pipeline

All AI requests flow through a secure backend proxy to prevent API key exposure in distributed APKs:

```mermaid
flowchart LR
    Input["Input Source\n(Quick-Add Text / Receipt Photo / Bank Statement PDF)"]
    LocalFallback["Local Heuristic Engine\n(LocalHeuristicParser / LocalStatementParser)"]
    EdgeFunction["Supabase Edge Function\n(gemini-proxy)"]
    GeminiModel["Google Gemini API\n(gemini-2.5-flash / gemini-3.8-flash)"]
    Result["ParsedTransactionResult / StatementParseResult"]

    Input -->|Network Unavailable / Over Quota| LocalFallback
    Input -->|Authenticated TLS| EdgeFunction
    EdgeFunction -->|Secret Key & Daily Quota Check| GeminiModel
    GeminiModel -->|JSON Response| EdgeFunction
    EdgeFunction -->|Validated Data| Result
    LocalFallback -->|Heuristic Data| Result
    Result --> BottomSheet["Reactive Confirmation Sheet\n(MainViewModel)"]
```

### 7.1 Key Ingestion Modes
1. **Quick-Add Natural Language:** Converts unstructured lines (e.g. *"auto 120 with Ankit"*) into typed expenses, categories, and split shares. Runs `LocalHeuristicParser` concurrently on device as an immediate fallback.
2. **Bill Photo Multimodal OCR:** Downsamples camera/gallery bitmaps off the main thread ($\le 2048$px) and extracts merchant titles, line items, taxes, and total amounts.
3. **Bank Statement Parsing:** Ingests PDF statements through `gemini-proxy`, with deterministic offline regex parsing in `LocalStatementParser`.
4. **Daily AI Quotas:** Server-side rate limits (50 quick-adds, 10 bill OCRs, 3 statements, 20 Insights prompts per day) protect the backend while allowing uninterrupted local hand entry.

---

## 8. Mathematical & Algorithmic Foundations

- **Debt Simplification (`DebtSimplifier.kt`):** Executes an in-memory bipartite greedy cash-flow reduction algorithm. Given $N$ group members with arbitrary pairwise balances, it cancels cyclic debts and computes an optimal settlement plan in at most $N-1$ transactions.
- **Paisa-Exact Split Rounding (`SplitRounding.kt`):** Ensures split shares sum to the exact transaction amount to the paisa (0.01 INR). Any leftover fractional paisa is deterministically distributed to the paying party to guarantee zero rounding divergence across clients and server check constraints.
- **Unified Balance Derivation (`balanceEntries()`):** The single source of truth in `LedgerRepository.kt` computing friend balances from `BalanceEntry.Share`, `BalanceEntry.Guest`, and `BalanceEntry.Payment`. Guarantees that the friend history feed matches the headline balance number to the exact paisa. Only **`CONFIRMED`** settlements alter balance values.

---

## 9. Android OS Integration

- **Home-Screen Widgets (`widget/`):** `SplitFlowWidgetProvider` renders Glance/RemoteViews widgets displaying current monthly spend, net balance, and one-tap quick-add and voice action buttons.
- **System Notifications (`util/NotificationHelper.kt`):** Delivers payment confirmation requests and daily spending alerts. Enforces `VISIBILITY_PRIVATE` on lock screens to redact personal financial numbers, and honors user-configurable Quiet Hours (default 10:30 PM - 8:00 AM).
- **Share Target Integration (`MainActivity.kt`):** Accepts inbound Android OS share intents (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) for images, PDFs, and plain text. Rejects non-content URIs and internal FileProvider paths to prevent file traversal vulnerabilities.
- **Data Portability:** Exports full ledger history to RFC-4180 CSV format with formula injection sanitization (protecting against leading `=`, `+`, `-`, `@` characters).
