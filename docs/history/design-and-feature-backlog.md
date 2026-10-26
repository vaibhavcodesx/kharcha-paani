# Design & Feature Backlog — Consolidated
### For: the Claude Code agent working on Kharcha Paani
### Combines: a full external design audit (8 screenshots, screen-by-
### screen), the user's direct feedback after real device use, and six
### competitor-app screenshots with specific features to borrow from.
### Status: recommendations + explicit product decisions — read §0.

---

## 0. How to read this document, and one setup step first

Three kinds of instruction are mixed together, carrying different weight:
- **✅ DECIDED**: stated directly by the user. Build it, don't re-litigate.
- **🐛 BUG**: broken, not an opinion. Fix-first priority.
- **💡 SUGGESTION**: design opinion with reasoning behind it, but your
  judgment about the actual codebase wins over anything here if you have
  a reason not visible from a screenshot.

**Before anything else**: the actual image files (8 screenshots of this
app, 6 of competitor apps) should be copied into the project repo —
suggest `docs/design-reference/` — so you can view them directly rather
than relying only on the text descriptions below. Text descriptions are
provided in full for every image regardless, in case that's not done or
you want the written detail either way, but seeing the actual pixels
matters for anything about exact color, spacing, or visual weight.

---

## 1. Bugs to fix first (🐛)

1. **Bank/UPI statement scanning does not work.** The "Statement" button
   exists on Home but doesn't actually scan or import anything currently.
   Fix before design work below — this blocks real use.
2. **Two-letter avatar initials overlap in the group member stack**
   (carried over from batch 4, still open).

---

## 2. Explicit product decisions (✅)

1. **Home hero metric: "Spent this month," not "You owe."** This
   reverses batch 3's decision to make debt balance the header metric.
   Rationale: spending is relevant daily; debt is occasional and already
   has a dedicated home in Splits. Demote owed/owed-to-you to the
   secondary stat row where "Came in" currently sits.

2. **The bottom nav's glass effect isn't reading as intended on the real
   device** — user's words: "looks ugly." Looking at the actual
   screenshot (Image 2 below): the dock renders with a **dashed/dotted
   outline border**, which reads as the ticket-stub motif from the group
   cards having bled into the nav bar, not a glass material at all. This
   may be a real implementation regression, not just "needs polish" —
   re-check the actual blur/tint/depth code against the Liquid Glass
   principles already in the PRD (real depth hierarchy, tint adapting to
   content, a subtle specular highlight, restraint), and against what a
   working version looked like earlier in batch 2 if that helps isolate
   when/why this changed.

---

## 3. Full design audit — Kharcha Paani screenshots (8 images reviewed)

### 3.1 Core diagnosis

The app reads as a well-organized dark theme, not yet a designed product
with a voice. Every screen leans on what appears to be the OS default
sans rather than the Outfit/Space Grotesk/Plus Jakarta Sans system
already chosen — worth checking whether this is a real regression on
these specific screens or a rendering artifact of the screenshots. A
single orange→lime gradient is applied inconsistently as the only
distinctive visual element.

But the app already contains several genuinely good, distinctive ideas
that aren't applied consistently enough to register as intentional:
- A dashed/perforated "ticket-stub" divider on group cards — genuinely
  good, thematically apt (bills/receipts), worth deliberately extending
  to other receipt-like surfaces (**explicitly not the nav bar**, per §2.2).
- "Settle up" correctly appearing only on debt rows involving the current
  user, absent from rows that don't (Group Detail → "Who owes whom").
- An outline-style button (transparent fill, visible border) used
  identically in two unrelated places: "Invite people" (Group Detail)
  and "Sign out" (Settings) — your only real secondary-button component,
  just not reused elsewhere it would fit.
- Green already applied to "you're owed" amounts in at least one place
  (Splits→Groups, Sample GRP-2).

**The highest-leverage work is finding these existing good patterns and
applying them everywhere, before inventing anything new.**

### 3.2 Research grounding

Two real-product references were used to sanity-check these
recommendations, not just taste:

**Nothing (nothing.tech / Nothing OS)** — typographic discipline: a
strict three-tier type system (a dot-matrix/monospace face reserved for
numerals and product names, an ultra-light editorial face for headlines
that intentionally recedes, an uppercase tracked mono face for every
label/button/caption), total color restraint, and — most transferable to
a finance app — **tabular (fixed-width) numerals used everywhere a
number appears**, so digits don't shift alignment as values change
length.

