Here is the complete, comprehensive technical and architectural dossier for Kharcha Paani, structured specifically for senior engineers, tech leads, and system architects.
Kharcha Paani — System Architecture & Technical Specifications
Kharcha Paani is an offline-first personal financial manager and group expense ledger built in Kotlin and Jetpack Compose. It combines local relational persistence with Gemini-powered multimodal ingestion pipelines (NLP speech/text, OCR receipt vision, and PDF bank statement parsing) and debt-simplification algorithms.
1. System Architecture & High-Level Design
code
Code
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│  - Jetpack Compose (Material 3) + M3 Navigation                        │
│  - Typography Engine (Outfit + Space Grotesk + Plus Jakarta Sans)       │
│  - Rolling Digit Counter Engine & Bouncy Physics Touch Modifiers        │
│  - Screens: Activity Feed, Splits & Trips Ledger, Monthly Insights     │
│  - Reactive UI State: StateFlow<UiState> with combine & stateIn        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                           VIEWMODEL LAYER                              │
│  - MainViewModel (AndroidViewModel scope)                              │
│  - Flow coordination, state reconciliation, async intent handling      │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼────────────────┐   ┌───────────▼───────────────────┐
│     LOCAL PERSISTENCE / DOMAIN     │   │      AI INGESTION PIPELINE    │
│  - Room 2.6.1 + SQLite (WAL mode)  │   │  - Gemini REST client         │
│  - 5 Normalized Entity tables      │   │  - Model: gemini-2.5-flash    │
│  - DebtSimplifier (Greedy Min-Flow)│   │  - Structured JSON Schema     │
│  - Local regex fallback engine     │   │  - Multimodal Vision + PDFs   │
└───────────────────┬────────────────┘   └───────────┬───────────────────┘
                    │                                │
┌───────────────────▼────────────────────────────────▼───────────────────┐
│                       SYSTEM INTEGRATION LAYER                         │
│  - Android App Widgets (AppWidgetProvider + RemoteViews)               │
│  - Notification Engine (POST_NOTIFICATIONS channels + BigTextStyle)   │
│  - Share Intent Target (ACTION_SEND for images, PDFs, raw text)        │
└────────────────────────────────────────────────────────────────────────┘
2. Database Architecture & Relational Data Model (Room + SQLite)
The local database runs SQLite via Room with 5 normalized entities connected through relational joins and cascading foreign keys:
code
Code
┌────────────────────────┐         ┌────────────────────────┐
│      GroupEntity       │         │      PersonEntity      │
├────────────────────────┤         ├────────────────────────┤
│ PK id: Long            │         │ PK id: Long            │
│    name: String        │         │    name: String        │
│    description: String │         │    colorHex: String    │
│    emoji: String       │         │    avatarUri: String?  │
│    colorHex: String    │         └───────────┬────────────┘
│    createdAt: Long     │                     │
└───────────┬────────────┘                     │
            │                                  │
            │ 1:N                              │ 1:N
            ▼                                  ▼
┌────────────────────────┐         ┌────────────────────────┐
│   TransactionEntity    │ 1:1     │    SplitShareEntity    │
├────────────────────────┤◄────────┤────────────────────────┤
│ PK id: Long            │ Split   │ PK id: Long            │
│    type: expense|income│ Entity  │ FK splitId: Long (CAS) │
│    amount: Double      │         │ FK personId: Long      │
│    category: String    │         │    shareAmount: Double │
│    merchant: String    │         │    percentage: Double  │
│    paymentMethod: Str  │         └────────────────────────┘
│    date: Long (Epoch)  │
│    note: String        │         ┌────────────────────────┐
│ FK groupId: Long? (SET)│         │    SettlementEntity    │
│    isSplit: Boolean    │         ├────────────────────────┤
│    isIou: Boolean      │         │ PK id: Long            │
│    createdAt: Long     │         │ FK fromPersonId: Long  │
└────────────────────────┘         │ FK toPersonId: Long    │
                                   │    amount: Double      │
                                   │    date: Long          │
                                   │    note: String        │
                                   │    isIou: Boolean      │
                                   └────────────────────────┘
Relational Schema Breakdown:
transactions:
Stores core ledger lines with categorical metadata, payment methods (Cash, UPI, Credit Card), and timestamps.
Includes nullable groupId targeting groups with ON DELETE SET NULL.
splits & split_shares:
SplitEntity maps 1:1 to a TransactionEntity with paidByPersonId (-1 denotes "You/Self", 
 denotes a friend).
SplitShareEntity references splitId with ON DELETE CASCADE. Stores split fractions, explicit amounts, and exact percentages for each participant.
people & group_members:
Stores friends and group membership associations with custom avatar hex codes.
settlements:
Dedicated double-entry audit log for debt payments and IOUs (fromPersonId 
 toPersonId).
