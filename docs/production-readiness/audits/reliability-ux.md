# Kharcha Paani: reliability and UX audit before launch

Repo `Z:\V\.The_Future\Vibeeee\Kharcha Paani`, branch `main` at `7a1342a`. Read-only audit: no builds, no emulator, no changes.
All paths below are relative to `app/src/main/java/com/vaibhav/kharchapaani/` unless they start with `app/` or `supabase/`.

**Out of scope, not reported:** security/RLS/keys, notification design, Play policy, the launch hang with no internet, and everything in open PRs #61–#76 (R8, Back goes Home, java.time desugaring, statement-import flags, backup preview, bill photos, month rollover, and the rest).

**How I checked.** I read every finding below in the code. Where a finding depends on library behaviour I checked the library itself. Example: supabase-kt 3.6.0's `HttpRequestException` extends `java.io.IOException` (I read the class's constant pool from the cached AAR), so the existing `catch (e: IOException)` blocks *do* catch offline Supabase calls. That is not a finding. I have marked the places that need a device to confirm.

---

## Summary table

| ID | Severity | Area | One line |
|---|---|---|---|
| B1 | **Blocker** | Visual | Status-bar icons are invisible and a white nav-bar scrim shows on every screen when the phone is in light mode |
| H1 | High | Data integrity | No double-submit guard anywhere: double-tap Save, Import, Record settlement, IOU or Create group makes duplicates |
| H2 | High | Offline / ANR feel | The statement "Reading your statement" dialog can't be dismissed and has no Cancel, for up to about 3–6.5 minutes |
| H3 | High | Input validation | Amount fields take raw text: "1,200" silently disables Save, a pasted "1e999" or a 19-digit number crashes, and ₹≥1e10 never syncs |
| H4 | High | Observability | No crash reporting and no non-fatal telemetry. Failures go only to logcat on the user's phone |
| H5 | High | Support / trust | No app version, feedback or support contact, privacy link, or about/licenses anywhere in the app |
| M1 | Medium | Crash risk | Most `viewModelScope.launch` blocks catch only domain exceptions. Any IO/SQLite/FileProvider error kills the app |
| M2 | Medium | Process death | A half-filled add-expense sheet, a statement read and the open sheets are all lost on process death. No `SavedStateHandle` |
| M3 | Medium | Sync UX | A "couldn't sync" row is not marked anywhere in the feed. A failed shared expense looks shared but isn't |
| M4 | Medium | Destructive actions | Retract settlement / delete IOU / delete local group happen in one tap, with no confirm and no undo |
| M5 | Medium | Offline / flaky | Quick-add text parse can spin for about 40 s with no cancel. A late result overwrites an entry being typed |
| M6 | Medium | Data integrity | Importing the same statement twice adds every row twice. There's no duplicate detection |
| M7 | Medium | Accessibility | Fixed-height cards and the dock clip text at large font scale. Amounts ellipsize ("₹1,23,4…") |
| M8 | Medium | Performance | Every write re-runs about 25 full-table Room queries. `currentAsks` multiplies by asked friends. The feed builds on the main thread |
| M9 | Medium | Sync robustness | Sync worker: an unexpected exception means silent `failure()`, and a 401 retries forever. The user sees nothing |
| M10 | Medium | Polish | No splash-screen setup or window background. The first frame is a blank box. The themed (monochrome) icon is a solid disc |
| M11 | Medium | Features | Missing launch-expected features: in-app updates, app lock, edit history for shared expenses, pull-to-refresh / "Sync now" |
| L1–L14 | Low | Various | See the end of the report |

---

## Blocker

### B1. Status-bar icons are invisible in system light mode (every screen)

**Evidence**
- `MainActivity.kt:123` calls `enableEdgeToEdge()` with no arguments. In androidx.activity 1.10.1 the defaults are `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)` for the status bar and `SystemBarStyle.auto(DefaultLightScrim, DefaultDarkScrim)` for the navigation bar. `auto` decides "dark" from the **system** `uiMode`, not from the app.
- The app is dark-only by decision (`ui/theme/Theme.kt:30-38`, background `CharcoalBg #0E0D0C`, `ui/theme/Color.kt:6`).
- Nothing else sets `isAppearanceLightStatusBars`. A grep for `SystemBarStyle|isAppearanceLight|WindowCompat` finds nothing.
- `app/src/main/res/values/themes.xml:4`: the theme is bare `android:Theme.DeviceDefault.NoActionBar`, with no `windowBackground` and no system-bar attributes.

**Failure scenario.** Most Indian phones ship in light mode. On those phones, `enableEdgeToEdge` sets `isAppearanceLightStatusBars = true`, which gives dark clock, battery and notification icons on a #0E0D0C background, so they effectively disappear. With 3-button navigation, the system also draws a translucent white scrim behind the nav bar: a white strip at the bottom of a dark app. This is the first impression on every screen, and the Robolectric screenshot tests can't show it (they don't render system bars).

**Fix**
```kotlin
// MainActivity.onCreate
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
)
```
Also give the theme an explicit dark window: `parent="android:Theme.Material.NoActionBar"` (or keep DeviceDefault) with `android:windowBackground=#0E0D0C`, `android:statusBarColor`/`navigationBarColor` transparent, and `android:windowLightStatusBar=false` (see M10). **Verify on a device in light mode, with both gesture and 3-button navigation.**

---

## High

### H1. No double-submit protection: duplicate expenses, settlements, imports and shared groups

