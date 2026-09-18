# Splitting with friends who aren't on the app

Short answer: **you already can, and it works well — it just isn't explained
anywhere in the app, which is the actual problem.** This is how it works today,
what it can and can't do, and what changed on 2026-09-19 to make it obvious.

## The two kinds of person in this app

The app has always had two, and the difference is the whole story:

| | **A local friend** | **A groupmate** |
|---|---|---|
| Who they are | A name and a colour you typed | Someone signed in on their own phone |
| Where they exist | Only on your phone | Your phone and theirs |
| Needs the app? | **No** | Yes |
| Splitting | Yes — full splits, IOUs, settle up | Yes |
| Balances | Yes, tracked exactly the same | Yes |
| Do they see it? | No. It's your record of what's owed | Yes, live on their phone |
| Sync | Backed up to your account, but private to you | Shared with the group |

A local friend is stored as a `PersonEntity` with no `profileId`. Everything
the ledger does — splits, shares, settlements, the debt simplifier, the
who-owes-whom list — treats them identically to a groupmate. **Nothing is
degraded.** The only thing they can't do is see it, because there's no "them"
in the system to show it to.

## How to do it

1. **Splits → Friends → Add a friend.** Type their name, pick a colour. That's
   it — no email, no phone number, no invite.
2. **Log the expense as normal**, tap **Split it**, and tick them.
3. Their balance shows on the Friends tab like anyone else's.
4. When they pay you back, tap **Settle** and record it.

To split a whole trip with them, make a **local group** (Splits → Groups → New
group) and leave it unshared. A local group behaves exactly like a shared one —
member shares, the donut, who-owes-whom, per-group CSV export — and never
leaves your phone.

## The one real limit

**A shared group can't contain a local friend.** If a group syncs with other
people's phones, every member has to be a real account — otherwise their phone
would receive a split naming someone who, as far as it's concerned, doesn't
exist.

The app already enforces this rather than failing halfway: `SharedRecordGroups`
computes `canSync` as "every other person in this split has an account", and
the settle-up sheet falls back to **"Save on this phone only"** when they don't.
That's the right behaviour and it stays.

So the rule is:

- **Trip with three friends, none on the app** → local group. Everything works.
- **Trip with two on the app and one not** → either a shared group between the
  two of you *plus* a separate local record for the third, or a local group for
  all three. The second is usually what you want.
- **A local friend later installs the app** → see below.

## What changed on 2026-09-19

Nothing about the model — it was already right. Three things about explaining it:

1. **The Friends tab now says it.** An empty friends list said "No friends yet"
   and left the most useful fact about the app unstated. It now says a friend
   doesn't need the app.
2. **"Add a friend" says what it buys you.** The sheet's supporting line already
   said "They don't need the app", which is good, and now also says what a local
   friend *can* do, not just what they don't need.
3. **This document**, linked from the PRD.

## The gap worth knowing about

**A local friend who later joins can't be merged into their account.** You'd
have a local "Rohan" with a history, and a groupmate Rohan with none, and no
way to say they're the same person.

This is deliberately *not* built, for a reason worth stating: merging two people
means rewriting every split share, settlement and balance that names either of
them, and getting it wrong silently corrupts a ledger — the one thing this app
must never do. It needs its own design pass, its own migration, and tests that
prove balances are identical before and after.

Until then, the workaround is what you'd expect: keep using the local friend,
or settle up to zero and start fresh in a shared group. It is on the backlog as
its own item rather than smuggled into a UI change.

## A note on what "not on the app" means for your data

A local friend is a name you typed. It syncs to **your** account as part of your
ledger backup, so it survives a lost phone, and it is visible to nobody else —
the RLS policies scope every row to its owner or to a shared group's members,
and a local friend is in neither. Their name never reaches another person's
device.
