# Design audit — verification against code and device (2026-09-18)

`design-and-feature-backlog.md` §3 was written from screenshots alone.
This is the cross-check the owner asked for, before anything gets built
on it. Every claim below was checked against the current source; the
device evidence is the owner's own eight screenshots taken today, now
committed at `docs/design-reference/`.

**Verdict key:** ✅ confirmed · ❌ not true · ⚠️ true but the cause or the
fix differs from what the audit assumed · ✔️ already done.

### Closed on this pass (owner-confirmed 2026-09-18)

These backlog items are **not applicable** and are not to be built. They
were verified against the code, and the owner has signed off on closing
them:

| Backlog item | Status |
|---|---|
| §6.4 — bottom safe-area padding on every scrollable list | **Closed, not applicable.** Already correct on all three screens and in `SheetScaffold`. |
| §6.12 — deterministic per-person avatar colours | **Re-scoped.** The colour system already exists; see the re-scope below. |
| §3.5 / §3.7 — "Simplify who pays whom" is a static tagline | **Closed, not a defect.** It is a real button and always was. |
| §3.6 — the dock renders behind an open Group Detail sheet | **Closed, not reproducible.** Sheets are separate windows. |
| §1.2 / §6.18 — two-letter avatar initials overlap | **Closed, already fixed** at `TripCard.kt:190`. |
| §6.7 — enable Space Grotesk's tabular figures | **Closed, already enabled** on every `MoneyText` step. |

### Re-scope of §6.12 — it is a rendering bug, not a missing system

The item was written as "give each person a deterministic, distinct
colour (hash their profile ID into a fixed palette of 6-8 hues), reused
for avatars *and* any donut/chart". **All of that already exists** —
`GroupSyncEngine:453` hashes the profile ID into an eight-colour
palette, `AddFriendSheet` offers the same eight locally, and
`GroupDetailSheet:313` already draws the donut from `person.avatarColor`
rather than a chart-local ramp. Building it again would be rework on top
of working code.

What is actually broken is one line. **`PersonAvatar.kt:43` draws the
person's colour at `alpha = 0.2f`** over charcoal. At 20% on `#1E1C1A`,
sky blue, rose, coral and lilac all resolve to within a few points of
the same brown — which is why You, Lal and Shubham read as three
identical avatars in the screenshots despite genuinely holding three
different colours. The palette is distinct; the tint destroys the
distinction before it reaches the screen.

Two smaller faults sit behind it, both real:

- **The palette is warm-heavy.** Rose `#FFB3BA`, Coral `#FF9E79` and the
  amber sit close enough that three people can legitimately draw three
  near-neighbours, which is what makes the Group Detail donut read as
  three shades of one orange even at full opacity.
- **"You" is hardcoded `#FF7A3D`** (`GroupInsights.MY_COLOR`), outside
  the palette, so the current user can collide with a member who drew
  Coral.

**The item is therefore: raise the avatar tint so the palette survives
contact with the screen, spread the palette's hues, and pull "You" into
the palette.** Not: build a colour system.

**What was not verified here:** nothing needed a live interactive
session except the Add Expense focus behaviour and the modal layering,
and both were settled from source (noted per item). No emulator was
started — this laptop had 2.3 GB free and a Gradle build plus an AVD is
what crashed it on 2026-09-15.

---

## §1 — Bugs

| # | Claim | Verdict |
|---|---|---|
| 1.1 | Statement scanning does not work | ✅ **confirmed, root cause found** — see below |
| 1.2 | Two-letter avatar initials overlap in the member stack | ✔️ **already fixed — closed** |

**1.1 — Statement scanning.** The wiring is all intact: `CaptureCard`'s
Statement button → `GetContent("application/pdf")` → `MainViewModel.
parseBankOrUpiStatement` → `GeminiRepository.parseBankOrUpiStatementPdf`
→ `StatementImportDialog`. Nothing is stubbed. The failure is inside the
network call, and the app does surface it — the message "Couldn't read
that statement…" appears in `KpSnackbar`, which is the bordered pill the
audit mistook for a warning banner in §3.7.

Two things made this look like "the button does nothing":

- **The 30-second read timeout** (`GeminiRepository:29-32`) is shared by
  all four Gemini calls. Text parsing and single-image OCR fit inside it;
  extracting *every* row across *all* pages and months of a statement
  with a thinking model does not. This is the only AI path in the app
  long enough to hit it, which is exactly why it is the only one that
  fails.
