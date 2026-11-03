# Kharcha Paani: Architecture & Domain Understanding

Written 2026-11-03 for Antigravity onboarding (Item O2). Citations link directly to current source code at `file:line`.

---

## 1. How a Typed "chai 40 with Rohan" Becomes Rows

### 1.1 Input Capture
A typed or spoken quick-add string enters the app via `MainViewModel.parseQuickAdd(rawText)` in [MainViewModel.kt:1010-1035](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1010-L1035).

### 1.2 On-Phone Heuristic Parsing
Before or alongside any network request, `LocalHeuristicParser.parse(rawText, knownFriends)` runs locally in [LocalHeuristicParser.kt:50-138](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/domain/LocalHeuristicParser.kt#L50-L138):
1. **Type Detection** (`:54-61`): Checks against `incomeKeywords`. No match defaults to `"expense"`.
2. **Amount Detection** (`:64`): `AiParse.amountIn(text)` in [LocalHeuristicParser.kt:154-185](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/domain/LocalHeuristicParser.kt#L154-L185) matches numeric tokens, currency prefixes, and shorthand (`k`/`lakh`), extracting `40.0`.
3. **Payment Method** (`:68-74`): Scans `paymentMethods` with whole-word regex `containsWord`. For `"chai 40 with Rohan"`, none matches, returning `""` (the confirmation sheet subsequently applies the user's default payment method).
4. **Category Detection** (`:77-85`): Evaluates word lists in `categories`. `"chai"` matches the Food list (`:27`), assigning category `"Food"`.
5. **Split Friends** (`:87-107`): Checks `knownFriends` using whole-word boundaries. `"Rohan"` matches, producing `splitWithPersonNames = ["Rohan"]`.
6. **Merchant / Title** (`:110-125`): Drops common stop words (`"with"`, `"for"`, etc.) and returns capitalized title `"Chai"`.
7. **Result**: Emits `ParsedTransactionResult` (`:127-137`).

If online, `GeminiRepository.parseText` ([GeminiRepository.kt:92-120](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/GeminiRepository.kt#L92-L120)) queries the `gemini-proxy` Edge Function; if offline, rate-limited, or on failure, the `LocalHeuristicParser` result is used seamlessly.

### 1.3 State Preparation
In `MainViewModel.applyParsedResult` ([MainViewModel.kt:1207-1243](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1207-L1243)):
- Maps fields into reactive StateFlows: `_type`, `_amount` (via `Money.input`), `_merchant`, `_category`, `_paymentMethod`, `_note`, `_date`.
- Resolves participant names to existing person IDs using `SplitTargets.resolve(rawText, parsed.splitWithPersonNames, people.value, rawGroups.value)` (`:1223`).
- Constructs `_shares` list: `-1L` ("You") and Rohan's `PersonEntity.id`, both with `isSelected = true`.
- Calculates equal shares using `recalculateShares()` (`:1236`), which invokes `SplitRounding.splitPaisa` to divide ₹40.00 into ₹20.00 for "You" and ₹20.00 for Rohan.

### 1.4 Database Persistence
When the user taps Save in the confirmation sheet, `MainViewModel.saveTransaction()` ([MainViewModel.kt:1582](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1582)) delegates to `LedgerRepository.saveTransactionWithSplits(...)` ([LedgerRepository.kt:390-414](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L390-L414)):
- Executes inside a Room transaction `db.withTransaction`:
  1. Validates amount with `requireSavable(tx.amount)`.
  2. Verifies group writability via `requireWritableGroup(tx.groupId)`.
  3. Computes initial sync state via `syncStateFor(...)` (`SyncState.PENDING` for personal splits).
  4. Inserts into the `transactions` table via `db.transactionDao().insertTransaction(...)`, returning auto-generated `txId`.
  5. Inserts into the `splits` table via `insertSplit(...)` ([LedgerRepository.kt:522-538](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L522-L538)) with `totalAmount = 40.0`, `paidByPersonId = -1L`, `splitType = "equal"`, returning `splitId`.
  6. Inserts two rows into `split_shares`:
     - `SplitShareEntity(splitId = splitId, personId = -1L, shareAmount = 20.0, isGuest = false)`
     - `SplitShareEntity(splitId = splitId, personId = <rohanId>, shareAmount = 20.0, isGuest = false)`
- Calls `onLocalTransactionChange()` (`:412`), triggering `SyncScheduler.requestSync` and updating home-screen widgets.

---

## 2. How One Friend's Balance is Computed

### 2.1 Single Source of Truth: `balanceEntries()`
Per-friend balances and friend history share a single derivation path in `LedgerRepository.balanceEntries(personId, splits, sharesBySplit, settlements)` ([LedgerRepository.kt:315-342](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L315-L342)).

### 2.2 Balance Contributions (`BalanceEntry`)
Defined in [LedgerRepository.kt:285-312](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L285-L312):
1. **Expense Shares (`BalanceEntry.Share`, `:290-292`)**:
   - If I paid (`split.paidByPersonId == -1L`): They owe me their share plus any guests they covered. `owedToMe = true`, `netChange = +amount`.
   - If they paid (`split.paidByPersonId == personId`): I owe them my share plus any guests I covered. `owedToMe = false`, `netChange = -amount`.
2. **Guests on Group Splits (`BalanceEntry.Guest`, `:295-298`)**:
   - If a friend participated as my guest on a group expense, they owe me their portion regardless of who paid the group bill. `owedToMe = true`, `netChange = +amount`.
3. **Settlements & IOUs (`BalanceEntry.Payment`, `:305-311`)**:
   - **Crucial Rule:** Unconfirmed settlements do not move the balance:
     ```kotlin
     override val netChange: Double get() = when {
         !settlement.isConfirmed -> 0.0
         owedToMe -> settlement.amount
         else -> -settlement.amount
     }
     ```
   - From me to them (`fromPersonId == -1L`, `toPersonId == personId`): `owedToMe = true`, `netChange = +settlement.amount`.
   - From them to me (`fromPersonId == personId`, `toPersonId == -1L`): `owedToMe = false`, `netChange = -settlement.amount`.

### 2.3 Aggregation in `personBalances` Flow
In [LedgerRepository.kt:99-123](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L99-L123):
- `theyOweMe` sums all `entry.netChange` where `entry.owedToMe == true`.
- `iOweThem` sums `-entry.netChange` where `entry.owedToMe == false`.
- `net = theyOweMe - iOweThem`.
- Emits `PersonBalance(person, netBalance = roundPaisa(net), totalTheyOweMe = roundPaisa(theyOweMe), totalIOweThem = roundPaisa(iOweThem))`.
- **Convention:** A positive `netBalance` means they owe me; a negative `netBalance` means I owe them.

---

## 3. What Happens to a Row from Local Save to Server

### 3.1 Initial Tagging & Queueing
1. When saved locally in `LedgerRepository.saveTransactionWithSplits`, `syncStateFor(...)` ([LedgerRepository.kt:400](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L400)) marks eligible transactions as `SyncState.PENDING`. Transactions in local-only groups are marked `SyncState.LOCAL_ONLY` and never leave the device.
2. `onLocalTransactionChange()` calls `SyncScheduler.requestSync(context, userId)` in [SyncScheduler.kt:18-25](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/sync/SyncScheduler.kt#L18-L25), which enqueues a `SyncWorker` task via Android WorkManager.

### 3.2 Sync Execution Loop
`SyncWorker.doWork()` ([SyncWorker.kt:35-85](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/sync/SyncWorker.kt#L35-L85)) runs background sync passes via `TransactionSyncEngine.sync()` ([TransactionSyncEngine.kt:27-33](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/sync/TransactionSyncEngine.kt#L27-L33)):

#### Push Pass (`pushUpserts()`, lines 39-64):
- Reads batches of `PENDING` rows via `TransactionSyncStore.pendingUpserts(PUSH_BATCH_SIZE)` (`:41`).
- Converts `TransactionEntity` into `RemoteTransaction` DTOs with `ownerId`.
- Calls `TransactionRemote.upsert(batch)` (`:55`), issuing PostgREST calls to Supabase.
- **Success:** Calls `store.markSyncedIfUnchanged(id, updatedAt)` (`:56`), advancing status from `PENDING` to `SYNCED`.
- **Permanent Rejection (`RowsRejectedException`):** If a batch fails (e.g., share totals do not equal transaction amount to the paisa), rows are isolated one-by-one via `pushSingle` (`:66-74`). Offending rows are marked `SyncState.FAILED` so they do not block the rest of the queue.
- **Network / Transient Error:** Propagates upward, triggering WorkManager retry with exponential backoff.

#### Pull Pass (`pull()`, lines 105-155):
- Pulls records from Supabase modified since `lastPulledAt`.
- Matches incoming rows by `remoteId`.
- **Conflict Rule:** Client edits take precedence. Pulled rows never overwrite a local row that is currently `PENDING` or `FAILED`, nor do they resurrect rows with pending remote deletes. Otherwise, local Room rows are updated to match server state and marked `SYNCED`.

#### Deletes (`pushDeletes()`, lines 76-89):
- Local deletes stage rows in `pending_remote_deletes` (`Entities.kt:139`).
- Pushes soft-deletes (`deleted_at = now()`) to Supabase. On server confirmation, clears local delete markers.

---

## 4. Which Settlement States Move a Balance

Defined in [Entities.kt:257-260](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L257-L260) and [LedgerRepository.kt:305-311](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L305-L311):

| Settlement Status | `isConfirmed` | Impact on Friend / Group Balance | Meaning |
|---|---|---|---|
| `SettlementStatus.PENDING` | `false` | **0.0 (None)** | Payment recorded by one party, awaiting review/acceptance by the recipient. |
| `SettlementStatus.REJECTED` | `false` | **0.0 (None)** | Payment declined by the recipient. |
| `SettlementStatus.CONFIRMED` | `true` | **Moves balance by full amount** | Acknowledged payment that changes balance. |

IOUs are stored as settlements with `isIou = true` (`Entities.kt:254`) and adhere to the exact same rule: only confirmed IOUs adjust the balance.

---

## 5. How a Shared Group Differs from a Local One

Defined by `GroupEntity.syncState` in [Entities.kt:61-68](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L61-L68):
`val isShared: Boolean get() = syncState != SyncState.LOCAL_ONLY`

| Aspect | Local Group (`syncState == LOCAL_ONLY`) | Shared Group (`syncState != LOCAL_ONLY`) |
|---|---|---|
| **Storage & Sync** | Strictly private to this device; `syncState = LOCAL_ONLY`. Never pushed to Supabase (`LedgerRepository.kt:400`). | Synchronized with Supabase (`groups`, `group_members`, `transactions`, `splits`, `settlements`) via `GroupSyncEngine.kt`. |
| **Membership** | Members are local `PersonEntity` records linked via `GroupMemberEntity` (`personId`). | Members are linked to remote user profiles (`createdByProfileId`, `GroupMembershipRemote.kt`). |
| **Joining & Invites** | No invite capability. Created and managed solely on one phone. | Sharable invite links (`InviteLink.kt`). Members can join, leave, or be removed via `SharedGroupsController.kt`. |
| **Visibility** | Only visible to the local device user. | All active group members see group expenses, splits, and who paid. |
| **Guests** | N/A (all participants are local person records). | Supports non-member participants via guest records (`isGuest`, `guestPersonId`, `guestName` in `Entities.kt:182-185`). |
| **Archival Rules** | Deleted or edited at will locally. | When archived (`isArchived == true`), the group becomes read-only app-wide; mutations throw `ArchivedGroupException` ([LedgerRepository.kt:399](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L399)). |

---

## 6. What Happens at Sign-Out

### 6.1 Authentication & Credential Cleanup
In `AuthRepository.signOut(context)` ([AuthRepository.kt:195-209](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/data/auth/AuthRepository.kt#L195-L209)):
1. Calls `supabase.auth.signOut()` (or `clearSession()` on network/auth errors) to invalidate local Supabase JWT session tokens.
2. Invokes Google `CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())` (`:205`) to prevent Credential Manager from silently re-selecting the previous Google account upon the next login attempt.

### 6.2 Session Holder & Storage Teardown
In `AccountSessionHolder.onAccountChanged(null)` ([AccountSessionHolder.kt:49-65](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/ui/session/AccountSessionHolder.kt#L49-L65)):
1. **Cancels Background Work:** `SyncScheduler.cancelFor(application, activeUserId)` cancels queued periodic WorkManager sync tasks for the account.
2. **Purges External Account Artifacts:** `AccountLeftovers.clear(application)` ([AccountLeftovers.kt:24-30](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/app/src/main/java/com/vaibhav/kharchapaani/ui/session/AccountLeftovers.kt#L24-L30)):
   - Cancels all active Android notifications via `NotificationManagerCompat.cancelAll()`.
   - Recursively deletes temporary shared file caches: `cache/exports` (CSV exports) and `cache/asks` (Ask-to-pay QR codes).
3. **Clears Scoped ViewModels:** `endSession()` destroys the user's `ViewModelStore`, tearing down `MainViewModel` and releasing collectors before the database closes.
4. **Closes SQLite Database:** `LedgerDatabases.close()` (`:63`) safely closes the active Room database connection.
5. **Preserves Ledger File:** The database file (`ledger-<uid>.db`) remains on local storage so offline access or subsequent re-login retains existing local ledger state (only an explicit Account Deletion wipes the database file).
6. **Emits UI State:** Updates `_state.value = AccountSessionState.None`, switching the root UI to the sign-in screen.

---

## 7. What Must Never Happen to Stored IDs

### 7.1 Canonical Stored String IDs
Category names, payment method tags, and transaction types are stored as canonical raw string tokens in SQLite and Supabase:
- **Categories:** `"Food"`, `"Beverages"`, `"Packaged goods"`, `"Self-care"`, `"Transport"`, `"Shopping"`, `"Bills"`, `"Entertainment"`, `"Health"`, `"Rent"`, `"Income (Client)"`, `"Team Payment"`, `"Other"`.
- **Payment Methods:** `"GPay"`, `"Credit Card"`, `"Debit Card"`, `"Bank Transfer"`, `"PhonePe"`, `"Paytm"`, `"Cash"`, `"Card"`.
- **Transaction Types:** `"income"`, `"expense"`.
- **Split Modes:** `"equal"`, `"exact"`, `"share"`.

### 7.2 Strict Rules on Stored IDs
Per [HANDBOOK.md:92-94](file:///z:/V/.The_Future/Vibeeee/Kharcha%20Paani/docs/antigravity/HANDBOOK.md#L92-L94) and database constraints:
1. **Never Translate or Localize Stored Strings:** Stored IDs must never be translated into Hindi or other languages in database rows. Changing `"Food"` to `"खाना"` or `"Transport"` to `"यात्रा"` in a database column breaks SQLite aggregation queries (`MonthAnalytics`), violates Supabase check constraints, breaks cross-device sync with friends whose apps run in different locales, and breaks historical reporting.
2. **Presentation-Layer Only Localization:** Display string resolution must occur strictly in UI composables and display formatting helpers using `res/values/strings.xml` (as established by the X12 string resource refactor).
3. **Never Mutate or Regenerate Entity Primary Keys:** `TransactionEntity.id`, `SplitEntity.id`, etc., are local SQLite autoincrement keys; regenerating them orphans child records (`split_shares`, `receipt_photos`).
4. **Preserve `remoteId` Across Edits:** `remoteId` (UUID) uniquely binds the local row to its Supabase row. Overwriting `remoteId` severs server sync identity, resulting in duplicate records or sync rejection.
5. **Preserve Sentinel IDs:** `-1L` is reserved app-wide for `"Me"` (the current user). It must never be assigned to a contact, friend, or guest.

---

## 8. Observations & Questions

No architectural contradictions found between `docs/antigravity/HANDBOOK.md` and the codebase. All domain rules, balance calculations, sync states, and session lifecycles in current code match the contract.
