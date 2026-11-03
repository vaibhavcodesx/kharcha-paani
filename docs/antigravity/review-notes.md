# Technical Review Notes: PRs #133, #135, #136, #138, #140, #141

Written 2026-11-03 for Item **F3** (`docs/antigravity/HANDBOOK.md` section 7).
These 6 PRs were implemented and merged by Codex during the solo lead period without prior Claude review. This review evaluates each PR's correctness, architectural alignment, test rigor, and safety against hard limits.

---

## Executive Summary

| PR | Author | Scope / Audit ID | Area | Verdict | Notes |
|---|---|---|---|---|---|
| **#133** | Codex | X10 / REL L5 | `SignInScreen.kt` | **PASS (Clean)** | Offline recovery advice before account/Play services advice. |
| **#135** | Codex | X10 / REL L7 | `CaptureCard.kt` | **PASS (Clean)** | Renamed gallery button "Scan bill" -> "Bill photo". |
| **#136** | Codex | X10 / REL L10 | `LocalStatementParser.kt` | **PASS (High Value)** | Replaced static `SimpleDateFormat` instances with call-local getters; 8-thread test. |
| **#137** | Codex | X7 / REL M7 | `KpDock.kt` | *Not in F3 scope* | (Dock accessibility labels at >150% font scale, noted for context). |
| **#138** | Codex | X7 / REL M7 | `TripCard.kt` | **PASS (Exemplary)** | Dynamic card expansion and auto-sized money at 200% font scale. |
| **#140** | Codex | X7 / REL M7 | `InsightsScreen.kt`, `SpendCharts.kt` | **PASS (Solid)** | Complete cash-flow & per-day money at 200% font; donut center hides duplicate text. |
| **#141** | Codex | X7 / REL M7 | `Sheets.kt` (`GlassActionBar`) | **PASS (Solid)** | Stacked action button below full summary figure at >150% text size. |

No code regressions, weakened assertions, or violations of hard limits were found in these 6 PRs.

---

## Detailed Review by Pull Request

### 1. PR #133: `[codex] X10: Explain offline recovery in sign-in notices`
- **Commit:** `f027658`
- **Item / Audit:** X10 / T3.10 / REL L5
- **Objective:** Ambiguous Google Credential Manager picker errors (e.g. `NoGoogleAccount`, `GoogleUnavailable`) occur prior to making network calls. When a user is in Airplane mode or has poor connectivity, blaming Play services or missing Google accounts creates confusion.
- **Changes in `app/src/main/java/com/vaibhav/kharchapaani/ui/auth/SignInScreen.kt`:**
  - `GoogleSignInFailure.NoGoogleAccount`: Changed from `"Add a Google account to this phone, then try again."` to `"If you're offline, connect and try again. Otherwise, add a Google account to this phone."`
  - `GoogleSignInFailure.GoogleUnavailable`: Changed from `"Google sign-in isn't available right now. Check that Google Play services is up to date."` to `"If you're offline, connect and try again. Otherwise, check that Google Play services is up to date."`
- **Tests Added (`SignInScreenTest.kt`):**
  - `an unavailable Google picker offers an offline recovery before Play services advice`
  - `a missing account result offers an offline recovery before account advice`
- **Review Assessment:**
  - **Correctness:** Safe and accurate. It uses conditional phrasing (*"If you're offline..."*) rather than asserting that the user *is* offline, avoiding incorrect claims when network state is ambiguous.
  - **Hard Limits:** Did not touch `AuthRepository`, Supabase RLS, or credential secrets.
  - **Follow-up Note:** Subsequent X12 PRs appropriately migrated these raw strings into `res/values/strings.xml` (`signin_failure_no_account`, `signin_failure_google_unavailable`) without altering the English wording.

---

### 2. PR #135: `[codex] X10: Call the gallery action Bill photo`
- **Commit:** `7108c4a`
- **Item / Audit:** X10 / T3.10 / REL L7
- **Objective:** The button on Home's `CaptureCard` that opens the system photo picker was labeled "Scan bill", implying an in-app document scanner camera experience rather than picking an existing receipt image.
- **Changes in `app/src/main/java/com/vaibhav/kharchapaani/ui/home/CaptureCard.kt`:**
  - Line 186: Renamed label from `"Scan bill"` to `"Bill photo"` while preserving the icon `KpIcons.Scan` and test tag `scan_receipt_button`.
