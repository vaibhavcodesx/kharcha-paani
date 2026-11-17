# Multi-currency and country: design skeleton

**Status: draft, not approved.** Written 2026-11-17 from a read-only pass over `origin/main` at
`8839b3f`, following the owner's answers in `docs/audits/2026-11-17/OWNER-ANSWERS.md` (A-05, E-02,
E-04, E-05, E-08). The agent that takes this on verifies every claim against the code, fills in the
gaps, and gets the owner's answers to the five questions at the end before any code is written.
Planned for 1.9.0. Counts are over `app/src/main/java/com/vaibhav/kharchapaani/`.

## Decision summary

- One home currency per user, one per group, fixed at creation. No conversion, no sums across
  currencies.
- Launch set: INR, USD, AED, CAD, all with two decimals. English only.
- Every money row carries its own ISO 4217 code, so changing the home currency in Settings never
  relabels history.
- How much it touches:
  - `Money.*`: 116 calls in 30 files, of which `Money.format` is 84.
  - `roundPaisa`: 54 uses in 15 files (defined in `domain/Money.kt:11`).
  - A literal `₹`: 69 in 28 files.

## Data model changes

**Room** (`data/db/entity/Entities.kt`, `AppDatabase.kt` version 10 → 11, additive, with `11.json`
and a `Migration10To11Test`):
- `currency TEXT NOT NULL DEFAULT 'INR'` on `groups`, `transactions`, `settlements`,
  `subscriptions`, `payment_asks`.
- `splits` and `split_shares` inherit from their transaction.
- `people.paymentLink TEXT`, nullable.
- Existing rows become INR through the default.
- Home currency and country go in `data/settings/AppSettings.kt`, not in Room.

**Postgres** (owner review):
- `currency text not null default 'INR' check (currency in ('INR','USD','AED','CAD'))` on `groups`,
  `transactions`, `settlements`, `local_splits`, `local_settlements` and the bill-split table.
- `profiles.home_currency` with the same check, and `profiles.payment_link text` with an https and
  host allow-list check modelled on `profiles_upi_id_format`.
- A trigger makes `groups.currency` immutable.
- `save_group_expense(jsonb, jsonb, jsonb)` and settlement inserts take the currency from the
  group, never from the client. `create_group` gets a new overload with `p_currency`.
- Old app versions:
  - Personal rows that send no currency get the INR default, so they keep working.
  - In a non-INR group, a payload with no `currency` key is rejected, so the row is marked FAILED on
    that phone. An old build then cannot write rupee-minded amounts into a dollar group.

## Minor units

Recommended: restrict to two-decimal currencies by an allow-list (the server check plus a
`domain/Currency.kt` registry with `decimals = 2`).
- Every layer assumes two decimals today: `numeric(12,2)`, `SplitRounding.paisa()` (`setScale(2)`),
  `ItemSplitMath.paise` (×100), `amount_paise bigint`, and `Money.plainNumber` (`\.\d{0,2}`).
- Under that rule all of them stay correct, with no change to split rounding or `DebtSimplifier`.
- Yen and dinar would need `numeric(14,3)` and a scale parameter through those paths. Defer them,
  and add a registry test that fails if a currency without two decimals is added.

## Formatting

`ui/format/Money.kt`:

```kotlin
fun format(amount: Double, currency: Currency, showPlus: Boolean = false): String
fun digits(amount: Double, currency: Currency): String
fun compactDigits(amount: Double, currency: Currency): String
fun parseInput(text: String): Double?   // strips any registry symbol
```

- `Currency` carries the code, symbol, grouping (Indian or Western) and compact units.
- INR keeps `groupIndian` and L/Cr unchanged, and the existing tests stay as the INR case. The
  others group in threes with K/M.
- Symbols: `$`; `CA$` when shown beside US dollars, otherwise `$`; `AED` as a prefix with a space.
- `LARGE_AMOUNT` becomes per-currency.
- Do not keep an overload that defaults to INR: the compile errors are the checklist for the 84
  `format` call sites.
- A CI grep fails on `₹` outside `Money.kt` and `Currency.kt`.

## Balances and invariants