**Revolut** — a direct fintech comparison: a strict **four-tier button
hierarchy** (primary solid / secondary solid-light / outline / ghost),
all built on a single fixed pill radius; a comprehensive semantic color
token set (dedicated danger/warning/success/info colors used
consistently everywhere, not just on one hero number); and **zero
drop-shadows** — depth created through flat contrast steps between
surface layers, not blur/elevation, reading as more considered in
dark-mode UI than soft shadows.

The tabular-numerals point isn't just a Nothing quirk — it's
near-universal in Cash App, Monzo, Wise, and Apple Wallet, for the same
reason: proportional digits visibly jump/misalign in a list as amounts
change length, and money is the one thing in this app where that jitter
is most noticeable and most worth eliminating.

### 3.3 Typography

**What was seen:** All visible text, including large hero currency
figures, appears to use the OS default sans. The ₹ symbol in the hero
number looks visually lighter/thinner than the numerals beside it.
Section labels ("YOU OWE," "SPENT THIS MONTH," "BY CATEGORY," "WHO'S IT
FOR," "NEW ENTRY") are mostly small-caps-styled but in the same default
sans as body text. List amounts are right-aligned but in a proportional
font, so digit widths vary row to row.

**Why it matters:** A default system font is the fastest visual signal
of "assembled from a component library," not designed. For a money app,
proportional-digit currency figures are both a subtle usability cost
(numbers appear to shift as length changes down a list) and a
"looks unfinished" signal.

**Recommendations:**
- Use a monospaced font with **tabular/fixed-width figures** for every
  currency amount — hero totals, list-row amounts, chips, the
  amount-entry screen. Space Grotesk (already chosen) supports tabular
  figures — confirm this feature is actually enabled where currency
  renders, not just that the font family is applied.
- Use one deliberately-chosen sans for everything else (merchant names,
  body copy) instead of any OS default.
- Render section labels in the same mono family as amounts, ~11px,
  slightly increased letter-spacing, muted grey — currently inconsistent
  (some labels are plain sentence-case default sans).
- Apply slightly positive letter-spacing to general body text.

*Not a requirement to go full literal dot-matrix/LED styling app-wide —
the tabular-mono-for-money piece has the strongest evidence and should
be the priority; anything more "retro calculator" is optional flavor.*

### 3.4 Color

**What was seen, screen by screen:**
- **Home:** the hero "YOU OWE ₹1,953.34" figure renders in an
  orange→lime gradient.
- **Splits (Friends tab):** the same ₹1,953.34 figure appears again, in
  plain white, not the gradient — same data point, styled two different
  ways on two different screens.
- **Splits (Groups tab):** Sample GRP-3 shows "YOU OWE ₹2,902.34" in
  plain white. Sample GRP-2 shows "YOU'RE OWED ₹949" in green.
- **Insights:** every category's progress bar (Transport, Food,
  Entertainment, Bills, Shopping) renders in the same solid orange
  regardless of the category icon's own color (Transport's icon is
  blue, Food's is brown, Entertainment's is blue-grey, Shopping's is
  pink) — bar color doesn't match icon color anywhere. The unfilled
  portion of each bar is very low-contrast against the card background.
- **Group Detail:** the "Who spent what" donut chart divides spend
  between You/Lal/Shubham using three shades of the same orange/salmon
  hue — distinguishable only via the legend dot, not the chart itself.
- **Avatars throughout:** "You," "Lal," and "Shubham" all render as
  near-identical brown/rust circles differing only by initial letter, in
  both Group Detail member chips and the donut chart. In the Friends
  tab, Rahul's avatar happens to be blue while Shubham and Lal are both
  the same brown — so even the color variation that exists is
  inconsistent/accidental, not a deliberate per-person system.