- **Until `c0efe0f` the failure was invisible**, because an unreadable
  statement returned a *generated sample* (two ₹75,000 salaries, Zomato,
  Netflix, some future-dated) presented as the person's own data. That
  commit correctly replaced the fake with `null`. It did not cause the
  failure — it revealed a failure that had been there all along.

Secondary defects found in the same path, all of which make this
undiagnosable rather than merely broken:

- A non-2xx response is dropped silently — status code and Google's error
  body are never read or logged, so a 400 / 403 / 429 / quota error is
  indistinguishable from a timeout.
- One generic message covers every cause, so the person cannot tell an
  expired key from a slow network from a password-protected PDF.
- `parts[0].text` only. Gemini 3.x can return a thought part first; the
  answer part is then skipped and the whole parse returns `null`.
- No `maxOutputTokens` and no `finishReason` check, so a `MAX_TOKENS`
  truncation arrives as unparseable JSON and is swallowed by the same
  catch-all.
- Password-protected PDFs (the norm for Indian bank statements) are not
  detected locally, so the round trip is spent before failing.

`gemini-3.5-flash` was checked against Google's current model list and is
a valid ID, so the model name is **not** the problem.

**1.2 — Initials overlap.** Already solved and commented at
`TripCard.kt:190`: the stack takes one initial per person
(`name.substringBefore(' ')`) precisely because a second letter was half
hidden by the next avatar at 30dp. Closes backlog §6 item 18.

---

## §3.3 — Typography

| Claim | Verdict |
|---|---|
| Everything uses the OS default sans | ❌ **not true** |
| Section labels are in the same default sans as body | ❌ **not true** |
| Amounts use a proportional font, digits shift row to row | ❌ **not true** |
| Space Grotesk's tabular figures may not be enabled | ❌ **they are enabled — closed** |
| The ₹ renders lighter than the digits beside it | ⚠️ **true, different cause** |

`Type.kt` carries a complete three-family system: Outfit for display and
headline steps, Plus Jakarta Sans for titles/body/labels, and a separate
`MoneyText` scale in Space Grotesk with `fontFeatureSettings = "tnum"` on
every step — hero, large, title, row, small. `EyebrowText` is a real
component (12sp, 1.2sp tracking) and section labels use it. The
screenshots bear this out: app text is visibly not the Roboto in the
status bar beside it.

**The one real finding here** is the ₹ symbol. Space Grotesk's rupee
glyph is weighted lighter than its digits, so it reads thin next to them
wherever `MoneyText` renders a prefixed amount. That is a glyph problem
to solve with optical sizing at the call site, not a font-stack problem.

**The real bug behind the audit's claim — since fixed.** There was no
`res/font` directory. All three families were fetched at runtime from
the GMS downloadable-font provider, so on a device without Play Services
fonts, or on a cold first launch offline, every one of them fell back to
the system sans without a word — producing exactly the screen the audit
described, on some devices and not others, from the same build. The
owner called this correctly on 2026-09-18: a device-inconsistency bug,
not a style question.

All three now ship inside the APK as `res/font` resources (the variable
font per family, which is how Google Fonts publishes all three; Space
Grotesk has no static SemiBold at all), the
`androidx.compose.ui:ui-text-google-fonts` dependency and the
`font_certs.xml` provider certificates are gone, and the licences are at
`docs/licenses/`. **One caveat:** Android applies a variable font's
weight axis from API 26, and this app's `minSdk` is 24. On Android 7.0
and 7.1 only, every weight renders at the file's default instance —
still the correct typeface, just one weight. Raising `minSdk` to 26
would remove even that, and is the owner's call.

---

## §3.4 — Colour

| Claim | Verdict |
|---|---|
| Home hero is gradient, the same figure on Splits is plain white | ✅ confirmed |
| "YOU'RE OWED" green but "YOU OWE" plain white on group cards | ✅ confirmed |
| Insights bars are all amber regardless of the category icon's colour | ✅ confirmed |
| The unfilled bar track is very low contrast | ✅ confirmed |
| The Group Detail donut is three shades of one orange | ⚠️ true, different cause |
| Avatar colours are inconsistent/accidental, not a system | ❌ **a system already exists — item re-scoped** |
| Fix: hash profile ID into a palette of 6-8 hues | ✔️ **already built — closed** |

