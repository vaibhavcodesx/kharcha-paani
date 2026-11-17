# UX, UI and feature gaps, 2026-11-17

Reviewed from the code on `origin/main` at `8839b3f`: `MainActivity.kt`, everything under `ui/`,
`widget/`, `reminder/`, `ask/`, `voice/`, `updates/`, `reviews/`, the string resources and
`site/split.html`. Read as three people: someone who just installed it, a product designer, and a
daily user with 30 friends and 2,000 entries.

**How it was done and what that means.** A reviewer read the UI code in full; the ten High findings
and several Medium ones were then re-checked against the source by a second pass (marked ✔). Nothing
here was seen on a phone: there is no emulator on the owner's machine, so motion, keyboard
behaviour, TalkBack in use and real rendering are unchecked. Section "Needs a phone" lists what to
look at. Paths are relative to `app/src/main/java/com/vaibhav/kharchapaani/` unless they start with
`app/`, `site/` or `docs/`.

Counts: **0 Blocker, 10 High, 28 Medium, 21 Low.**

## The honest overall picture

The craft is real. The copy is warm and specific, errors say what to do, destructive actions
explain their consequence, settlements are a two-sided confirm, and accessibility semantics are
better than in most shipped apps. This does not read as a generated app.

What is weak is **the shape of the product around that craft**. The app grew feature by feature
through 290 pull requests, and it shows in three ways:

1. **Things you can create but not change.** Groups cannot be renamed or get a new member, friends
   cannot be renamed, and an expense cannot be deleted from the screen where you edit it.
2. **Everything else lives behind your own initial.** UPI ID, subscriptions, categories, backup,
   export, notifications and AI settings are 25 rows in one sheet opened from an unlabeled avatar.
3. **The app never explains itself.** There is no first-run teaching, the gestures that do the work
   are invisible, and the single most important fact (a split is private unless you share a group)
   is explained in a dialog after you press Save.

A new user from the Play Store will get through sign-in happily and then not find half the app.

## Fix these first (the ten High findings)

| Id | What | Size |
|---|---|---|
| UX-19 ✔ | Groups can't be edited after creation, and the app says they can | M |
| UX-06 ✔ | No visible way to delete an expense | S |
| UX-22 | Private vs shared is explained only after Save | S |
| UX-02 ✔ | Declining the AI notice isn't remembered; it reopens every time | S |
| UX-31 ✔ | "Use a local ledger instead" switches ledgers in one tap | S |
| UX-01 | A guest ledger never moves into an account | M |
| UX-21 ✔ | Leaving a group with a balance shows no warning | S |
| UX-20 | Who paid and who shares is shown by colour dots only | S |
| UX-05 | Settings and half the features hide behind an unlabeled avatar | M |
| UX-03 | No teaching after sign-in | M |

---

## First run

**UX-01 · High · "Use without signing in" creates a second ledger that never merges.**
Evidence: `app/src/main/res/values/guest_strings.xml:3,7` ("Signing in never uploads what's here");
`ui/auth/SignInScreen.kt:254-261` (a plain text button with a small caption);
`ui/auth/AccountSheet.kt:276-279`. No import path exists anywhere in the source.
Why it hurts: someone who logs as a guest for three months and then signs in "to back it up" lands
on an empty ledger and believes the data is gone. A guest who reinstalls loses everything unless
they found "Export everything".
Fix: at sign-in, offer "Move what's on this phone into my account" (the backup restore path can do
it). Until that exists, make the warning a proper notice with an Export shortcut, and nudge guests
on Home to back up.

**UX-02 · High ✔ · Declining the AI notice isn't remembered.**
Evidence: `ui/viewmodel/MainViewModel.kt:2480-2484` (`declineAiNotice` closes the sheet and stores
nothing); `ui/components/AiNoticeSheet.kt:20-26` (swiping the sheet away counts as declining).
Why it hurts: the first thing a new user does is type "chai 40". A long sheet naming two AI vendors
blocks it. Choosing "Type it myself" brings the same sheet back on the next entry, forever, unless
they find Account → Use AI.
Fix: on decline, turn the "Use AI" setting off and show the existing quiet "AI is off" line with a
"Turn on" link. Retitle the sheet as a question ("Let AI fill in your entries?").

