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

- 2026-10-27 · #147 · X12 / T3.11 entry review copy · merged; entry/split/date/discard labels and shared validation messages use resources with unchanged comparisons, rounding and English; apostrophes and percent signs remain user data.

- 2026-10-27 · #146 · X12 / T3.11 Insights copy · merged; screen/chart labels, cash-flow sentences, existing entry plurals and spoken descriptions use resources with unchanged English and Indian money formatting.

- 2026-10-27 · #145 · X12 / T3.11 Splits copy · merged; resource-backed labels, counters, existing expense plural choices, menus and spoken actions retain English wording; sort/filter IDs and names as data are unchanged.

- 2026-10-27 · #143 · X12 / T3.11 Home copy · parked: first CI 40415903485 rejected a non-void test signature; corrected implementation CI 40416904970 passed, but integrated CI 40418934304 could not find Today after seeing the streak. Two failures reached the stop rule. Likely fixture follow-up: await the feed as well as analytics before checking day headers; extraction remains unmerged.

- 2026-10-27 · #144 · X12 / T3.11 navigation copy · merged; tabs store resource IDs and Compose resolves visual/spoken labels before callbacks; English labels, stable enum values, tags and the 150% accessibility threshold are unchanged.

- 2026-10-27 · #151 · X12 / T3.11 payment forms · merged; IOU, settlement and answer labels use resources with unchanged English, directions and record guards; user notes remain data.

- 2026-10-27 · #150 · X12 / T3.11 export picker copy · merged; resource-backed scope labels and existing count plurals retain English, range IDs/calculation and machine-readable CSV columns; no sharing was triggered by tests. Integrated CI 40427051347 exposed SafetyNetTest racing the submission finally block; await both the message and released guard, without changing production behavior.

- 2026-10-27 · #149 · X12 / T3.11 statement review copy · merged; existing selection/found plurals, row/month actions and reading notices use resources; row flags/data and import behavior are unchanged, and parked X8 is not resumed.

- 2026-10-27 · #148 · X12 / T3.11 shared sheet controls · merged; discard/large-amount/Cancel and amount-field spoken labels use resources with unchanged actions, English and full Indian-formatted amounts.

- 2026-10-27 · #155 · X12 / T3.11 group creation · merged; resource-backed form copy keeps English and local/shared behavior. First CI 40426701681 missed the selected member after a coordinate click; the test now uses the semantic action and asserts selection before saving.

- 2026-10-27 · #154 · X12 / T3.11 group detail · merged; screen labels/statuses/counts and participant captions use resources; domain-generated credit/movement descriptions remain a shared presentation follow-up, and AI output/prompts are untouched.

- 2026-10-27 · #153 · X12 / T3.11 friend and colour forms · merged; visible/spoken labels use resources, default possessives resolve in Compose and names remain data; test-only palette names remain metadata.

- 2026-10-27 · #152 · X12 / T3.11 backup preview · merged; resource-backed count plurals and labels keep English and additive confirmation; domain summary/JSON APIs are unchanged.

- 2026-10-27 · #156 · X12 / T3.11 friend history · merged; resource-backed balance/action/count/empty copy preserves English; repository-generated row descriptions remain a separate presentation extraction.

- 2026-10-27 · #157 · X12 / T3.11 expense timeline · merged; action, bill-photo and participant/visit labels use resources while swipe guards, data and amounts stay unchanged.

- 2026-10-27 · #158 · X12 / T3.11 settlement timeline · merged; resource-backed actions, removal consequences and IOU note controls preserve English, permissions and response data; generated activity titles/statuses are tracked separately in #173.

- 2026-10-27 · #159 · X12 / T3.11 subscriptions · merged; resource-backed form/cycle/schedule/confirmation copy preserves English, dates and stored names; scheduling behavior stays unchanged.

