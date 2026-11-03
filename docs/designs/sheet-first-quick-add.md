# Design: Sheet-first quick add

Written 2026-11-03 by Claude (cloud), Stream C item 1 of `docs/claude-cloud/HANDOFF.md`.
Work order T3.5; reliability audit REL M5 part 2 (`docs/production-readiness/audits/reliability-ux.md`).
Status: built and in review. The owner judges it on the phone.

## Problem

Quick add is the product (`docs/antigravity/HANDBOOK.md` §1). Before this change, a typed or spoken
line went to the AI, and the sheet only opened when the answer came back. Until then the capture
card showed "Reading it…" with a spinner and a disabled field. On a good connection that was a beat
too long. On a captive portal or 2G it was up to 40 s with no way to cancel. And if someone gave up
and tapped "+" or opened an old entry in the meantime, the late answer overwrote what they were
typing, or quietly turned their edit into a new entry.

The phone already had its own reading of the line (`LocalHeuristicParser`), computed and then thrown
away while it waited.

## What changes

1. **The sheet opens at once** on the phone's reading: amount, a merchant guess, a category, the method
   if one was said, and the split it found ("with Rohan", "Goa trip"). It can be checked and saved
   from the first moment.
2. **"Improving with AI…"** sits quietly at the right of the sheet's eyebrow ("NEW ENTRY"), with a
   12 dp amber working mark, in `labelMedium` and `MutedWarmGray`. It's on the eyebrow line on purpose:
   nothing on the sheet moves when it comes or goes, so a thumb already on the amount doesn't
   land on the wrong thing. It fades in and out, and TalkBack reads it once (polite live region).
3. **The AI's reading fills only what they haven't touched.** Every sheet control reports which part
   of the entry it changed: type, amount, merchant, category, method, note, date, or anything about
   the split (group, who's in, who paid, how it's divided). When the reading arrives, only the
   untouched parts take it. Type and category move together, because a category belongs to its type.
   A split they changed stays theirs, re-sized to whatever amount is on the sheet.
4. **"Updated by AI", briefly, when it changed something.** The improving label cross-fades in
   the same slot to a small amber tick and "Updated by AI" for about 3.5 s, then goes quiet. It's
   there so fields changing under someone's eyes don't read as a glitch. When the AI agreed with
   the phone, nothing is said: the AI's "no date" (its own "now", moments after the phone's)
   counts as the same time, so it doesn't trigger the label or move the entry's time.
5. **The reading stops when they move on.** Closing the sheet, saving, opening "+" or a group's add,
   opening an entry to edit, reading a bill, or typing another line all cancel it. A save writes
   exactly what was on the sheet, and nothing lands after it.
6. **Notices are unchanged in wording.** When the AI couldn't help (offline, out of quota), the same
   notice as before appears once the attempt ends, and the phone's guess stays. When a name in the
   line didn't find its friend, that's said as before, unless they've already changed the split.

Not changed: the AI notice gate (D2), the prompts, the server, the bill-photo path (which still
shows its in-sheet placeholder; its own cancel is Y5, Codex's lane), and the 40 s timeouts in
`GeminiRepository` (the audit suggests ~10 s connect for text. That's in Codex's lane, and with the
sheet already open, a slow answer no longer blocks anyone).

## Why this and not alternatives

- **A cancel button on the spinner** fixes being stuck but keeps the wait. Rejected: the wait is the problem.
- **Applying the AI's answer wholesale unless the sheet is "dirty"**: one stray tap on Category
  would throw away the AI's merchant and split too. Field by field costs a handful of lines and
  never loses a correction.
- **A banner or toast listing what the AI changed**: more noise on a sheet whose job is to be
  glanced at and saved. A per-field highlight was considered too; it's a new visual pattern for
  one moment. The owner chose good UX over silence (#188), so the brief "Updated by AI" in the
  label's own slot is the middle way: it explains the change without moving anything.

## Risks and how they're covered

| Risk | Cover |
|---|---|
| A late answer overwrites typing | `SheetFirstQuickAddTest`: typed amount, merchant and split survive; untouched fields are filled |
| A late answer turns an edit into a new entry | Test: an edit opened while reading keeps its id, amount and merchant |
| A late answer changes a save that's writing | Save cancels the reading first; test checks the row in the real Room database |
| A closed sheet reopens | Test: dismiss while reading; the sheet stays closed and its state unchanged |
| Shares stop adding up | Test: the split they set is re-sized to their amount, and the shares sum to it |
| Builds without the AI | No "Improving…" at all; the phone's guess, as before (`EqualSplitPaisaTest`) |
| "Updated by AI" when nothing changed | Test: an AI reading equal to the phone's keeps the flag down and the entry's time |

## Check on the phone (CI can't)

- Type "dinner 1,299 with Rohan gpay" on Home. The sheet should be up before you can blink, with
  "Improving with AI…" at the top right, which should turn into "Updated by AI" within a second
  or two and then fade. Does that read as calm, or as too much?
- Same line in airplane mode with Wi-Fi to a captive portal if you can (or very weak signal): the
  sheet should still open instantly and be saveable while the label shows.
- While the label shows, change the amount, then wait. Your amount must stay.
- From the widget's mic, and by sharing an SMS or WhatsApp payment message into the app: the sheet opens
  on the guess the same way. (The widget's "+" opens an empty sheet, as before.)
- Save while the label shows. Then check the entry in Activity: it's exactly what the sheet showed.
- TalkBack: "Improving with AI…" is read once and doesn't interrupt the amount field.
