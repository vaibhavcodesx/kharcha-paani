# Solo progress — 27 October 2026

**50 product PRs merged; 6 prepared text-cleanup PRs remain; 4 PRs are parked.** Work is paused at the owner's request to conserve the weekly limit. These are code/integration results, not a claim that the app is released or phone-tested.

This is an update alongside the [master readiness tracker](README.md), not a replacement for it. Its older status labels have not been edited because that file belongs to Claude. See the [solo log](../agents/solo-log.md) for decisions and the [restart guide](../agents/codex-resume-2026-10-27.md) for exact technical state.

## Done in this batch

| Area | What works now on main | PRs |
|---|---|---|
| Groups | Settlements view, group sorting, member colour dots, recent eligible group picked when splitting | [125](https://github.com/vaibhavcodesx/kharcha-paani/pull/125), [127](https://github.com/vaibhavcodesx/kharcha-paani/pull/127), [128](https://github.com/vaibhavcodesx/kharcha-paani/pull/128), [123](https://github.com/vaibhavcodesx/kharcha-paani/pull/123) |
| App updates | Flexible Play updates; explicit restart after download | [129](https://github.com/vaibhavcodesx/kharcha-paani/pull/129) |
| Reviews | Rare Play review attempt after a confirmed settlement; no request for stars and no prompt after an error | [131](https://github.com/vaibhavcodesx/kharcha-paani/pull/131) |
| Small fixes | Clearer offline sign-in wording, successful-money-action haptics, Bill photo naming, thread-safe statement date parsing | [133](https://github.com/vaibhavcodesx/kharcha-paani/pull/133)–[136](https://github.com/vaibhavcodesx/kharcha-paani/pull/136) |
| Accessibility | Navigation dock, Splits/group cards, Insights and money-confirmation bars handle larger text better | [137](https://github.com/vaibhavcodesx/kharcha-paani/pull/137), [138](https://github.com/vaibhavcodesx/kharcha-paani/pull/138), [140](https://github.com/vaibhavcodesx/kharcha-paani/pull/140), [141](https://github.com/vaibhavcodesx/kharcha-paani/pull/141) |
| Search | Friends/participants and exact amounts are searchable | [142](https://github.com/vaibhavcodesx/kharcha-paani/pull/142) |
| Text resources | Navigation, Splits, Insights, entry/payment/group/friend forms, most sheets, timeline/history descriptions, backup/error notices, sign-in display, AI presentation, Ask status and widgets now resolve resources | [144](https://github.com/vaibhavcodesx/kharcha-paani/pull/144)–[178](https://github.com/vaibhavcodesx/kharcha-paani/pull/178); full register below |

English wording and money/data behavior were preserved during the text work. **A Hindi build is not complete:** Home and protected account/auth text still need work, translations are not supplied, and stored category/payment identifiers must remain stable.

## Next: finish these six PRs, in order

| PR | Remaining area | Resume action |
|---|---|---|
| [179](https://github.com/vaibhavcodesx/kharcha-paani/pull/179) | Notification channels, daily/private/public payment-reminder text | Latest main is integrated. Final CI was running at the snapshot; refresh it before any merge. |
| [180](https://github.com/vaibhavcodesx/kharcha-paani/pull/180) | Payment tag spoken label | Integrate the then-latest main and run final CI. |
| [181](https://github.com/vaibhavcodesx/kharcha-paani/pull/181) | AI-report email draft | Same; no email is sent by the tests. |
| [182](https://github.com/vaibhavcodesx/kharcha-paani/pull/182) | Unknown/ambiguous split-name notices | Same; matching behavior stays unchanged. |
| [183](https://github.com/vaibhavcodesx/kharcha-paani/pull/183) | Statement warning text | Same; does not resume the parked duplicate-import bug. |
| [184](https://github.com/vaibhavcodesx/kharcha-paani/pull/184) | Resource-backed self labels in split rows | Same; canonical participant data stays unchanged. |

PRs 180–184 passed their initial Android CI, but that is **not** a merge gate after newer main changes. Each needs a fresh integrated head and its own green unit-tests, lint and release-bundle.

## Parked: do not silently count these as done

The solo contract stops an item after two failed Android CI runs; all four remain open and unmerged.

| PR | Still missing | Recorded next step |
|---|---|---|
| [130](https://github.com/vaibhavcodesx/kharcha-paani/pull/130) | Statement re-import detection | Reserve all exact matches before near-date matching, so a new neighbouring purchase cannot consume an existing match. |
| [132](https://github.com/vaibhavcodesx/kharcha-paani/pull/132) | Cold-start empty-state flash | Build Home readiness from a complete repository snapshot; don't infer loaded state from default-empty StateFlows. |
| [139](https://github.com/vaibhavcodesx/kharcha-paani/pull/139) | Home large-text money layout | Fit the whole balance when animated digits cannot fit. |
| [143](https://github.com/vaibhavcodesx/kharcha-paani/pull/143) | Home text extraction | Repair the regression fixture to await both feed and analytics; extraction remains unmerged. |

## Work outside this Codex pass

- **Edited by X:** search landed, but accurate last-editor identity needs protected database/sync/server changes. Original adder plus edit time cannot establish who edited. See [142](https://github.com/vaibhavcodesx/kharcha-paani/pull/142).
- **Account/auth/reminder copy:** protected AccountSheet and owner-review account-deletion/auth work stay with Claude. The existing outgoing Ask body also needs a friendly-wording decision before its separate localization; see [177](https://github.com/vaibhavcodesx/kharcha-paani/pull/177).
- **Claude/owner work:** account deletion [103](https://github.com/vaibhavcodesx/kharcha-paani/pull/103), R8 [71](https://github.com/vaibhavcodesx/kharcha-paani/pull/71), Dependabot and releases were not touched. Abuse limits, remaining sync/notification work and other unassigned Tier 3 items remain governed by the [master tracker](README.md#5-the-work-in-tiers) and [lane contract](../agents/work-split.md).
- **Already present before this batch:** Claude's opening [solo-log snapshot](../agents/solo-log.md#state-when-the-period-began-2026-10-27-written-by-claude) records the proxy, share-sum guard, DB hygiene, Say the split, crash-report switch and categories as landed. That is inherited owner-side evidence, not a fresh live Supabase verification by Codex.

## Verification and release plan

1. Finish 179–184 under the current lane rules, then audit scope, resources, PR states and the log once more.
2. Run the [phone checklist format](../testing/1.5.0-device-checks.md): groups/splits/history, large text and TalkBack, notifications/lock-screen redaction, and Play-installed update/review behavior. Accessibility Scanner/pre-launch reports remain outstanding.
3. Claude handles protected/owner-review work and the parked follow-ups. The owner handles Play Console/legal/consoles and the eventual release under the [master tracker](README.md#4-things-only-the-owner-can-do-consoles-money-legal).

Every merged product PR passed the three required Android CI jobs on its integrated head. Local Gradle was skipped below the repository's 4 GB free-RAM floor. No emulator, phone acceptance, release workflow, repository-settings change or Supabase action was performed. Separate Claude-review automation has failed execution without posting findings on recent PRs; that limitation is recorded in the solo log and was not disguised as a passing review.

## Source documents

- [Master readiness/work order](README.md)
- [Reliability and UX audit](audits/reliability-ux.md)
- [Security audit](audits/security.md)
- [Play Store audit](audits/play-store.md)
- [Notifications audit](audits/notifications.md)
- [Notion/reels audit](audits/notion-reels.md)
- [Current work split](../agents/work-split.md), [owner feature notes](../../TODOS.md), [product rules](../../PRD.md)

## Full product PR register

One line per PR. This register excludes the documentation-only handoff PR. Snapshot of GitHub at wrap-up; follow each link for current state.

<details>
<summary>Show all 60 product PRs</summary>

| PR | State | Change |
|---|---|---|
| [#123](https://github.com/vaibhavcodesx/kharcha-paani/pull/123) | Merged | X5: Default new splits to the recent group |
| [#125](https://github.com/vaibhavcodesx/kharcha-paani/pull/125) | Merged | X5: Add a group settlements view |
| [#127](https://github.com/vaibhavcodesx/kharcha-paani/pull/127) | Merged | X5: Remember group sorting choices |
| [#128](https://github.com/vaibhavcodesx/kharcha-paani/pull/128) | Merged | X5: Show member colours in group expenses |
| [#129](https://github.com/vaibhavcodesx/kharcha-paani/pull/129) | Merged | X6: Offer flexible in-app updates |
| [#130](https://github.com/vaibhavcodesx/kharcha-paani/pull/130) | Parked: two failures | X8: Leave statement re-imports unticked |
| [#131](https://github.com/vaibhavcodesx/kharcha-paani/pull/131) | Merged | X9: Request rare Play reviews after confirmed settlements |
| [#132](https://github.com/vaibhavcodesx/kharcha-paani/pull/132) | Parked: two failures | X10: Wait for the ledger before showing Home |
| [#133](https://github.com/vaibhavcodesx/kharcha-paani/pull/133) | Merged | X10: Explain offline recovery in sign-in notices |
| [#134](https://github.com/vaibhavcodesx/kharcha-paani/pull/134) | Merged | X10: Confirm successful money writes with haptics |
| [#135](https://github.com/vaibhavcodesx/kharcha-paani/pull/135) | Merged | X10: Call the gallery action Bill photo |
| [#136](https://github.com/vaibhavcodesx/kharcha-paani/pull/136) | Merged | X10: Keep statement date parsing safe across threads |
| [#137](https://github.com/vaibhavcodesx/kharcha-paani/pull/137) | Merged | X7: Keep dock navigation accessible at large fonts |
| [#138](https://github.com/vaibhavcodesx/kharcha-paani/pull/138) | Merged | X7: Fit full group amounts at large font sizes |
| [#139](https://github.com/vaibhavcodesx/kharcha-paani/pull/139) | Parked: two failures | X7: Keep Home money figures whole at large fonts |
| [#140](https://github.com/vaibhavcodesx/kharcha-paani/pull/140) | Merged | X7: Show complete Insights money values at large fonts |
| [#141](https://github.com/vaibhavcodesx/kharcha-paani/pull/141) | Merged | X7: Keep confirmation amounts readable at large fonts |
| [#142](https://github.com/vaibhavcodesx/kharcha-paani/pull/142) | Merged | X11: Find related people and amounts in Activity search |
| [#143](https://github.com/vaibhavcodesx/kharcha-paani/pull/143) | Parked: two failures | X12: Move Home copy into string resources |
| [#144](https://github.com/vaibhavcodesx/kharcha-paani/pull/144) | Merged | X12: Move navigation labels into string resources |
| [#145](https://github.com/vaibhavcodesx/kharcha-paani/pull/145) | Merged | X12: Move Splits copy into string resources |
| [#146](https://github.com/vaibhavcodesx/kharcha-paani/pull/146) | Merged | X12: Move Insights copy into string resources |
| [#147](https://github.com/vaibhavcodesx/kharcha-paani/pull/147) | Merged | X12: Move entry review copy into string resources |
| [#148](https://github.com/vaibhavcodesx/kharcha-paani/pull/148) | Merged | X12: Move shared sheet controls into string resources |
| [#149](https://github.com/vaibhavcodesx/kharcha-paani/pull/149) | Merged | X12: Move statement review copy into string resources |
| [#150](https://github.com/vaibhavcodesx/kharcha-paani/pull/150) | Merged | X12: Move export picker copy into string resources |
| [#151](https://github.com/vaibhavcodesx/kharcha-paani/pull/151) | Merged | X12: Move payment form copy into string resources |
| [#152](https://github.com/vaibhavcodesx/kharcha-paani/pull/152) | Merged | X12: Move backup preview copy into string resources |
| [#153](https://github.com/vaibhavcodesx/kharcha-paani/pull/153) | Merged | X12: Move friend and colour picker copy into resources |
| [#154](https://github.com/vaibhavcodesx/kharcha-paani/pull/154) | Merged | X12: Move group detail copy into string resources |
| [#155](https://github.com/vaibhavcodesx/kharcha-paani/pull/155) | Merged | X12: Move group creation copy into string resources |
| [#156](https://github.com/vaibhavcodesx/kharcha-paani/pull/156) | Merged | X12: Move friend history screen copy into resources |
| [#157](https://github.com/vaibhavcodesx/kharcha-paani/pull/157) | Merged | X12: Move expense timeline copy into string resources |
| [#158](https://github.com/vaibhavcodesx/kharcha-paani/pull/158) | Merged | X12: Move settlement timeline copy into string resources |
| [#159](https://github.com/vaibhavcodesx/kharcha-paani/pull/159) | Merged | X12: Move subscription copy into string resources |
| [#160](https://github.com/vaibhavcodesx/kharcha-paani/pull/160) | Merged | X12: Move shared group control copy into resources |
| [#161](https://github.com/vaibhavcodesx/kharcha-paani/pull/161) | Merged | X12: Move category control copy into string resources |
| [#162](https://github.com/vaibhavcodesx/kharcha-paani/pull/162) | Merged | X12: Move debt management copy into string resources |
| [#163](https://github.com/vaibhavcodesx/kharcha-paani/pull/163) | Merged | X12: Move UPI card copy into string resources |
| [#164](https://github.com/vaibhavcodesx/kharcha-paani/pull/164) | Merged | X12: Move bill photo copy into string resources |
| [#165](https://github.com/vaibhavcodesx/kharcha-paani/pull/165) | Merged | X12: Move AI presentation copy into string resources |
| [#166](https://github.com/vaibhavcodesx/kharcha-paani/pull/166) | Merged | X12: Move notification screen copy into resources |
| [#167](https://github.com/vaibhavcodesx/kharcha-paani/pull/167) | Merged | X12: Move licence screen copy into string resources |
| [#168](https://github.com/vaibhavcodesx/kharcha-paani/pull/168) | Merged | X12: Move shared chrome copy into string resources |
| [#169](https://github.com/vaibhavcodesx/kharcha-paani/pull/169) | Merged | X12: Move action notices into string resources |
| [#170](https://github.com/vaibhavcodesx/kharcha-paani/pull/170) | Merged | X12: Move app shell copy into string resources |
| [#171](https://github.com/vaibhavcodesx/kharcha-paani/pull/171) | Merged | X12: Move sign-in display copy into string resources |
| [#172](https://github.com/vaibhavcodesx/kharcha-paani/pull/172) | Merged | X12: Move group descriptions into string resources |
| [#173](https://github.com/vaibhavcodesx/kharcha-paani/pull/173) | Merged | X12: Move activity descriptions into string resources |
| [#174](https://github.com/vaibhavcodesx/kharcha-paani/pull/174) | Merged | X12: Move friend ledger descriptions into resources |
| [#175](https://github.com/vaibhavcodesx/kharcha-paani/pull/175) | Merged | X12: Move backup descriptions into string resources |
| [#176](https://github.com/vaibhavcodesx/kharcha-paani/pull/176) | Merged | X12: Move shared group notices into string resources |
| [#177](https://github.com/vaibhavcodesx/kharcha-paani/pull/177) | Merged | X12: Move friend ask status copy into resources |
| [#178](https://github.com/vaibhavcodesx/kharcha-paani/pull/178) | Merged | X12: Move widget copy into string resources |
| [#179](https://github.com/vaibhavcodesx/kharcha-paani/pull/179) | Prepared; not merged | X12: Move notification content into string resources |
| [#180](https://github.com/vaibhavcodesx/kharcha-paani/pull/180) | Prepared; not merged | X12: Move payment tag spoken labels into resources |
| [#181](https://github.com/vaibhavcodesx/kharcha-paani/pull/181) | Prepared; not merged | X12: Move support draft copy into string resources |
| [#182](https://github.com/vaibhavcodesx/kharcha-paani/pull/182) | Prepared; not merged | X12: Move split notice copy into presentation resources |
| [#183](https://github.com/vaibhavcodesx/kharcha-paani/pull/183) | Prepared; not merged | X12: Move statement warning display copy into resources |
| [#184](https://github.com/vaibhavcodesx/kharcha-paani/pull/184) | Prepared; not merged | X12: Resolve split self labels from resources |

</details>
