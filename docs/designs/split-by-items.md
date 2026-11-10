# Split by items: everyone pays for what they had

Status: designed and built by the agent on 2026-11-10, as the owner asked ("decide things yourself,
work on autopilot"). The app, the web page and the database migration are in one PR. **The migration
is not applied and the page is not published**: both are the owner's to do (see "To ship it").

The idea is Splitwise's Itemize (announced 2026-10-14): one person photographs the bill, everyone at
the table taps what they had, tax and tip follow the items, and nobody else needs the app.

## What it does

1. **Bill photo** on Home now asks *Take photo* or *Choose from gallery*. The bill is read as before
   and the entry sheet opens with its total and place.
2. Under *Who's it for* the sheet has **Split by items**. It opens a second sheet that reads the same
   photo again, this time for its lines: each thing ordered, how many, and the line's price.
3. Pick a person at the top, tap what they had. A dish that was shared is tapped for everyone who
   had some and divided between them; an item with a quantity (4 beers) gets a count per person.
4. **Share with the table** puts the bill behind a link and shows its QR code. Friends open it in
   any browser, type a first name, and tap their own items. No app, no account. Their picks show on
   the host's phone within about three seconds, and the host's on theirs.
5. **Use this split** hands each person's amount back to the entry sheet as an ordinary split by
   amount. Saving it is the same save as any other expense: same sync, same balances, same groups.

Whatever the bill's total has beyond its items (GST, service charge, tip, round-off, or less than
them for a discount) is shared in proportion to what each person picked. Anything nobody picked is
shared equally between everyone at the table when the split is used, and the sheet says so first.

## Decisions

These are the agent's calls. Each is marked so the owner can overrule it.

**D1. It produces an ordinary expense.** The ledger, balances, sync and debt simplification are not
touched. The feature's whole output is the entry sheet's own split, filled in. Nothing about items is
stored with the expense after it's saved. *(Keeps the "don't re-architect what works" rule. The cost:
you can't reopen a saved expense and see who had what. Worth adding only if people ask.)*

**D2. The total is the truth; the items explain it.** There are no separate tax, tip or discount
fields. The gap between the items and the total is one number, shared proportionally. A misread
price therefore can't make the split add up to the wrong total, and the sheet warns when the gap is
more than 35% of the items ("check the prices").

**D3. Paise, and one rounding rule on both sides.** Amounts are whole paise. An item is divided by
weight, rounded down, and the leftover paise go to the largest remainders, earlier people first.
Extras the same way. Once every item is picked the shares add up to the total exactly. The app
(`domain/ItemSplit.kt`) and the page (`site/split.html`) each implement this, and both are run
against the same cases (`app/src/test/resources/item-split-vectors.json`): `ItemSplitTest` in the
app, `scripts/check_split_page.mjs` for the page, on CI.

**D4. Tapping means "I had some"; the count is relative.** Two people on "Kingfisher ×4" with
counts 2 and 2 pay half each; 3 and 1 pay three quarters and a quarter. One person alone on it pays
all of it until someone else taps it. The alternative (each count is a fixed number of units, the
rest left unpicked) gets "we shared the naan basket" wrong unless people adjust numbers. The row
shows "your part ₹360" whenever it isn't simply the price, so nobody has to work it out.

**D5. The link is the table.** Anyone holding the link can see that one bill, add a name, and tick
items for anyone except the host ("I'll tick for Priya, her phone's dead" is meant to work). There
are no per-guest secrets to lose. The host's own picks need the host's session. Nothing a guest does
reaches a ledger: the host looks at the picks and saves the expense themself, and can remove a
person or stop sharing. *(Stricter would be a secret per guest; it costs a "that's not me" recovery
flow for little gain at a dinner table.)*

