# Sync and backup: data-integrity findings, 2026-11-17

The first pass of the audit did not read group sync, private-ledger sync, bill-photo sync or the
backup and restore code in depth. This report covers them. Two reviewers read the files end to
end; findings marked ✔ were then re-checked against the source by the Lead. **CONFIRMED** means the
code path was read end to end; **SUSPECTED** means it depends on something not read (usually the
server's SQL) or not run. Nothing was executed.

Paths are relative to `app/src/main/java/com/vaibhav/kharchapaani/`. Ids are `DATA-`.

Counts: **4 High, 14 Medium, 6 Low.** The four High ones can each lose or duplicate a user's
records without any error, and should be in 1.8.0.

## High

### DATA-01 ✔ · A rate limit is treated as a permanent refusal · CONFIRMED

`data/sync/GroupRemote.kt:188` and `data/sync/LocalLedgerRemote.kt:193`:
`val retryable = e.statusCode == 401 || e.statusCode >= 500`. Any other 4xx, including 429 (too
many requests) and 408 (timeout), becomes `RowsRejectedException`. The engine then:
- marks the expense or settlement FAILED, and never retries it;
- drops a queued confirm or reject and puts the settlement back to pending
  (`data/sync/GroupSyncEngine.kt:270-271`);
- clears a queued settlement delete, so the settlement comes back (`:251`).

On Supabase's free plan, rate limiting is exactly what a busy evening looks like. **Fix (S):** treat
408 and 429 as retryable. **Test:** a fake remote that throws 429 leaves the rows PENDING.

### DATA-02 ✔ · A bill photo can be uploaded and never linked · CONFIRMED

`data/sync/ReceiptSyncEngine.kt:64-66`: upload, then `setRemotePath`, then `setSharedPath`. If the
third call fails, the row already has its remote path, so it never re-enters the queue, and sharing
is only re-applied when the "share bill photos" switch changes. The photo is then invisible to
groupmates and is not restored on a new phone. **Fix (S):** record "path published" separately and
retry until it is.

### DATA-03 ✔ · Restoring an old backup re-charges subscriptions, and uploads the charges · CONFIRMED

`data/repository/LocalBackupRepository.kt:244` restores `chargedThrough` from the file. The
subscription pass then creates a charge for every due date after it, as PENDING rows in an
account. A person who signs in on a new phone (the cloud brings the real charges) and then restores
a three-month-old backup gets three months of duplicate charges, which sync. A file with a start
date far in the past could create thousands. **Fix (S):** on restore set `chargedThrough` to today,
or skip dates that already have a matching charge, and bound the start date.

### DATA-04 ✔ · A restore that keeps failing locks the app · CONFIRMED that there is no way out

The "everything" restore writes a recovery journal and deletes it only after success. While the
journal exists, `recoveryBlocked` is true (`ui/viewmodel/MainViewModel.kt:197`), and
`fullBackupBlocksActions()` (`:2898`), which guards 53 actions, refuses them with "recovery
pending". The periodic maintenance (subscription charges, reminders) does not run either. If the
restore can never complete (a constraint the validator doesn't check, or a row added after a failed
first attempt, since it requires an empty ledger), the user is stuck until they clear the app's
data. What triggers it is SUSPECTED; that nothing releases it is CONFIRMED. **Fix (S):** a "Discard
this restore" action, and run maintenance regardless.

## Medium

**Sync**

- **DATA-05 · Retried photo uploads leave orphans in storage.** `data/receipts/ReceiptCloud.kt:66`
  uses a random id per attempt, and the comment at `:94` says a 409 is handled by the engine, which
  has no such handling. On a 1 GB plan, orphans matter. Derive the id from the receipt row. CONFIRMED.
- **DATA-06 · Removing or replacing a photo never clears the path on the server.** Only the object
  is deleted (`ReceiptSyncEngine.kt:44-50`); groupmates and restores keep a row pointing at a file
  that is gone, and a replaced photo's new path never reaches them (`:101` inserts only when there
  is no local photo). CONFIRMED.
- **DATA-07 · One bad photo stops all photo sync.** No per-row catch in the upload loop; a 413 or
  400 aborts the pass every time, before sharing changes are applied. So turning sharing off may
  never take effect. CONFIRMED.
- **DATA-08 · With sharing off, photos on group expenses have no backup.** The path is skipped and
  nulled (`:66`, `:78`); the object stays in storage with nothing pointing at it. CONFIRMED.
