# Bill photos in the cloud, and shared in splits

Owner decisions, 2026-11-06:
- Every bill photo backs up to the person's account.
- On a split, the others in it see the photo. A Settings switch turns that sharing off, and it's
  **on by default**.
- The storage bucket and its access rules are applied by Claude after their SQL tests pass.

## What each phone keeps, and what the cloud keeps

| Copy | Where | Size | Who sees it |
|---|---|---|---|
| Original | The phone that took it: `filesDir/receipts/<account>/`, 2000 px JPEG q80, EXIF stripped (unchanged) | 200–400 KB | Only that phone |
| Cloud copy | Supabase Storage, private bucket `receipts`, at `<uploader uid>/<transaction uuid>/<photo uuid>.webp` | **60–150 KB** | The uploader (backup), plus anyone who can see the expense when sharing is on |
| Downloaded copy | Another phone in the split, stored as an ordinary bill photo once opened | Same as the cloud copy | That phone |

**Why the cloud copy is smaller:** it exists so people can see the bill and so a new phone gets it
back. Nobody needs it at print resolution, and the AI never reads it (the AI gets its own 1600 px
JPEG straight from the phone at scan time).
- **Format:** 1280 px on the long side. WebP lossy at quality 70 on Android 11+, and JPEG quality 72
  below that, because WebP lossy encoding needs API 30.
- **Readability:** receipt print stays readable at that size; long receipts are the hard case, and
  1280 px still gives about 2.5 px per character line on an 80 cm receipt.
- **No location:** the copy is encoded from pixels, so no EXIF, and so no GPS position, can leave
  the phone.
- **Bucket limits:** 1 MB per file and image types only, enforced by the bucket itself, so a buggy
  or hostile client can't fill storage with large files.

### Long-term cost

At about 100 KB a photo, 1 GB holds about 10,000 photos. Supabase's free plan includes 1 GB of
storage, and Pro includes 100 GB (owner decision 14 is about Pro at launch). With 100 active users
attaching 30 bills a month, that's about 300 MB a month, so the free plan lasts about three months
and Pro lasts for years.

**Downloads are lazy.** Another phone fetches a photo only when someone opens it, never during
sync. That way egress follows real interest, not the number of members times the number of photos.

**Retention, not yet built:** photos on deleted expenses are removed straight away (below). Ageing
out photos older than about two years is a later decision for the owner.

## Who can read and write: storage RLS

Object names are `<uploader uid>/<transaction uuid>/<file>`.

- **Insert, update and delete:** only in your own folder (`foldername[1] = auth.uid()`).
- **Read:** your own folder, **or** any object whose name is some transaction's
  `receipt_storage_path` that you can access, using the existing
  `private.can_access_transaction(id)`: your own expense, or one in a group you're a member of.

So sharing is controlled by **whether the uploader sets `receipt_storage_path`** on the expense.
- **Sharing on:** the path is set, and everyone who can see the expense can read the photo.
- **Sharing off:** a group expense gets no path, so nobody else can read the photo, even by guessing
  its name, because read access is checked against the path stored on a transaction they can see.
  The uploader still reads their own folder, which is how a new phone restores their bills.
- **Personal expenses:** the path is always set. Only the owner can see a personal transaction, so
  this changes nothing for anyone else.
- **Someone else's expense:** a photo attached to an expense somebody else created stays on the
  phone. RLS lets only the creator (or the payer) update the row, and making that work isn't worth
  a security-definer function yet.

## App flow

1. **Upload:** in `SyncWorker`, after transactions have synced. Pick receipts with
   `remotePath = null` whose transaction has a remote id and which this account created.
   - Compress, upload with `upsert = false`, and save `remotePath` locally.
   - Then set `transactions.receipt_storage_path`: always for personal expenses, and for group ones
     only while sharing is on.
   - At most 10 photos per run, and a failure leaves the row for the next run.
2. **Turning sharing off:** clears the path on your group expenses. **Turning it back on:** sets it
   again. Both run in the next sync.
3. **Pull:** group and personal pulls copy `receipt_storage_path` into a new Room column,
   `transactions.receiptRemotePath` (Room v11). An expense with a remote path and no local photo
   shows the bill as "Tap to load". Opening it downloads into `receipts/`, and from then on it's an
   ordinary bill photo whose `remotePath` is already set, so it's never uploaded again.
4. **Deletes:**
   - Removing the photo from an expense deletes the object and clears the path.
   - Deleting the expense deletes its folder `<uid>/<tx uuid>/`.
   - Deleting the account removes the whole `<uid>/` folder before the account goes.
5. **Offline and local mode:** nothing changes, because no account means no cloud.

## Privacy and store text

**Privacy policy:**
- Bill photos are stored in the cloud, in Supabase (the same processor as today).
- They're shared with the people in a split unless the person turns that off.
- They're deleted with the expense or the account.

**Play Data Safety:** "Photos: collected, stored, shared with other users of the app at the user's
direction." Antigravity's W8 drafts these lines.

## Tests

- SQL, in a rollback-only script:
  - The owner can write and read their own folder; nobody can write into another's folder.
  - A group member can read a shared photo, but not once the path is cleared.
  - A non-member can never read it.
- App:
  - Compression produces the expected size and format.
  - Upload sets `remotePath` and the path, and respects the sharing switch.
  - The pull fills `receiptRemotePath`.
  - A download turns into a normal photo.
  - Room migration 10 → 11.
