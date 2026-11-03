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

- 2026-10-27 · #176 · X12 / T3.11 shared-group notices · merged; join/invite/leave/error notices resolve resources through an injected display-text function; membership, auth and sync rules unchanged. Re-integrated #175 after its squash merge was confirmed; its first approval attempt was rejected on stale pending-check context, then fresh exact-head green checks authorized the successful retry.

- 2026-10-27 · #175 · X12 / T3.11 backup descriptions · merged; summaries, validation reasons and restore reports use resources; backup JSON fields/version and additive restore behavior unchanged. Consolidate the parked/protected/phone handoffs below.

- 2026-10-27 · #177 · X12 / T3.11 ask display · merged; asked-status and shared-code labels use resources, preserving calendar-day/share behavior. Outgoing message body is untouched; its existing tone conflict and localization remain the Claude handoff below.

- 2026-10-27 · #178 · X12 / T3.11 widget copy · merged; widget labels, full-amount spoken summaries and signed-out guidance use resources; analytics, data sources and widget actions stay unchanged.

- 2026-10-30 · #185 · documentation handoff · merged; refreshed the owner progress report and technical checkpoint after the six-PR queue completed (56 product merged, 0 prepared, 4 parked). Validated links/register/log/resource/scope checks; relocated the misplaced 179/180 entries to Unreleased without changing release headings. Independent review found no issues in 180–184; their new automated review jobs also passed, without changing the historical failed-review record. Phone/protected handoffs remain. Docs PR excluded from product counts.

- 2026-10-27 · #179 · X12 / T3.11 notification content · merged; channel, daily and payment-reminder text use resources with exact English; privacy redaction, stage accounting and quiet-hour/delivery gates stay unchanged.

- 2026-10-30 · #180 · X12 / T3.11 payment tag spoken labels · merged; Paid by resolves through resources, preserving payment method names/keys and full amounts; real VM/Room save and TalkBack semantics tests.

- 2026-10-30 · #181 · X12 / T3.11 support email drafts; resource templates preserve exact line breaks and user question/answer data; Robolectric captures the draft intent without sending mail · merged.

- 2026-10-30 · #182 · X12 / T3.11 split-name notices; display resources preserve matching rules and exact English, with name/percent/apostrophe parity and real VM/Room coverage · merged.

- 2026-10-30 · #183 · X12 / T3.11 statement warnings; display resources preserve canonical flags, unknown source notes and unticked selection; actual reviewed rows tested, without resuming parked X8 · merged.

- 2026-10-30 · #184 · X12 / T3.11 split self labels; resolve self by ID through entry_you in editable/locked rows, preserving canonical participant data; actual VM/Room save test · merged.

- 2026-11-03 · #193 · R2 phone checklist · merged; the post-1.5.0 PR phone lists are deduplicated and grouped, with release-build sign-in/proxy/widget checks first, throwaway-only deletion, exact owner release clicks and Play-only limits. No device/server acceptance is claimed. The owner subsequently requested a quick release in chat, authorizing the single v1.6.0 phone-test prerelease after green R3; exact build status and download steps go on #188.

- 2026-11-03 · #192 · F2 Insights first-day fixture · merged; the test uses day one of the current month and identifies the amount beside PER DAY SO FAR, preserving every money/overflow/single-line assertion. Predicted red run 41662128957 had 967 tests and exactly one selector failure, reproducing run 41270005355; no production layout, timing or stored data changed. Local Gradle skipped below the 4 GB RAM floor.

- 2026-11-03 · #190 · R1 friendly partial asks · merged; the outgoing partial-request sentence uses the decided friendly wording. Android CI 41661445167 proved both pinned and real-Room regressions fail on the old wording (968 tests, exactly 2 predicted failures); totals, ledger rows and sharing behavior remain unchanged. Local Gradle skipped below the 4 GB RAM floor; phone sharing still needs owner verification.

