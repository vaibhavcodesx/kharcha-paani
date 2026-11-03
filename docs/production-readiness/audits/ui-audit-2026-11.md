# UI audit, every screen (2026-11-03)

By Claude (cloud), at the owner's request: "review each page and find gaps, awkward placements, premium
UI". Stream C item 2, widened to the whole app. Fixes are listed at the end with their PRs.

## How it was looked at

There is no emulator in this container (no KVM). Every screen was instead rendered by
`app/src/test/java/com/vaibhav/kharchapaani/ui/audit/` from the **real app shell**, the real
`MainViewModel` and a real Room database seeded with a lived-in fictional account
(`AuditFixture`): a friend with a long name, one not on the app, a shared trip, a local flat, an
archived group, income, a ₹1.25 lakh purchase, a payment waiting for an answer, one turned down, an
IOU, a subscription, last month's spending. Sheets and dialogs were opened the way a tap opens them,
and every window was drawn in order, so a sheet appears over the app as it does on a phone.

Four devices, 27 screens each (105 images; a new account skips the six that need data):

| Device | What it catches |
|---|---|
| `pixel`: 412×915 dp, 420 dpi | the common phone |
| `small`: 360×640 dp | crowded rows, things below the fold |
| `font-2x`: Pixel at the largest system font | wrapping, clipping, collisions |
| `new-user`: nothing logged | empty states |

Regenerate: `./gradlew :app:testDebugUnitTest --tests "*.ui.audit.*"`, output in
`app/build/test-results/testDebugUnitTest/ui-audit/<device>/` (also each PR's `unit-test-results`
artifact). The evidence below is in [`ui-audit-2026-11/`](ui-audit-2026-11/), at half size.

**What this can't see:** motion, haptics, the keyboard, scroll feel, real fonts on the owner's phone,
TalkBack in use, edge-to-edge insets on a real device. Those stay on the phone.

## What already works