The deterministic per-person colour the audit recommends **already
exists**: `GroupSyncEngine:453` hashes the profile ID into an eight-colour
palette, the same palette `AddFriendSheet` offers locally, and the donut
already draws from `person.avatarColor` rather than a chart-local ramp
(`GroupInsights:51`, `GroupDetailSheet:313`). Building it again would be
rework. Three narrower things are actually wrong:

1. **`PersonAvatar` renders the person's colour at `alpha = 0.2f`** over
   charcoal (`PersonAvatar.kt:43`). Sky blue, rose, coral and lilac all
   collapse into near-identical mud at 20% on `#1E1C1A`. The palette is
   distinct; the tint destroys the distinction. This is why You/Lal/
   Shubham all read brown.
2. **The palette's eight colours are warm-heavy** — Rose `#FFB3BA`,
   Coral `#FF9E79` and the hardcoded amber sit close together, so three
   people can legitimately draw three near-neighbours and the donut has
   no ramp separation.
3. **"You" is hardcoded `#FF7A3D`** (`GroupInsights.MY_COLOR`) outside
   the palette, so the current user can collide with a member who was
   assigned Coral.

Bars: `CategoryBarRow` uses `WarmAmberPrimary` unconditionally
(`InsightsScreen.kt:280`) while `CategoryBadge` beside it uses the
category's own colour — confirmed, and a one-line fix. The track is
`CharcoalBg` `#0E0D0C` on a `CharcoalCard` `#1E1C1A` row, 1.2:1.

---

## §3.5 — Buttons

| Claim | Verdict |
|---|---|
| Only two weights exist: gradient pill, and bare coloured text | ✅ confirmed |
| No Outline or Ghost component exists | ✅ confirmed — `Controls.kt` has only `ChoiceChip` and `SegmentedControl` |
| "Invite people" and "Sign out" are the same ad-hoc outline style | ✅ confirmed |
| "Record" has the same weight as "Settle" | ⚠️ true, and deliberate |
| "Simplify who pays whom" is a static tagline styled like a link | ❌ **not true — closed** |

"Simplify who pays whom" is a real `TextButton` that opens the debt
simplification sheet (`BalanceHeader.kt:87-94`). There is nothing to fix.

"Record" vs "Settle" is one `Text` chip with a swapped label
(`FriendRow.kt:113`), and the comment says why: every row carries the
button so the amounts above them stay aligned. Demoting one to a ghost
tier has to keep that alignment or it trades one defect for another.

---

## §3.6 — Flows and layering

| Claim | Verdict |
|---|---|
| The dock renders behind an open Group Detail sheet | ❌ **not true — closed** |
| Add Expense's big ₹0 may not be where you enter the amount | ⚠️ **true, sharper than stated** |
| Home / Insights / Group Detail clip their last rows | ❌ **not true — closed** |
| Splits and Insights are missing Home's header avatar | ✅ confirmed |
| "Sanitizer" is filed under Food | ✅ confirmed — sample data, not a rule |
| Month browsing exists only on Insights | ✅ confirmed |

**Clipping.** All three top-level screens already pass `dockClearance()`
as their `contentPadding` (`HomeScreen:113`, `InsightsScreen:127`,
`SplitsScreen:102`), and `SheetScaffold` appends an `ActionBarClearance`
spacer for sticky action bars (`Sheets.kt:204`). Everything can be
scrolled clear. What the screenshots show is content passing *under*
floating chrome mid-scroll, which is what a floating dock is for. Backlog
§6 item 4 — billed as the highest-value quick win, fixing three bugs at
once — is fixing three bugs that do not exist.

**Layering.** Sheets are separate windows; the dock cannot draw over
them, and `MainActivity:470` documents this. The Group Detail screenshot
shows no dock.

**Amount entry.** There is no hidden keypad step — the ₹0 *is* a
`BasicTextField` with a decimal keyboard (`Sheets.kt:295`). But it is
sized `IntrinsicSize.Min` with a 2dp floor, so when empty the entire
touch target is the width of a single "0", roughly 20dp, with no
autofocus when the sheet opens. On the most-used flow in the app, the
first action is a pixel hunt. Worth fixing, for a different reason than
the audit gave.

---

## §3.7 / §2.2 — The glass, and the "dashed outline"