- 2026-11-03 · #194 · backup-preview CI fixture · merged; clear the real ViewModel and await its existing scope job before Room close/Main reset, with partial-setup guards. Run 41662753973 exposed concurrent resetMain cancellation and secondary setup failures; every test body/assertion is unchanged, no sleeps or production changes. This corrects the fixture lifecycle rather than ignoring the race.

- 2026-11-03 · #198 · R3 version 1.6.0 (7) · merged; promote the reviewed Unreleased entries without rewording, consolidate duplicate headings/markers and retain a fresh Unreleased section. The owner requested a quick release in chat, authorizing one GitHub phone-test prerelease after exact-head CI; no Play rollout or phone acceptance is claimed. Product merges freeze until #188 records the signed build result; the owner tests with docs/testing/1.6.0-device-checks.md.

- 2026-11-03 · #201 · F4 / T3.6 statement re-import · merged; exact local dates are reserved before ±1-day matches, with one saved row consumed per match. Real-Room CI 41672138721 ran 975 tests with exactly the five predicted re-import failures before production changed; the new/extra rows remain importable, original IDs/totals survive, groupmate rows are ignored and hints can be reticked. Local Gradle skipped below 4 GB free RAM; exact-head Android CI 41684982587 and independent full-diff review passed before squash merge d759d6a. Supersedes closed #130; no schema/server changes or phone verification claimed.

- 2026-11-03 · #213 · F5 / REL L1 Home cold start · merged; nullable Home snapshot waits for actual feed, people, balances and analytics, preserving search/filter and pending deletes. Real-Room app-shell red CI 41686478819 ran 977 tests with exactly the two predicted early-hero failures before production changed. Loading mark and both returning/empty paths are tested; complete snapshot checks include saved expense, friend and IOU balance. Local free RAM was 2.50 GB, below the 4 GB build floor; exact-head CI 41687876859 and independent full-diff review passed before squash merge 47e70ef. No schema/server work or phone verification claimed.

- 2026-11-03 · #216 · F7 / X12 Home strings · merged; original Home/capture/filter/day-total wording moves to resources with canonical IDs and Money.format retained. Downloaded historical run 40418934304 XML: missing Today node, not a wording or precision failure; the fixture had waited only for the streak. It now also waits for both real activity rows, with every original assertion intact and complete Home readiness from #213. Local Gradle skipped below the 4 GB floor; final exact-head CI and independent review gate merge. Supersedes parked #143. F6/#139 stays parked under the 200% font deferral reported on #188 (comment 6611367782); no phone acceptance claimed.


- 2026-11-03 · #222 · Claude cloud session log and next-session prompt · prepared for merge; docs only. `docs/claude-cloud/session-log-2026-11-03.md` records #195, #210, #212, #214, #215 and #220 with their states, the owner's decisions (200% deferred; whole-rupee hero and compact Home top on short phones approved), the no-phone render harness and the creative stream's next items; `NEXT-SESSION-PROMPT.md` is the owner's template, with a placeholder for their 1.6 improvements and screenshots.


- 2026-11-03 · #220 · BackToHomeTest fixture (Claude) · merged; the test owned no ViewModelStore, so app-shell flows queried Room after db.close() ("connection pool has been closed"), failing #195 CI 41666122928 and a local merged run. Its MainViewModel and SharedGroupsViewModel now live in a store that's cleared, with the main looper idled until their jobs finish, before Room closes. Assertions unchanged; three consecutive local runs green. Test-only.


- 2026-11-03 · #210 · Store assets, Stream C item 3 (Claude) · merged; six listing screenshots (1081×1921) and the 1024×500 feature graphic rendered by test-only `ui/store/` from the shipped screens with the fictional fixture in a real Room database. Review fixes: captures clear and await their ViewModels (incl. SharedGroupsViewModel) before Room closes; fixture dates stay in the capture's month (StoreFixtureDatesTest fails without the guard: 2026-11 for a 2026-12-01T00:02 capture). Provenance and phone checks in docs/store-listing/README.md. Test/docs only; owner approves and uploads.