**Why it matters:** A finance app's whole job is making "who owes what,
in which direction" scannable at a glance. Color is currently applied to
that signal in exactly one place and nowhere else, defeating the point
of using color for it at all. Separately, mixing a warm gradient and a
"positive" green on a single number representing one money direction
(Home's hero figure) sends a mixed signal about what that number means.

**Recommendations:**
- Finish the semantic color system: extend the existing green
  ("you're owed") everywhere that direction is shown, add a consistent
  warm red/amber for "you owe" (currently plain white almost
  everywhere), so both directions are colored the same way on every
  screen.
- Reconsider whether the orange→lime gradient belongs on a debt figure
  at all, versus being reserved for brand/CTA moments (the FAB, primary
  buttons) while the debt number itself uses the plain semantic color —
  resolved by §2.1's hero-metric change either way, but worth applying
  the same logic wherever a debt figure still appears.
- Match each category's progress-bar fill color to its own icon color in
  Insights, and raise the contrast of the unfilled track.
- Give each person a deterministic, distinct color (hash their profile
  ID into a fixed palette of 6-8 hues), reused for avatars *and* any
  donut/chart, so "Lal is always this color, everywhere" becomes a
  reliable visual anchor.

### 3.5 Component system — buttons and tappable affordances

**What was seen:** Across all 8 screens, only two visual weights of
"tappable thing" exist: (1) a solid gradient pill — "Add expense" and
the FAB; (2) plain colored text with no container, no border, no
background — used for essentially everything else: "See who pays whom,"
"Log an IOU," "Add a friend," "Join," "New group," "Settle up,"
"Export," "Get a summary," and "Simplify who pays whom" (this last one
is actually a static tagline, not a link, but styled in the same orange
as real links — risks a first-time user tapping it expecting
navigation).

Two exceptions exist and are identical to each other despite appearing
on unrelated screens: **"Invite people"** (Group Detail) and **"Sign
out"** (Settings) both use an outline style (transparent background,
visible border, full-width pill).

Separately, on the Friends tab, "Rahul — All settled" shows a
fully-styled, equal-weight "Record" button, visually identical to the
active "Settle" buttons for Shubham/Lal — despite settling an active
debt and logging a new IOU with someone already square being very
different levels of consequence/frequency.

**Why it matters:** Relying on color alone as the only tappability
signal is weaker than premium apps generally use, and flattens genuinely
different importance levels (a full debt settlement vs. a low-stakes
"see more" link) into the same visual treatment. The one place this is
already solved correctly (Settle up appearing only where the current
user can act) is worth protecting, not accidentally undoing.

**Recommendation — four-tier hierarchy** (mirrors Revolut, not a random
invention):
1. **Primary** — solid/gradient fill, pill radius. Add expense, FAB.
2. **Secondary/Outline** — transparent background, visible border, pill
   radius. Already exists in two places — reuse for: Settle up, Add a
   friend, Join, New group, Invite, Export.
3. **Ghost** — low-opacity fill, no border, no current example. Good for
   lower-emphasis repeatable actions — demote "Record" (already-settled
   friend) here.
4. **Text link** — color only, no container. Reserve for genuinely
   low-stakes inline links only ("See who pays whom," "Get a summary").

Optional scaffolding — if the component library already expresses this
more cleanly, use that instead.

### 3.6 Flows and interactions worth re-checking against the live app

These looked like real issues in screenshots, but need verification
against the actual running app in case they're screenshot artifacts:

- **Group Detail modal/nav bleed:** presented as a bottom sheet (drag
  handle visible), but the floating bottom-nav pill is still partially
  visible peeking out from behind the sticky "Add expense" button in the
  same screenshot. If the tab bar genuinely renders behind an open modal
  sheet, that's a z-index/layering issue — breaks the "separate layer"
  illusion the drag handle establishes.
- **Add Expense amount-entry flow:** the sheet shows a large static
  "₹0" near the top, but the primary action at the bottom is "Add an
  amount" in what looks like a disabled state — implying numeric entry
  (a keypad) happens in a step not visible in this screenshot. If so,
  the large "₹0" may mislead about what's interactive here. Worth a
  quick manual walkthrough — probably the single most frequently used
  flow in the app, so any ambiguity has outsized cost even if small.
- **Bottom-content clipping**, in three separate screenshots:
  - Home: the last visible transaction row ("Rapido to McD") is
    partially obscured by the nav bar.
  - Insights: the "Get a summary" link and part of a rupee figure below
    it are clipped at the very bottom of the scroll.
  - Group Detail: "Where it went → Transport ₹5,900" is clipped by the
    sticky "Add expense" button — if more categories exist below
    Transport in real data, they may currently be unreachable.
  Check every scrollable container has bottom padding/safe-area inset
  accounting for both the nav bar height and any sticky button height.
