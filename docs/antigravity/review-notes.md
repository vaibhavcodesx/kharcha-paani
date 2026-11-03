# Review notes: PRs #133, #135, #136, #138, #140 and #141

F3, reviewed against the six merged diffs and current `main` `ebfc2b6` on 2026-11-04.
This is a source and test-coverage review. No tests were run, original test PNGs visually
inspected, phone checks performed or live services queried for this revision. Writing a PNG
is not evidence that someone inspected it.

## Scope and evidence

| PR | Merged commit | Actual change | Tests in that PR |
|---|---|---|---|
| [#133](https://github.com/vaibhavcodesx/kharcha-paani/pull/133) | `e72df17` | Conditional offline advice before account/Play-services recovery | Two `SignInScreenTest` component assertions |
| [#135](https://github.com/vaibhavcodesx/kharcha-paani/pull/135) | `600ed95` | Home gallery action reads Bill photo | Exact label assertion in real-VM/Room app-shell test |
| [#136](https://github.com/vaibhavcodesx/kharcha-paani/pull/136) | `2f192c3` | Fresh statement date formatters per access | Eight-worker parser concurrency test |
| [#138](https://github.com/vaibhavcodesx/kharcha-paani/pull/138) | `250506c` | Growing group cards and fitted full money strings | Real Room/repository fixture, real VM construction, TripCard layout/open callback |
| [#140](https://github.com/vaibhavcodesx/kharcha-paani/pull/140) | `9bae86e` | Insights cash-flow/per-day fitting and ring-centre accessibility | Real Room/VM analytics rendered in InsightsScreen |
| [#141](https://github.com/vaibhavcodesx/kharcha-paani/pull/141) | `4db2674` | Confirmation summary fitting and large-text stacking | GlassActionBar layout and a real VM/Room save |

Only these six PRs are assessed; #137 is outside F3. The hashes above are merged commits,
not earlier development heads. Subsequent string-resource moves retain the relevant English.

## #133: offline recovery in sign-in notices

`ui/auth/SignInScreen.kt` changes only display messages for `NoGoogleAccount` and
`GoogleUnavailable`. Conditional “If you're offline, connect and try again” precedes account
or Play-services advice, without claiming that an ambiguous picker failure proves no network.
The two component tests pin that copy; they do not perform Google sign-in, inspect connectivity
or build a ViewModel. No AuthRepository, credentials or server-auth change occurs. Later X12
work puts these messages in `signin_failure_no_account` and `signin_failure_google_unavailable`.

## #135: Bill photo label

`ui/home/CaptureCard.kt` changes Scan bill to Bill photo. Its icon, `scan_receipt_button` tag
and `imagePicker.launch("image/*")` action remain. `AppShellLaunchTest` builds the real Main/
SharedGroups ViewModels with Room and asserts the label. It does not open the system picker
or prove a real photo import.

## #136: concurrent statement dates

`domain/LocalStatementParser.kt` replaces stored SimpleDateFormat instances/list with property
getters, creating fresh mutable formatters instead of sharing them across callers. Supported
patterns and date-reading logic are unchanged. `ConcurrentStatementDatesTest` starts eight
workers together, running 500 cases each over four patterns, and checks the parsed local day,
display/month labels and readsAsDate. This is parser-level coverage, not a Room import, proxy
or live statement check. No performance benchmark supports an allocation-cost claim.

## #138: group cards

`ui/components/TripCard.kt` replaces fixed heights with minimum heights and uses FlowRow for
the lower section. Above font scale 1.5, the card omits ticket notches/perforation and permits
name/metadata wrapping. The net-position column is bounded to 160 dp. GroupMoney renders the
whole formatted string with single-line BasicText and TextAutoSize.StepBased, minimum **8 sp**,
at all font scales. Layout changes do not change group totals or balance calculations.

`SplitsAccessibilityTest` seeds Room, queries LedgerRepository group details, constructs a real
MainViewModel and renders TripCard at 200%. It checks no visual overflow and one line for total
expenses, own share and net balance, then checks its open callback. It writes splits_font_2.png;
this review did not inspect that image. It does not test deletion or universal fit for every
amount, width and font scale.

## #140: Insights figures and ring centre

`ui/screens/InsightsScreen.kt` retains **two** cash-flow figures, Spent and Came in. Above font
scale 1.5 they use single-line auto-sized BasicText with an **8 sp minimum**. At normal scale
the rolling numbers remain. The net cash-flow sentence is separate; it did not become a third
auto-sized figure. Money StatTile values, including per-day spending, auto-size down to 8 sp at
all scales; non-money tiles retain their existing ellipsis behavior.

`ui/components/SpendCharts.kt` hides the ring-centre label and amount above font scale 1.5.
At normal scale its centre amount auto-sizes down to 8 sp. The spoken ring description includes
the selected category/total amount and category percentages, retaining an exact figure when
centre text is hidden. Category totals and selection calculations are unchanged.

`InsightsAccessibilityTest` seeds a large Room expense, collects actual VM monthly analytics and
renders Insights at 200%. It checks the Spent figure, per-day amount and ring description, not
every income/net figure or ring state. It writes insights_font_2.png, not viewed in this review.

**Later fix #192:** on day one, daily average equals monthly total, so the old global amount
selector could match multiple nodes. Current main deliberately uses day one and scopes the
per-day selector to sibling PER DAY SO FAR, retaining amount/overflow/one-line assertions.
This was **selector ambiguity**, not a paisa precision defect or a production fix. The solo log
records red run 41662128957: 967 tests, one expected selector failure reproducing 41270005355.

## #141: confirmation action bar

`ui/components/Sheets.kt` changes GlassActionBar to FlowRow. Above font scale 1.5, the summary
uses full width and the action sits below it. The whole summary uses single-line BasicText,
8 sp-minimum auto-sizing at all scales, retaining its theme colour. Existing 52 dp minimum
action height, enabled/busy conditions and callback remain. Detail text can still ellipsize;
it is not the money summary.

`ConfirmAccessibilityTest` renders this component at 200% with a real VM draft of ₹99,99,999.99.
It checks colour, overflow and one line, clicks Save, and asserts exactly one Room transaction
with that amount. It does not exercise the whole confirmation sheet or two rapid taps. It
writes confirm_font_2.png; this review did not inspect that image.

## Safety conclusions and acceptance limits

The six production diffs contain no Room schema/migration, Supabase/RLS, sync-engine or
credential changes. Their scopes are display copy, formatter instance ownership and layout;
no ledger/balance formula changes. No new safety blocker was identified within those diffs.
This source conclusion does not establish universal visual correctness or phone acceptance.

Release-build sign-in, picker/photo behavior and on-phone readability still need owner checks.
Later UI-audit findings and the owner's 200% deferral remain separate; these earlier tests do
not close those findings. No new product changes, fresh CI, phone, proxy or live-service
verification are claimed here.
