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

- 2026-10-27 · #131 · X9 / T3.9 Play reviews · merged; only saved confirmed settlements, one settling-in week and 180-day attempt cooldown, silent SDK failure and no star requests; Play may suppress its dialog.


- 2026-10-27 · #132 · X10 / REL L1 cold-start Home · parked: CI 40407939041 timed out on an off-screen feed fixture; corrected viewport CI 40408765164 proves the premature balance header (911 tests, two assertions). Two failed CI runs reached the stop rule. Next: nullable Home snapshot from direct Room flows; render the brand mark until the first feed/people/balance/analytics emission.

- 2026-10-27 · #133 · X10 / REL L5 offline sign-in wording · merged; conditional connection advice comes before account/Play-services advice, without changing authentication or guessing network state.

- 2026-10-27 · #138 · X7 / T2.10 Splits group cards · merged; cards grow, amounts fit without ellipses, large text wraps names and figures, and decorative perforation is omitted above 150% text size.

- 2026-10-27 · #136 · X10 / REL L10 statement dates · merged; formatters are local per call, preserving supported formats, strict date checks and the current time zone; eight threads verify 4,000 dates and labels.

- 2026-10-27 · #135 · X10 / REL L7 bill naming · merged; Bill photo describes the existing gallery picker; capture and cancellation behavior are unchanged.

- 2026-10-27 · #134 · X10 / REL L6 money confirmations · merged; Confirm haptic follows each successful entry/payment/nonempty batch write; invalid amounts, errors and empty imports stay silent, and disabled buttons stay disabled.

- 2026-10-27 · #142 · X11 / T3.8 Activity search · merged for search: exact-paisa amount magnitude, Indian grouping, related shares/payers/owners and named guests. Edited-by attribution remains a file-boundary handoff: authoritative last-editor identity needs protected DB/sync/server work; ownerProfileId is the original author, not necessarily the editor.

- 2026-10-27 · #141 · X7 / T2.10 confirmation action bars · merged; large text stacks the action below its complete auto-sized amount, while disabled and busy actions retain their guards.

- 2026-10-27 · #140 · X7 / T2.10 Insights money · merged; cash-flow/per-day figures fit full amounts, while large text hides cramped duplicate ring-centre text and keeps the full spoken total; normal cash-flow animations remain.

- 2026-10-27 · #137 · X7 / T2.10 dock · merged; above 150% font size keep full navigation targets and spoken tab names while hiding cramped visual labels; ordinary labels remain at normal sizes.

- 2026-10-27 · #139 · X7 / T2.10 Home money · parked: CI 40412344087 rejected a Boolean-returning render test; corrected CI 40413157415 reaches the missing whole-amount layout assertion. Two failures reached the stop rule. Next: auto-size a single full figure when animated digits cannot fit, retaining normal animation when they do.

- 2026-10-27 · #145 · X12 / T3.11 Splits copy · merged; resource-backed labels, counters, existing expense plural choices, menus and spoken actions retain English wording; sort/filter IDs and names as data are unchanged.

- 2026-10-27 · #143 · X12 / T3.11 Home copy · parked: first CI 40415903485 rejected a non-void test signature; corrected implementation CI 40416904970 passed, but integrated CI 40418934304 could not find Today after seeing the streak. Two failures reached the stop rule. Likely fixture follow-up: await the feed as well as analytics before checking day headers; extraction remains unmerged.

- 2026-10-27 · #144 · X12 / T3.11 navigation copy · merged; tabs store resource IDs and Compose resolves visual/spoken labels before callbacks; English labels, stable enum values, tags and the 150% accessibility threshold are unchanged.

- 2026-10-27 · #149 · X12 / T3.11 statement review copy · merged; existing selection/found plurals, row/month actions and reading notices use resources; row flags/data and import behavior are unchanged, and parked X8 is not resumed.

## Questions
