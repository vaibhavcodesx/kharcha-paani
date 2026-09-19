# Round 2: what each bug actually was

Companion to `design-and-feature-backlog-round2.md`. One line per report, then
the cause, because in most cases the cause was not where the report pointed and
in two cases the previous session had already "verified" the feature.

## The thing worth taking from this round

Two features were reported fixed and verified last session, and both were
broken on the phone. Neither verification was wrong; both tested the wrong
layer.

| Feature | What was verified | What was actually broken |
|---|---|---|
| UPI visibility | The RLS policy, against the live database, with two real users | The client's `select` column list |
| The AI prompts | The context builders, the daily cap, the answer states — 30-odd unit tests | The token budget, and the wiring between two composables |

Both have a test now that would have caught them — `GroupSelectColumnsTest`
and `MainViewModelConstructionTest` — and both of those tests check a *seam*
rather than a unit. That is the pattern: the units were fine.

---

## §1 — UPI visibility

**Reported:** the other person settling up sees "no UPI ID added" when one is
set. Each person can only see their own.

**Cause.** `SupabaseGroupRemote.visibleGroups()` asked PostgREST for:

```
*, group_members(profile_id, profiles(id, display_name, avatar_url, avatar_emoji))
```

`RemoteProfile` has five fields. The fifth, `upi_id`, was added when the
feature was built and never added to this string. PostgREST returns exactly
the columns it is asked for; a column it is not asked for is absent from the
JSON; an absent field with a default deserialises silently. So every
groupmate's handle arrived as `null` on every device, for ever, with no error
at any layer.

**Confirmed against the live database** — acting as one account and running
the query the client runs, the other account's handle comes back exactly as it
should. RLS was never the problem, which is why testing RLS found nothing.

**The guard.** `GroupSelectColumnsTest` walks the serial descriptors of
`RemoteProfile` and `RemoteGroupMember` and fails if a field exists that the
select string does not name. A query that silently drops data cannot be caught
any other way: there is no exception, no log line, and no wrong value — just a
default where a value should have been.

---

## §4.4 — the trip read "isn't even clickable"

**Cause, and it is worth reading twice.** `GroupDetailSheet` takes
`onAskTripReview` and passes it no further. `GroupDetailContent` declares it
with a default of `{}`. The button drew, at full contrast, enabled, and ran an
empty lambda on every tap.

Four parameters stopped at that boundary: `onAskTripReview`, `tripReview`,
`aiAsksLeft` and `aiAvailable`. The compiler had nothing to say, because every
one of them has a default. **A default on a composable's callback turns a
forwarding mistake into silence.**

It also wore the Ghost tier, whose fill is within a shade of the card behind
it (see §5 below), so it had no visible edge either. Two independent reasons
for the same report.

---

## §4.1 and §4.2 — cut off mid-sentence, and "What to change" usually fails

**One cause.** `maxOutputTokens` was 2048, and on a thinking model that
budget covers the thinking as well as the answer — confirmed in Google's own
documentation: *"sets the maximum number of tokens a response can generate,
including thought tokens."*

That single fact explains the exact shape of the report:

- **"This month"** is a short question. Thinking took most of 2048, the answer
  got what was left, and it stopped mid-clause at "You also had".
- **"What to change"** asks for three findings, each justified with a figure,
  against three months of history. Thinking took the lot; the answer came back
  empty; `takeIf { it.isNotBlank() }` turned that into `null`; the screen
  reported a failure. Hence "fails most of the time" rather than "fails".

The harder the question, the worse it got. Now 8192, with `AiAnswerText`
trimming to the last finished sentence if anything ever outgrows even that.

This is the same shape as the statement-scanning bug fixed last session — a
budget sized for the answer, on a model that spends the budget thinking first.
It should have been looked for everywhere the first time.

---

## §4.3 — one tab's error shows on all three

**Cause.** Three independent requests sharing one `_aiSummary` string and one
`_aiSummaryFailed` boolean. Whatever finished last was what every chip showed.

