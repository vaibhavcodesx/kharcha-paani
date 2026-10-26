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

- 2026-10-27 · #130 · X8 / T3.6 statement re-import · parked: CI 40403130740 proved the original selected-duplicate bug; CI 40404218629 exposed an exact-date match consumed by a neighbouring new purchase. Reserve all exact-day matches before applying the one-day tolerance; do not merge this branch.

## Questions