- **Header avatar inconsistency:** Home shows a profile avatar top-right;
  Splits and Insights don't show one in the same position. If
  unintentional, all top-level tabs probably want identical header
  chrome.
- **Category miscategorization (likely sample data):** "Sanitizer"
  renders under the same brown "food bowl" icon used for actual food —
  likely just placeholder data, flagging in case it points to a real
  category-assignment issue.
- **Month-browsing only available from Insights:** Home's "Spent this
  month" stat and the Activity tab's date-grouped list don't offer any
  way to jump to a different month, while Insights has a month
  switcher. Minor discoverability gap, or intentional if Insights is
  meant to be "the" place for time-travel through spend — your call.

### 3.7 Screen-by-screen detail

**Home / Activity feed.** Hero "YOU OWE" figure in gradient text,
tagline ("Simplify who pays whom"), a two-column Spent-this-month/
Came-in stat row with a streak note ("Logged 4 days in a row"), a
capture bar ("What did you spend on?" + mic icon, three sub-actions
below: Scan bill/Statement/IOU), then a date-grouped Activity feed with
category-icon rows.
- The mic icon (solid orange) sits directly above the three sub-action
  icons (Scan bill/Statement/IOU, rendered as plain grey outline icons)
  — creates an unintended hierarchy where the mic looks like the "real"
  action and the others look like afterthoughts, even though all four
  are equally valid ways to log an expense. Consider equalizing weight.
- The bottom nav (fully-rounded capsule) and the FAB (rounded square)
  use two different corner-radius languages while occupying the same
  floating band — pick one shape language for floating bottom elements.
- "Simplify who pays whom" is styled in the same orange as real
  tappable links but is a static tagline — risks a first-time user
  expecting it to do something.

**Activity — transaction/split detail list.** Date-grouped rows (e.g.
"Tue, 15 Sept") each showing merchant/item name, time, payment method,
split-between count, and a two-line amount showing "your share"
(bold, larger) and "of ₹total" (smaller, muted) — plus a footer note
("Only Shubham can edit this expense") and further down an IOU-style row
("You lent Lal −₹349").
- The "your share / of total" two-line pattern is **already
  well-designed** — bold+large for share, small+muted for total. Keep
  and reuse this pattern anywhere else this ratio needs showing.
- 8 of 11 visible rows (Panipuri, Lunch, Chai N Snacks, Boating, McD,
  Dominoz Pizza, Diet Coke, and even the miscategorized Sanitizer) all
  render the identical brown "steaming bowl" icon since they're grouped
  under one broad "Food" category. Once an icon repeats 8 times in one
  list, it stops carrying information. Consider merchant-level
  sub-icons, or accept the limit and lean on merchant-name typography
  weight for scannability instead.
- "Only Shubham can edit this expense" is styled as a full-width
  bordered banner, reading closer to a warning/error state than a
  neutral permissions note. Consider a lighter inline treatment, or
  only surfacing it contextually when a user actually attempts to edit
  and is blocked.
- Repeated "PopUPI" as plain text on nearly every row adds reading load;
  a small payment-method icon/badge might reduce this.

**Insights.** A month switcher ("‹ September 2026 ›"), Spent/Came-in
stats, a plain-language delta sentence, a "By category" list (icon,
name, entry count + percentage, amount, progress bar per row), and two
footer stat chips ("Spent most at," "Per day so far").
- Bar-color/icon-color mismatch and low-contrast track (§3.4).
- The two footer chips have no icons and sit in a comparatively empty,
  sparse layout relative to the denser list above — feels like unused
  template space. A small icon per chip, or merging into a single stat
  row, would help.
- The month switcher exists only on this screen (§3.6).
- Bottom-clipping issue (§3.6) — "Get a summary" and part of a figure
  below it are cut off.
- **See also §4 below — this screen gets a fuller makeover beyond these
  specific notes.**