This is the one report in the round that named an architecture problem rather
than a symptom, and it was right. `AskBoard` keys state by question.

Side effect worth having: a question already answered is shown again instead
of re-asked, which is instant and does not spend another of the day's asks.

---

## §2 — the group donut's three oranges

**Two faults, stacked.**

**The arithmetic.** `abs(profileId.hashCode() % AVATAR_COLORS.size)`. Kotlin's
`%` takes the sign of its dividend, so a negative hash gives a negative
remainder, and `abs` folds it onto the same slot as its positive twin — index
3 is reached from both `+3` and `-3`. On this account's four real profile IDs
that is not a theoretical risk:

| Person | hash | `% 8` | `abs(%)` | `floorMod` |
|---|---|---|---|---|
| Mayank | −399321934 | −6 | **6** | 2 |
| Shubham | 332113659 | +3 | **3** | 3 |
| Lal | −862288227 | −3 | **3** | 5 |
| Vaibhav | 1569362038 | +6 | **6** | 6 |

Two pairs collide outright. `floorMod` separates all four. `PersonColoursTest`
pins this with the real IDs.

**Nothing checked afterwards.** A colour was chosen once, at insert, against
nobody. Two people could hold the same one for ever with nothing to notice or
fix it. `PersonColours.respace` treats the people on a phone as a set instead:
first holder keeps it, a duplicate moves to the nearest free slot, in
`createdAt` then `id` order so it is the same answer on every device. It runs
at startup, writes only rows that must change, and is idempotent.

**And a third.** `parseColor`'s fallback was `WarmAmberPrimary` — which is
also `PersonColours.MINE`, "You". An unreadable colour did not fail visibly;
it quietly made a groupmate look like the account holder, in the one chart
where telling people apart is the entire point. Fallbacks should never equal a
meaningful value.

---

## §5 and §6 — the invisible container

`CharcoalElevated` is `#22201D`. `CharcoalCard` is `#1E1C1A`. Four points per
channel. The Ghost button tier filled itself with the first and lives mostly
on the second, so its container has been effectively undrawn since it was
written — "Record" on every friend row, "Read this trip", sheet rows.

A fixed opaque colour cannot serve a tier used on two different surfaces. A
10% white wash can: it lands around `#343230` on a card and `#262523` on the
page, visible on both, and never becomes an Outline.

**Measuring it needed the right metric.** WCAG contrast ratio has a `+0.05`
term that dominates at near-black; it rates this app's card against this app's
background at 1.14:1, a number that would condemn a pairing anyone can see
perfectly well on a phone. `SurfaceContrastTest` uses L\* instead, which is
near-linear in perceived lightness all the way down and cleanly separates 1.9
points (the fill that failed) from 7–11 (the pairs that work). Text keeps the
WCAG ratio in `TextContrastTest`, which is what the ratio was built for.

---

## §7 — the perforation "looks oddly placed"

The settlement card clips itself to `KpShapes.ticket`, which cuts notches at
half height, and then drew its perforation at `0.82`. The line met nothing at
either end — it ran a few pixels under the subtitle with only padding beneath
it.

Moving it to `0.5` would not have helped. A perforation separates two bands of
content; this row is one band. On a `TripCard` it tears off a stub, which is
why it was written there. It came across to a layout that cannot carry it.
Notched edges stay, line goes.

---

## What was checked and turned out to be fine

- **§9, font bundling.** Shipped in #40. `res/font/` holds all three variable
  fonts, `Type.kt` reads them, the GMS provider and `font_certs.xml` are gone.
- **§10.2, disabled button contrast** and **§10.7, red/green on black.** Both
  pass, on every surface they appear on, and `TextContrastTest` keeps it that
  way. §10.7 passes at body size as well as figure size, which is the stricter
  bar and the honest one.
- **§10.3, bottom clipping.** The floating dock passing over content
  mid-scroll. `contentPadding` clears it at the end of every list.
- **§10.9, the wordmark.** Already at the top of the account sheet.