The dock is not bordered and the ticket motif has not bled into it. What
renders is `liquidGlass`'s **edge light** (`Glass.kt:79-91`): a 1dp
stroke on the squircle outline, running 30% white at the top through 6%
white to 22% amber at the bottom. On charcoal, a hairline that fades in
and out along its length reads as a dashed rust outline. The audit read
the symptom correctly and the cause wrongly.

It is not the dock's problem alone. `GlassPanel` is used by the dock, the
add button, `GlassActionBar` on every sheet, and `KpSnackbar` — so the
same rust hairline is around the Add Expense action bar and every
snackbar, which is most of what the screenshots show.

Two structural reasons it cannot currently read as glass:

- **The bezel is 5dp.** That is the entire visible glass area; everything
  else is an opaque core. Five density-independent pixels of blurred
  backdrop cannot establish depth, so the edge light is the only thing
  left carrying the effect, and it carries it alone.
- **There is no depth hierarchy.** The dock, the snackbar and the sheet
  action bar use identical tint, blur radius and stroke, so nothing reads
  as nearer or further than anything else.

This supported the owner's §2.2 call: re-derive it, do not polish it.

**Owner decision, 2026-09-18, and what was done.** Keep Liquid Glass —
make the blur do the visible work, and replace the all-the-way-around
gradient stroke with a subtle top-only specular highlight, since that is
where light would actually hit. Applied to the material itself, so the
dock, the snackbar and every sheet action bar change together:

- The rim went from 5dp to a shared `GlassBezel` of 12dp, wide enough
  that the backdrop blur behind it is legible as a material. The dock's
  core lost 6dp so the dock itself is only 8dp taller than before, and
  the core shapes were re-cut concentric with the outer shapes.
- The edge stroke is no longer a ring. It is a hairline clipped to the
  top 22% of the shape, fading out before the shoulders, at a flat 34%
  white — no amber, so nothing can read as rust.
- The tint dropped from 42% charcoal + 9% amber to 30% + 7%: with a
  visible rim the blur carries the surface, so the tint only has to keep
  it legible over bright content rather than stand in for it.

**Not a regression, for the record.** §2.2 asked when and why this
changed. It never did: `git diff` between the two glass commits
(`0eed37b`, `5a6f27e`) shows the only change to `Glass.kt` is a doc
comment. The material has been byte-for-byte the same since it was
written. What changed in `5a6f27e` is that it was put on a 5dp-bezel
dock spanning the bottom of every screen, where the edge light became
the dominant read. The Sept 14 screenshots in `Screenshots/` are the
pre-Claude-Code AI Studio app, so there is no earlier good version of
this glass to return to.

---

## §4 — Insights makeover

| Claim | Verdict |
|---|---|
| Insights has no chart at all, only flat bars | ✅ confirmed |
| The only donut in the app is on Group Detail | ✅ confirmed |
| There is one generic "Get a summary" AI button | ✅ confirmed |
| The footer stat chips have no icons and sit in sparse space | ✅ confirmed |

No correction needed. §4 stands as written.

---

## What this changes in the backlog's §6 priority order

**Closed — do not build (see the table at the top):** items 4, 7 and 18,
plus the "Simplify who pays whom" and dock-behind-sheet findings.

**Re-scoped:**

- **Item 12** — fix `PersonAvatar`'s 0.2 alpha, spread the palette's
  hues, pull "You" into the palette. The hash and the palette exist.
- **Item 11** — resolved here. Layering is fine; what the amount field
  needs is a real touch target and autofocus, not an investigation.

**Done on this pass:**

- **Item 1** (statement scanning) — fixed; root cause was a 30-second
  read timeout shared with the one-line text parse.
- **Item 2** (Home hero metric) — done.
- **Item 3** (the dock's glass) — reworked, per the owner's decision
  above.
- **Bundled fonts** — not in the backlog; the real bug behind §3.3.

**Still open, unchanged by verification:** items 5, 6, 8, 9, 10, 13, and
the whole of §4's Insights makeover (14, 15, 16), whose claims all stood.

**New, and not in the backlog:**

- A non-2xx Gemini response was silently discarded across all four AI
  paths. Fixed alongside item 1.
- The Add Expense amount field's touch target is the width of one "0"
  when empty, with no autofocus on open (folded into item 11).
- `minSdk 24` costs the bundled fonts their weight axis on Android 7.x.
  Raising it to 26 is an owner call.
