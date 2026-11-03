# Kharcha Paani: architecture and domain understanding

Source review updated 2026-11-04 for onboarding item O2. Links below are pinned to
`ebfc2b66110d0897685369f2c124fac2dec68862` (including sheet-first quick add, #195).
These are code-traced behaviors, not phone acceptance or proof that server migrations, quotas
or Edge Functions are deployed. Read the current lane contract and issue #188 before acting.

## 1. From “chai 40 with Rohan” to Room rows

The entry point is [MainViewModel.parseInputText](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1059).
It first uses the AI-notice gate. Choosing local entry calls `fillInOnPhone`; accepting AI calls
`readTextWithAi`. Both use `openOnPhoneGuess` to open the confirmation sheet immediately with
a local reading. No database row is saved merely by parsing.

[LocalHeuristicParser.parse](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/domain/LocalHeuristicParser.kt#L50) takes the text and known
friend names. For this example, assuming a unique known friend named Rohan, the local result is:
- type `expense`, amount `40.0`, category `Food`;
- blank payment method (the ViewModel then supplies the account's default);
- split name `Rohan`, merchant `Chai Rohan`, and the original line as the note.

The amount comes from [AiParse.amountIn](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/domain/LocalHeuristicParser.kt), not a separate
parser in the ViewModel. The title filter removes “with” and the amount but retains the name.
An AI result can differ from this local example.

`applyParsedResult` fills draft StateFlows and uses
[SplitTargets.resolve](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/domain/SplitTargets.kt#L35) to match names/groups. Unknown or
ambiguous names produce a notice rather than a guessed friend. With no selected/inferred group
and just You plus Rohan selected, [recalculateShares](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1551)
uses [SplitRounding.equal](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/domain/SplitRounding.kt#L13): ₹20 each, with any rounding
remainder handled by the rounding helper.

If AI is available, the sheet stays usable while `improveJob` calls
[GeminiRepository.parseText](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/GeminiRepository.kt).
[improveWith](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1128) updates only fields not touched
during improvement; type/category and split relationships have their own coordinated rules.
Dismissal, a new draft/edit and saving stop improvement. The first Save tap calls
[holdDraftForSave](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L644), so a late result cannot change
an amount already shown in the large-amount confirmation. Failure keeps the local draft and
shows the appropriate notice; it is not proof that a remote AI request succeeded.

Save calls [saveCurrentTransaction](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/viewmodel/MainViewModel.kt#L1637).
It validates the draft/split and may first ask for a shared group when registered participants
would otherwise not receive the split. The new-entry path then calls
[LedgerRepository.saveTransactionWithSplits](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L390).
The repository validates the amount before `db.withTransaction`; inside that transaction it
checks group writability, computes sync eligibility, inserts the transaction and, when shares
exist, inserts its split header and share rows. After commit, `onLocalTransactionChange`
requests sync and refreshes widgets through the ViewModel's `requestSync` callback.

For the two-person example the split header names payer `-1L` and mode `equal`; the two
[SplitShareEntity](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L197) rows have participant IDs `-1L`
and Rohan's local ID, each with `shareAmount = 20.0`. There is no `isGuest` constructor
argument: it is a computed property, `guestName != null`.

## 2. One friend's balance and history

[LedgerRepository.balanceEntries](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L315) is the
shared derivation for friend balances and friend history. Its
[BalanceEntry](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L285) variants contribute:
- `Share`: their covered share when I paid is positive; my covered share when they paid is negative.
- `Guest`: a local friend who joined a shared-group expense as my guest owes me that guest share,
  regardless of which group member paid.
- `Payment`: a confirmed record from me to them increases their net debt to me; the reverse
  decreases it. Pending/rejected records contribute zero.

[personBalances](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L99) sums these signed
contributions into its two directional totals and rounds the resulting net to paisa.
Positive `netBalance` means they owe me; negative means I owe them. Group-wide positions use
[GroupBalances.netPositions](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/domain/GroupBalances.kt), which also accounts for
expenses and confirmed payments between other groupmates; that is distinct from a friend's
balance with me.

## 3. From local save to server

[syncStateFor](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L877) makes shared-group writes and
personal unsplit entries `PENDING`. A personal split can also be `PENDING` for private sync
when local-friend sync is enabled and all participants are this user or local friends without
profiles. Local-only group entries and other ineligible cases remain `LOCAL_ONLY`.
Do not assume every personal split is eligible or that every local row leaves the device.

[SyncScheduler](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/sync/SyncScheduler.kt) requests WorkManager work.
[SyncWorker.syncIfSignedIn](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/sync/SyncWorker.kt#L84) checks that the requested
account matches the available network session and serializes passes with its mutex.
[Its engine sequence](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/sync/SyncWorker.kt#L54) is personal transactions, shared
groups, then private local-friend splits/settlements when enabled. “Last synced” is stamped only
after the configured sequence succeeds.

[TransactionSyncEngine](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/sync/TransactionSyncEngine.kt) pushes personal upserts
and deletes before pulling:
- Push acknowledgement marks a row synced only if its local `updatedAt` is unchanged.
  Conversion failures and isolated permanent row rejections become `FAILED`.
- Pull matches `remoteId`, uses a saved cursor with overlap and updates only eligible synced
  rows. Pending/failed/local-only changes are preserved; a queued delete prevents resurrection.
- Local deletion queues a tombstone in `pending_remote_deletes`. Successful soft-deletes clear
  it. A permanently refused individual delete is also dropped and resets the shared-expense
  cursor for reconciliation; it is not guaranteed server deletion.
- The worker retries IO/network and REST failures; its expected database-closure failure path
  returns failure. These are specific catches, not a guarantee that every exception retries.

[GroupSyncEngine](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/sync/GroupSyncEngine.kt) handles shared group membership,
expenses/splits and settlements.
[LocalLedgerSyncEngine](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/sync/LocalLedgerSyncEngine.kt) handles private
`local_splits` and `local_settlements`, not a cloud copy of local-only groups.
These source paths describe request behavior and conflict guards, not live server acceptance.

## 4. Settlement states that move money

[SettlementEntity.isConfirmed and SettlementStatus](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L257)
define `PENDING`, `REJECTED` and `CONFIRMED`. Only `CONFIRMED` contributes to friend or
group balance. IOUs use `isIou = true` on a settlement and follow the same confirmation rule.
Pending/rejected records remain visible as history without counting as acknowledged money.

## 5. Shared versus local groups

[GroupEntity](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L52) defines `isShared` as
`syncState != LOCAL_ONLY`.

| Aspect | Local group | Shared group |
|---|---|---|
| Visibility/sync | Device-local group and entries; no shared invite | Shared records sync for group members |
| Membership | Local person IDs in `GroupMemberEntity` | Registered profile identities mapped to local people; nonmembers can be guests |
| Invites | No shared invite flow | [SharedGroupsController](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/groups/SharedGroupsController.kt) uses remote membership/invite operations |
| Guests | Ordinary local participants | Guest shares store the host as `personId`, with `guestName`; the host's device can also retain `guestPersonId` |
| Archived writes | Do not infer the shared-only restriction | [requireWritableGroup](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/repository/LedgerRepository.kt#L909) rejects archived shared-group mutations |

## 6. Sign-out and account isolation

[AuthRepository.signOut](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/auth/AuthRepository.kt#L195) attempts Supabase sign-out,
clears the local session on its handled failures, and clears Credential Manager state.
[MainActivity](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/MainActivity.kt) observes authentication and supplies the account ID
to [AccountSessionHolder.onAccountChanged](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/session/AccountSessionHolder.kt#L49).

When leaving an active account, the holder cancels its scheduled sync and calls
[AccountLeftovers.clear](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/session/AccountLeftovers.kt): cancel notifications and
remove cached exports/Ask images. It cancels the opening job and clears the account's
ViewModelStore before invoking database close/open. Store clearing cancels ViewModels; this
method does not explicitly await all their jobs.

[LedgerDatabases](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/db/LedgerDatabases.kt) scopes storage to
`ledger-<userId>.db`, closes the previous account's connection when switching, and retains the
file on ordinary sign-out. Sign-out returns to sign-in; it does not delete the ledger or itself
grant access to it while signed out. Account deletion has a separate
[AccountDeletion](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/ui/session/AccountDeletion.kt) workflow. No deletion or server
operation was exercised for this document.

## 7. Stored identities must stay stable

Keep display translation separate from stored values. Examples are category `Food`, payment
method `GPay`, transaction types `income`/`expense`, and split modes
`equal`/`exact`/`percentage`; the modes follow
[SplitMode](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/model/Models.kt#L118) and
[SplitEntity](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L175). Custom names remain user data.

Preserve existing local primary keys, parent-child relationships and remote UUIDs across edits.
`remoteId` binds a local record to its remote identity. The sentinel `-1L` means this user,
not a generated friend/guest ID. Bill photos use
[ReceiptEntity and the receipts table](https://github.com/vaibhavcodesx/kharcha-paani/blob/ebfc2b66110d0897685369f2c124fac2dec68862/app/src/main/java/com/vaibhav/kharchapaani/data/db/entity/Entities.kt#L316), not an
invented `receipt_photos` table.

## 8. Evidence limits

This document traces the cited revision. No phone, release sign-in, proxy, group sync, account
deletion, quota enforcement or live database acceptance was tested here. The proxy/client and
SQL/migration files are implementation evidence, not proof of deployment. Refresh source links
and lane instructions when using this document to plan later work.
