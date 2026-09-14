# Part B UI/UX audit (before any changes)

2026-09-14. This is a code-level audit of the Compose source against the PRD's Part B anti-slop checklist, plus the owner's direction:

- a Liquid Glass depth hierarchy;
- an amber→lime identity;
- a structural rework rather than decoration;
- a bottom nav redesign;
- real typography;
- considered shapes.

Numbers below were measured with greps over `app/src/main` and a WCAG contrast calculation, not estimated. Screenshots referenced are the owner's (Home, Splits & Trips, Add Expense).

Triage: **[High]** fix in this pass · **[Medium]** fix in this pass if it fits the batch · **Nit** optional.

---

## What already works (keep)

- **Palette tokens are sound.** Amber `#FF7A3D` passes 6.3–7.5:1 on every surface, lime `#C6F24C` 12.5–15:1, and off-white 14–17:1. Charcoal text on amber or lime buttons is 7.5:1 and 15:1.
- **The font pairing is right on paper:** Outfit for display, Space Grotesk for numbers, Plus Jakarta Sans for body. All three load through downloadable Google Fonts with sensible weights.
- **Motion has a foundation.** `bounceClick` / `pressBounce` have haptics, and there is a rolling number component and a shimmer modifier.
- **Part A's newer screens** (sign-in, join sheet, shared group panel) already use clearer, human copy ("split it with friends, and settle up without the awkward maths").

---

## 1. System-level findings (these affect every screen)

### Typography: the scale exists but isn't what screens use **[High]**
- **268 `typography.X.copy(...)` overrides versus 8 plain uses.** Almost every text re-specifies weight, size or family at the call site, so the scale doesn't actually define the hierarchy.
- **141 overrides force `FontWeight.Bold`** (plus 10 ExtraBold). When nearly everything is bold, nothing is emphasized: titles, labels, amounts and chips all sit at the same visual weight.
- **Tiny text everywhere.**
  - `bodySmall` is 11sp and `labelSmall` 10sp, and they are the two most used roles (56 and 46 uses).
  - There are 30 more explicit `fontSize = 10.sp / 11.sp` overrides, plus one 9sp and one 8sp.
  - Metadata lines ("Today, 03:57 PM • POPUPI") and nav labels are all 10–11sp.
- **Space Grotesk isn't in the scale.** Numbers get `fontFamily = numericFontFamily` patched onto body or title styles in 24 places, with no tabular figures (`tnum`). Digits shift width while the balance animates.
- **Outfit barely appears.** It is used for headline/title roles, which screens rarely use: `headline*` 10 uses and `display*` 5, against 100+ label/body uses. It is also patched on by hand in HomeScreen and AccountSheet. The app mostly reads as Plus Jakarta Bold at 11–15sp.
- **Screen titles are generic and use different styles on each screen:**
  - Home: `headlineMedium` at 21sp ExtraBold, "Kharcha Paani".
  - Splits: `titleLarge`, "Split & Trip Ledger".
  - Insights: "Data & Analytics".
  - Each has a different subtitle tagline.

### Shapes: 11 radii, no system **[High]**
- **Corner radii used:** 12dp ×54, 14dp ×39, 16dp ×30, 10dp ×24, 20dp ×16, 8dp ×12, 22dp ×7, 6dp ×6, 18dp ×6, 24dp ×4, 50% ×1. On top of that there are 50 `CircleShape`s, and no `Shapes` in the theme.
- **Every container is the same object:** a rounded rectangle with a 1dp `CharcoalBorder` outline on a slightly lighter charcoal. Hero card, capture bar, filter chips, transaction rows, group cards, summary cards and dialog panels differ only by a few dp of radius.
- **Buttons** are M3 `Button` / `OutlinedButton` with 12–14dp radii, and chips are 10dp rounded rectangles. They look like "plain pill / plain square", exactly as described.

### Surfaces and depth: everything sits on one flat plane **[High]**
- **Only 3 gradients and 0 blurs in the app,** and no elevation or tonal distinction. Floating layers (bottom sheets, dialogs, FAB, nav bar) use the same opaque charcoal as content cards, so nothing reads as above the content.
- **Two languages for "floating":**
  - 7 centered `Dialog`s: Add friend, Create group, Delete friend, IOU, Settle up, Simplify debts, Statement import.
  - 6 `ModalBottomSheet`s: Confirm, Group detail, Person ledger, Account, Join, Statement import.
  - Similar tasks, such as Settle up (dialog) versus Group detail (sheet), open in different containers.
