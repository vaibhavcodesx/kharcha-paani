# TODOS

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