- **Tests Updated (`AppShellLaunchTest.kt`):**
  - Added assertion `compose.onNodeWithTag("scan_receipt_button").assertTextEquals("Bill photo")`.
- **Review Assessment:**
  - **Correctness:** Minor, surgical, and accurate terminology change aligning user expectations with system behavior.
  - **Test Rigor:** The launch smoke test `AppShellLaunchTest` asserts the new label.

---

### 3. PR #136: `[codex] X10: Keep statement date parsing safe across threads`
- **Commit:** `2805c5d`
- **Item / Audit:** X10 / T3.10 / REL L10
- **Objective:** Java's `SimpleDateFormat` is inherently mutable and not thread-safe. `LocalStatementParser` previously held static singletons for `displayDateFormatter`, `monthYearFormatter`, `monthSortFormatter`, and `supportedDateFormats`. Concurrent statement parsing or background worker threads could interleave calendar fields and corrupt parsed timestamps or group labels.
- **Changes in `app/src/main/java/com/vaibhav/kharchapaani/domain/LocalStatementParser.kt`:**
  - Replaced static `val` declarations with Kotlin property getters (`val supportedDateFormats get() = listOf(...)`, `val displayDateFormatter get() = SimpleDateFormat(...)`). Every invocation receives clean, call-confined instances.