Worth saying, because most of the app holds up: every figure on every screen was the app's own and
was right (pending and turned-down payments don't move balances; an IOU isn't counted as spending).
The dark palette, the type scale and the sheet structure (eyebrow, rows, glass action bar) are
consistent everywhere. Disabled buttons say why ("Add an amount", "Type their name", "Choose who you
lent to"). Settlements read as tickets and a turned-down one shows the friend's note inline. Empty
states are warm and specific. The issues below are about the edges, not the core.

## Findings

Severity: **High** shows wrong or cut information or a broken layout; **Medium** is awkward or
inconsistent; **Low** is polish. Files are under `app/src/main/java/com/vaibhav/kharchapaani/`.

### High

| # | Where | What | Evidence |
|---|---|---|---|
| U1 | Insights, day strip | Money is cut: ₹1,27,366 reads **"1,27,36"**. Each day cell is a fixed 52 dp and the figure is `maxLines = 1` with a clip (`ui/components/SpendCharts.kt` `DayCell`). Breaks the rule "money never ellipsizes". | [pixel insights](ui-audit-2026-11/pixel--insights-1-top.png) |
| U2 | Friend history sheet | A payment Rohan says he made, still waiting for my answer, reads **"They paid you ₹0"**. The column shows the effect on the balance (nothing yet), but it reads as "paid nothing". | [pixel ledger](ui-audit-2026-11/pixel--sheet-person-ledger.png) |
| U3 | Splits, largest font | **"Log an IOU" collapses letter by letter** into a column ("Lo / g / an / IO / U"): two pills share a row and the first takes all the width. | [font-2x splits](ui-audit-2026-11/font-2x--splits-1-friends.png) |
| U4 | Settle-up sheet | The direction toggle breaks a friend's name **mid-word** at large font ("Venkataragha / van paid you"); at normal size both halves are crowded with a long name. | [font-2x settle](ui-audit-2026-11/font-2x--sheet-settle-up.png), [pixel](ui-audit-2026-11/pixel--sheet-settle-up.png) |
| U5 | Home capture card, largest font | **"State / ment"** and "Bill / photo" break mid-word: three icon+label modes in one row. | [font-2x home](ui-audit-2026-11/font-2x--home-1-top.png) |
| U6 | Group sheet, 360 dp phones | **"You're owed" is clipped to "YOU'RE"**: the third stat's label is `maxLines = 1` with no room (`GroupDetailSheet.kt` `Figure`). | [small group](ui-audit-2026-11/small--sheet-group-detail.png) |

### Medium

| # | Where | What |
|---|---|---|
| U7 | Insights donut and categories | Non-zero categories show **"0%"** (Transport ₹580, Entertainment ₹320). Say "<1%". |
| U8 | Add/edit sheet | The big amount reads **"2400"** while the bar under it says ₹2,400: the input has no Indian grouping. |
| U9 | Feed rows, largest font | The "+1" people badge wraps onto two lines ("+" over "1"). |
| U10 | Insights legend, largest font | "Groceries**1%**" with no gap; "Shoppi…", "Entertai…" cut. |
| U11 | Feed and Splits rows, largest font | Titles truncate ("Rohan paid …", "Weekly gr…", "Settl…") where two lines would fit. |
| U12 | Splits header, largest font | ₹4,913.33 runs into the divider line. |
| U13 | Add friend, colour picker | "Dimmed spots" are dark hatched bands that look like a rendering glitch, not "taken"; "Room for about 79 more" is engineer copy; the intro is four lines. |
| U14 | Subscriptions | Dates are US-style ("Nov 5, 2026") where the rest of the app says "5 Nov"; the Amount field has no ₹. |
| U15 | Splits → Groups | "Sort: Latest activity" floats, indented unlike the cards, not visibly a control; "Join" (filled, no icon) and "+ New group" (outlined) don't match. |
| U16 | Insights, nothing logged | "Spent most at —" and "Per day so far ₹0" tiles, Ask chips that look tappable under "there's nothing to read yet", and Export with nothing to export. |
| U17 | Home, small phones | On 360×640 the first screen shows **no entries**: hero and capture card fill it. |
| U18 | Settle-up sheet | "Ask Venkataraghavan to pay" is both the section title and the button under it. |
| U19 | Payment answer sheet | The ₹ in "Rohan says they paid you ₹300" falls back to a thin font (the title face lacks the glyph). |

### Low

| # | Where | What |
|---|---|---|
| U20 | Every tab header | A small grey dot on the account avatar, never explained (it's the sync state). |
| U21 | Feed | Only the IOU row has a chevron. |
| U22 | Feed and Splits | A payment still waiting for my answer already shows "+₹300". |
| U23 | Friend history, Who pays whom | Expenses all use the split icon (Home uses category icons); "pays you" uses the IOU icon. |
| U24 | Insights | "Spent most **at** Laptop" assumes the merchant is a place. |
| U25 | Account sheet | A long paragraph of version history ("Ones from before version 1.3…"). |
| U26 | Insights header | The subtitle wraps to two lines next to Export and the avatar. |
| U27 | Home hero | Paise at hero size (₹1,29,066.**17**) add noise to the one number that should read at a glance. |
| U28 | Splits, new account | "No friends yet" body runs seven lines. |

## Questions for the owner

1. **AI notice (D2 copy):** "While Kharcha Paani is free, Google may use…" implies it won't stay
   free, which brushes against D5 (no pricing hints). It's legally reviewed wording, so it's yours:
   keep, or "Google may use…" without the clause? [evidence](ui-audit-2026-11/pixel--dialog-ai-notice.png)
2. **Hero paise (U27):** show whole rupees in the Home hero only (rows and sheets keep paise)?
3. **Small phones (U17):** shrink the hero and capture card on short screens so the first entries show?

## Fixes

Each batch is a small `[claude]` PR with before/after captures from this harness. Product fixes wait
for the 1.6 freeze; shared files are listed on #188 before editing.

| Batch | Findings | PR |
|---|---|---|
| A. Money is never cut or misread | U1, U2, U6, U7, U9 | (opening) |
| B. The largest font | U3, U4, U5, U10, U11, U12 | next |
| C. Consistency and empty states | U8, U14, U15, U16, U18, U21, U24 | after B; U13, U17, U19, U27 need a design call first |

## Check on the phone

The owner's phone at its normal and largest font: Home, Splits (both tabs), Insights (scroll to the day
strip), a friend's history, and settle up with the friend whose name is longest. Anything here that
looks different on the phone is worth a line on #188.
