# Codex solo log (2026-10-27 →)

One line per PR merged or parked during the solo period (rules in `work-split.md`). Questions for the
owner or Claude go at the bottom.

## State when the period began (2026-10-27, written by Claude)

- On `main` and live: Gemini proxy (#101), shares add up (#106), database hygiene (#107), Say the
  split (#114), crash-report switch (#118), new categories (#119), paisa fix (#117), Codex X1–X5 so far.
- Waiting for Claude: #103 account deletion (its live SQL test hasn't run; the tool's permission prompt
  declined it), #71 R8, Dependabot #82 #86 #97. Don't touch these.
- Release 1.6.0 waits until Claude is back and the solo work is merged.

## Log

- 2026-10-27 · #125 · X5 / TODOS 8 group settlements view · merged; All activity stays default, payments and IOUs retain confirmation status, archived history is read-only.

- 2026-10-27 · #127 · X5 / TODOS 5 group sorting · merged; device-wide display preference, latest activity includes additions, edits and settlement responses; no ledger maths changed.

- 2026-10-27 · #128 · X5 / TODOS 2 member colour dots · merged; keep names and credit lines, use the account colour for You and a neutral dot when a guest colour is unknown.

- 2026-10-27 · #123 · X5 / TODOS 9 recent split group · merged; use the most recently added shared expense, preserve explicit group/No group and edits, and ignore archived groups.
- 2026-10-27 · #130 · X8 / T3.6 statement re-import · parked: red CI 40403130740 proved selected duplicates; CI 40404218629 exposed exact-day matches consumed by neighbouring new purchases. Reserve all exact-day matches before the one-day tolerance; branch remains unmerged.

- 2026-10-27 · #129 · X6 / T2.8 flexible Play updates · merged; no immediate flow, defer during drafts/deletion, download without blocking the ledger, restart only on an explicit tap; actual Play test-track delivery still needs the phone.

- 2026-10-27 � #132 � X10 / REL L1 cold-start Home � parked: CI 40407939041 timed out on an off-screen feed fixture; corrected viewport CI 40408765164 proves the premature balance header (911 tests, two assertions). Two failed CI runs reached the stop rule. Next: nullable Home snapshot from direct Room flows; render the brand mark until the first feed/people/balance/analytics emission.

## Questions