**Add Expense sheet.** A modal sheet (drag handle visible, Insights
dimly visible behind it) containing an Expense/Income toggle, a large
static "₹0" amount display, five list-style rows (Merchant, Category,
Paid with, When, Group), a "Just me / Split it" toggle, an optional note
field, and a sticky bottom bar showing a live summary ("₹0 · Food ·
PopUPI") next to a disabled "Add an amount" button.
- Of the five form rows, only **Category** (icon + "Food") and **When**
  (icon + "Today, 3:37 pm") show a leading icon. **Merchant**, **Paid
  with**, and **Group** don't — 2-out-of-5 reads as an oversight.
  Normalize all five to icon + label + value + chevron.
- The disabled "Add an amount" button's fill color looks close in
  luminance to the card/background behind it — double-check contrast in
  the actual dark-mode render, since a disabled button that's hard to
  perceive as a button at all is worse than one that's merely
  low-emphasis.
- The big "₹0" / potential keypad-flow ambiguity — see §3.6, the most
  important item on this screen to verify against the live app.

**Splits — Friends tab.** "Owed to you / You owe" stat header (mirrors
Home's pattern but in plain white here, not gradient — §3.4), "See who
pays whom" / "Log an IOU" text links, a Friends/Groups segmented toggle,
an "Add a friend" link, and three friend rows (Shubham, Lal — both owed
by user, "Settle" button; Rahul — all settled, "Record" button).
- No header avatar (§3.6).
- Shubham's and Lal's avatars are both the same brown circle; Rahul's is
  blue — avatar-color variation exists but isn't a deliberate,
  consistent per-person system (§3.4).
- "Record" (Rahul, all settled) has identical visual weight to the
  active "Settle" buttons for Shubham/Lal, despite representing a much
  lower-stakes, lower-frequency action — see §3.5 for the fix (demote to
  ghost-tier).
- Large empty vertical space both between the Friends/Groups toggle and
  "Add a friend," and below the three friend rows down to the bottom
  nav. On a screen with only 3 friends, this dead space reads as
  unfinished. Consider vertically centering the content, or filling the
  space with something useful (e.g. a one-line summary, or recent
  shared-activity context per friend).

**Splits — Groups tab.** Same header/stat pattern as Friends, toggle set
to Groups; two group cards (Sample GRP-3: 3 people, 14 expenses, Shared,
"YOU OWE ₹2,902.34"; Sample GRP-2: 2 people, 5 expenses, Archived,
"YOU'RE OWED ₹949"), each showing a Spent/Your-share row and stacked
member-avatar chips, separated from the top stat row by a dashed/
perforated divider line.
- The dashed perforated divider is a genuine, distinctive visual detail
  — resembling a torn ticket stub or receipt perforation. Thematically
  appropriate (bills/receipts), worth deliberately extending elsewhere
  (expense-detail sheets, settlement confirmations) rather than leaving
  as one occurrence.
- Sample GRP-2's "YOU'RE OWED ₹949" renders in green; Sample GRP-3's
  "YOU OWE ₹2,902.34" is plain white — the semantic-color idea is half-
  implemented; finishing it is a small, precise fix.
- "Archived" (GRP-2) and "Shared" (GRP-3) render with identical text
  weight/card brightness — an archived group probably shouldn't compete
  visually with an active one; consider dimming the card or a small
  status-badge treatment for "Archived" specifically.
- Group "cover" icons are small illustrated/emoji-style graphics (a car,
  an umbrella) inside a rounded square — a different icon language from
  the simpler line/duotone category icons used elsewhere (Transport,
  Food, etc.). Worth deciding deliberately: either lean into illustrated
  group covers as a real, expanded feature (let users choose from a
  curated set when creating a group), or bring them in line with the
  rest of the icon system if the inconsistency wasn't intentional.

**Group Detail.** A modal sheet showing group name/icon, Spent/Your
share/You-owe stats, a "Shared group · Synced" status row, member
avatar chips, an "Invite people" outline button, "Archive group"/"Leave
group" text actions, a "Who spent what" donut chart with a per-person
legend, a "Who owes whom" list, a "Where it went" category breakdown,
and a sticky "Add expense" button at the bottom.
- The modal/nav-bar layering issue (§3.6) is the most important item on
  this screen.
- The donut chart's three segments (You 36%, Lal 32%, Shubham 32%) are
  rendered in three shades of the same orange/salmon hue —
  distinguishable only by reading the legend dot, not the chart itself.
  Reusing the deterministic per-person color system (§3.4) for both
  avatars and this chart fixes this.
- "Who owes whom" correctly shows "Settle up" only on the row involving
  the current user ("You → Lal ₹2,902.34"), and no action on the row
  that doesn't ("Shubham → Lal ₹676.32") — good, deliberate-feeling
  restraint worth explicitly preserving, and worth double-checking this
  same logic is applied consistently elsewhere (§3.5 found a
  counter-example: the "Record" button for an already-settled friend).
- "Settle up" is currently plain colored text despite being arguably the
  single most important action on this screen (a real debt between two
  named people, in a specific amount) — strong candidate for promotion
  to at least outline-tier (§3.5).
- "Where it went → Transport ₹5,900" is clipped by the sticky "Add
  expense" button (§3.6) — if more categories exist below Transport,
  they may be unreachable.
- "Synced" (green checkmark status) is good, reassuring microcopy worth
  keeping and possibly extending with more states (e.g. "Syncing…",
  "Offline — will sync").

**Profile / Settings.** A modal sheet with avatar + name/email header, a
Profile section (Name, Avatar-type dropdown), an Account section
("Cloud profile ready" and "All changes synced · 1 minute ago," both
green check-style), a Settings list (UPI ID for getting paid, showing
the actual UPI ID as subtitle; Subscriptions; Categories & payment
methods; "Ask before discarding an entry" toggle; Export to a
spreadsheet), and a full-width outline "Sign out" button.
- This is the cleanest, most already-good screen in the set — clear
  grouping, generous spacing, genuinely good microcopy ("Signing out
  keeps everything already on this phone" is a strong, specific line —
  use this voice as the template for empty states and other microcopy
  elsewhere, not just here).
- "Cloud profile ready" and "All changes synced · 1 minute ago" both use
  near-identical green-checkmark iconography for overlapping reassurance.
  Consider merging into a single row, or clearly differentiating what
  each specifically confirms.
- The UPI ID row's subtitle shows a live data value ("9512476357@yespop"
  in the actual screenshot — note: user's real UPI ID, treat any
  reference to it as illustrative only, not something to expose further),
  while the three rows below show feature *descriptions* instead — a
  small visual distinction (weight/color, or a "Connected"/data badge)
  would help a scanning user tell these apart.
- No brand mark (wordmark or logo) appears anywhere across any of the 8
  screens — a small Kharcha Paani mark near the top of this screen would
  help it feel like a designed product's settings page.
- The "Sign out" outline button uses the exact same treatment as "Invite
  people" (Group Detail) — your first real secondary-button component,
  already used consistently in two places. Worth explicitly naming/
  documenting it and reusing it everywhere §3.5 recommends an outline-
  tier action.

### 3.8 Priority ordering from the original audit (merged into §6 below)

---

## 4. Insights tab — full makeover (from user feedback + competitor screenshots)

The user's own assessment after using the real app: "so generic, needs a
makeover." Specific asks, backed by concrete visual references from six
competitor screenshots (all described in full in §5 below):

1. **A real interactive, non-generic pie/donut chart for category
   spending.** Currently Insights only has flat progress bars — no chart
   at all (the donut that exists is on Group Detail, not here).
   Reference: Image 9 (§5) shows a colorful multi-hue arc/gauge-style
   category distribution ring — borrow the *idea* of a real chart with
   genuine per-category color variation, not that specific app's exact
   visual style.

2. **An expense-flow graph — spend per day, viewable per month.**
   Reference: Image 12 (§5) shows exactly this pattern — a day-by-day
   area/line chart for a given period, with a tap-to-reveal tooltip
   (e.g. "Jan 07 · $95.00") pinned to a specific data point, and a
   compact day-by-day data table below the chart. This is a concrete,
   specific pattern worth following closely: chart above a table, with
   an interactive tooltip on tap, not just a static line.

3. **Expanded AI features beyond the single month-in-review summary.**
   Add a set of pre-built prompt options the user can tap, not just one
   generic summary button:
   - Category-wise deep dive ("where am I overspending")
   - "What should I improve" — actionable, specific feedback, not
     generic advice
   - **Group-aware analysis**: the AI should analyze a specific *group's*
     spending when asked — e.g. for a trip group named "Mumbai 2026," it
     should comment on how efficiently/cheaply the trip was run, call
     out standout spending, or genuinely praise good budgeting, not just
     report totals. This is a distinctive, personality-driven feature
     worth real design attention — the tone should feel like a
     knowledgeable friend reviewing the trip, not a generic financial
     report. Treat this as meaningfully bigger than the existing single
     "Get a summary" button, not just an addition to it.
   - **Propose the actual prompt set and presentation UI before
     building** (same pattern as every other feature-design conversation
     in this project) — this needs a short design pass, not just
     implementation.

4. **Supporting ideas from competitor screenshots** (§5 has full detail
   on each), relevant to this same makeover:
   - A merchant "visit frequency" indicator (Image 10) — pairs well with
     the AI's category analysis (e.g. "you've paid Bharti Airtel 3 times
     this month").
   - An upcoming-charge/reminder card style (Image 13) — worth
     considering for the already-planned Subscriptions feature, as the
     visual treatment for an upcoming charge, rather than a plain list
     entry.

---

## 5. Competitor screenshots — full detail (6 images, features the user
## specifically identified as liked)

These are from other expense-tracking apps on the Play Store. The user
did not like every screenshotted app wholesale — only the specific
feature/detail called out for each. Treat each as "borrow this specific
idea," not "make the app look like this app."

**Image 9 — category distribution ring + income/expense flow chart.**
A light-themed "Reports" screen: a Weekly/Monthly toggle, then a
"Monthly income and expense flow" combo chart — a green line (income,
spiking sharply around day 19, flat near-zero the rest of the month) and
red bars (expense, appearing days 19-28) plotted against a day-of-month
axis (1, 4, 7, 10... 28). Below that, an "Expense categories"
distribution section begins to render as a **colorful multi-segment
donut/arc gauge** — visible segments in green, yellow, pink, blue,
purple, and orange, cut off at the bottom of the screenshot. **What to
borrow:** the *combo* chart idea (income as a line, expense as bars, on
one shared timeline) is worth considering for the expense-flow graph in
§4.2, and the genuinely multi-hue (not single-tint) category ring is the
direct reference for §4.1.

**Image 10 — merchant visit frequency + transaction detail layout.**
A dark green-gradient "Debit transaction" detail screen for a Bharti
Airtel payment of ₹349. Top-right of the merchant name is a small pill
chip reading **"3 visits"** with a small bar-chart icon — a merchant
visit-frequency indicator. Below: a "Bills" category chip, a bank
account row ("SBI 7520") with an Expense/Income toggle switch, a Notes
field, then two side-by-side cards — "Split expense with friends or
family" (overlapping friend-avatar circles + a split icon, "Split"
button) and "Add a photo of a receipt/warranty" (image icon, "Attach"
button) — and a Tags row. **What to borrow:** the "3 visits" chip
specifically (§4.4) — a small, cheap addition that adds real insight
("you've been to this merchant 3 times this month/ever").