**UX-03 · High · No teaching after sign-in.**
Evidence: no string mentions swiping or long-pressing; `ui/components/TransactionItemCard.kt:63-68,
131-143` (swipe to edit and delete); `ui/components/FriendRow.kt:99-103` and
`ui/components/TripCard.kt:46-50` (delete is long-press only). TODOS item 10 (walkthrough) is deferred.
Fix: not a tour. Three one-time tips: a swipe hint after the first entry; a primary "Add your first
friend" button on the empty Splits tab (`EmptyState` already has an unused `action` slot); an "Add
your UPI ID so friends can pay you" row on Home until it is set.

**UX-04 · Low · The microphone permission is asked for cold.** `ui/home/VoiceCapture.kt:150-156`.
The fallback when it is refused is good. Add one line before the system dialog (also Play item P-03).

## Navigation

**UX-05 · High · Settings and half the features hide behind an unlabeled avatar.**
Evidence: `ui/components/AccountAvatar.kt:28-60` (a 40 dp circle with the user's initial);
`ui/auth/AccountSheet.kt:393-551` (UPI, Subscriptions, Categories, bill-photo sharing, AI,
Notifications, Export, Backup, Restore, Help, Sign out, Delete). Copy elsewhere says "turn it on in
Settings" (`strings.xml:994`) but nothing is called Settings.
Fix: a gear, or the word. Split the sheet into three: You (profile, account), Money setup (UPI,
categories, subscriptions), Your data (export, backup, restore, delete). Put UPI and Subscriptions
where people look for them: Splits and Home.

**UX-06 · High ✔ · No visible way to delete an expense.**
Evidence: `ui/components/ConfirmBottomSheet.kt` contains the word "delete" zero times; group detail
rows only open the editor; delete exists only as a swipe on Home (`TransactionItemCard.kt`).
Fix: a "Delete" button at the bottom of the edit sheet using the existing undo path, and the same
in the group's expense rows.

**UX-07 · Medium · Opening anything from the Account sheet closes it, and sheets stack on sheets.**
`MainActivity.kt:872-883,926-933`: every child sets `isAccountSheetOpen = false`, so after changing
the UPI ID you are back on Home. The entry sheet can stack Add friend, Item split, the AI notice,
"Share this split" and Create group (`:1037-1114,1194-1252`). Reopen Account when a child closes,
and never stack more than two layers.

**UX-08 · Medium · Only the entry sheet protects what you typed.**
`ui/components/Sheets.kt:108-131` makes the discard guard optional and only
`ConfirmBottomSheet.kt:165-170` uses it. `ui/components/CreateGroupSheet.kt:71` keeps the ticked
members in plain `remember`, so rotating the phone clears them. A stray tap outside loses a
settle-up amount or a group with eight members ticked.

**UX-09 · Medium · No "split with this friend" shortcut.** `ui/components/FriendRow.kt:93-137`
offers history and Settle only. Splitting dinner with one friend is: add, amount, title, "Split
it", pick the friend, Save, then a dialog. Add "Add expense" on the friend row and in their history,
with the friend preselected.

**UX-10 · Medium · "Who pays whom" is a dead end.** `ui/components/SimplifyDebtsSheet.kt:28-65` has
no actions. The same thing is called "Simplify who pays whom" on Home and "Who pays whom" on Splits.
Add a Settle button on the rows that involve you.

**UX-11 · Medium ✔ · The "Expenses" filter hides split expenses.** `domain/ActivityFeed.kt:62`:
`EXPENSES -> tx.type == "expense" && !tx.isSplit`. A person filtering to see what they spent does
not see dinners they split. There is no category, date or group filter at all
(`ui/screens/HomeScreen.kt:380`).

**UX-12 · Low · History lines can't be opened.** `ui/components/PersonLedgerSheet.kt:98-150`,
`GroupDetailSheet.kt:431-488`. Tapping a line in a friend's history should open that expense.

## Capture

**UX-13 · Medium · The amount field shows no digit grouping while you type.**
`ui/components/Sheets.kt:379-435` draws "240000". Was U8 in the earlier audit, still open.

**UX-14 · Medium · The keyboard doesn't help.** `ImeAction` appears in three files. `SheetTextInput`
(`Sheets.kt:464-496`) passes only the keyboard type: names aren't capitalised, "Next" doesn't move
between fields, "Done" doesn't save.

**UX-15 · Medium · Several fields still accept more than the server allows.**
`domain/TextLimits.kt:14-15` defines limits that nothing uses. No maximum in `SettleUpSheet.kt:130`,
`IouSheet.kt:139`, `CreateGroupSheet.kt:106`, `SubscriptionsSheet.kt:157`, `UpiSheet.kt:94`,
`CategoriesSheet.kt:255`. A long pasted note becomes a row that fails to sync. Was REL L9.

**UX-16 · Medium · A half-filled entry is lost if Android kills the app.** No `SavedStateHandle`
anywhere; the draft lives in plain flows (`MainViewModel.kt:1211-1233`). Codex's draft PR #252 does
this and is stuck with conflicts.

**UX-17 · Low · Date and time are two system dialogs, and the time picker ignores the phone's
24-hour setting** (`ConfirmBottomSheet.kt:654-677`, `TimePickerDialog(..., false)`).

**UX-18 · Low · Every new entry starts as "Food" paid by "UPI"** (`domain/Categories.kt:47-49`).
People who skip that row mislabel their spending, which then skews Insights.

## Splits and groups

**UX-19 · High ✔ · Groups can't be edited after creation, and the app says they can.**
Evidence: `strings.xml:438` says "You can add members after creating it".
`data/repository/LedgerRepository.kt:836` has `updateGroup(group, memberPersonIds)` and nothing
under `ui/` calls it. No rename, no emoji or colour change, no adding a member to a local group. A
shared group offers only Archive and Leave (`ui/groups/SharedGroupPanel.kt:126-135`); nobody can be
removed (see security S-03).
Fix: an Edit group sheet for local groups now. For shared groups the rename exists server-side
(`groups_update_creator`); removing a member needs a new function and owner review.

**UX-20 · High · Who paid and who shares is shown by colour dots only.**
Evidence: `ui/components/GroupExpensePeople.kt:60-72` (a 12 dp ring for the payer, 8 dp dots, "+N"
after four). Names exist only for screen readers.
Why it hurts: this was done on purpose to save space (the owner's request of 2026-11-04), but it
went too far. It fails anyone who can't tell the hues apart, and with five people you cannot tell
who the fifth is. Meaning must not rest on colour alone.
Fix: keep the dots, and write the payer in words on each row ("Aaru paid"). One short name costs
less space than the old two-line layout.

**UX-21 · High ✔ · Leaving a group while money is owed gives no warning.**
Evidence: `ui/groups/SharedGroupsController.kt:182-203` never looks at the balance;
`strings.xml:541` only says the group's records leave this phone.
Fix: show the balance in the dialog ("You're owed ₹1,200 here") and make Settle up the main button.

**UX-22 · High · Whether an entry is private or shared is explained only after Save.**
Evidence: nothing in `ConfirmBottomSheet.kt` says who will see the entry;
`ui/groups/ShareSplitPrompt.kt:25-82` explains it afterwards, with the word "guest".
`RecordInGroupSection.kt:55-57` already does it right for settlements.
Why it hurts: a new user assumes a friend sees a split made with them. The friend sees nothing
unless both are in a shared group.
Fix: one live line under "Who's it for": "Only on your phone" or "Everyone in Goa 2026 will see
this". Say "not on the app yet" instead of "guest".

**UX-23 · Medium · A friend can't be renamed, recoloured or given a UPI ID.** No edit path in `ui/`.
A typo in a name is permanent short of deleting the friend and their history.

**UX-24 · Medium · With 20+ friends, most pickers have no search.** Only `SplitEditor.kt:86-96` has
search and frequent-first. Missing in `IouSheet.kt:122`, `CreateGroupSheet.kt:180`, the item-split
add-person dialog and the friends list. One shared picker would fix all four.

**UX-25 · Low · No "split the rest equally"** in By amount or By percent (`SplitEditor.kt:195-221`).

**UX-26 · Low · Group rows show dates without a year** (`GroupDetailSheet.kt:162`).

## Settle up

**UX-27 · Medium · Paying by UPI doesn't lead back to recording it.** `ui/components/UpiQr.kt:175-195`
opens the UPI app and does nothing when the user returns. Ask "Did it go through? Record it".

**UX-28 · Low · Eight verbs for one idea:** Settle, Settle up, Record, Record a payment, Record
settlement, Withdraw, Retract settlement, Clear it away.

## Insights

**UX-29 · Medium · One month, no budgets, no trend, nothing per person or group.**
`ui/screens/InsightsScreen.kt:163-249`. The PRD names budgets as the thing that makes the app a
habit. It is the largest product gap, and rightly sequenced after launch.

**UX-30 · Low · For guests and with AI off, "Ask" just disappears** (`InsightsScreen.kt:235`).

## Account and settings

**UX-31 · High ✔ · "Use a local ledger instead" switches ledgers in one tap.**
Evidence: `ui/auth/AccountSheet.kt:385-390` is a full-width text button at the top of the sheet;
`MainActivity.kt:194-201` switches immediately. The explanation sits below it in small type.
Why it hurts: an accidental tap replaces the whole app with an empty ledger. To the user, their
data has vanished.
Fix: a confirm dialog that says the account's entries are safe and how to come back, and move the
button down next to Sign out.

**UX-32 · Medium · No app lock, and widgets show balances on the home screen.** No `BiometricPrompt`
in the source. The recents thumbnail is hidden (good). `widget/WidgetSummary.kt` shows spending and
"Owed ₹x" with no way to hide it.

**UX-33 · Medium · A failed sync is invisible in the feed.** `TransactionItemCard.kt` has no sync
mark; only the Account sheet and the pull-to-refresh line say something couldn't sync. Was REL M3
and T2.4, half done.

**UX-34 · Low · "Last synced" is only inside the sheet, and there are two similar export rows.**

## Copy

**UX-35 · Medium · Home permanently names two AI vendors.** `strings.xml:997` ("Typed lines use
Groq · bills, statements and voice use Google Gemini") is drawn under the capture line
(`ui/home/CaptureCard.kt:272-278`). The disclosure belongs in the notice and in Settings.

**UX-36 · Medium · Jargon.** "ledger", "sync" (about 30 strings), "IOU", "guest", and the built-in
names "Team Payment", "PopUPI", "Income (Client)" (`domain/Categories.kt:36-45`), which look like the
original author's own life, not a product's defaults.

**UX-37 · Medium · The same thing has several names.** friend / person / member / groupmate / guest;
group / trip; entry / expense / transaction. Write a ten-line glossary and apply it.

**UX-38 · Medium · Default notes are stiff and the other person sees them.** "Informal Loan" and
"Borrowed Amount" (`strings.xml:307-308`) become the IOU's note; "Client Payment" is the fallback
title for income (`domain/LocalHeuristicParser.kt:154`). Against the tone rule in the PRD.

**UX-39 · Medium ✔ · Reminders never stop.** `reminder/SettlementReminders.kt:18-28`: a payment
waiting for an answer notifies again every 3 days with no upper limit. The daily "nothing logged
today" reminder is on by default with no cut-off for someone who has stopped using the app.
Cap payment nudges at three; stop the daily reminder after a week of being ignored.

**UX-40 · Low · "You owe" appears as a label in three places** (`strings.xml:8,75,286`). It faces
the user themself, so it is probably fine; decide once and write it into the tone rule.

**UX-41 · Low · About 35 user-facing strings are still in Kotlin,** including the ask-to-pay
message a friend reads (`ui/format/AskMessage.kt:30-61`), `ui/auth/AccountSheet.kt:244-248,705`,
`domain/CsvExport.kt:44-45`, and the widget labels in the manifest.

**UX-42 · Low · The search hint is out of date** ("Merchant, category or note"; search also matches
names and amounts now).

**UX-43 · Low · The colour picker speaks engineer.** "Hue 187, deep", "Room for about 79 more"
(`strings.xml:373-376`).

**UX-44 · Low · A raw status code reaches the user:** "Something went wrong (500)" (`strings.xml:930`).

## Accessibility

Strong overall: headings, merged semantics, custom actions for every swipe and long-press, live
regions, 48 dp targets almost everywhere.

**UX-45 · Medium · 9 sp labels on every feed row** (`ui/components/PaymentPill.kt:61`,
`ui/components/TripCard.kt:268`).

**UX-46 · Medium · Colour alone separates people** (see UX-20), and choosing a colour means a
360-hue canvas with three shade rows (`ui/components/FriendColourField.kt`), which is a lot for
someone who only wants to add a name. Offer twelve swatches, with "More" for the canvas.

**UX-47 · Low · Sheets have no close button** (`Sheets.kt:103-106`).

**UX-48 · Low · Reduced motion is honoured on the sign-in screen only.**

**UX-49 · Low · Dark theme only** (`ui/theme/Color.kt:5`), while the web page supports both.

## Visual

**UX-50 · Low · Thirteen dialogs in at least three styles** (a custom one in
`ui/groups/ShareSplitPrompt.kt:44-77`, a bare Material one in `MainActivity.kt:431-442`, system
date and time pickers). One wrapper would settle it.

**UX-51 · Low · Three one-off colours** outside the theme (`DeleteFriendDialog.kt:85`,
`TransactionItemCard.kt:252`, `PersonAvatar.kt:74`).

## Performance a user would feel

**UX-52 · Medium ✔ · The root screen listens to about 44 data streams without lifecycle
awareness.** `MainActivity.kt:496-581`: 58 `collectAsState` calls, 14 of them lifecycle-aware. They
include the full transaction list and each field of the entry being typed, so with 2,000 entries
every keystroke and every sync re-runs the whole app's top-level composable. Was REL L2.

**UX-53 · Medium · A long statement is drawn all at once and saved whole on rotation.**
`ui/components/StatementImportDialog.kt:73-190` is a plain column, and its saver (`:355-380`) writes
13 values per row. A three-month statement is 300+ rows, which risks both jank and a
`TransactionTooLarge` crash.

**UX-54 · Low · A friend's history flashes "No splits or settlements yet" while it loads**
(`MainActivity.kt:1264-1265`).

## Widgets and notifications

Widgets are well built: they reuse the app's own maths, refresh after writes and syncs, and handle
signed-out and guest states.

**UX-55 · Low · No privacy option on widgets, and no "who owes me" widget.**
**UX-56 · Low · The widget's balance badge doesn't open Splits.**
**UX-57 · Medium · Nobody is told when a groupmate adds an expense.** The owner asked for exactly
this in the work order (§6). Notifications cover only payments to confirm and the daily reminder;
N2–N4 are not built.

## The public split page (`site/split.html`)

Good work: mobile-first, large targets, light and dark, optimistic updates, an offline banner, no
third-party code.

**UX-58 · Medium · A stranger isn't told what this is or how to get the app.** Every shared bill is
the app's best advertisement and it has no "Get Kharcha Paani" line.
**UX-59 · Low · Three small things:** anyone with the link can pick any listed name; "That's
everything" is disabled with no reason given; the page never says the link lasts 24 hours and the
"ended" screen can't tell an expired link from a wrong one.

## Feature gaps

**Before launch (fix, or change what the listing promises):**
- The listing says "divide by shares"; the app has Equal, By amount and By percent
  (`data/model/Models.kt:128-132`). Either add a ratio mode or change the sentence.
- The listing says Google sign-in is required; there is a guest mode. (Play item P-01.)
- The listing's AI paragraph names Gemini only; typed lines go to Groq.
- Editing groups and friends, deleting from the edit sheet (UX-19, UX-23, UX-06).
- Leaving a group with a balance (UX-21); removing a member (security S-03).
- "Edited by X" on shared expenses: the timestamp exists (`Entities.kt:60,112,240`), nothing shows it.
- Say "rupees only" in the listing until the international work is done.

**After launch, in the order the PRD already has them:** budgets with friendly nudges; trends and
per-person or per-group insights; recurring splits for flatmates; comments on an expense; Hindi and
Hinglish; light theme; tablets and landscape; multiple currencies; group roles.

## What is good and must not be broken

- The sign-in screen: one clear promise, one button, the 18+ and terms lines, a human message for
  every Google failure.
- Offline-first copy ("best guess from your words", "Saved on this phone"); quick add never waits
  for the network.
- The capture line with rotating examples, Hinglish included ("auto 5 sau", "kal sabzi 180").
- Disabled buttons that say why; the "that's ₹10 lakh, right?" check; Undo after a swipe-delete.
- Settlements as tickets the other person confirms or rejects, and destructive confirms that say
  what changes on whose phone.
- The delete-account sheet: what goes, what stays, how you'll appear.
- The notification ask held back until after the first entry; hidden recents thumbnail; redacted
  lock-screen notifications.
- A neutral ask-to-pay message.
- The item-split copy (`app/src/main/res/values/item_split_strings.xml`) and `site/split.html`. It is
  the best writing in the app; use it as the voice reference for the glossary.

## Needs a phone (could not be verified from code)

1. Touch targets: the 24 dp `IconButton` at `ui/components/SplitEditor.kt:142` and the remove-photo
   button at `ui/components/BillPhotoRow.kt:202`.
2. Rotating the phone with a very large statement open (UX-53).
3. Typing lag with 2,000 entries (UX-52).
4. Whether 30 friends' colours can be told apart on a real screen.
5. The vendor line under the capture field at 1.3× font size.
6. Whether Android's Auto Backup restores a guest ledger on a new phone (matters for UX-01).
7. Back with two sheets and a dialog stacked.
8. The system date and time dialogs on Samsung and older Android in dark theme.
9. Light-mode status bar and 3-button navigation (fixed in #87; confirm it stayed fixed).
