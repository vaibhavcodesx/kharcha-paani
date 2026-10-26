# TODOS

## From the owner's own use (2026-10-25)

Gaps and bugs the owner noticed using the app. Not yet investigated: check each in the code before
fixing. Kept in the owner's numbering (there was no item 1). Each one is a PR of its own unless noted.

### 2. Member colour dots in a group's expenses

**What:** In a group's expense list, show each person's colour dot (the colour each person already has)
beside who paid and who's in it, as the friend rows already do.
**Priority:** P2 · **Effort:** S

### 3a. More categories

**What:** Add "Packaged goods", "Beverages", and a category for haircuts, salon, spa, grooming and the
like. The owner didn't want it called "Personal". Name decided (2026-10-26): **"Self-care"**, covering
haircuts, salon, spa, grooming, gym and wellness. New categories must also reach the AI prompts
(quick add, bills, statements) so Gemini can choose them, and `CategoriesTest`.
**Priority:** P2 · **Effort:** S

### 3b. Saved cards as payment methods

**What:** Let people add a card as a payment method under a name they choose plus its last 4 digits
(for example "HDFC credit ••4321"), and keep several. Only the last 4 digits are stored, never a full
number, expiry or CVV, and the field takes 4 digits only. The entry then shows "Card ••4321".
**Check first:** Play's Financial features declaration and the Data safety form. Storing the last 4
digits is "financial info: other" on the phone. It is optional and stays on the phone unless it syncs,
so decide whether payment-method labels sync.
**Priority:** P3 · **Effort:** M (Room migration if methods move to a table)

### 4. Tapping an IOU card shows the whole note

**What:** A long IOU note is cut off on its card. Tapping the card should open it (a sheet or an
expanded card) with the full note, the date and who recorded it.
**Priority:** P2 · **Effort:** S

### 5. Sorting groups

**What:** Sort options on the Groups list: latest activity (default), name, what you're owed, what
you owe, newest. Remember the choice.
**Priority:** P3 · **Effort:** S

### 6. Handling a lot of friends (20+)

**What:** With 20 or more friends, the friend list, the split picker and the payer picker get long.
Add search, people you split with often at the top, and a compact row style. Check performance (lazy
lists in sheets, already in T3.7).
**Priority:** P2 · **Effort:** M

### 7. Fix the Groups → categories toggle

**What:** The categories toggle on a group doesn't work as expected. Reproduce first: the owner's
report is "Fix Groups -> categories toggle".
**Priority:** P1 (a bug) · **Effort:** S

### 8. A settlements view in a group

**What:** A toggle or tab in a group to see every recorded settlement (who paid whom, when, its
status), not just the expenses.
**Priority:** P2 · **Effort:** S

### 9. Pick a group by default when splitting with someone

**What:** When you start a split and pick a person, preselect the group the two of you most recently
added an expense in. You can still change it.
**Priority:** P2 · **Effort:** S

### 10. A walkthrough for new users, with Skip

**What:** A short first-run tour of the main features (quick add, splits, groups, settle up), always
skippable and never shown again once done or skipped. Later task: after the Play launch work.
**Priority:** P3 · **Effort:** M

## Ledger

### One source for the per-friend balance maths

**What:** Make `LedgerRepository.personBalances` sum the same signed per-entry rows that `getLedgerForPerson` produces, removing the duplicated loop.

**Why:** The balance and the friend history compute the same contributions twice, and only `PersonLedgerTest` keeps them equal. Ask-to-pay builds its itemised message from the history rows and promises they add up to the balance, so a future change made to only one of them would make asks show lines that don't match the total.

**Context:** Found in the ask-to-pay eng review (docs/designs/ask-to-pay.md, decisions D7/D9, 2026-10-14). Start at `LedgerRepository.kt:88` (`personBalances`) and the `getLedgerForPerson` loop in the same file. Keep `PersonLedgerTest`, `SettlementConfirmationTest` and `GuestSharesTest` green; the itemiser's tests from Phase 1 then catch any drift.

**Effort:** M
**Priority:** P3
**Depends on:** ask-to-pay Phase 1 shipped

## Ask-to-pay

### Eng review for Phase 2 (request page) once the gate passes

**What:** Run /plan-eng-review on the Phase 2 section of docs/designs/ask-to-pay.md before building it.

**Why:** Phase 2 adds the project's first public, token-based read path and RPCs (an owner RLS hard stop). It was deliberately deferred (D1) until the UPI test and Phase 1 check-ins exist, and its open issues weren't reviewed.

**Context:** Open items to settle in that review: office-hours reviewer concerns R3-2 (page spec vs gate variants), R3-4 and R3-13 (tap metrics for no-button variants and non-Android opens), R3-6 (where the device re-check reads live requests), R3-7 (remote-edit staleness window), R3-8 (revoke links when the UPI ID changes), R3-10 (numeric receipt threshold), R3-14 (WhatsApp in-app browser vs Chrome). The Supabase CLI isn't installed on the dev laptop and there's no `supabase/functions/` directory yet; decide CLI vs the Supabase MCP deploy tool.

**Effort:** M
**Priority:** P2
**Depends on:** the gate in docs/designs/ask-to-pay.md (UPI test results per path; 3 of 4 users sent asks; half repaid within 7 days)

## Completed
