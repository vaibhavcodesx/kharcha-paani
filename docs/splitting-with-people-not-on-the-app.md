# Splitting with friends who aren't on the app

Short answer: **you already can, and it works well — it just isn't explained
anywhere in the app, which is the actual problem.** This is how it works today,
what it can and can't do, and what changed on 2026-09-19 to make it obvious.

## The two kinds of person in this app

The app has always had two, and the difference is the whole story:

| | **A local friend** | **A groupmate** |
|---|---|---|
| Who they are | A name and a colour you typed | Someone signed in on their own phone |
| Where they exist | Your phone, and since 1.3 in your account, private to you. See below | Your phone and theirs |
| Needs the app? | **No** | Yes |
| Splitting | Yes — full splits, IOUs, settle up | Yes |
| Balances | Yes, tracked exactly the same | Yes |
| Do they see it? | No. It's your record of what's owed | Yes, live on their phone |
| Sync | **To your account only**, for splits and IOUs added from 1.3 on; older ones stay on this phone. See below | Shared with the group |

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

**A local friend can't be a member of a shared group.** If a group syncs with
other people's phones, every member has to be a real account — otherwise their
phone would receive a group naming someone who, as far as it's concerned,
doesn't exist.

Since 1.4.0 a local friend can still be *in* a shared group's expense, as your
guest. Split a dinner with Shubham (on the app) and Hist (not on it), pick the
group, and it syncs to Shubham with Hist on it. You cover Hist's share in the
group, and Hist owes it to you. Only whoever added the expense can change its
guests. If Hist paid, it can't go in the group and stays on your phone.

The app enforces the member rule rather than failing halfway: `SharedRecordGroups`
computes `canSync` as "everyone else in this record is a member of the group,
apart from a split's guests", and the settle-up sheet falls back to
**"Save on this phone only"** when they aren't. A settlement or IOU is between
two people, so both must be members; only a split can bring guests.

So the rule is:

- **Trip with three friends, none on the app** → local group. Everything works.
- **Trip with two on the app and one not** → a shared group between the two of
  you, with the third as a guest on the expenses you add (their share is yours
  to cover, and they owe it to you), or a local group for all three.
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

A local friend is a name you typed. Until 1.3 everything about them lived only
on this phone. That changed in 1.3.0 and again in 1.4.0, and an earlier version
of this section still said their name never left the phone. That stopped being
true, so here is what is true now.

**Splits and IOUs added from 1.3.0 on back up to your account.** A personal
expense split with friends who aren't on the app, and an IOU or payment with
one of them, upload to two tables of their own, `local_splits` and
`local_settlements`, with the friend stored by name. Every policy on those
tables is "the row is yours": no other account can read them, groupmates
included. They come back when you sign in on a new phone or after clearing the
app's data, and each friend comes back with them, by name (with a colour picked
from the name, not the one you chose). Design and test record:
[`local-friend-sync-v2.md`](local-friend-sync-v2.md).

**Older ones stay on this phone.** Splits and IOUs recorded before 1.3.0 were
never uploaded, and editing one doesn't upload it either. Only records made
after the feature shipped sync (owner rule, 2026-09-23). Two other kinds stay
here too: anything in a local (unshared) group, and a split that also includes
someone with an account but isn't in a shared group with them.

**A guest on a shared group's expense is seen by that group.** Since 1.4.0, a
local friend you add to a shared group's expense travels with it, by name, to
everyone in the group, because the expense's shares have to add up on their
phones too.

There is still no `people` table on the server. A local friend exists there
only as a name on the records above.

### Normal updates are safe — this was tested, not assumed

Installing a new version over the old one keeps everything. On 2026-09-19 the
published v1.0.0 was installed from the GitHub Release, a local-only friend, a
subscription and a custom category were added, and a v1.0.1 signed with the same
key was installed over the top without uninstalling. All three came through
untouched, and the package record showed `firstInstallTime` unchanged while
`lastUpdateTime` moved — Android kept the original install and its data
directory rather than starting a new one.

So the day-to-day answer is: **update freely, nothing is at risk.**

### The two things that do lose it

1. **Uninstalling**, including "uninstall and reinstall" to fix something, and
   a factory reset or a new phone.
2. **A change of signing key.** Android refuses to install an APK over one
   signed differently, so the only way through is to uninstall first — which is
   case 1 again. This is why the release keystore matters as much as the data:
   lose the key and every future update becomes an uninstall for everyone.

In both cases what comes back when you sign in again is everything on the
server; what does not is everything below.

| | Survives a normal update | Survives uninstall / new phone |
|---|---|---|
| Your own expenses and income | Yes | Yes |
| Shared groups, their expenses (guests included) and settlements | Yes | Yes |
| Your name and avatar | Yes | Yes |
| Your UPI ID | Yes | Your groupmates still see it; you add it again to show your own code |
| Splits and IOUs with local friends added from 1.3.0 on, and the friends in them | Yes | Yes |
| Splits and IOUs with local friends from before 1.3.0 | **Yes** | **No** |
| Local friends with nothing recorded since 1.3.0 | **Yes** | **No** |
| Local-only groups | **Yes** | **No** |
| Subscriptions | **Yes** | **No** |
| Custom categories and payment methods, and app settings | **Yes** | **No** |

**One catch on a restored phone.** A payment recorded after 1.3.0 can settle a
split from before it. The payment comes back from your account; the split
doesn't. So on a phone restored from the cloud, that friend's balance reads
backwards until you restore the JSON backup.

**If you are about to uninstall, move phones, or you have lost the release
keystore,** take a backup first — Settings → Export to a spreadsheet, and the
JSON backup for the local-only half. It is insurance against those three cases,
not something normal use requires.