- **The one existing gradient** on the Home hero, `CharcoalElevated → CharcoalCard`, is almost invisible and says nothing about the brand.

### Color and contrast **[High]**
- **`SubMutedWarmGray #6B665E` fails WCAG AA text contrast on every surface (2.85–3.41:1, needs 4.5:1).** It is the theme default for `bodySmall` and `labelSmall`, so it drives about 100 of the smallest text uses: the hardest-to-read text also has the least contrast.
- **The 1dp `CharcoalBorder #2E2B27` measures 1.15–1.38:1.** That's fine as decoration, but it is the only boundary on unselected chips, inputs and toggles, and UI component boundaries need 3:1.
- **The accent is "slapped on", not systemic.** Amber marks the FAB, the selected nav pill, primary buttons, expense amounts, icons and warnings. Lime marks income, "owed to you", the selected member chips and the streak. Neither informs shapes, focus states or loading, and there is no gradient or tint relationship between the two colors.

### Spacing rhythm **[Medium]**
- **Off-grid values dominate:**
  - paddings are 12 ×25, 14 ×20, 10 ×16, 8 ×13 and 20 ×12;
  - spacers are 8 ×30, 6 ×30, 16 ×23, 12 ×23, 2 ×20, 10 ×17 and 14 ×16.
- **No hierarchy between related and unrelated content.** Home uses a uniform `spacedBy(14.dp)` for every section: header, hero, capture bar, feed header and each row.

### Icons **[Medium]**
- **109 stock Material icon uses (49 distinct), all `Icons.Default` / Filled or Outlined, and no custom vectors** other than the logo and the Google G.
- **Category icons** (Food, Transport, …) and **nav icons** (`ReceiptLong`, `Group`, `AutoAwesome`) are the default Material set.
- Deprecated non-mirrored icons are still used: `ReceiptLong`, `TrendingUp/Down`, `ArrowForward`, `Sort`.

### Motion **[Medium]**
- **`bounceClick` runs the same `MediumBouncy` spring at 0.94 scale on every card, chip and row,** including every transaction in a list. Bounce everywhere reads as decoration.
- **The "rolling-digit" balance is a count-up tween, not a digit roll.** It animates a `Float`, which also causes a display bug: see §3.

### Money formatting is inconsistent **[High] (includes the reported bug)**
- **7 different formats:**
  - `String.format("%.2f")` ×15, with no locale;
  - `"%.0f"` ×8, with no locale;
  - `Locale.US "%.0f"` ×7;
  - `Locale.US "%.2f"` ×2;
  - `"%.1f"` ×3;
  - `Locale.US "%,.2f"` ×1;
  - plus `NumberFormat(en-IN)` with 0 fraction digits in 3 places.
- **The ₹34.50 → ₹35 bug is `%.0f`.** Transaction rows, group rows, share rows, donut centre and table sums all drop the paisa.
- **Locale-less `String.format` renders "34,50"** on devices whose locale uses a comma decimal.
- **Only the en-IN `NumberFormat` does Indian grouping** (1,00,000). Every other format prints "100000.00".

---

## 2. The three screenshot moments (PRD priority)

### Quick-add confirm sheet (`ConfirmBottomSheet`, 1001 lines) **[High]**
- **Structure is one long vertical form.** In order, each section a bordered rounded box: header, expense/income toggle, huge amount field, date card plus 3 preset chips, merchant field, category chips row, payment chips row, group chips row, "For me / Split" toggle, split editor, note field, save button.
  - The **amount** is the only thing that looks important. **Parsed details** (merchant, category, payment) are presented as a form to fill, not a confirmation of what the AI understood.
  - On the screenshot the save button is below the fold with a dim, almost disabled-looking surface.