**Evidence.** Every primary action launches a coroutine, and the sheet only closes after the DB write finishes. No button disables itself while that write is in flight.
- Save expense: `ui/components/ConfirmBottomSheet.kt:242-246` (`enabled = value > 0 && splitIssue == null`, `onClick = onSave`) leads to `MainViewModel.saveCurrentTransaction` (`ui/viewmodel/MainViewModel.kt:1210`). It launches at `:1282` and closes the sheet only at `:1303`.
- Import statement: `ui/components/StatementImportDialog.kt:110` leads to `MainViewModel.confirmImportStatementTransactions` (`:910-936`). The dialog is dismissed only after `batchInsertTransactions`.
- Record settlement: `ui/components/SettleUpSheet.kt:84-91` leads to `recordSettlementPayment` (`MainViewModel.kt:1455-1473`), which dismisses at `:1470`.
- IOU: `ui/components/IouSheet.kt:85-89` leads to `recordIou` (`:1483-1501`).
- Create group: `ui/components/CreateGroupSheet.kt:92` leads to `createNewGroup` (`:1374-1396`). For a shared group, `LedgerRepository.createSharedGroup` (`data/repository/LedgerRepository.kt:752`) inserts a PENDING group that sync pushes to the server. Two taps make **two shared groups on the server**.
- Add friend: `ui/components/AddFriendSheet.kt:84`. The second tap hits `DuplicateFriendException` and shows "You already have a friend called X…" right after a successful add.
- `GlassActionBar` (`ui/components/Sheets.kt:228-273`) is a plain `clickable(enabled = enabled)` with no in-flight state. A grep for `isSaving|inFlight|submitted` in `ui/components` finds nothing.

**Failure scenario.** On a mid-range phone, someone taps Save, sees nothing happen for about 100–300 ms (DB write plus recomposition), and taps again: the ₹1,200 dinner is logged twice and the friend's balance is doubled. On statement import, a double tap duplicates the whole statement (possibly hundreds of rows), and the only way to remove them is one swipe per row.

**Fix.** Add one `isSubmitting` flag per action in the ViewModel (a `MutableStateFlow<Boolean>`). Return early if it is set, set it before `launch`, and clear it in `finally`. Pass it to `GlassActionBar` as `enabled = … && !isSubmitting` and show a small spinner in the button while it is set. Closing the sheet *optimistically* (before the write) is also fine for local writes. For `createSharedGroup` and statement import, the guard is mandatory. Test: call `saveCurrentTransaction()` twice back-to-back on a real Room DB and assert one row.

### H2. The statement-reading dialog traps the user for minutes. Back does nothing and there is no Cancel

**Evidence**
- `MainActivity.kt:870-874`: `if (isParsingStatement) StatementParsingLoadingDialog(onDismiss = { viewModel.dismissStatementImportDialog() })`.
- `MainViewModel.dismissStatementImportDialog()` (`:901-904`) clears `_isImportStatementOpen` and `_statementParseResult`, but **not** `_isParsingStatement`. So a Back press or a tap outside calls `onDismiss`, and the dialog stays on screen.
- The read job is never stored or cancelled (`parseBankOrUpiStatement`, `:854-868`).
- Timeouts: `STATEMENT_TIMEOUT_SECONDS = 180` for read and write, plus `callTimeout = timeout + 15` (`data/repository/GeminiRepository.kt:322-327, 641`). A 503 or 429 retries once on the fallback model with the same budget (`:307-317, 628`). On a slow 3G upload of a 10 MB PDF, the user sits behind a modal for up to about 195 s, or about 390 s after a fast 503.
- `StatementParsingLoadingDialog` (`ui/components/StatementImportDialog.kt:274-293`) has no button.

**Failure scenario.** Someone shares an HDFC PDF on a train with a weak connection. A full-screen modal says "Reading your statement" and Back does nothing for 3+ minutes. The app looks frozen, so they force-close it and the read is lost (and Gemini quota is spent).

**Fix.** Keep `statementJob: Job?`. Give the dialog a "Cancel" button, and on cancel or dismiss call `statementJob?.cancel()` and set `_isParsingStatement.value = false` (also inside `dismissStatementImportDialog`). After about 20 s, add a line under the spinner ("Long statements take up to 3 minutes. You can cancel and try a shorter date range."). Optionally run the read in a WorkManager job so it survives backgrounding (see M2).

### H3. Amount input is unvalidated: confusing disabled Save, a paste can crash the app, large values never sync

**Evidence**
- `ui/components/Sheets.kt:326-330`: `BasicTextField(value = amount, onValueChange = onAmountChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))`, with no filter. The same field is used by settle-up (`SettleUpSheet.kt:111-115`), IOU and subscriptions.
- Parsing is `amount.toDoubleOrNull()` (`ConfirmBottomSheet.kt:240`, `SettleUpSheet.kt:78`, `IouSheet.kt:68`, `MainViewModel.kt:1211`). It accepts "1e5", "Infinity" and "NaN", and rejects "1,200".
- The rendering path goes from `Money.format`, through `roundPaisa` (`domain/Money.kt:11`, `BigDecimal.valueOf(rupees)`), to `Money.digits` (`ui/format/Money.kt`, `.movePointRight(2).longValueExact()`).
  - `BigDecimal.valueOf(Infinity)` throws `NumberFormatException`.
  - Above about 9.2e16 rupees (17+ digits), `longValueExact()` throws `ArithmeticException`.
  - Both happen inside composition: the action-bar summary at `ConfirmBottomSheet.kt:247` (`if (value > 0) Money.format(value)`). So the app crashes on paste.