Amounts in different currencies are never added. Today that could happen in:
- `data/repository/LedgerRepository.kt`: `balanceEntries` nets every split and settlement with a
  person into one `netBalance`, across groups.
- `ui/screens/SplitsScreen.kt:125-126` and `widget/WidgetData.kt:27`: `sumOf { netBalance }` over
  all people.
- `domain/ActivityDays.kt:43`, `domain/MonthAnalytics.kt`, `ui/screens/InsightsScreen.kt` and
  `domain/AiContext.kt`: `myPersonalShare` folds group shares into personal spending.
- `ui/home/BalanceHeader.kt` and the three widget providers.

Fix by extending, not redesigning:
- `PersonBalance` is keyed by (person, currency).
- Totals show one line per currency.
- Insights, the daily strip, subscription totals and the AI context include home-currency rows only.
- `GroupBalances.netPositions` and `DebtSimplifier` work per group, so they are already safe; add a
  `require` that all inputs share one currency.

## Sync and backup compatibility

- Add `currency: String = "INR"` to the DTOs in `RemoteTransaction.kt` (`RemoteTransaction`,
  `TransactionUpload`), `RemoteGroups.kt` (`RemoteGroup`, `RemoteGroupTransaction`,
  `RemoteSettlement`, `GroupExpenseUpload`, `SettlementUpload`) and both in `LocalLedgerRemote.kt`.
- Old builds decode the group DTOs leniently (`ignoreUnknownKeys`). **Not verified** that the
  personal-transaction pull is equally lenient: check before the server column ships.
- Old builds would still draw ₹ on rows pulled from a non-INR group. The minimum-version switch
  (security S-06) must exist and be raised before any non-INR group can be created.
- `domain/EverythingBackup.kt` does `require(version == 1)`. Bump to 2, read both 1 (as INR) and 2;
  old apps will refuse version 2 files.
- `domain/LocalBackup.kt` is lenient, so a defaulted field is enough.
- `CsvExport.kt` gains a currency column.

## AI prompts and parsers

- `domain/AiPrompts.kt:42-49` and `GeminiRepository.kt:224, 304, 317, 762` say "Indian" and
  "rupees". Parameterise by currency and country, including date order (US receipts are month-first).
- `domain/AiAnswerText.rupees` rewrites `Rs`/`INR` to `₹`. Make it per-currency, and a no-op
  elsewhere.
- `LocalHeuristicParser.kt` and `ActivityFeed.kt:39` accept the registry's symbols.
- `LocalStatementParser.kt` and `StatementImportDialog` stay India-only: hide the entry point when
  the home currency is not INR.

## Settle-up without UPI

- `UpiLink`, `UpiSheet.kt` and `UpiQr.kt` show only when the debt is in INR and the payee has a UPI ID.
- Otherwise the sheet offers "Mark as paid" (the existing settlement and confirmation flow) and
  "Open payment link" if the payee's link passes a cleaner modelled on `UpiLink.cleanId`.
- The amount is not put into third-party URLs.

## Rollout, as small PRs

1. Green: the `domain/Currency.kt` registry and the `Money` API with an explicit currency, every
   call site passing INR, the CI grep.
2. Yellow: Room 10 → 11 columns, schema snapshot, migration test.
3. Red: server columns, checks, the immutability trigger, RPC changes (owner review).
4. Yellow: sync DTOs and backup version 2.
5. Yellow: per-currency balances, totals, widgets, the Insights filter.
6. Green: country and currency at first run from the locale; change in Settings.
7. Yellow: currency picker when creating a group; group screens formatted in the group's currency.
8. Yellow: AI prompts and parsers; statement import gated.
9. Red: payment link on the profile and the non-UPI settle sheet.

## Questions for the owner

1. Home screen with debts in two currencies: one line per currency, or home currency only with an
   "also" row?
2. Do shares in a non-home-currency group stay out of Insights entirely, or get their own section?
3. Force old builds to update before the first non-INR group exists, or accept ₹ mislabelling on them?
4. Payment links: PayPal and Venmo hosts only, or any https address?
5. May a user who already has entries change their home currency (old entries keep theirs), or only
   while the ledger is empty?