- **Liquid Glass fit:** this is the app's most "floating" layer and the best place for the glass material. The sheet body stays solid for legibility, and the glass is the chrome around it: the drag handle bar and a floating save bar.
- **Save sits at the very end of a scroll.** While the amount is empty or 0 it is disabled, styled `CharcoalElevated` with `SubMutedWarmGray` text at 2.85:1, so it's barely legible. Nothing on the sheet says why it can't be tapped.

### Balance hero (Home) **[High]**
- A generic "big number in a bordered card" with an uppercase eyebrow ("NET SPLIT BALANCE"), a divider, and two secondary metrics.
- The hero number is amber or lime depending on sign, and `displayMedium` 30sp. That is about the same visual weight as the app title directly above it.
- The count-up animation isn't a digit roll and loses precision (§3).
- **It isn't obvious what the number means or what to do next.** "Simplify" is a 11sp chip.

### Widget (`widget_splitflow.xml`) **[High]**
- **Still branded "⚡ SplitFlow",** the AI Studio name.
- **Hardcoded colors don't match the theme:** `#4ADE80` Tailwind green (not the brand lime), `#F5F3EE`, `#8C8881`.
- **System font at 11–20sp.** Three buttons use emoji as icons ("🎙️ Speak", "👥 Splits").
- **Values show whole rupees only** (`maximumFractionDigits = 0`).

---

## 3. Screen-by-screen

### App shell (MainActivity): bottom nav and FAB **[High]**
- **The nav is a stock `NavigationBar` with 3 `NavigationBarItem`s:** default layout, M3 pill indicator filled amber, stock icons, and 11sp labels. It is visually indistinguishable from any Material template.
- **The FAB is a stock `FloatingActionButton` (`CircleShape`)** pushed up with `padding(bottom = 70.dp)`. On the Home screenshot it overlaps the "train ticket" row's amount.
- **Tab switching is a plain crossfade.**
- **The top area isn't a real app bar:** each screen draws its own title row inside the list, so titles scroll away inconsistently.

### Home (`HomeScreen`, 555 lines) **[High]**
Structure, top to bottom:
1. Header: logo, title, tagline, streak badge, IOU icon, account avatar.
2. Hero balance card.
3. Quick capture bar (text field with mic, Scan Bill, Import PDF, two preset chips).
4. "Recent Activity" with a search icon.
5. Four filter chips.
6. A flat list of identical bordered cards.

Findings:
- **Card soup.** Everything is the same bordered box at `spacedBy(14.dp)`, so there is no primary/secondary distinction. The feed has no day grouping, so "Today, 03:57 PM" is repeated on every row.
- **Header is overloaded:** 6 elements and a tagline in 48dp. The streak badge ("1d streak") competes with the balance.
- **Duplicate click handler left over:** the Home IOU `IconButton` still carries `bounceClick(onClick)` (lines 203–210).
- **Transaction row** (`TransactionItemCard`):
  - category icon box, merchant, a "2p" participant chip, an 11sp metadata line, a 3-dot menu, and an amount pair "-₹35 / Total: ₹69";
  - **the amount loses paisa (bug)**;
  - "2p" is cryptic;
  - the 3-dot menu duplicates swipe actions.
