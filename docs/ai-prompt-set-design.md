# Design proposal — the AI prompt set (backlog item 16 / §4.3)

**Status: proposal. Nothing built yet.** Backlog §4.3 says to propose the
prompt set and its presentation before building it, the same as every
other feature design in this project.

## What exists now

One button, "Get a summary", on the Insights month card. It sends
`MonthlyAnalytics` — month totals, a category breakdown, top merchant,
logging streak — and asks for two or three encouraging sentences. It is
generic because the prompt is generic: it has aggregates and nothing to
be specific about.

The owner's ask: a set of pre-built prompts you can tap, and
**group-aware analysis** that reads a trip the way a knowledgeable friend
would, not the way a bank statement would.

## The shape of the thing

Four prompts, not a chat box. A chat box makes the person do the work of
knowing what to ask, and every answer arrives cold. Four named questions
that this app can actually answer well are worth more than an open field.

Each one is a **different question over different data**, not the same
summary with a different adjective on it. If two prompts would send
nearly the same context, they should be one prompt.

| # | Prompt | Lives on | Data it gets |
|---|---|---|---|
| 1 | **Month in review** | Insights | Month totals, categories, top merchants, streak, and — new — the day-by-day series |
| 2 | **Where am I overspending?** | Insights | This month *and* the three before it, per category |
| 3 | **What should I change?** | Insights | Same as 2, plus recurring subscriptions and payment-method mix |
| 4 | **How did this trip go?** | Group sheet | One group: members, per-person shares, categories, date span, per-day rate |

### Why these four

**1. Month in review** stays, and stops being generic. Today it gets
aggregates only, so the best it can do is restate them. Adding the daily
series lets it say something a total cannot: *"Most of it was three days
— the 9th, 14th and 15th came to ₹8,400 between them."* That is the same
button, made specific by giving it something specific.

**2. Where am I overspending?** is the one question aggregates genuinely
cannot answer, because "overspending" is relative to something. It needs
history. Comparing this month against the previous three is the smallest
context that supports a real claim: *"Food is ₹2,100 above your last
three months; everything else is in line."* Without history this prompt
would be an opinion about a single number, which is exactly the generic
thing being replaced.

**3. What should I change?** is the actionable one, and the one most
likely to produce slop if under-specified. It gets what the app knows
that the person may not have added up: subscriptions that quietly renew,
and a payment-method mix. The prompt should ask for **at most three
changes, each naming a figure from the data**, and explicitly forbid
generic advice ("make a budget", "cook at home"). If it cannot find three
specific things, it should return fewer.

**4. How did this trip go?** is the distinctive one, and it belongs on
the **group sheet**, not Insights — it is about a group, and asking it
from Insights would need a group picker for no reason. It gets the
group's whole life: who paid what, the split, the categories, how many
days it ran, the daily rate. That is enough to say *"Nine days, ₹9,614,
₹356 a day between three of you — Lal carried the cost and is owed
₹3,071."*

### What is deliberately not in the set

- **A chat box.** See above.
- **"Am I saving enough?"** The app does not know income reliably —
  `Came in` is whatever was logged, which for most people is nothing.
  A prompt whose premise the data cannot support is a slop generator.
- **Predictions and forecasts.** Same reason: not enough history yet, and
  a confident wrong number about someone's money is worse than silence.

## Presentation

On **Insights**, the single "Month in review" card becomes an **Ask
section** with three chips in a row above the answer area. Tapping a chip
runs it, and its answer replaces whatever was there; the chip stays
selected so it is clear which question the text answers. The answer area
keeps the existing shimmer, the existing failure line, and a **Retry**.

On the **group sheet**, one full-width Ghost-tier button ("How did this
trip go?") below "Who spent what", answering in the same card style.

Three things worth deciding deliberately:

- **Answers are not saved.** They are cheap to regenerate and a stale
  read of last month's money is worse than no read. The existing summary
  already behaves this way.
- **One at a time.** Tapping a second chip while one is running cancels
  the first. No queue.
- **Nothing runs on open.** Every one of these is a paid call on the
  owner's key; they run when tapped.

## Voice

This is the part that decides whether the feature feels designed or
generated, so it is specified rather than left to the model.

The template is already in the app — Settings' *"Signing out keeps
everything already on this phone"*: plain, specific, and it tells you the
thing you actually wanted to know. Applied here:

- **Name figures.** "Food was ₹2,009" beats "you spent a lot on food".
- **Compare to something.** A number with nothing beside it is trivia.
- **Indian English, rupees, no currency conversion**, no "$".
- **No praise that isn't earned.** "Nice work!" on a month that went 40%
  over is the single clearest tell of a generated app. If the month was
  bad, say what happened without scolding.
- **Two to four sentences**, except prompt 3, which may use up to three
  short bullets.
- **Never invent a number.** Every figure must come from the data sent.
  This is the same rule that `c0efe0f` had to enforce on the statement
  importer after it showed generated sample transactions as the person's
  own — it is a mistake this codebase has made once already.

For prompt 4 specifically, the register is *a friend who has seen the
receipts*: it can be pleased about a cheap trip, can point out the one
day that blew the budget, and should mention people by name because the
data has names. It should not moralise about anyone's spending — it is
their trip.

## Failure, empty and offline

Every prompt needs all three states designed, not just the happy one —
Part B of the PRD calls collapsing them together an AI-slop tell.

| State | What the person sees |
|---|---|
| No API key in the build | The section is not rendered at all. Better than a button that can never work. |
| Nothing logged this month | Chips disabled with one line: "Log a few expenses first; there's nothing to read yet." |
| Not enough history (prompts 2, 3) | Chip runs anyway but the prompt is told how little it has, and is instructed to say so rather than overreach. |
| Offline / call failed | The existing line plus a Retry. The answer already on screen stays. |
| Group with no expenses (prompt 4) | Button hidden. |

## What this needs in code

- `GeminiRepository` gains one `ask(prompt, context)` rather than four
  near-identical methods. The timeout and error handling from the
  statement fix already generalise; these are short calls, so they keep
  the 30-second budget.
- The context builders are **pure domain functions** — they take
  transactions and return a string — so what the model is asked can be
  tested without a network, the same way `DailySpend` is.
- `MonthInsights` gains a three-month trailing comparison for prompts 2
  and 3. `GroupInsights` already has everything prompt 4 needs.
- One `AskSection` composable used by both Insights and the group sheet.

## Open questions for the owner

1. **Four prompts, or three?** Prompts 2 and 3 are close relatives —
   "where" and "what to do about it". They could be one, which would make
   the Insights row two chips instead of three and each answer meatier.
   My recommendation: keep them separate. "Where" is diagnostic and safe;
   "what to change" is advice, and merging them means every diagnosis
   arrives with advice attached whether or not it was wanted.
2. **Cost.** Four prompts on the owner's own Gemini key, each a real
   call. Should there be a per-day cap, or is tap-to-run enough?
3. **Prompt 4's placement** — group sheet only, or also a shortcut from
   Insights when exactly one group has been active this month?