- Server: `amount numeric(12, 2)` (`supabase/migrations/20260913144006_create_core_schema_with_rls.sql:149`), so the maximum is ₹9,999,999,999.99. Anything larger saves locally and then fails sync for good (FAILED, see M3).

**Failure scenarios**
1. Gboard's decimal keypad shows "," on Indian English. Someone types "1,200". The field shows 1,200 but Save stays grey with "Add an amount", and nothing says that the comma is the problem.
2. Someone pastes "1e999" or a long reference number. The app crashes.
3. Someone pastes a 10-digit phone number into the amount: ₹987 crore is logged, Insights is wrecked, and the row fails sync.

**Fix.** Use one shared `AmountInput.sanitize(raw): String`:
- drop grouping commas and spaces
- allow digits and at most one ".", with at most 2 decimals
- cap the length (for example 10 integer digits) and reject anything else.

Apply it in `BigAmountField` and the `SplitEditor` share fields (`ui/components/SplitEditor.kt:207,214`). Make `Money.format` / `roundPaisa` total-safe: `if (!amount.isFinite()) return "₹0"`. Ask for confirmation above a threshold such as ₹10,00,000 ("That's ₹10 lakh. Correct?"). Also block the future-date choice in `pickDateTime` (`ConfirmBottomSheet.kt:523-545`, add `datePicker.maxDate = now`) unless future dates are intended.

### H4. No crash reporting or non-fatal telemetry

**Evidence**
- No Crashlytics, Sentry or Bugsnag dependency (`app/build.gradle.kts` dependencies). A grep for `UncaughtExceptionHandler|CoroutineExceptionHandler|crashlytics|Sentry` returns nothing.
- Firebase BOM, `firebase-ai` and the google-services plugin are present, but there is no `google-services.json`, and nothing in `src/main` uses Firebase.
- Failures go to `Log.w/e` only, at 54 call sites. Examples: `GeminiRepository.failedCall` (`:351-354`), `SyncWorker.syncIfSignedIn` (`data/sync/SyncWorker.kt:74-83`), `AccountSessionHolder` (`ui/session/AccountSessionHolder.kt:57`).

**Failure scenario.** After launch, 1,000 strangers hit a crash in M1 or H3, or sync fails for everyone on one OEM, and you learn about it from 1-star reviews. Play Console vitals shows only fatal crashes and ANRs, days later. It shows nothing about "sync failing silently" (M9), "AI timing out", or "ledger couldn't open".