- 2026-11-03 · #195 · T3.5 / REL M5 sheet-first quick add (Claude) · merged; the sheet opens on the phone's guess and the AI fills only untouched fields, then says "Updated by AI" briefly. Review blockers fixed: the draft is held at the first Save tap (large-amount dialog) and a friend added from the sheet counts as a split change. Red proof: LateAiAnswerTest, written only against pre-fix APIs, fails both cases on pre-fix main f128a87 locally ("turned the edit into a new entry", "reopened a closed sheet"); LargeAmountWhileReadingTest and the add-friend case fail with their fixes removed (Rs 20,00,000 saved for a confirmed Rs 15,00,000; the new friend dropped). Tests clear and await their ViewModel before Room closes. Exact-head CI passed before merge; no phone verification claimed (not in the 1.6.0 APK).

- 2026-11-03 · #200 · T1 / Group balances zero-sum · merged; property tests assert group balances sum to zero, shares sum to total amount, and member balances are conserved across random add, edit, delete, and settle sequences. Independent source review completed; no production code changes; exact-head CI passed before merge.



- 2026-11-04 · #206 · T6 parsed statement-row golden tests · merged; pinned and restored Asia/Kolkata so the existing one-day boundary assertions expose the fixture mixing local parsed timestamps with UTC review. Red CI 41699431887 ran 1010 tests with exactly the predicted left-grace boundary failure before the fix. Review now uses the current parser-local zone through a getter after setup; all assertions remain. No production change or PDF/offline extraction claim. Current main integrated; final fix/review/exact-head CI gate merge.

- 2026-11-03 · #202 · T2 / DebtSimplifier net preservation · merged; generative property tests verify net preservation, <= n-1 transfers, and non-negative transfers with independent whole-paisa validation. Independent source review completed; no production code changes; exact-head CI passed before merge.




- 2026-11-04 · #207 · T7 backup file coverage · merged; the full supported-field fixture now encodes and decodes the actual backup file, asserts decoded settings/object equality, and restores that decoded payload into real Room. Every prior restored-row assertion remains. Coverage claim narrowed to supported backup fields; stored IDs/sync metadata are not falsely promised. Lead integrated current main and preserved all log/changelog entries; exact-head CI passed before merge. No production or phone behavior changes.

- 2026-11-03 · #203 · T3 / SplitRounding exact paisa · merged in 06d1bbc; CI 41707085214 passed all three jobs; generative property tests verify sum equality to exact paisa, representative sample coverage, negative/all-zero weight guards, and independent whole-paisa checks. Independent source review completed; no production code changes; exact-head CI gates merge.
- 2026-11-04 · #218 · F1 review timing · merged in b688fa1; exact-head CI 41704849025 passed all three jobs and independent source review cleared the final fixture/implementation; after 750 ms, wait up to six seconds for the UI to permit a review request, retaining lifecycle cancellation and claiming cooldown only through the requester. Real-Room/actual-composable red CI 41694356770 ran 980 tests with exactly the intended four-second snackbar failure; timeout/fresh-moment control passed. Earlier wrong-clock control failure and paused-clock stall/cancellation are documented in the PR; production changed only after isolated red. Local Gradle skipped below 4 GB free RAM; final exact-head CI and independent review gate merge. No review-asking copy, balance, server or phone acceptance changes.

- 2026-11-04 · #223 · Large-amount fixture subscription · merged in 4943146; CI 41707924993 passed all three jobs; reproduce evidence is CI 41702020917 with one initial friends-list timeout in 1011 tests. Hold a test-owned friends collector and retain the original 10-second bound plus every money/split assertion. Scheduling explanation remains a hypothesis. Collector cancellation and ViewModel clear/await precede Room close. Independent review and exact-head latest-main CI gate merge; local Gradle skipped below 4 GB free RAM. No production change.

