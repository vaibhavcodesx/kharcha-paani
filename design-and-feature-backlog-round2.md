# Design & Bug Backlog — Round 2
### For: the Claude Code agent working on Kharcha Paani
### Combines: DESIGN-RECOMMENDATIONS-v3.md (a follow-up audit, 9 screenshots,
### scoring the previous round's fixes), the user's direct bug reports from
### real-device screenshots taken after installing the last build, and one
### competitor-app reference screenshot for payment-method badge styling.
### Companion to design-and-feature-backlog.md (round 1) and PRD.md — not
### a replacement for either.

---

## 0. How to read this, and where the screenshots are

Same three categories as round 1:
- **🐛 BUG**: broken or regressed, not a design opinion. Fix-first.
- **✅ DECIDED**: the user stated this directly.
- **💡 SUGGESTION**: audit opinion, reasoning included, your judgment on
  the actual code wins if it conflicts.

The user is saving screenshots into three folders under
`docs/design-reference/2026-09-19 11;16 AM Post_Changes SS/`:
- the folder itself: post-change screenshots of the real app
- `Zoomed-In Specific Sections/`: cropped close-ups of specific bugs
  (the pie chart color collision, the IOU card, the expense-row badges,
  the nav bar)
- `Other App References/`: one competitor screenshot, payment-method
  badge design to reference for §4

View these directly rather than relying only on the descriptions below.

---

## 1. Priority bug: UPI visibility — a regression, not a new report

Last session's report claimed this was fixed and verified against the
live database (a stranger reads nothing, a groupmate reads it). The
user's actual on-device test shows close to the opposite of what was
intended:
- **The other person, trying to settle up, cannot see the current
  user's UPI ID or QR** — they see "no UPI ID added," even though one
  is set.
- **Each person can only see their own QR/UPI**, not their
  settlement counterpart's — which defeats the entire feature; the
  whole point is seeing *the other person's* payment details when you
  owe them.