- 2026-10-27 · #160 · X12 / T3.11 shared-group controls · merged; join/invite/leave/recording prompts use resources with existing English plurals and unchanged eligibility, permissions and sync behavior.

- 2026-10-27 · #161 · X12 / T3.11 category controls · merged; resource-backed preference labels keep English, canonical/default IDs and custom names; protected AppSettings stays unchanged.

- 2026-10-27 · #162 · X12 / T3.11 debt management copy · merged; exact English, real-VM friend deletion and empty simplify-state coverage; no debt algorithm changes.

- 2026-10-27 · #163 · X12 / T3.11 UPI card copy · merged; exact English, full amounts and user payee names preserved; QR/URI behavior unchanged.

- 2026-10-27 · #164 · X12 / T3.11 bill-photo copy · merged; exact menu/viewer/spoken English and optional spacing preserved; capture/storage behavior unchanged. First integrated CI 40437543151 exposed SafetyNetTest resetting Dispatchers.Main while its uncancelled ViewModel was reading Room; the fixture now cancels and awaits the real ViewModel before resetting Main, with a StandardTestDispatcher.

- 2026-10-27 · #165 · X12 / T3.11 AI presentation copy · merged; consent, labels, answer errors and parse notices use resources; parity tests preserve English, while protected prompt instructions and generated answers stay untouched. First integrated CI 40438740894 found a missing R import in the added local/bill display lookups; the explicit import fixes compilation.

- 2026-10-27 · #166 · X12 / T3.11 notification-screen copy · merged; exact quiet-hour and nudge English preserved; actual notification-moment dismissal tested. Reminder controls in protected AccountSheet remain a Claude handoff.

- 2026-10-27 · #167 · X12 / T3.11 licence-screen copy · merged; legal names/URLs unchanged. Initial CI 40429560045 failed the AI-notice fixture after a fixed consent delay; it now awaits persistence, drives StandardTestDispatcher and cancels/awaits its ViewModel before closing Room/resetting Main.

- 2026-10-27 · #168 · X12 / T3.11 shared chrome copy · merged; account avatar, month navigation and sync spoken labels use resources; navigation boundaries and sync state logic are unchanged. Android CI 40440799515 passed all three specified gates; both separate Claude-review jobs failed execution with is_error:true and posted no findings. No workflow or settings change.

- 2026-10-27 · #169 · X12 / T3.11 action notices · merged; save/error, deletion, sharing/import/export and backup notices use resources; submission guards and operation order stay unchanged. Preserve #165 parse-display lookups when resolving the shared ViewModel merge.

- 2026-10-27 · #170 · X12 / T3.11 app-shell and update notices · merged; copy/resources only for import/share/Undo/permission/settings/ledger-open errors and downloaded-update restart prompt; release policy and foreground guards unchanged.

- 2026-10-27 · #171 · X12 / T3.11 sign-in presentation copy · merged; labels, agreement/link spacing, backdrop captions and failure explanations use resources; auth requests, permissions and session behavior are untouched.

- 2026-10-27 · #172 · X12 / T3.11 group descriptions · merged; generated payment/IOU credit and self/former/friend labels use resources; exact amounts and English defaults remain. Initial CI 40431999195 exposed the simplifier overriding a supplied self label; its display parameters now pass through, with custom-label/amount tests.

- 2026-10-27 · #173 · X12 / T3.11 activity descriptions · merged; generated payment/IOU titles, age/status/note sentences and search/display fallback labels use resources; roles, filtering and amounts are unchanged. Keep existing resource-backed More options with the localized title when resolving timeline overlap.

- 2026-10-27 · #174 · X12 / T3.11 friend-ledger descriptions · merged; generated history titles/statuses use display resources while numeric balance/current-ask calculations keep the same shared computation; notes and full amounts preserved.

- 2026-10-27 · #176 · X12 / T3.11 shared-group notices · merged; join/invite/leave/error notices resolve resources through an injected display-text function; membership, auth and sync rules unchanged.

## Questions
