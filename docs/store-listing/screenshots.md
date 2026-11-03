# Screenshot capture plan

Use six real portrait app captures, each **1080×1920** pixels or larger with the long side
at most twice the short side. Deliver PNG or JPEG in the order below; the first four form
the minimum set. No emulator.

**Produced (2026-11-03):** [`assets/`](assets/) holds all six plus the feature graphic, rendered by
`app/src/test/java/com/vaibhav/kharchapaani/ui/store/StoreListingCapture.kt` and
`FeatureGraphicCapture.kt` from the shipped screens, with the fixture below in a real Room
database and the real ViewModel, so every figure is one the app worked out. 1081×1921 px
(412×732 dp, the common Pixel width, at 420 dpi), opaque 24-bit PNG. To regenerate after a UI
change: `./gradlew :app:testDebugUnitTest --tests "*.store.*"`, then copy
`app/build/test-results/testDebugUnitTest/store-listing/*.png` here. CI keeps the same files in
each PR's `unit-test-results` artifact.

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
| 3 | 03-shared-trip.png | Splits → Groups → Weekend trip detail as it opens, before the payment: ₹1,800 spent, Priya's ₹600 share and ₹1,200 owed, the three participants and who owes whom. Dinner's own row sits below the fold; its split is screen 4. | "Keep the trip's hisaab together" |
| 4 | 04-split-details.png | Open Dinner's real edit sheet, scrolled to its split: paid by Priya, equally, three ₹600 shares, with ₹1,800 and Dinner in the save bar. Use the shipped sheet labels, don't invent a preview screen. | "Split it your way" |
| 5 | 05-settle-up.png | Priya's Splits with Rohan's ₹600 waiting for her, and its answer sheet open over it with the existing Confirm and "That's not right" controls. Stop before confirming; no external payment app or QR. | "Keep settle-ups clear" |
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
- `ScreenRenderCapture.kt` remains a design reference, not upload art: its sample data includes
  payment brands, and its 411×891 dp frame is taller than the 2:1 limit. The listing art comes
  from `ui/store/`, whose fixture uses only Cash and UPI and fictional names.
- A sheet opens in its own window, which a test capture can't see. Screens 2 and 4 are the
  sheet's real content on its surface, as it fills the phone; screen 5 is the real Splits screen
  with Material's default scrim and the real answer sheet drawn over it the way `KpModalSheet`
  draws it. No figure or control is painted on.

The assets are generated, not yet approved: the owner approves them against the installed
release before upload (README, "Before upload").