**This needs actual re-investigation, not a re-application of the same
fix** — the previous fix was verified with a synthetic RLS test (two
users, no shared group, confirming isolation), which is the *opposite*
of the bug now being reported (two users who DO share a group, one
unable to see the other's UPI in the settle-up flow specifically). The
bug is very likely in the **query the settle-up screen actually runs**
(e.g., fetching `currentUser`'s own profile instead of the
counterparty's profile — a classic wrong-ID-variable bug), not in RLS
policy correctness, which was already separately confirmed.

**✅ DECIDED — add a fallback/alternative delivery method regardless of
the bug fix:** alongside the QR code, also expose the UPI payment as a
**shareable/tappable deep link** (`upi://pay?pa=...&pn=...&am=...&cu=INR`
— the same link format already used to generate the QR, per the earlier
UPI design decision). The person settling up can tap it directly and
pick whichever UPI app they have installed, rather than depending
entirely on the QR-image fetch path working correctly. This is a
sensible resilience addition on its own merits, not just a workaround —
build it either way.

---

## 2. Group "Who spent what" donut — color collision (🐛, ties to v3 §1)

**User's report, screenshot attached (zoomed crop):** the group donut
chart sometimes renders two people in colors close enough to be
confused at a glance.

**This is the exact issue the v3 audit flags as the single biggest
carryover item from round 1** — the *Insights* category donut got a
proper fix (six categories, six genuinely distinct hues, colors matched
in the list below it), but the *group* "Who spent what" donut still
divides people into shades of the same hue rather than a deterministic,
visually distinct palette per person.

**Fix:** apply the same deterministic-distinct-palette approach used for
the (already-fixed) category donut to the person-based group donut.
Hash each person's stable ID into a fixed palette of 5-8 clearly
distinct hues, and reuse that single mapping everywhere a person is
represented in color: avatar backgrounds, member chips, and this donut
— not a separate one-off palette just for the chart.

**Also from v3, worth doing at the same time:** Rahul's avatar is
already blue and Mayank's is a custom sunflower icon — some per-person
distinction already exists informally. Check the Settings → Avatar
picker as the likely easiest place to extend a proper color-hash default
for anyone who hasn't picked something custom.

---

## 3. Insights category chart — completion, not just polish

**What's already working well** (confirmed good, don't touch): the
six-category donut with genuinely distinct hues, colors matched between
the donut and the list below it, and the new daily-spend trend chart —
all called out by name in v3 as strong, premium-feeling additions worth
protecting.

**What's incomplete, per the user's direct report:**
1. When there are more categories than fit compactly, it currently shows
   "+2 more below" as plain text instead of listing everything. Fix:
   show the full category list — if card height is a concern, make the
   list section scrollable within a fixed-height container rather than
   truncating with a "+N" label. This needs to handle however many
   categories a given user actually has, including custom ones they've
   added (round 1's categories feature) — don't hardcode against the
   default category set.
2. Tapping a slice on the donut chart already opens/expands that
   category's row in the list below (confirmed working), **but the row
   doesn't scroll into view or reorder to the top** — if it's off-screen
   below the fold, the user taps the chart and sees nothing happen where
   they're looking. Fix: scroll the selected category's row into view
   (or move it to the top of the list) when its slice is tapped.

---

## 4. AI insight features — several real bugs, not just the length cap

**What's broken, per the user's direct report:**
1. **Response length is capped mid-sentence** — confirms v3 §2's exact
   finding independently (the "...You also had" cutoff). Fix the actual
   cause (generation token limit, or a display truncation) rather than
   just hiding the symptom, and make the response area scrollable so a
   longer answer is always fully readable regardless of length.
2. **"What to change" fails most of the time.**
3. **When one of the three tabs (This month / Overspending / What to
   change) errors, all three tabs show the same error** — this points to
   shared error state across what should be three independent requests/
   results. Each tab's request and error state needs to be independent —
   one tab failing shouldn't affect the other two's ability to show a
   real result.
4. **The trip-level AI feature in Group Detail ("How did this trip go?"
   / "Read this trip") doesn't work at all — it isn't even clickable.**
   This is a bigger gap than a bug: per round 1's backlog, the
   group-aware AI analysis (with real personality — praising or roasting
   how a trip was run) was meant to be a headline feature of this whole
   AI expansion, not an afterthought. Right now it doesn't function at
   all.
5. **Minor, from v3:** the generated narrative text uses literal "Rs"
   instead of ₹ — normalize whatever prompt/template produces this
   narrative copy to emit ₹, matching the currency formatting discipline
   used everywhere else in the app.

**Sequencing suggestion:** given how many independent problems are
bundled into "the AI features," this is worth its own dedicated
investigation pass (is it a backend/prompt issue, a network/timeout
issue similar to what caused the statement-scanning bug, a client-side
state-sharing bug, or several different things at once) before assuming
a single fix covers all four numbered points above.

---

## 5. Splits screen layout (💡, from user's direct feedback)

1. **"Log an IOU" should have an icon, not be plain text** — inconsistent
   with the rest of the app's now-normalized icon+label pattern
   (Add Expense form rows, category rows, etc.).
2. **"Add a friend" placement feels odd** and throws off the page's
   overall feel — reconsider its position/treatment.
3. **Friend rows need general UI/UX improvement** — the user's
   words: they "need improvement" without more specific detail than
   that. Given the round 1 audit already flagged a related but
   different issue on this exact screen (uneven dead space, the
   "Record" vs "Settle" weight mismatch — now fixed per v3's scorecard),
   treat this as an invitation to look at the row design fresh rather
   than assume it's the same already-fixed issue. Screenshot attached.

---

## 6. Bottom nav bar — mostly good now, minor optional polish (💡)

**User's assessment, verbatim reasoning: "it is kinda transparent and
good... it can be better tho, like a small border or something (only
you decide, the one right now is fine too and I'm not a designer so you
decide)."**

This means: the glass rework from last session **worked** — the
previous "dashed rust ring" problem is resolved, real blur is visible,
and the user is satisfied with it as a baseline. This is now genuinely
optional, low-priority polish, not a bug or a must-fix:
- Consider whether a subtle, thin border/edge treatment would add
  refinement — but this is explicitly left to your judgment per the
  user's own words, not a specific requirement. Don't over-invest here;
  the core problem from last round is already solved.

---

## 7. "Lent you" / IOU card — the perforation line (💡)

**User's report, screenshot attached (zoomed crop):** on an IOU credit
card ("Shubham lent you +₹1,000"), the dashed/perforated divider line
"looks oddly placed" and "other things" could be better (user's exact
phrasing, not more specific than that).

Looking at the referenced screenshot: the perforation sits directly
under the merchant/description row, cutting close beneath the text
baseline rather than sitting in a clearly separate visual band — this
may just need more breathing room above/below the perforation, or the
perforation may not belong on every card type (it was originally
designed for group cards' ticket-stub motif per round 1 — worth
confirming whether it's appropriate on an individual IOU row at all, or
whether it migrated there without a deliberate decision to extend it
there).

---

## 8. Expense row — split-avatar badges and payment method styling (💡,
## with a concrete reference image)

**Split-avatar badges ("L," "S," "+1"):** the user's direct report —
these currently carry too much visual weight, "grab your attention" more
than they should for what's meant to be a secondary detail, and the
"+1" specifically is described as "irritating." This was actually
called out as a *positive* addition in v3 (real information density,
lets you tell at a glance which rows are shared) — the concept is right,
the execution is too loud. Find a smart, quieter treatment: smaller
badge size, lower contrast/opacity, or a different visual form entirely
(e.g. a single small icon indicating "shared" with the count as a
tooltip/expandable detail on tap, rather than every participant's
initial rendered at full visual weight inline).

**Payment method styling:** the user attached a reference screenshot
from another app (a dark card showing "−₹20" next to a compact rounded
pill reading "DIGITAL" with a small card icon) as the specific visual
target for how payment method should be displayed on a transaction row.
Compare against the current "PopUPI" plain-text treatment repeated on
nearly every row (already flagged in round 1 as adding reading load) —
this reference image is the concrete direction to implement: a compact
icon+label pill/badge rather than plain inline text.

---

## 9. Font bundling — confirm this actually shipped

Last session's instruction was to bundle all three font families as
local `res/font` resources instead of relying on the runtime Google Play
Services font provider, specifically because silent fallback to system
font (when GMS fails) is the most likely explanation for "the fonts
look generic on my device" despite the font system itself (`Type.kt`,
tabular figures) already being correctly built. **Confirm whether this
shipped in the last round of work** — the report covering PRs #40-44
doesn't explicitly mention it, and it's the kind of fix that's easy to
lose track of amid a large batch. If it didn't ship, prioritize it: this
is very likely the actual root cause behind two independent complaints
(the user's "looks generic on all devices" and v3's own confused,
self-contradicting assessment of the font across different screenshots).

---

## 10. Carried over from v3, not yet addressed by the user's direct
## report but still valid

These come from the v3 audit and weren't specifically called out by the
user this round, but remain open per v3's own scorecard — listed here
so they aren't lost, lower priority than everything above:

1. Archived groups still carry the same visual weight as active ones —
   still not dimmed/deprioritized.
2. Disabled "Add an amount" button contrast — still unconfirmed either
   way.
3. Bottom-content clipping — partially fixed, but reproducing again in
   the Activity list (a different row than last time — "Chai N Snacks"
   now, "Rapido to McD" before — same underlying pattern, not fully
   solved) and in a new place: the AI-insight card's narrative text runs
   off its own container edge (related to §4 above, but worth noting
   as also being a clipping/overflow issue specifically, separate from
   the response-length-cap issue).
4. Avatar icon and category icon sharing a visual vocabulary — the
   user's own avatar (a lightning bolt) is visually identical to the
   Bills category icon used elsewhere, creating a possible mix-up
   between "this is a Bills expense" and "this person is in the split."
5. Repeated food-category icon scannability — still ~7-8 identical bowl
   icons in transaction lists.
6. Month-switcher control uses a different visual pattern from the
   app's other toggle/segmented controls — minor consistency nit, the
   app now has three or four different toggle styles.
7. Contrast check on the semantic red/green against actual background
   black, especially at small sizes (e.g. "You're owed ₹949") — cheap
   to verify, not yet confirmed either way.
8. Profile/Settings screen has "more premium potential than it's using"
   — it's the one screen unchanged since round 1 while everything else
   picked up distinctive color/charts/new components. Specific ideas
   from v3: a card treatment (with subtle glass, consistent with the
   nav bar material) for the merged account/sync status area; a subtle
   gradient ring/glow around the profile avatar specifically (echoing
   the brand gradient used on the FAB/hero numbers, used nowhere as a
   border treatment yet); and still no Kharcha Paani wordmark/logo
   anywhere in the app — Settings is the cheapest place to add one.
9. One pass confirming every icon (category, avatar, form-row, utility)
   comes from one consistent family/stroke weight — more icons are now
   in play than when this was first flagged.

---

## 11. Priority order

**Fix first (bugs, regressions):**
1. UPI visibility regression (§1) — genuinely broken, was reported
   fixed last session, isn't. Add the deep-link fallback alongside the
   fix.
2. Confirm font-bundling actually shipped; ship it now if not (§9).
3. AI features — all four numbered bugs in §4, especially the trip-level
   AI being completely non-functional and the shared-error-state issue
   across tabs.

**Quick, high-value fixes:**
4. Group donut per-person color collision (§2) — reuse the already-
   working category-donut palette approach.
5. Insights category list: full list instead of "+N more," scrollable
   if needed, and scroll-into-view on chart-tap selection (§3).
6. Split-avatar badge visual weight, especially "+1" (§8).
7. Payment-method badge redesign per the reference screenshot (§8).

**Medium:**
8. Splits screen: icon for "Log an IOU," reconsider "Add a friend"
   placement, general friend-row polish (§5).
9. IOU card perforation-line placement (§7).
10. Carried-over items from §10, roughly in the order listed there.

**Optional / low priority:**
11. Nav bar border refinement (§6) — explicitly the user's call, not a
    requirement.

---

*Same closing note as every round: these are observations from
screenshots and direct testing, not a full code audit. Where you know
something these descriptions don't capture, your judgment wins.*