**Image 11 — home screen widget design.** A dark marketing/onboarding
slide, "Widgets that keep you on track," showing two sizes of a "Quick
Add" home-screen widget. The larger widget has three quick-add category
rows on the left (Food, Transport, Shopping, each with its own icon) and
a Today/This week/This month totals column on the right, each total in
a different accent color (green/blue/orange). The smaller widget shows
just a total plus the same three category quick-add rows stacked.
**What to borrow:** this is a useful reference for Kharcha Paani's own
existing home-screen widget (already built) — specifically the pattern
of quick-add category shortcuts directly on the widget, not just a
balance display, and using distinct colors per stat rather than one
uniform color for all three time periods.

**Image 12 — day-by-day expense flow chart with tooltip.** A cream/
yellow light-themed "MyMoney" app marketing slide, "Expense Flow — See
how much you are spending everyday." Shows a week view (Jan 03 - Jan 09)
with Expense/Income/Total stats, an expandable "EXPENSE FLOW" section
header, then a **red-filled area/line chart plotting spend per day**,
with a tooltip callout box reading **"Jan 07 · $95.00"** pinned directly
to that day's point on the chart. Below the chart, a compact 7-column
table shows each day's exact total (Sun through Sat, e.g. -416.2,
-218.7, -175.5...). **What to borrow:** this is the single most direct,
literal reference in the whole set for §4.2's expense-flow graph —
area chart, tap-to-reveal tooltip on a specific day, plus a data table
underneath for exact numbers. Follow this pattern closely.

