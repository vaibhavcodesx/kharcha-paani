<!-- status: approved by the Lead 2026-11-19 (owner answer G-22: a main agent may approve designs) | owner: lead -->
# Budgets per category (D-18)

Owner request: budgets before launch, set per category in Settings. Issue #313. Only the first PR is red (a new
Room table); the Lead merges it under the 3a conditions in `docs/agents/PLAYBOOK.md`.

## What the person sees

- **Settings > Budgets** (the Settings split is issue #327; until it lands, a "Budgets" row in the Account sheet):
  one row per expense category with a monthly amount field. An empty field means no budget. A first row, **Total
  per month**, is optional. The field's hint shows last month's spend for that category ("Last month: 3,100") so
  a person can set something real in a few seconds.
- **Insights**: each category with a budget shows a progress bar and "3,200 of 4,000". The bar changes at
  **80 percent** (a "Close" label and an icon) and **100 percent** ("Over by 300", a different icon). Never colour alone.
- **After saving an expense** that takes its category past 80 or 100 percent for the first time this month, one
  quiet message under the save confirmation: "Food is at 85% of its budget." No notification in 1.8 (N2 is
  later), no blocking, nothing on income.
- **Home**: one compact line showing the category closest to its limit, only when some category is at 80 percent or
  more. Tapping opens Insights.

## What counts

The same number Insights already shows: `MonthAnalytics.categoryTotals` (the person's own share of split
expenses; income and settlements excluded), for the calendar month Home and Insights use. One source of truth, so a
budget never disagrees with a chart. No rollover. The month starts on the 1st (a salary-day month start comes with
the income setting, D-10, in 1.9).

## Data

- New Room entity `BudgetEntity(category TEXT PRIMARY KEY, amount REAL NOT NULL, updatedAt INTEGER NOT NULL)`. A
  missing row means no budget. The total uses the reserved key `__total__`. Amounts follow the app's current money
  type (a `Double` in the person's currency; ENG-08 is a later cleanup).
- Room version 10 to 11: a `Migration(10, 11)` that only creates the table, the exported `app/schemas/.../11.json`,
  and a `MigrationTestHelper` test (rule: `docs/engineering/testing.md`). Additive: nothing existing changes.
- **Phone-only in 1.8.** Not synced; it joins subscriptions, custom categories and settings in the "phone-only
  data" sync work (D-20, issue #319). Included in Export and Backup: an optional `budgets` list in the backup file
  that older files simply lack (decode defaults to empty), and the round-trip test must build a real database,
  export, restore and assert a budget's amount (the "test the gatherer" rule).
- A custom category that is renamed moves its budget row; one that is deleted hides its budget (the row is kept
  for undo). The default categories can never lose a budget.

## Edge cases to cover in tests

A split expense counts only the person's share; a refund or income does not reduce spending; the 31st and the 1st
(month boundary, UTC and IST); a budget of 0 is allowed and means "none planned": shows Over on the first expense;
editing a budget mid-month recalculates at once; a very large amount is clamped to the app's money maximum; rapid
double save does not show the message twice.

## Work split (three PRs, in this order, each by the Builder)

1. **Data**: entity, DAO, migration, schema JSON, backup/restore, tests. Red (schema); `risk:red`, the Lead merges
   under 3a (additive migration, tests green).
2. **Settings > Budgets** screen and its state holder, with the hint of last month's spend. Strings are a hot file.
3. **Progress**: Insights bars, the post-save message, the Home line, and the 80/100 percent logic as one pure,
   unit-tested function (`BudgetStatus.of(spent, budget)`).

## Not now

Notifications and push (N2, later), per-group or per-person budgets, rollover, sync, a currency other than the
person's own. Each can be added without changing this data model.