**Fix.** Add Firebase Crashlytics (add `google-services.json`, `firebase-crashlytics` and its Gradle plugin, and upload the mapping file once #71's R8 lands). Record non-fatals at:
- `SyncWorker` catch blocks (with `statusCode`)
- `AccountSessionHolder` Failed
- `GeminiRepository.failedCall` (reason only, never content)
- the M1 safety net.

Set a custom key for the app version and whether an AI key is present. Mention it in the privacy policy and the Data Safety form (Play policy is out of scope here).

### H5. Nowhere to send feedback, no version shown, no about/licenses or privacy link

**Evidence**
- `ui/auth/AccountSheet.kt` has rows for UPI, Categories, Subscriptions, Export, Backup/Restore, Reminder and Sign out. A grep for `VERSION_NAME|privacy|licen|feedback|mailto|support` in it finds nothing. `BuildConfig.VERSION_NAME` is used only to stamp backups (`MainViewModel.kt:1746`).
- `ui/auth/SignInScreen.kt` has no terms or privacy link.
- `app/src/main/res/values/strings.xml` has 4 lines.

**Failure scenario.** A stranger whose sync fails, or whose ledger "lost" a split, has no way to reach you except a Play review. When they do reach you, nobody can tell which version they run.

**Fix.** Add a "Help & about" section at the bottom of the account sheet:
- "Send feedback": an `ACTION_SENDTO mailto:` intent with the version, device and Android version pre-filled. Wrap it in try/catch for `ActivityNotFoundException`.
- "Privacy policy": a link to a hosted URL.
- "Open-source licences": `com.google.android.gms:play-services-oss-licenses`, or a static list.
- "Kharcha Paani 1.4.0 (5)" as the last line.

Optionally add a Play In-App Review prompt after, say, the 10th saved entry and a settle-up (Low priority, L13).

---

## Medium

### M1. Uncaught exceptions in ViewModel coroutines crash the app

**Evidence.** There is no `CoroutineExceptionHandler`, and `viewModelScope` uses `Dispatchers.Main.immediate`, so any exception that escapes a `launch` crashes the process. Examples:
- `saveCurrentTransaction` catches only `ArchivedGroupException` (`MainViewModel.kt:1283-1302`). An `SQLiteFullException` (disk full), or `IllegalStateException` (DB closed during sign-out) crashes the app.
- `confirmImportStatementTransactions` (`:916-935`), `createNewGroup` (`:1382-1395`), `deleteGroup` (`:1411-1415`), `deleteFriend` (`:1545-1551`), `addSubscription` (`:243-262`): no try at all.
- `exportCsv`/`writeExport` (`:1712-1730, 1813-1822`): `writeText` can throw `IOException` on a full disk, and `FileProvider.getUriForFile` can throw `IllegalArgumentException`. Then `context.startActivity(...)` runs on a captured Activity after a suspension.
- `exportLocalBackup` (`:1740-1765`) and `importLocalBackup` (`:1768-1789`): `backupRepo.restore()` and `restoreSettings` are unguarded. A backup that decodes but holds odd data (hand-edited, from another build) can throw from Room constraints.
- `parseInputText` / `parseReceiptImage` (`:810-844`) set `_isParsing = true` without try/finally. Gemini code is defensive (it classifies `IOException` and JSON errors), but `bitmapToBase64` on a huge bitmap can throw OOM. If anything escapes, the app crashes, or the spinner stays on forever.
- `SharedGroupsController.setArchived` (`ui/groups/SharedGroupsController.kt`), `scope.launch { repository.setGroupArchived(...) }`: no catch.

**Fix**
1. Wrap each user action in a small helper:
   ```kotlin
   private fun launchAction(failMsg: String, block: suspend () -> Unit) =
       viewModelScope.launch {
           try { block() }
           catch (e: CancellationException) { throw e }
           catch (e: ArchivedGroupException) { _statusMessage.value = archivedMessage(e) }
           catch (e: Exception) {
               Log.e(TAG, failMsg, e); recordNonFatal(e); _statusMessage.value = failMsg
           }
       }
   ```
   Use it for every action (with messages such as "Couldn't save. Your phone may be out of space.").
2. Use `try/finally` for every `_isParsing` / `_isParsingStatement` flag.
3. Wrap the `startActivity` calls in a try/catch for `ActivityNotFoundException`.

### M2. Process death loses the half-filled entry, the statement read and the UI state

**Evidence**
- All entry-draft state is plain `MutableStateFlow` in `MainViewModel` (`:537-571`, plus `_isConfirmSheetOpen` `:510`). A grep for `SavedStateHandle` returns nothing.
- Rotation is fine: the ViewModel lives in `AccountSession.viewModelStore`, so it survives configuration changes.
- Process death is not: switch to GPay to check the amount, Android kills the process, and on return the sheet is closed with the draft gone.
- The statement result (`_statementParseResult`) is lost the same way, after a read of up to 3 minutes.
- The user's ticks and category fixes in the import sheet are `remember(parseResult)` (`ui/components/StatementImportDialog.kt:94`), so they reset even on **rotation**.
- Swipe-deleted rows waiting on Undo are committed only in `onCleared` (`MainViewModel.kt:1347-1350`), which doesn't run on process death, so the deleted row comes back.

**Scenario.** Someone types ₹2,340 and splits it four ways, switches to PhonePe to copy the UPI note, and comes back to an empty Home.

**Fix**
- Give `MainViewModel` a `SavedStateHandle`. `viewModelFactory { initializer { MainViewModel(app, db, uid, createSavedStateHandle()) } }` works with the custom `ViewModelStoreOwner` if `AccountSession` also implements `HasDefaultViewModelProviderFactory` / `SavedStateRegistryOwner`. The simpler route is to persist the `EntryDraft` to DataStore on change, debounced.
- Restore the draft and `isConfirmSheetOpen` on init.
- Make `items` in `StatementImportContent` `rememberSaveable` with a Saver, or hoist it into the ViewModel.
- For statements, cache the parsed result to a file in `cacheDir`, keyed by the PDF's hash.

### M3. Failed-to-sync rows look normal everywhere except one line in the account sheet

**Evidence**
- `SyncBadge.Failed` puts an amber dot on the avatar (`ui/sync/SyncBadge.kt`).
- The account sheet shows "N changes couldn't sync · Retry" (`ui/auth/AccountSheet.kt:686-735`).
- The feed row (`ui/components/TransactionItemCard.kt`) never reads `syncState`. A grep shows `SyncState` used in `ui/` only by `SharedGroupPanel`.
- Retry just requeues (`MainViewModel.retryFailedSync`, `:151-160`). A row the server rejects for good (the H3 amount cap, a 65-character friend name in L9, a share sum the server refuses) fails again, and nothing says why.

**Scenario.** In a shared Goa group, someone adds "Hotel ₹18,000". The push is refused, so it stays FAILED, and it looks exactly like every other expense on their phone. Their friends never see it, and the balances disagree between phones, with nobody aware of it.

**Fix**
- Show a small "Not synced" chip with a cloud-off icon on rows whose `syncState == FAILED`, and give the row a TalkBack label.
- Store the server's error code or message on the row (a new nullable column, which needs a migration) or in a side table, and show it in the edit sheet: "The server refused this: amount too large."
- In the account sheet, "N couldn't sync" should open a list of those rows.

### M4. Retract settlement, delete IOU and delete local group: one tap, no confirm, no undo

**Evidence**
- `ui/components/SettlementItemCard.kt:150-174`: the dropdown "Retract settlement" / "Delete IOU" / "Withdraw" calls `onRetract()` directly. That goes to `MainViewModel.retractSettlement` (`:425-439`) and then `LedgerRepository.deleteSettlement` (`:559+`), which queues a **server delete** for synced ones.
- `ui/components/TripCard.kt:166-184`: long-press, then "Delete <group>", then `onDelete()`. That calls `LedgerRepository.deleteGroup` (`:842-852`), which ungroups every expense and deletes the members, with no undo.
- The TalkBack custom action "Delete <group>" (`TripCard.kt:108-110`) also deletes immediately.
- For comparison, expenses have an Undo snackbar (`MainActivity.kt:457-476`), and friends and subscription-cancel have dialogs (`SplitsScreen.kt:111-117`, `SubscriptionsSheet.kt:276-310`).

**Scenario.** Someone opens the menu on "Rohan paid you ₹5,000", mis-taps Retract, and Rohan's debt reappears on both phones.

**Fix.** Use the existing `PendingDeletes` + undo-snackbar pattern for settlements, and add it (or an AlertDialog) for group delete. Word the dialogs with the consequence: "Rohan will owe you ₹5,000 again, on both phones."

### M5. Quick-add text parse on a flaky network: about 40 s spinner, no cancel, and a late result overwrites the sheet

**Evidence**
- `GeminiRepository.parseText` uses `TEXT_TIMEOUT_SECONDS = 25` for read/write, a default `connectTimeout` of 30 s (`:609, 664-669`), and `callTimeout = 40 s` (`:327`). Offline (DNS failure) returns fast; "connected, no internet" (captive portal, 2G) runs to the timeout.
- `CaptureCard` shows only a spinner and disables the field (`ui/home/CaptureCard.kt:143-167`). There is no cancel.
- `parseInputText` (`MainViewModel.kt:810-825`) is a fire-and-forget launch. When it finishes, `applyParsedResult` rewrites amount, merchant, shares and `_editingTransactionId = null`, and then opens the sheet.
- If the user meanwhile tapped the dock "+" and started typing (or opened an edit), the late result **overwrites their entry**. In the edit case it also silently turns the edit into a new entry, because `_editingTransactionId = null`.
- `parseReceiptImage` (`:827-844`) has the same race.

**Fix**
- Track `parseJob`, and cancel it in `openManualAdd`, `openEditTransaction` and `dismissConfirmSheet`.
- Open the sheet **immediately** with the on-phone `LocalHeuristicParser` result (already computed in `parseText`, `:86`), show "Improving with AI…", and apply the AI result only if the user hasn't edited a field.
- Lower `connectTimeout` to about 10 s for text.

### M6. Re-importing a statement duplicates every row

**Evidence.** `confirmImportStatementTransactions` (`MainViewModel.kt:910-936`) and `LedgerRepository.batchInsertTransactions` (`:401-413`) insert unconditionally. Nothing compares the rows with existing entries. PR #72 only flags dates and outliers.

**Scenario.** Someone imports September, then later imports a "Jul–Sep" statement. All of September comes in twice and Insights doubles.

**Fix.** Before showing the import sheet, mark rows as "Already in your ledger" when an existing transaction has the same type, the same amount to the paisa, and a date within ±1 day (optionally a similar merchant). Leave those unticked with that reason, using the same mechanism as #72.

### M7. Large font scale (accessibility): fixed heights clip text, and amounts ellipsize

**Evidence**
- `ui/components/TripCard.kt:216`: `HalfHeight = 68.dp`, used for both halves of the card (`:117, :160`). Each half holds titleMedium + bodySmall, or eyebrow + money. At 200% font scale, that is about 32sp + 24sp of text plus padding, more than 68 dp, so names and amounts are cut.
- `KpDock` puts a fixed `DockHeight` around an icon and a `labelMedium` label (`ui/shell/KpDock.kt:104-157`).
- There are 46 `maxLines = 1` sites in `ui/`. Several are on money: `GlassActionBar` summary (`ui/components/Sheets.kt:249`), TripCard, and transaction rows. An ellipsized amount ("₹1,23,4…") is wrong information, not just untidy.
- Only `SignInScreen.kt:113` reads `fontScale`.

**Fix**
- Replace fixed heights with `heightIn(min = …)`.
- For money `Text`, use `softWrap = false` with auto-sizing (`BasicText(autoSize = TextAutoSize.StepBased(...))` in Compose 1.8+, or a `fontScale`-aware style) instead of an ellipsis.
- Let the dock grow, or hide its labels when `fontScale > 1.5`.
- Add Roborazzi captures at `fontScale = 2f` for Home, Splits and the confirm sheet.

What is already good: TalkBack labels on amounts (`AnimatedRollingNumber` uses `clearAndSetSemantics { contentDescription = text }`, `ui/components/AnimationUtils.kt:164`), custom actions on swipe rows, 48 dp minimum targets, and the contrast fixes in `Color.kt`.

### M8. Performance: duplicated full-table queries on every write; feed built on the main thread

**Evidence**
- `LedgerRepository` exposes each table as a cold Room `Flow` (`:70-76`). Every derived flow starts its **own** queries:
  - `fullTransactions` runs 3 (`:78-91`)
  - `personBalances` runs 4 (`:93-117`)
  - `groupsWithDetails` runs 5, plus another `fullTransactions` (`:119-201`)
  - `analyticsFor` runs 3, used by Home and by Insights (`:365-374`)
  - `getLedgerForPerson` runs 4 (`:203-264`)
  - `earliestInsightsMonth` and the reminder collector each re-read `allTransactions` (`MainViewModel.kt:218-225, 399-401`).
- `currentAsks` runs `getLedgerForPerson` **per asked friend** (`LedgerRepository.kt:343`), so 4 extra full-table queries for each friend asked to pay.
- One expense save therefore re-reads every table about 25 or more times, plus 4×N. A year of statement imports (about 3,000 rows) makes each save visibly slow on low-end phones.
- `activityFeed` (`MainViewModel.kt:413-423`) runs `ActivityFeed.build` (filter, map, sort of every row) inside `combine` with no `flowOn`, on **Main**, on every keystroke in search and on every DB change.
- `SplitFlowApp` collects about 60 flows at the root (`MainActivity.kt:311-397`). Typing in the amount field recomposes the root and re-runs `rawTransactions.any { … }` (`:417`), `Categories.categoriesFor(...)` and `rawGroups.filterNot {…}` (`:670`, a new list each time, so `ConfirmBottomSheet` can't skip).
- `MainActivity.kt:846` calls `viewModel.getPersonLedgerFlow(id)` during composition, so every recomposition makes a new Flow and 4 new queries.
- Group feed and person ledger render in a non-lazy `Column` (`ui/components/GroupDetailSheet.kt:319`, `ui/components/PersonLedgerSheet.kt:101`). A year-long flat group with 1,000 entries composes all of them at once.

**Fix**
- In `LedgerRepository`, `shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)` the base table flows, using a repository-scoped `CoroutineScope` (or move them into the ViewModel).
- Make `currentAsks` derive from those shared flows plus `balanceEntries`, instead of N `getLedgerForPerson` calls.
- Add `.flowOn(Dispatchers.Default)` to `activityFeed` and debounce the search query by about 150 ms.
- Collect form state inside `ConfirmBottomSheet` (pass the ViewModel or a state holder), not at the root.
- Compute `hasOwnEntry` in the ViewModel.
- `remember(personId) { viewModel.getPersonLedgerFlow(personId) }`.
- Use `LazyColumn` for long sheet lists.
- Add a Baseline Profile (`androidx.baselineprofile`) for cold start and first scroll (L12).

### M9. Sync worker: unexpected errors fail silently, and auth errors retry forever

**Evidence.** `SyncWorker.syncIfSignedIn` (`data/sync/SyncWorker.kt:67-84`):
- `IOException` returns `retry()`.
- **Any** `RestException`, including 401/403, returns `retry()` with exponential backoff, indefinitely.
- `IllegalStateException` returns `failure()`.
- Anything else (a `SerializationException` from an unexpected server shape, `SQLiteConstraintException` during a pull) escapes. `CoroutineWorker` then marks it failed, with only WorkManager's internal log.

The UI shows `failedChanges` from **rows** only (`MainViewModel.kt:138-145`), so "the whole sync is broken" never shows. The user sees "All changes synced · 3 days ago" with no badge.

**Fix**
- Store `lastSyncError` (code and time) in `sync_meta` from each catch, and catch `Exception` last.
- Show "Couldn't sync since <time>: <reason>" with "Try again" in the account sheet, and put the amber dot on the avatar when the last attempt failed more than about 1 h ago.
- For 401/403, stop retrying, surface "Sign in again", and report a non-fatal (H4).

### M10. Launch polish: blank first frame, no splash setup, themed icon is a blob

**Evidence**
- `MainActivity.kt:153-156`: while auth initializes and the ledger opens, the gate draws `Box(Modifier.fillMaxSize().background(CharcoalBg))`, an empty black screen with no brand mark.
- No `androidx.core:core-splashscreen`. On Android 12+ the system splash uses the theme's window background. The theme (`res/values/themes.xml:4`) sets none, and `DeviceDefault` differs by OEM: some Samsung builds are light, which risks a white flash before a dark app.
- `res/mipmap-anydpi-v26/ic_launcher.xml:5`: `<monochrome android:drawable="@drawable/ic_launcher_foreground" />`. The foreground starts with a filled 62 dp disc (`res/drawable/ic_launcher_foreground.xml`, path 1, `fillColor="#1F1D1A"`). Monochrome rendering uses alpha only, so the themed icon on Android 13+ is a solid tinted circle and the ₹ mark disappears.
- The platform `DatePickerDialog`/`TimePickerDialog` (`ConfirmBottomSheet.kt:523-545`) take the Activity's `DeviceDefault` theme, so they may look out of place next to the custom dark sheets.

**Fix**
- Add core-splashscreen with `Theme.App.Starting` (background #0E0D0C, icon `ic_kharcha_logo`) and `installSplashScreen().setKeepOnScreenCondition { gate == Loading }`, capped at about 1.5 s.
- Draw the brand mark in the Loading gate.
- Set an explicit `windowBackground`.
- Make a dedicated `ic_launcher_monochrome.xml` with only the ₹ strokes.
- Use Material3 `DatePicker` in a `DatePickerDialog` composable, or give the platform pickers a dark `ContextThemeWrapper`.

### M11. Features users of Splitwise-like apps expect at launch

| Feature | State | Evidence / note |
|---|---|---|
| Search | Partial | `ActivityFeed.build` (`domain/ActivityFeed.kt:30-35`) matches merchant, category and note only. Searching "Rohan" or "1200" finds nothing. Add friend names (from shares) and the amount. |
| Edit history / "who changed what" in shared groups | Missing | No audit trail. A groupmate editing an amount changes my balance silently. At minimum, show "Edited by X · time" on the row (Medium). |
| Pull-to-refresh / "Sync now" | Missing on the main screens | Sync runs on foreground (`MainActivity.kt:206-209`) and through Realtime, but people pull down to check whether a friend's expense arrived. Add `PullToRefreshBox` to Home and Splits, calling `requestSync()` and stopping when `isSyncing` turns false. |
| In-app updates | Missing | After 1,000 installs you'll ship fixes fast. Add Play Core `AppUpdateManager` flexible updates, and immediate updates for a min-version flag. |
| App lock (biometric) | Missing | Expected in Indian finance apps. Add `androidx.biometric`, an opt-in setting, lock on cold start or after 5 min in background, and optionally `FLAG_SECURE` on balances. |
| Export | Present | CSV (`MainViewModel.kt:1712-1730`) and the JSON backup. |
| Recurring | Partial | Personal subscriptions only. No recurring *split* (monthly flat rent). |
| Multi-currency | Missing | Fine for an INR-first launch. Say "INR only" in the listing. |
| Rate-the-app | Missing | L13. |
| About / licenses / version / feedback | Missing | H5. |
| Account deletion | Missing | No "Delete account" row. This is a Play policy requirement for apps with account creation; flagged for the policy agent. |

---

## Offline and flaky-network behaviour, screen by screen

| Action | Offline behaviour | Verdict |
|---|---|---|
| Sign-in | `AuthRepository.signInWithGoogle` maps `IOException` to "You're offline. Connect once to sign in." (`ui/auth/SignInScreen.kt:288`). Credential Manager failures *before* Supabase may come back as `GetCredentialUnknownException`/`NoCredentialException` and show "Check Play services" or "Add a Google account" (`data/auth/AuthRepository.kt` `mapCredentialFailure`). **Needs a device test in airplane mode.** Fix: check `ConnectivityManager` when mapping and prefer `Network`. | OK, maybe misleading (L5) |
| Add or edit expense | Saved locally as PENDING, with a grey "waiting" dot on the avatar. | Good |
| AI quick add | No network: instant phone guess with a notice (`domain/AiPrompts.kt:199-205`). Captive portal or 2G: spinner up to about 40 s, no cancel, late overwrite. | M5 |
| Receipt scan | Notices per reason (`AiPrompts.kt:207+`). Spinner inside the sheet, which can be closed. Same late-overwrite race. | M5 |
| Statement import | Trapped modal for up to minutes. | **H2** |
| Groups: create | Saved locally. Invite says "hasn't synced yet" (`SharedGroupsController.createInvite`). | Good |
| Join / leave / invite / revoke | Clear offline message through `runRemote` (`SharedGroupsController.kt`). supabase-kt's default 10 s request timeout applies. | Good |
| Settle up / IOU | Local PENDING. | Good (but H1, M4) |
| Ask-to-pay | Local share sheet. Works offline. | Good |
| UPI / profile / colour save | Explicit offline copy (`MainViewModel.saveUpi`, `AuthViewModel.saveHue/saveProfile`). | Good |
| Insights AI | "You're offline. Connect and try again." The attempt doesn't spend an ask. | Good |
| Sync errors | Row-level failures are counted only. Whole-sync failures are invisible. There is no "you're offline" indicator: the grey dot means "waiting". | M3, M9 |
| Realtime doorbell | Backs off and retries (`data/sync/SyncDoorbell.kt`). | Good |

---

## Cold start and ANR

- **Network calls before the first frame:** none blocking. Before first content, the main thread:
  - builds the Supabase client (`SupabaseClientProvider.get`, reading SharedPreferences on main, `data/remote/SupabaseClientProvider.kt`)
  - creates `AuthViewModel`
  - waits for the auth session to load
  - opens the DB (`LedgerDatabases.open`, on IO)
  - then constructs `MainViewModel` on main. That builds `AuthRepository`, `GeminiRepository` (and `AppIdentity.of`, a package-signature read and hash on main) and `AppSettings`, and starts about 6 init coroutines.
- **After the first App frame**, the app makes:
  - a profile fetch (`AuthViewModel.loadProfile`)
  - `publishUpiIfMissing` (one or two calls if a UPI ID is saved, `MainViewModel.kt:334-342`)
  - a Realtime websocket
  - a sync after 2 s (several requests).
- No `runBlocking` in `src/main`. `awaitInitialization` is used only with a timeout in background code (`data/remote/BackgroundAuth.kt:41,47`).
- No custom `Application` class. No StrictMode in debug: add `StrictMode.enableDefaults()` under `BuildConfig.DEBUG` to catch future main-thread IO.
- Widget and receiver work uses `goAsync` with timeouts (`widget/KpWidgetProvider.kt:27-34`, `ask/AskSentReceiver.kt:22`). Good.

## First-run experience for a stranger with no friends

1. Sign-in screen: the value line is clear ("Log what you spend, split it with friends, and settle up…"). It has a preview and one Google button, but no privacy or terms link (H5).
2. A blank black frame (M10), then Home: ₹0 header, the capture card ("What did you spend on?") and the empty-feed card "Your first entry goes here: Type what you spent above, like 'chai 40 cash'" (`ui/screens/HomeScreen.kt:350-365`). That is good guidance for logging.
3. Splits: empty states for friends and groups, explaining that friends don't need the app (`ui/screens/SplitsScreen.kt:194-202, 233-241`). Good.
4. Notification permission is asked only after the first own entry (`MainActivity.kt:417-425`). Good.

**Gaps**
- Nothing teaches the three tabs, the swipe gestures (swipe right edits, swipe left deletes, `TransactionItemCard.kt:110-121`), or that "Scan bill" opens the gallery, not the camera (`ui/home/CaptureCard.kt:126,186`). PR #76 adds the camera for photos only.
- A one-time, dismissible tip row above the feed after the first entry ("Swipe left to delete, right to edit") would help.
- No sample data, which is correct.

## i18n readiness

- 0 uses of `stringResource`/`pluralStringResource`. About 640 capitalized string literals in `ui/`, and 68 direct `Text("…")` calls. All copy lives in Kotlin.
- Plurals are hand-rolled in English (`if (n == 1) "1 entry" else "$n entries"`, for example `MainViewModel.kt:933`).
- Money is always Indian grouping with ₹ (`ui/format/Money.kt`), which is right for this product. Dates use `Locale`-aware `DateTimeFormatter` (`domain/ActivityDays.kt:26-27`).
- Not a launch blocker for English-first. Before a Hindi or Hinglish build, extract strings to `strings.xml` (start with error and notice copy in `domain/AiPrompts.kt`, `MainViewModel.statementFailureMessage`, and the sheets).
- `domain/LocalStatementParser.kt:15-31` keeps shared `SimpleDateFormat` instances in an `object`. They are not thread-safe (L10).

---

## Low

- **L1.** Empty-state flash on every cold start. The feed and balances use `stateIn(..., emptyList()/0.0)` (`MainViewModel.kt:355-376, 413-423`), so returning users see "Your first entry goes here" and ₹0 for one frame before Room emits. Use a `Loading` sentinel (`null` initial) and render nothing or a skeleton until the first emission.
- **L2.** `collectAsState()` (not lifecycle-aware) for most root flows (`MainActivity.kt:318-397`). With `WhileSubscribed(5000)`, the derived computations keep running while the app is in the background and sync writes rows. Use `collectAsStateWithLifecycle` everywhere.
- **L3.** Unguarded `startActivity` / launcher calls can throw `ActivityNotFoundException` on stripped OEM ROMs or work profiles:
  - the notification settings intent (`MainActivity.kt:443`)
  - `imagePicker`/`pdfPicker` `GetContent` (`ui/home/CaptureCard.kt:186-187`)
  - `pickBackup` `OpenDocument` (`MainActivity.kt:633`).

  `UpiQr.kt:187-188` already shows the right pattern. Exports and asks call `startActivity` on an Activity captured before a suspension (`MainViewModel.kt:1451, 1728, 1763`); if the user left the app meanwhile, Android 10+ blocks the background start silently. Emit a one-shot event to the UI instead.
- **L4.** Statement import ticks and category fixes reset on rotation (`StatementImportDialog.kt:94`). See M2.
- **L5.** Sign-in error mapping while offline may blame Play services or a missing account. See the offline table.
- **L6.** No haptics on money-moving confirmations. Only `ui/components/AnimationUtils.kt` uses haptic feedback. Add `HapticFeedbackType.Confirm` on Save, Record settlement and Import, and `Reject` on a disabled-button tap.
- **L7.** "Scan bill" opens the gallery picker (`CaptureCard.kt:126,186`). Rename it to "Bill photo", or offer Camera / Gallery (after #76).
- **L8.** Search doesn't match friend names or amounts (M11 row).
- **L9.** The client doesn't cap lengths that the server enforces:
  - friend names (server: 1..64 for `local_name`, `supabase/migrations/20260923000000_owner_private_local_ledger.sql:52-53`; `AddFriendSheet.kt` has no cap; `LedgerRepository.addPerson` `:618`). A longer name makes every private split with that friend FAILED.
  - the settlement reject note (server ≤200, `20260926000000_settlement_confirmation.sql:65`).

  Add `maxLength` to the fields.
- **L10.** Shared `SimpleDateFormat` instances in `LocalStatementParser` (`domain/LocalStatementParser.kt:15-31`) are not thread-safe if two parses overlap. Switch to `DateTimeFormatter` or use a local instance per call.
- **L11.** Pending swipe-deletes are lost on process death (M2). The row reappears.
- **L12.** No Baseline Profile or `profileinstaller`. The first launches of a Compose app run interpreted and janky until Play's cloud profile arrives. Add a `:baselineprofile` module covering start → Home → open confirm sheet.
- **L13.** No In-App Review prompt. Trigger it once after a positive moment (the 10th entry, or a confirmed settlement), never after an error.
- **L14.** `DataStore` without `corruptionHandler` (`data/settings/AppSettings.kt:4`). A corrupted prefs file throws `CorruptionException` on every read. Add `ReplaceFileCorruptionHandler { emptyPreferences() }`.

---

## What is already solid (no action)

- AI failure handling: every Gemini path goes through one classifier (offline, timeout, rate-limited, refused key, blocked, truncated), with specific user copy and timeouts per call type (`GeminiRepository.kt:301-354`). Offline asks aren't counted.
- Offline-first ledger with idempotent pushes. The undo snackbar for expense delete is committed safely on sign-out (`MainViewModel.kt:1329-1345`).
- Discard-changes guard on sheets (`ui/components/Sheets.kt:104-160`). IME padding in `SheetScaffold` (`:201`).
- `LazyColumn` with stable keys on Home, Splits and Insights (`HomeScreen.kt:187`, `SplitsScreen.kt:143-243`).
- Paisa-exact split rounding for synced splits (`domain/SplitRounding.kt`, `LedgerRepository.sharesFor` `:903-921`). EQUAL splits that don't divide evenly (₹100/3) are normalized before push.
- Accessibility: content descriptions, custom actions for swipe rows, WCAG-checked colours, live-region notices.
- Shared-group membership operations give clear offline and error messages (`ui/groups/SharedGroupsController.kt` `runRemote`/`messageFor`).

## Suggested order for implementing agents

1. B1 (system bars), H1 (submit guards), H3 (amount sanitiser and finite-safe `Money`). These are small, contained changes with high impact.
2. H2 (statement cancel), M1 (`launchAction` safety net), M4 (confirm/undo for settlements and groups).
3. H4 (Crashlytics), H5 (help & about), M9 (whole-sync error surfacing), M3 (per-row FAILED chip).
4. M2 (draft persistence), M5 (parse job cancel and sheet-first), M6 (import duplicates).
5. M7, M8, M10, M11, then the Low items.