3. Mathematical & Algorithmic Core
A. Debt Simplification Algorithm (DebtSimplifier.kt)
Group ledger debt networks can generate circular, redundant debts (e.g., A owes B, B owes C, C owes A). The app executes an in-memory Greedy Minimum Cash-Flow Reduction Algorithm:
Partitioning: Split non-zero net balances into two lists:
Creditors (Net 
): People owed money.
Debtors (Net 
): People who owe money.
Greedy Matching:
Pick the maximal debtor (
) with debt 
 and maximal creditor (
) with credit 
.
Settle amount 
.
Emit a single directed transaction: 
.
Update 
 and 
.
Repeat until all balances converge to 
.
Time Complexity: 
 where 
 people. It guarantees reducing 
 edges down to at most 
 optimal settlement transfers.
B. Relational Balance Aggregation (LedgerRepository.kt)
The repository computes real-time net balances for each person across transactions, multi-party splits, and direct settlements using reactive Kotlin Flows:
code
Kotlin
// Net balance for Person P relative to "You" (-1L):
val netBalance = (Amount P owes You for your paid splits) 
               - (Amount You owe P for P's paid splits) 
               + (Settlements/IOUs paid by You to P) 
               - (Settlements/IOUs paid by P to You)
4. Ingestion Pipelines & Multimodal AI
All ingestion pipelines feed into a single unified output schema (ParsedTransactionResult) before triggering the reactive confirmation sheet:
code
Code
[Raw Ingestion Source]
 ├── Voice / Messy Text   ──► Gemini 2.5 Flash NLP
 ├── Receipt Image / Photo ──► Gemini 2.5 Flash Multimodal OCR
 └── PDF Bank Statement   ──► Gemini PDF Document Vision
                                    │
                                    ▼
                     [ParsedTransactionResult (JSON)]
                     ├── amount: Double
                     ├── type: "expense" | "income"
                     ├── merchant: String
                     ├── category: String
                     ├── paymentMethod: String
                     ├── date: Long (Epoch ms)
                     ├── isSplit: Boolean
                     └── splitWithPersonNames: List<String>
                                    │
                                    ▼
                      [Reactive Bottom Sheet] ──► Room DB
1. NLP Messy Text & Voice Ingestion
Engine: Gemini 2.5 Flash (generateContent).
Prompt Logic: Injected with known friend names from Room DB. Resolves Indian colloquialisms (e.g., "Chai 40 gpay and split with Rohit" or "Salary received 85000").
Enforcement: responseMimeType = "application/json" with strict schema fields.
2. Receipt & Payment Screenshot Vision (Multimodal OCR)
Input: Bitmap (software allocated, downsampled to prevent memory pressure).
Processing: Converts to JPEG Base64 and executes visual extraction of merchant banners, line items, taxes, total amounts, and transaction reference numbers.
3. Bank & UPI PDF Statement Analyzer
Input: Direct raw ByteArray of user-selected or shared PDF statements.
Processing: Base64 encoded with application/pdf inline MIME data.
Output: Multi-row StatementParseResult with total debits, total credits, date ranges, and individual transaction rows parsed for batch insertion.
Local Fallback Engine (LocalStatementParser.kt): Built-in deterministic regex parser for standard Indian bank SMS/statement logs that functions completely offline without an active internet connection.
5. UI Engine & Design System Architecture
code
Code
┌─────────────────────────────────────────────────────────────┐
│                       SPLITFLOW THEME                       │
│  - Background: CharcoalBg (#121214)                         │
│  - Surfaces: CharcoalSurface (#18181B) / Elevated (#222226)│
│  - Primary Accent: Warm Amber (#FF7A3D)                     │
│  - Secondary Accent: Chartreuse (#CCFF00)                   │
│  - Numeric Figures: Space Grotesk Monospaced Tabular Fonts │
└─────────────────────────────────────────────────────────────┘
Custom Typography Hierarchy:
Outfit: Headlines, navigation badges, and modal headers.
Space Grotesk: Numerical data, currency indicators, rolling digit animations, and financial breakdown tables.
Plus Jakarta Sans: Body text, category chips, and dialog actions.
Motion Engine (BounceModifier.kt & AnimatedRollingNumber.kt):
Interactive components utilize a custom Compose modifier (bounceClick) powered by spring physics (dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow).
Numerical figures utilize an animated rolling decimal engine that transitions between values over a 750ms easing curve.
Swipe-to-Dismiss Gesture System:
Transaction list items implement SwipeToDismissBox with directional visual targets:
Start-to-End: Triggers instant transaction edit mode (Amber backdrop).
End-to-Start: Triggers transactional deletion with cascading cleanup (Crimson backdrop).
Soft Keyboard & Window Inset Safety:
Interactive bottom sheets handle WindowInsets.ime and WindowInsets.navigationBars with explicit LocalFocusManager.clearFocus() hooks on sheet dismiss and save events to prevent orphaned IME back-dispatcher leaks.
6. Android System Integrations & Capabilities
A. Home Screen App Widget (SplitFlowWidgetProvider.kt)
Built on AppWidgetProvider + RemoteViews.
Queries LedgerRepository on a background thread and renders:
Total pending split balance (formatted in ₹).
Monthly expense total.
Active daily tracking streak.
Deep-link PendingIntent triggers for instant manual entry (ACTION_QUICK_ADD) and voice capture.
B. System Notification Channel (NotificationHelper.kt)
Configured with POST_NOTIFICATIONS runtime permission checks for Android 13+ (API 33+).
Generates BigTextStyle high-priority notifications for monthly spending milestones, top spending category alerts, and group settlement reminders.
C. Android Intent Share Target (MainActivity.kt)
Direct integration with Android OS share sheets (ACTION_SEND):
Images (image/*): Auto-loads GPay/PhonePe screenshots into the Gemini vision OCR pipeline.
PDFs (application/pdf): Opens the statement parsing and batch-import dialog.
Text (text/plain): Captures SMS notifications and WhatsApp payment messages.
D. CSV Data Exporter (LedgerRepository.exportCsv())
Compiles the full local Room database into a standard RFC-4180 compliant CSV stream and dispatches it via ACTION_SEND (text/csv) with chooser intents for external backup and spreadsheet analysis.
7. Security, Privacy & Offline Architecture
Local-First Privacy:
100% of transaction entries, group structures, friend lists, and ledger balances reside on the device in local SQLite tables.
Zero Hardcoded Secrets:
API keys are injected at compile time via BuildConfig.GEMINI_API_KEY (sourced from .env), keeping credentials out of version control and application logs.
Fail-Safe Offline Mode:
If network connectivity is unavailable, manual transaction logging, group splitting, debt simplification, settlement tracking, and local statement parsing remain completely functional.
Summary Feature Matrix
Functional Module	Architectural Component	Core Technology
Relational Storage	Room DB, 5 Entities, Relational Queries	SQLite, Room KSP, WAL Mode
Multimodal Ingestion	Voice, Receipt OCR, PDF Statements	Gemini 2.5 Flash REST Client
Debt Simplification	Greedy Min-Flow Cash Algorithm	Kotlin In-Memory Collections (
)
Reactive UI	Dynamic M3 Dark Theme, Rolling Digits	Jetpack Compose, StateFlow, Coroutines
OS Integrations	Android Widget, Notifications, Share Receiver	AppWidgetProvider, NotificationCompat
Data Portability	Full CSV Statement Export	RFC-4180 CSV Builder + Intent Chooser

this is the tech stack used :
1. Complete Tech Stack & Architecture
Core Platform & Language

Language: Kotlin (100% modern idiomatic Kotlin with Coroutines and Flow).
Architecture: MVVM (Model-View-ViewModel) with an offline-first repository layer and bidirectional reactive streams.
Target OS: Android 8.0 (API 26) up to Android 15 (API 35), supporting predictive back gestures, edge-to-edge system insets, and dynamic permissions.
UI & Design System (Frontend)

Jetpack Compose (Material 3): Fully declarative UI without legacy XML layouts.
Typography Pairing:
Outfit: Display headings, title banners, and trip cards.
Space Grotesk: High-legibility tabular figures for prices, split shares, rolling numbers, and ledger amounts.
Plus Jakarta Sans: Body text, category chips, and dialog actions.
Custom Motion & Animation:
Spring-physics tactile interactions (bounceClick).
Rolling digit animations (AnimatedRollingNumber).
Smooth tab crossfades and swipe-to-dismiss actions.
Themes: Bespoke deep charcoal palette with warm amber (
#FF7A3D) and chartreuse secondary accents (
#CCFF00).
Local Persistence & Business Logic (Backend / Data)

Room Database (SQLite + KSP):
Local persistence for transactions, persons, split shares, group trips, and settlements.
Relational querying (@Relation and @Embedded) for joint ledger histories and multi-member splits.
100% Offline Capability: All transaction records, expense histories, and balance calculations reside directly on your device.
Debt Simplification Engine (DebtSimplifier.kt):
In-memory bipartite matching algorithm to minimize the total number of cash settlement transactions between group members.
Local Regex & PDF Statement Fallback Engine (LocalStatementParser.kt):
Rule-based regex parser capable of parsing standard Indian bank/UPI SMS formats and transaction summaries locally without cloud calls.
Android System Integrations

App Widgets (SplitFlowWidgetProvider.kt):
Android Home Screen Widget for quick-logging expenses, voice dictation, and real-time net split balance monitoring.
System Notifications (NotificationHelper.kt):
Android 13+ POST_NOTIFICATIONS support delivering expandable monthly summaries and high-spending alerts.
Intent Receivers (Sharing Flow):
Direct intent filter handling for shared screenshots, WhatsApp text messages, and PDF bank statements directly from other apps (Paytm, GPay, PhonePe).
AI & Multimodal Intelligence

Google Gemini API (gemini-2.5-flash):
Messy Text & Speech Parsing: Translates raw text like "chai 40 cash and split with rohit" into structured transaction fields.
Receipt / Screenshot OCR: Extracts merchant names, totals, and timestamps from uploaded bill photos and payment confirmations.
Bank & UPI PDF Statement Parsing: Extracts and categorizes multi-row statements.
AI Financial Coach: Generates natural language budget digests and actionable spending insights.