**Image 13 — split transaction highlight + upcoming-charge reminder
card.** A dark-themed marketing slide, "Bills, split in seconds," for an
app showing "Recent transactions" on a home screen. An Uber transaction
(₹360, tagged "#office · Shared trip") is highlighted with a glowing
border and shows a **"Split" badge alongside three overlapping friend-
avatar faces** directly on the transaction row itself (not requiring a
tap to see who it's split with). Below it, a "Dues & Reminders" section
shows a gradient card: **"AUTO DEBIT IN 2 DAYS · axio Pay Later ₹1,200"**
with a document icon and a black **"Pay now →"** pill button.
**What to borrow:** the avatar-faces-directly-on-the-row pattern for
split transactions (worth considering for Kharcha Paani's own activity
feed, so a split expense visibly shows who it's shared with at a glance,
not just a "Split between 3" text label); and the "Dues & Reminders"
upcoming-charge card style, directly relevant to the already-planned
Subscriptions feature (§4.4) as the visual treatment for an upcoming
charge.

**Image 14 — category icon/color consistency + payment-method badges.**
A dark-themed, tilted 3D marketing slide showing a "Search transactions"
screen: a search bar, an "All Time" date filter chip, All/Income/Expense
filter tabs, an Income/Expense stat row, and a transaction list grouped
by date (Today/Yesterday/Apr 2026). Each row has a **colorful rounded-
square category icon badge** — an orange fork-and-knife for Food, a
green heart for Health, a purple fuel-pump/tag icon for Fuel/Transport —
each icon's badge background color matching that category's assigned
color, paired with a small "DIGITAL" payment-method tag next to the
amount. **What to borrow:** this directly reinforces §3.4's existing
recommendation (category icon color should match its badge/bar color
everywhere) — this is a clean reference for what that actually looks
like done consistently across an entire list.

---

## 6. Priority ordering (merged: original audit + new items)

**Fix first (bugs, not opinions):**
1. Bank/UPI statement scanning — currently non-functional.

**Explicit decisions to build:**
2. Home hero metric swap (spent-this-month, not owed).
3. Re-examine and fix the nav bar's glass implementation against the
   Liquid Glass principles already in the PRD — likely needs real
   rework, not a tweak.

**Quick wins from the design audit:**
4. Bottom safe-area padding on every scrollable list (fixes three
   separate clipping issues at once: Home, Insights, Group Detail).
5. Add the missing header avatar to Splits and Insights, if
   unintentional.
6. Finish the semantic owe/owed color system across Home, Splits, and
   Group Detail consistently.
7. Switch currency figures to tabular numerals — confirm Space Grotesk's
   tabular feature is actually enabled, not just the font applied.
8. Match category bar/chart colors to their icon colors (§3.4, and
   directly reinforced by Image 14 in §5).

**Medium effort:**
9. Build out the missing Outline/Ghost button tiers and migrate plain-
   text actions into the right tier (§3.5).
10. Normalize the Add Expense form's icon usage across all five rows.
11. Manually re-verify the Add Expense amount-entry flow's interaction
    model, and the Group Detail modal/nav-bar layering issue (§3.6).
12. Deterministic per-person avatar colors, reused across avatars and
    any donut/chart.
13. Demote "Record" (already-settled friend) to a lower-emphasis tier.

**The Insights makeover (bigger effort, its own batch):**
14. Real category chart (colorful, interactive) to replace flat bars —
    reference Image 9.
15. Day-by-day expense flow graph with tap tooltip, per month —
    reference Image 12 closely.
16. Expanded AI prompt set, including group-aware analysis — propose
    the design before building (§4.3).

**Backlog / smaller items:**
17. Extend the ticket-stub perforation motif to other receipt-like
    surfaces deliberately (explicitly not the nav bar).
18. Two-letter avatar initials overlap (carried over from batch 4).
19. A brand mark somewhere in the app (Settings header, at minimum).
20. Consider a subtle number roll-up animation for changing totals.
21. Merchant visit-frequency chip (Image 10) and split-avatar-faces-on-
    the-row pattern (Image 13) — smaller, nice-to-have additions.

---

*Same closing note as the original audit: this is a set of observations
from screenshots and stated preferences, not a full audit of the live
running app or its code. Where actual project knowledge says otherwise,
that wins — treat this as a checklist to triage, not a list to complete
verbatim and in full.*