- **DATA-09 ✔ · An unknown settlement status counts as paid.** `GroupSyncEngine.kt:470-472`:
  `else -> SettlementStatus.CONFIRMED`. Any status added on the server later (withdrawn, expired)
  becomes confirmed money on old phones. Skip the row or treat it as pending. CONFIRMED.
- **DATA-10 · The phone's clock decides whether a private split is dropped.**
  `data/sync/LocalLedgerSyncEngine.kt:181` compares `Clock.System.now()` with the row's server time.
  A phone a day ahead marks a waiting split as settled and the expense stays unsplit. `deleted_at`
  is also written with phone time. CONFIRMED.
- **DATA-11 · One undecodable row could block the group pull forever.** A page is applied in one
  transaction; a row that fails to decode throws on every pass, and `SyncWorker` has no catch-all
  (security S-06). SUSPECTED; test with a page holding one bad split.
- **DATA-12 · Silent refusals.** Settlement deletes and group updates are plain UPDATEs. If a policy
  blocks them, zero rows match and no error is raised; the phone clears its queue and the record
  stays live for everyone else. SUSPECTED; needs the SQL read alongside.

**Backup and restore**

- **DATA-13 · A near-maximum "everything" backup may run out of memory, at every launch.** The
  import holds the bytes, the text, the parsed JSON and the decoded archive at once, then re-reads
  it for the journal: roughly 300 MB for a 43 MB file. `catch (Exception)` does not catch
  `OutOfMemoryError`, and the journal is decoded again at each start. SUSPECTED (sizes estimated).
  Stream it.
- **DATA-14 · More than 32 MB of bill photos makes "Export everything" fail** with a generic
  message (`data/repository/EverythingBackupRepository.kt:51`). One changed photo file does the
  same. Use a zip, raise the cap, say what happened, skip and count bad photos. CONFIRMED.
- **DATA-15 ✔ · The JSON restore drops real expenses as duplicates.** `LocalBackupRepository.kt:257`
  matches on date, merchant and amount only, including against rows added earlier from the same
  file. Two equal entries at one merchant with the same timestamp lose one, reported only as
  "skipped". Match on the row's id; use the shape only for rows without one. CONFIRMED.
- **DATA-16 · An old backup can turn shared-group expenses into personal ones.** The filter is the
  group's current name on this phone (`:262`). If the group was renamed, or not pulled yet, the
  expense is restored as a personal split and balances double. Store the group's id in the file.
  CONFIRMED.
- **DATA-17 · People are matched by lower-cased name only** (`:176`, `:196`). A "Rahul" from the
  file attaches to an existing registered Rahul; two friends with the same name merge. CONFIRMED.
- **DATA-18 · An account's JSON backup leaves out personal expenses that never uploaded**
  (`domain/LocalBackup.kt:182`). Exporting while offline and then uninstalling loses them. Include
  every row that is not SYNCED. CONFIRMED.

## Low

- **DATA-19 ·** A group whose creation the server refused (FAILED) is never cleaned up; and purging
  a group deletes the user's own unsent expenses in it. CONFIRMED.
- **DATA-20 ·** A member who leaves shows as "Group member"; only deleted accounts get their name. CONFIRMED.
- **DATA-21 ·** Private-ledger identity is the friend's name; a renamed friend could be re-created
  under the old name. SUSPECTED.
- **DATA-22 ·** The photo delete queue is a read-modify-write with no lock; a concurrent add can be lost. CONFIRMED.
- **DATA-23 ·** The JSON backup format has no amount ceiling or date bounds. CONFIRMED.
- **DATA-24 ·** Restored photo files are written without `fsync` before the commit. CONFIRMED.

## What is done well

- A pulled row for an unknown profile gets a placeholder person: never "me", never dropped. Local
  friends and group members are never merged (the lookup filters on `profileId IS NULL`).
- Joining a group resets both cursors in the same transaction as the insert; a cursor moves only
  with the page it applied.
- A refused settlement answer reverts and forces a full re-pull, so "answered offline while the
  other side withdrew" converges without applying anything twice.
- The "everything" restore: local session only, an empty target, one transaction, a hash-marked
  journal, references validated before any write, a filename allow-list with a canonical-path check
  (no path traversal), rows forced to LOCAL_ONLY.
- The JSON restore is additive and never wipes; input is size-capped, previewed, and rejects NaN,
  infinity and negatives. The two `!!` flagged in report 5 (`LocalBackupRepository.kt:268, 300`) are
  guarded and safe.
- Exports go to a place the user picks or to the private cache through the FileProvider; nothing is
  world-readable.