**D6. Guests use the web page even if they have the app.** The link always opens the page. Handling
it inside the app needs verified App Links (plan T3.3, not built) and a second in-app screen. *(A
real follow-up: an app user's pick could then land on their own account instead of as a name.)*

**D7. A guest's name becomes a friend when the split is used.** A name that matches a friend already
on the phone is that friend; a new name is added as a friend who isn't on the app, in a free colour.
In a shared group they're a guest, covered by the host, as guests already are.

**D8. Nothing is paid on the page.** No UPI ID is shown to link-holders. Friends settle through the
app's existing ways once the expense is saved. *(Ask-to-pay's public request page is still gated in
its own design; this doesn't jump that gate.)*

**D9. A split lives 24 hours on the server, and dies with its entry.** Saving the expense, closing
the entry without saving, or "Stop sharing" deletes it and the link stops working. Otherwise it
can't be opened after 24 hours and is deleted the next time anyone starts a split. On the phone the
draft is one small file in the cache folder; if the app is closed mid-split, Home offers it back.

**D10. Reading the items is a second AI read.** It uses one of the day's 10 bill reads and takes a
couple of seconds, only for people who tap Split by items. The existing bill prompt is unchanged.
*(One combined read would save the second call but slow every bill and screenshot for everyone, and
change a prompt the benchmark was run against.)*

**D11. Without an account it still works, on the phone.** A local ledger can split by items by
hand. It can't read the bill (AI needs an account) or share a link, and says so.

## The server

Migration `20261110000000_bill_item_splits.sql`. Four tables in `private`, which the API doesn't
expose, reached only through eight functions:

| Function | Who | What |
|---|---|---|
| `create_bill_split`, `update_bill_split`, `remove_bill_split_person`, `close_bill_split` | the host, signed in | start, correct the bill, take someone off, finish |
| `bill_split_view`, `bill_split_join`, `bill_split_claim`, `bill_split_done` | anyone with the link, **signed out included** | see the bill, add a name, pick, say "that's everything" |

**This is the first thing a signed-out caller can run**, which is why the PR is for the owner's
review. Until now `anon` could execute nothing (20261025000300, audit M-4). The four link functions
are granted to `anon` by name; `database_hygiene_test.sql` now asserts that these four are the only
ones. What bounds them:

- The token is 32 hex characters from `gen_random_uuid()` (122 random bits). There is nothing to
  enumerate and no way to list splits.
- Each function looks its split up by token, then touches only that split's rows. Claims carry the
  split id in a composite foreign key, so a claim can't tie one split's item to another's person.
- 30 people and 100 items a split, names cleaned and cut to 40 characters, weights 1 to 20, 10 open
  splits a host.
- What is stored: item names and prices, the total, first names. No guest account ids, no phone
  numbers, no photo, no UPI ID.

The alternative was an Edge Function holding the service role, so the database stays closed to
`anon`. It was passed over: the exposure is the same four operations either way, SQL functions are
tested in `supabase/tests/`, and a service-role key in a function that takes unauthenticated input
is the bigger thing to get wrong. The owner may prefer it anyway; the page would change one URL.

## The page

`site/split.html`: one hand-written file, no build step, no libraries, nothing loaded from anywhere
else, a Content-Security-Policy that allows only the project's API. The split's key is the part of
the address after `#`, which a browser never sends to GitHub Pages or puts in a referrer. It polls
every three seconds while visible, shows a pick at once and settles on the server's reply, and
remembers which name is yours in that browser only. All text is set as text, never as HTML.

States: loading, who are you (tap a name already there, or type one), picking, done, reconnecting,
"this bill isn't open any more" (finished, expired or a bad link, all the same to a guest).

## The app

| Piece | File |
|---|---|
| The arithmetic | `domain/ItemSplit.kt` |
| Reading the lines | `GeminiRepository.readBillItems` (feature `bill`, no proxy change) |
| The server calls | `data/sync/BillSplitRemote.kt` |
| The draft file | `data/itemsplit/ItemSplitStore.kt` |
| The flow | `ui/itemsplit/ItemSplitController.kt` |
| The sheet | `ui/itemsplit/ItemSplitSheet.kt` |
| Into the entry | `MainViewModel.openItemSplit`, `useItemSplit`, `resumeItemSplit` |

While shared, a change shows at once, is sent in order, and the reply settles it. People and picks
follow the server. The bill itself (items, total) is the host's alone: the phone's copy wins and is
sent again if the server's differs, at most twice in a row so a refusal can't loop. A change that
didn't arrive is put back, and says so.

## Not built

- Opening the link inside the app (D6), paying from the page (D8), items kept with the saved
  expense (D1).
- Editing an item's price from the page: only the host corrects the bill.
- A scheduled clean-up: expired splits go when the next one starts. Enough at this size; `pg_cron`
  if it ever isn't.
- Hindi or Hinglish on the page.

## Tests

- `supabase/tests/bill_item_splits_test.sql`: 56 checks. Run on a local Postgres (PGlite,
  `supabase/tests/local/run.mjs`) with every migration replayed: 56/56. **Not run against live**: the
  rolled-back live run was declined because the script changes grants.
- `ItemSplitTest`, `scripts/check_split_page.mjs`: the arithmetic, both sides, same cases.
- `ItemSplitControllerTest`: the flow against a table that behaves like the server, including a
  dropped connection, a full table, a link that ran out, and the app being closed mid-split.
- `ItemSplitEntryTest`: the real view model and a real ledger. The picks become the split, saving
  moves each friend's balance by what they had, a guest's name becomes a friend.
- `GeminiBillItemsTest`, `ItemSplitSheetTest`.
- The page was driven in a browser against the real SQL (PGlite behind a small local server): join,
  pick, count, a host's pick arriving, the host's name refused, finish, bad link.

## To ship it (owner)

1. Review and apply the migration (Supabase SQL editor, or ask the agent once approved).
2. Copy `site/split.html` into the public `kharcha-paani-site` repository beside the legal pages
   and push. The app's links point at `…/kharcha-paani-site/split.html`.
3. Republish the privacy policy (it has a new paragraph) the same way.
4. On the phone, with a signed release build: photograph a bill, Split by items, share, open the
   link on a second phone that doesn't have the app, pick, use the split, save.
