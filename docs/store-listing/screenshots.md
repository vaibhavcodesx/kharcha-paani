# Screenshot capture plan

Use six real portrait app captures, each **1080×1920** pixels or larger with the long side
at most twice the short side. Deliver PNG or JPEG in the order below; the first four form
the minimum set. No emulator. Capture on the owner's phone with a dedicated demo account,
or use real Compose rendering once an approved listing fixture exists.

## Shared fixture

Use fictional first names: Priya (signed-in demo user), Rohan and Ankit. Use generic merchants
such as Chai stall, Groceries and Dinner. Payment method labels: Cash or UPI. No real names,
emails, UPI IDs, avatars, receipts, bank statements, invite codes or scannable payment codes.
Use the same chosen month throughout, and record the capture build/version with final assets.

A reproducible base: in a shared group named Weekend trip, Priya pays ₹1,800 for Dinner,
split equally among three. Before settlement, Rohan and Ankit each have a ₹600 share and
Priya's own share is ₹600. Rohan then records ₹600 to Priya, still awaiting confirmation.
Screens 3–4 use the state before this payment; screen 5 uses the pending state afterwards.
Keep personal entries separate: Chai stall ₹40, Groceries ₹1,260 and Bus ₹80, all Cash.
Let the app calculate displayed totals; don't paint numbers onto the UI or force Insights
totals to equal group gross spending. If a proposed state cannot be reproduced, revise the
plan before capture rather than fabricate it.

## Ordered captures

| # | Output | Screen and exact state | Caption |
|---|---|---|---|
| 1 | 01-daily-spending.png | Home, populated activity feed in the chosen month; personal entries above visible, no sheet or keyboard covering the feed. | "Everyday spending, in one place" |
| 2 | 02-add-an-expense.png | Add entry sheet, manually enter Chai stall, ₹40, Food, Cash; show fields before Save. Use a separate draft so the saved fixture isn't doubled. No AI result needed. | "Add the little things too" |
| 3 | 03-shared-trip.png | Splits → Groups → Weekend trip detail, Dinner ₹1,800 visible before the payment; show three participants and the actual balance summary. | "Keep the trip's hisaab together" |
| 4 | 04-split-details.png | Open Dinner's real edit/detail sheet; show equal split, three ₹600 shares and ₹1,800 total. Use the shipped sheet labels, don't invent a preview screen. | "Split it your way" |
| 5 | 05-settle-up.png | Priya's view of Rohan's ₹600 pending payment, with the existing confirmation controls visible. Stop before confirming; no external payment app or QR. | "Keep settle-ups clear" |
| 6 | 06-monthly-insights.png | Insights for the same month with category totals and chart populated from the fixture; no generated answer or loading/error state. | "See where the month went" |

## Art treatment and checks

- Use the shipped theme, typography, icons and rupee formatting with Indian grouping. Keep
  whole money values visible. Keep content inside status/navigation insets and above the dock.
- Prefer clean screen captures. If adding a caption strip, put it outside the unaltered app
  capture on a portrait canvas, preserve aspect ratio, and retain at least 1080×1920 overall.
  Don't remove controls, retouch amounts, invent screens or add device frames or Play badges.
- Captions explain the view; avoid rankings, pricing, install counts, download prompts and
  financial-product wording excluded by X1. Show no third-party payment logos or brands.
- Hide personal status-bar notifications before capture. Inspect every image at full size
  and at a phone-sized preview; check for clipped amounts, personal data and capture artefacts.
- The existing `ScreenRenderCapture.kt` is a reference, not upload-ready art: its sample data
  includes payment brands and restricted payment-method wording, and its 411×891 dp fixture
  is taller than the 2:1 limit. Do not upscale/crop it blindly. A separate compliant capture
  fixture or a phone capture is needed; changing the Kotlin fixture is outside this docs item.

Final PNGs have not been generated or approved by X1. Keep raw captures with the final art
so the owner can verify that each asset represents the installed release.