- 2026-11-03 · #204 · T4 / Money parsing and formatting · prepared for merge; invariant tests verify parse-format round-trip across representative samples of the numeric domain, symbol resilience, and formatting invariants. Independent source review completed; no production code changes; exact-head CI gates merge.

- 2026-11-04 · #205 · T5 local-parser golden table · prepared for merge; 30 deterministic cases pin current heuristic output, including English/Hinglish and known split names. Existing tests/assertions preserved; no claim of ideal natural-language understanding, no production changes. Independent source review completed; exact-head current-main three-job CI gates merge; local Gradle skipped below 4 GB free RAM.

- 2026-11-04 · #208 · T8 migration-chain coverage · prepared for merge; real legacy v2 database runs the actual chain to v10. Retained all 108 original assertion lines and added 26 assertions for second-row legacy values/defaults, membership pairings and remote UUID uniqueness. Room closes in finally. New-table writes are smoke checks, not historical rows. No production/schema migration change. Independent source review completed; current-main exact-head CI gates merge; local Gradle skipped below 4 GB free RAM.

- 2026-11-04 · #209 · T9 UPI-link coverage · prepared for merge; independent full source review found no blocker; original assertions retained, no production behavior change. Lead integrated current main with one Unreleased entry. Exact-head unit-tests, lint and release-bundle gate merge; local Gradle skipped below 4 GB free RAM. No phone acceptance claimed.

- 2026-11-04 · #221 · T13 formatting checker · correction prepared; initial red CI 41710054770 ran 1038 tests with exactly three predicted valid-format failures; second red CI 41711669426 ran 1042 tests with exactly two predicted mixed-reuse/signature failures, the initial three now passing. Checker corrections followed each isolated red; all resource assertions and five positive self-tests remain, plus incompatible reuse rejection. No production resources changed. Independent final review and current-main exact-head three-job CI gate merge; local Gradle skipped below 4 GB free RAM.

- 2026-11-04 · #217 · T11 Ask-message totals · prepared for merge; independent full source review found no blocker; original assertions retained, no production behavior change. Lead integrated current main with one Unreleased entry. Exact-head unit-tests, lint and release-bundle gate merge; local Gradle skipped below 4 GB free RAM. No phone acceptance claimed.

## Questions

- Claude handoff, X11 / #142: authoritative last-editor identity needs protected DB/sync/server work. ownerProfileId identifies the original adder; updatedAt cannot establish who edited. Do not show a guessed editor.
- Parked work update: #130 was superseded by merged #201; #132 by merged #213; #143 by merged #216. #139 remains parked under the owner deferral of 200% font work recorded on #188. Keep normal-size money complete; remaining review-prompt work #218 is draft and not green.
- X12 handoff: Home copy is done in #216. Remaining Account/reminder/auth/profile/deletion copy was outside the completed batch; follow current lead rights and auth/security hard stops. AI prompt instructions and generated/source text are not UI resources. This is not a complete Hindi build.
- Ask-to-pay handoff from #177 is resolved in #190: the partial Ask now uses the decided friendly wording with the real amounts. Sharing still needs owner phone verification.
- Keep canonical category/payment/type IDs, user names/notes, machine-readable CSV/backup fields, URLs/licence metadata and date/format patterns as data. Translation of their display equivalents must preserve stable stored values.
- Phone verification remains outstanding. The signed v1.6.0 GitHub prerelease built on November 3 and its checklist is attached to the release; owner phone/Play acceptance is not confirmed. The owner deferred 200% font-only work; normal-phone checks still apply. No Supabase changes were applied.
- Local Gradle was skipped below the repository's 4 GB free-RAM floor; the three specified Android CI jobs decide each merge. From #168 onward the separate Claude-review automation has failed execution with is_error:true and posted no code findings; Android unit-tests, lint and release-bundle still must pass on each exact head. Safe choice: follow the solo section's explicit three gates; leave workflow/provider repair to Claude and change no repository settings.