- **Swipe-to-delete bug.**
  - `SwipeToDismissBox` returns `true` from `confirmValueChange` before the ViewModel decides, so the card animates off-screen. When the repository refuses (a groupmate's expense, or an archived group), the row stays in the data and "comes back", or stays half-dismissed until recomposition.
  - Swipe-to-edit on others' expenses opens nothing but a toast.
- **Empty state is the checklist's exact slop tell:** centered `ReceiptLong` icon, bold "No transactions logged yet", gray subtext.

### Splits & Trips (`SplitsScreen`, 462 lines) **[High]**
Structure, top to bottom:
1. Title and tagline with a "+ New Group / Add Friend" button.
2. Friends | Trips & Groups segmented tabs.
3. "Total to Collect / Total to Pay" card.
4. Two equal "Simplify Debts / Record IOU" buttons.
5. The list.

Findings:
- **Same card soup as Home.** The page's real question ("who owes whom, and what should I do") is answered by two small numbers.
- **Friend card** (`PersonBalanceCard`): avatar circle, name and status pill, balance, and two full-width buttons on every card ("History", "Settle Up"). Repeating two buttons per row makes a list of 10 friends a wall of buttons.
- **Group card:** the "You owe -₹415.50" red number plus "View Breakdown" link repeat Home's pattern.
- **Empty states:** "No teammates added yet. Tap '+ Add Friend' above…" and "No trip groups created yet." Same generic tell, and they point up the screen instead of offering the action.
- **The top action button changes meaning with the tab** (New Group vs Add Friend), and the list header also has "+ Create" / "Join", so there are two create entry points on one screen.

### Insights (`InsightsScreen`, 1458 lines) **[Medium]**
- **Four stacked "Notion-style" views:** cash-flow card, interactive donut, category progress bars, a horizontally scrolling database table, and a "Gemini AI Financial Intel" card. Taglines like "Multi-view expenses, charts & AI intelligence" and component names like "NOTION-STYLE DATABASE TABLE VIEW" are AI-builder vocabulary, not product voice.
- **The donut uses per-category pastel colors** unrelated to amber/lime, and the table repeats data already in Home.
- **`%.2f` everywhere without locale,** mixed with `%.0f`.

### Sheets and dialogs **[Medium]**
- **GroupDetailSheet, PersonLedgerSheet, AccountSheet, JoinGroupSheet, StatementImportDialog:** charcoal sheets with a gray drag-handle bar, a close X (redundant with swipe-down), and the same bordered boxes inside.
- **Settle up, IOU, Add friend, Create group, Delete friend, Simplify debts:** centered `Dialog`s with a close X, 20dp padding and 22–24dp radii. Their forms have the same bordered-box language.

### Sign-in, account and sync indicators (Part A surfaces) **[Medium]**
- **Sign-in** has the only radial gradient and the best copy, but still stacks a stock logo, title, body and button.
- **"Syncing…" / last-synced status** lives only in AccountSheet as a text row, with no ambient indicator.

### Loading, empty and error states **[Medium]**
- **Loading:** 17 `CircularProgressIndicator`s, 2 linear, and a shimmer modifier used in 4 places. The generic spinner is the default everywhere, including the AI parse on the confirm sheet.
- **Errors surface as `Toast`s** built from exception text ("Failed to open PDF statement: ${e.message}").
- **Empty, loading and error look the same:** a blank or spinner in the list area.

### Copy **[Medium]**
- **Placeholder or builder voice:**
  - "Successfully added N transactions across respective months!"
  - "Monthly insight notification pushed to system tray!"
  - "Group '…' created successfully!"
  - "Gemini AI Financial Intel"
  - "Real-time 2-way debt settlement & group trips"
- **Inconsistent nouns:** teammates / friends; trips / groups; "Splits & Trips" in the nav vs "Split & Trip Ledger" as the title.

---

## 4. Direction for the fixes

These are intentions, not final pixels; each PR shows the result.

### Depth hierarchy (Liquid Glass, translated to Compose)
- **Only floating layers become glass:** the bottom nav dock, the add action, the confirm sheet's chrome and floating save bar, sheet headers, and dialogs converted to sheets. **Content cards stay solid and opaque.**
- **Glass material** (one component, reused everywhere):
  1. real **backdrop blur** of what's behind;
  2. an **accent-tinted** translucent fill;
  3. a **specular sheen**, a soft top-edge highlight gradient catching light, plus a hairline inner edge light;
  4. **text and icons always on a solid inner layer**, never directly on the blur.
- **Implementation:**
  - Compose has no native backdrop blur, so this uses **Haze** (`dev.chrisbanes.haze`) for true backdrop blur on Android 12+ (API 31+).
  - `minSdk` is 24, so API 24–30 fall back to a denser tinted scrim with the same sheen. It loses the blur but keeps the hierarchy.
- **The tint adapts to what's behind** through the blurred backdrop itself. The app is dark-only today (see Decisions), so there's no light/dark variant to adapt to yet.

### Signature gradient
- **One brand gradient: amber `#FF7A3D` → lime `#C6F24C`,** through a warm golden midpoint rather than a muddy brown. It appears in exactly three places:
  - the hero balance number (gradient text);
  - the primary add/save CTA;
  - the nav dock's active indicator.
- **No gradient backgrounds on content.**

### Structure
- **Home:** the balance hero *is* the header, with no separate app-title row. Capture becomes one primary input with capture modes behind it. The feed is **grouped by day** with sticky day labels and lighter, borderless rows (a timeline, not a stack of boxes), with rhythm: tight within a group, generous between sections.
- **Nav:** a floating glass **dock** detached from the screen edge, holding the three destinations with custom icons and a gradient active mark. The **add action is part of the dock**, which removes the FAB and its overlap bug.
- **Splits:** a "who owes whom" summary with a single primary action. Friend rows become compact rows whose action appears on the row (no two buttons per card), and groups become distinct trip cards.
- **Dialogs → sheets** where they're tasks (Settle up, IOU, Add friend, Create group), so there's one floating language.

### Shapes
- **A small shape system replacing the 11 ad-hoc radii:** continuous-curvature "squircle" corners (smoothed, not circular arcs) at three sizes.
- **A distinct primary-action shape:** asymmetric corners, so the CTA isn't a pill.
- **A "ticket" treatment for money-moved moments,** such as settlement rows and the confirm summary.

### Typography
- **A real scale in the theme, used as defined:**
  - Outfit for screen and section titles, with tight tracking and larger steps;
  - **dedicated money styles** in Space Grotesk with tabular figures (`hero`, `large`, `row`, `small`);
  - Plus Jakarta for body at 14–16sp;
  - labels at **12sp minimum**.
- **Two weights per family in normal use.** Bold is reserved for emphasis.
- **Call-site `.copy()` overrides go away** as screens move to the scale.

### Also in scope (small bugs folded in)
- **Money formatting:** one `Money.format` using en-IN grouping, ₹ and paisa shown when non-zero ("₹34.50", "₹35"), used by every screen, the widget and the rolling number.
- **Rolling number:** animate in paisa as `Long`/`Double`, not `Float`, with a real per-digit roll and tabular figures.
- **Swipe-to-delete:** don't dismiss until the delete is allowed. Rows that can't be deleted (groupmates' expenses, archived groups) don't offer the swipe at all.
- **Contrast:** raise the smallest-text token to at least 4.5:1, and give interactive boundaries 3:1.
- **Home IOU duplicate click handler.**

---

## 5. Proposed PR batches (fast-track, no RLS/auth)

1. **Foundations**:
   - tokens (spacing, shapes, type scale, colors and contrast fix);
   - the glass material component with Haze;
   - the brand gradient;
   - `Money.format`;
   - the rolling-number fix;
   - the swipe-to-delete fix.
2. **App shell:** floating glass dock with integrated add, custom nav icons, tab transitions, consistent screen headers.
3. **Home and the confirm sheet** (two screenshot moments): hero restructure, capture, day-grouped feed, transaction and settlement rows, confirm-sheet restructure with floating save.
4. **Splits & Trips and its sheets:** summary, friend rows, trip cards, group/person sheets, dialogs converted to sheets.
5. **Insights and the widget** (third screenshot moment): de-Notion the view stack, brand-tied chart colors, rebuilt widget.
6. **States and voice:** empty, loading and error states per screen; sign-in/account/join polish; ambient sync indicator; copy pass.

Each PR runs `/material-3 audit` on the screens it changes, and records before/after notes.

## 6. Decisions needed from the owner

1. **Light mode.** The app is dark-only today. Liquid Glass adapting to light/dark context implies a light theme, which roughly doubles the visual QA of every screen. Recommendation: stay dark-only for this pass, with tokens structured so a light scheme can be added later.
2. **Haze dependency** (Chris Banes' backdrop-blur library, well maintained, matches this project's chrisbanes-skills convention) for the glass material. The alternative is scrim-only glass with no real blur.
3. **Insights scope.** Keep all four views (cash flow, donut, table, AI card) and restyle them, or cut the "Notion" table view, which duplicates Home's feed. Recommendation: cut it, and keep search/sort in Home.
4. **Custom icons.** Hand-drawn custom vectors for the ~13 nav and category icons (distinctive, more work), or a curated Material Symbols **Rounded** set with custom treatment. Recommendation: custom vectors for nav and categories; Rounded Symbols for secondary actions.