- **Tests Added (`ConcurrentStatementDatesTest.kt`):**
  - Uses an `Executors.newFixedThreadPool(8)` with a `CountDownLatch` starting 8 threads simultaneously.
  - Iterates 4,000 dates (500 per worker) across 4 date patterns (`yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `dd MMM yyyy`).
  - Asserts parsed epoch day, display date formatting, and month-year string matching.
- **Review Assessment:**
  - **Correctness:** Essential concurrency bugfix. Eliminates silent data corruption risk during asynchronous document/statement parsing.
  - **Performance:** Formatter creation overhead is negligible for typical statement batches ($\le 1,000$ rows) and avoids synchronization locks.
  - **Test Rigor:** Exceptionally strong concurrency test.

---

### 4. PR #138: `[codex] X7: Fit full group amounts at large font sizes`
- **Commit:** `0ae9c79`
- **Item / Audit:** X7 / T2.10 / REL M7 (Splits Group Cards)
- **Objective:** At accessibility text sizes (up to 200% font scale), ticket-style group cards (`TripCard.kt`) with fixed 50/50 heights clipped group names, member metadata, and truncated Indian currency values, violating the core rule: *"Money never ellipsizes."*
- **Changes in `app/src/main/java/com/vaibhav/kharchapaani/ui/components/TripCard.kt`:**
  - Detects accessibility mode: `val largeText = LocalDensity.current.fontScale > 1.5f`.
  - When `largeText` is active:
    - Replaces fixed `height(HalfHeight)` with `heightIn(min = HalfHeight)`.
    - Switches shape from notched `Ticket` to rounded `KpShapes.large`.
    - Omit decorative perforation line (`Modifier.perforation()`), which would otherwise collide with multi-line content.
    - Allows group name and metadata to wrap (`maxLines = if (largeText) Int.MAX_VALUE else 1`).
    - Uses `BasicText` with `TextAutoSize.StepBased` for `Money.format` amounts to guarantee full visibility on a single line.
- **Tests Added (`SplitsAccessibilityTest.kt`):**
  - Robolectric test running under `@GraphicsMode(GraphicsMode.Mode.NATIVE)` with `@Config(qualifiers = "w411dp-h891dp")` and `fontScale = 2.0f`.
  - Asserts `hasVisualOverflow == false` and `lineCount == 1` across `totalExpenses`, `myShare`, and `netBalanceForMe`.
  - Emits and inspects visual screenshot `splits_font_2.png`.
- **Review Assessment:**
  - **Correctness:** Outstanding adaptive UI design. Gracefully trades off decorative skeuomorphism (ticket notches) for legibility and accessibility compliance when needed.
  - **Adherence to Rules:** Adheres strictly to the prohibition against ellipsizing currency amounts.

---

### 5. PR #140: `[codex] X7: Show complete Insights money values at large fonts`
- **Commit:** `ef803a2`
- **Item / Audit:** X7 / T2.10 / REL M7 (Insights Screen)
- **Objective:** Prevent currency truncation in monthly cash-flow figures and daily averages on `InsightsScreen.kt`, and resolve text clipping inside `CategoryRing` when enlarged.
- **Changes in `SpendCharts.kt` & `InsightsScreen.kt`:**
  - `SpendCharts.kt`:
    - In `CategoryRing`: When `fontScale > 1.5f`, omits the cramped center text inside the donut ring (`if (!largeText) Column(...)`), providing a full spoken description for TalkBack (`"${centreSlice?.category ?: "Spent"} ${Money.format(...)}. " + slices.joinToString(...)`).
    - Center text uses `BasicText` with `TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MoneyText.row.fontSize)` with `softWrap = false`.
  - `InsightsScreen.kt`:
    - `CashFlow`: Replaced fixed typography with auto-sizing `BasicText` (`minFontSize = 10.sp`) for Income, Expense, and Net figures.
    - `PerDay`: Replaced fixed typography with auto-sizing `BasicText` so per-day rupee values do not wrap or overflow.
- **Tests Added (`InsightsAccessibilityTest.kt`):**
  - Native 2.0x font scale test checking `insights_spent_figure` and per-day spend (`amount / today.dayOfMonth`), verifying `hasVisualOverflow == false` and `lineCount == 1`, plus spoken description assertion.
- **Review Assessment:**
  - **Correctness:** Properly handles the geometric constraints of circular charts. Removing center text at $>150\%$ text scale while enriching the accessibility semantic description is standard best practice.
  - **Note on Flakiness:** The original test calculated `amount / today.dayOfMonth` against live `LocalDate.now()`, which caused a precision edge case on month transitions; Codex subsequently pinned this in PR #192.

---

### 6. PR #141: `[codex] X7: Keep confirmation amounts readable at large fonts`
- **Commit:** `5747b17`
- **Item / Audit:** X7 / T2.10 / REL M7 (Confirmation Action Bar)
- **Objective:** Prevent bottom sheet action bars (`GlassActionBar` in `Sheets.kt`) from cramping summary totals and action buttons when font scaling is set to 200%.
- **Changes in `app/src/main/java/com/vaibhav/kharchapaani/ui/components/Sheets.kt`:**
  - In `GlassActionBar`:
    - Detects `val largeText = LocalDensity.current.fontScale > 1.5f`.
    - Switches outer layout container from a horizontal `Row` to `FlowRow(verticalArrangement = Arrangement.spacedBy(Spacing.s))`.
    - At large text sizes, the summary column expands to `fillMaxWidth()` and the action button expands below it (`Modifier.weight(1f)`), ensuring touch targets remain $\ge 52$dp.
    - Uses `BasicText` with `TextAutoSize.StepBased` for the summary figure to eliminate risk of truncation.
- **Tests Added (`ConfirmAccessibilityTest.kt`):**
  - Builds real `MainViewModel` and `Room` database with `amount = 9999999.99`.
  - Evaluates layout with `LocalDensity provides Density(..., 2f)`.
  - Asserts `hasVisualOverflow == false`, `lineCount == 1`, and correct theme color.
  - Triggers the Save action and asserts that the transaction is inserted into Room with exact amount.
- **Review Assessment:**
  - **Correctness:** Completely sound. Responsive vertical stacking prevents horizontal squishing without altering button state guards or duplicate-click protection.

---

## Conclusion & Recommendations

All 6 unreviewed PRs (#133, #135, #136, #138, #140, #141) meet the repo's quality and architectural bar:
1. No schema or database changes were introduced.
2. Hard limits were respected (no Supabase modifications, no security regressions).
3. Every fix included real gatherer/ViewModel tests.
4. No further code changes are required for these items.
