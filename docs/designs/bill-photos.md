# Bill photos: keep the picture with the expense

Status: Phase 1 designed and built by the agent on 2026-10-24 (PR: feature/bill-photos), as the owner asked ("the agent does
the brainstorming and the design itself"). Phase 2 (cloud) needs the owner's decisions and is not
started.

## Why

Someone at DMart wants the bill with the expense: to check a total later, to return something, to
show a flatmate what the ₹2,340 was. Today "Scan bill" reads the picture, fills the sheet and throws
the picture away. The one moment the app holds the bill is the moment it lets go of it.

## Goals

- Any expense can carry one bill photo. It is optional and never asked for.
- Scan bill and a shared-in picture keep their picture by default, removable in one tap.
- Add, view, replace and remove all happen in the add/edit sheet, where the expense is.
- The feed shows which expenses have a photo.
- A photo never outlives its expense, and Undo-delete never loses it.

## Non-goals (Phase 1)

- Photos on other phones, in the cloud, or for groupmates. Phase 1 is this phone only, and the UI
  says so.
- Several pages per bill. The table allows it later; the UI allows one.
- Reading the photo again with Gemini after saving. Scan bill already did that.
- Photos in the JSON backup. A ZIP export can come later.

## Anti-goals: it would be a failure if

- the add sheet got heavier for the 95% of entries with no bill;
- a photo carried the place it was taken (phone photos hold GPS in EXIF);
- photos ate storage unseen: a 12 MP camera JPEG is 3–5 MB, and Android's 25 MB Auto Backup quota
  would silently stop backing up the app at all once photos pushed past it;
- deleting an expense left its photo behind, or Undo brought the expense back without it.

## Decisions

These are the agent's calls, made without the owner, and marked so the owner can overrule them.

### Storage

- Files live in `filesDir/receipts/<accountId>/<uuid>.jpg`. `filesDir`, not `cacheDir`, which the
  system clears under pressure. Per account, because each account already has its own Room file
  (`LedgerDatabases`), and a second account must never see the first one's bills.
- The database stores the **file name**, not an absolute path. The directory is derived, so a
  device transfer or a changed data path doesn't strand the rows.
- Every picture is re-encoded on the way in: decoded through `SharedFiles.decodeImage` (orientation
  applied by the decoder), scaled to a long side of at most **2000 px**, written as JPEG quality
  **80**. That comes to about 200–400 KB a bill, and re-encoding from a `Bitmap` writes **no EXIF**,
  so location, device and time metadata never reach the file. A test reads the written file's EXIF
  and asserts there is no GPS tag.
- The original the camera or picker gave us is never kept.
- **Cloud backup excludes the receipts folder; device transfer includes it.** Auto Backup has a
  25 MB quota per app and backs up *nothing* once it's exceeded, so a few hundred bills would
  otherwise switch off backup of the ledger itself. Device-to-device transfer has no such limit.

### Data

A new table, `receipts`, in Room **v10** (v9 is ask-to-pay, #54):

| column | type | why |
|---|---|---|
| `id` | Long, primary key | |
| `transactionId` | Long, FK → `transactions.id`, `ON DELETE CASCADE`, indexed | the expense |
| `fileName` | String | `<uuid>.jpg` inside the account's receipts folder |
| `width`, `height` | Int | the viewer can lay out before decoding |
| `bytes` | Long | a later storage line in Settings |
| `createdAt` | Long | |
| `remotePath` | String?, null | Phase 2's Storage object; unused now |

A table, not a column on `transactions`: several pages per bill later is a UI change, not another
migration. The index on `transactionId` is **not unique** for the same reason; the repository keeps
Phase 1 to one photo per expense. Migration 9→10 is additive (`CREATE TABLE` + index), with the
exported schema JSON and a migration test. No destructive fallback.

The server already has `transactions.receipt_storage_path`; Phase 1 doesn't touch it.

### The draft photo

A photo chosen in the sheet is not attached until Save:

1. Picking or taking a photo processes it (off the main thread) into
   `filesDir/receipts/<accountId>/staged/<uuid>.jpg` and the sheet shows it.
2. **Save** waits for a photo still being written, saves the expense, then moves the staged file
   into place and writes the `receipts` row (`ReceiptRepository.attach`), replacing any earlier one.
   A replaced photo's old file is deleted only after that transaction commits. If the photo can't be
   kept, the expense still is, and the status message says so. (Built as two steps rather than one
   transaction: the expense save lives in `LedgerRepository`, and a crash between the two leaves
   an expense without its photo and a staged file the sweep removes, never a row pointing at
   nothing.)
3. **Dismiss**, or replacing the staged photo again, deletes the staged file.
4. Editing an existing expense shows its photo; Replace and Remove only stage the change, so
   closing the sheet without saving leaves the old photo as it was.

### Deleting

- The expense delete already waits for the Undo snackbar (`PendingDeletes`); nothing touches the
  database until it commits. The photo rides on that: the `receipts` row goes with the expense
  (cascade) when the delete commits, and the file is deleted straight after. Undo needs no extra
  work because nothing was deleted yet.
- Rows deleted in other ways (a sync pull, a group's expense removed by a groupmate, a friend's
  deletion collapsing a split) go by cascade too, and leave their file behind. An **orphan sweep**
  runs when an account's session opens: it deletes files with no row, and staged files older than
  a day.

### Camera and gallery, with no permission

- **Take photo**: `ActivityResultContracts.TakePicture` into a FileProvider URI under
  `cacheDir/camera/`. The app doesn't declare `CAMERA`, so the system camera handles it without a
  permission prompt. The `camera/` path is added to `file_paths.xml`; the raw capture is deleted as
  soon as it's processed.
- **Choose from gallery**: the Photo Picker (`PickVisualMedia(ImageOnly)`), which needs no
  permission either.

### The sheet

A row under Note, in the same `SheetRow` style:

- **Empty:** `Bill photo` · `Add` (with a small "optional" hint). Tapping opens a small menu: *Take
  photo*, *Choose from gallery*. One row, no new section, so the sheet doesn't feel heavier for the
  entries that have no bill.
- **Filled:** a 56 dp thumbnail, "On this phone only", and a remove (×) button. Tapping the
  thumbnail opens the viewer.
- **Viewer:** full screen, black, pinch-to-zoom and pan, double-tap to zoom, with *Replace* and
  *Remove* along the bottom and Back to close.
- **Scan bill and shared pictures:** the picture that was read arrives as the staged photo, so the
  sheet opens with it in the row. One tap on × drops it. *(The handoff suggested a "Keep bill photo"
  switch; a thumbnail with × is the same choice, in the same control every photo uses, so the agent
  chose that.)*
- The row shows for income too (a payslip is a fair thing to keep). It costs nothing to allow and
  needs no special case when the type is switched with a photo staged.
- **Scan bill says it kept the picture.** The row is the last one in a long sheet, so after a scan
  the notice line at the top reads "Read from your bill · photo kept below", and the row is there
  when they scroll. One place for the photo, and no silent keeping.

Exact pieces, all existing ones:

| Part | What it is |
|---|---|
| Row | `SheetRow(label = "Bill photo", position = RowPosition.Single)`, same top padding as Note |
| Empty value | `Add` in `WarmAmberLight` labelLarge, then "optional" in `SubMutedWarmGray` bodySmall |
| Menu | a `DropdownMenu` on `CharcoalElevated` with `KpShapes.medium`, like the statement category menu: *Take photo*, *Choose from gallery* |
| Thumbnail | 56 × 56 dp, `KpShapes.small`, `ContentScale.Crop`, loaded with Coil from the file at about 168 px |
| Remove | a 48 dp `IconButton` with `KpIcons.Close` in `MutedWarmGray` |
| Feed icon | `Icons.Outlined.ReceiptLong`, 14 dp, `MutedWarmGray`, in the row's detail line after the payment method |

### States, all of them

| State | What the row shows |
|---|---|
| No photo | Add · optional |
| Processing (decoding and compressing a camera photo takes up to a second) | the 56 dp box shimmering (`shimmerLoading()`), "Saving photo…"; Save stays enabled and waits for it |
| Photo | thumbnail · "On this phone only" · × |
| Couldn't read it (unreadable file, out of memory, out of storage) | whatever was there before stays (so a failed Replace never loses the old photo), and the status message says "Couldn't use that photo." |
| No camera app | the status message suggests the gallery instead |
| Camera or picker cancelled | unchanged |
| Editing an expense whose file is gone (data cleared, restored from a backup, which carries no photos) | "Photo not on this phone" · Remove, which drops the row |
| The expense saved but the photo couldn't be moved into place | the expense is kept; the status message says "Saved. The bill photo couldn't be kept." |

### The feed

Expense rows with a photo get a small receipt icon in their detail line, with
`contentDescription = "Has a bill photo"`. The set of transaction ids with a photo is one Flow from
the `receipts` table, not a join on every feed query.

### Accessibility

- The row reads "Bill photo, optional, add" or "Bill photo attached, open"; × reads "Remove bill
  photo".
- The viewer's buttons have labels; zoom isn't the only way to read it (the image fits the screen
  first).

## Tests

- Migration 9→10 (`MigrationTestHelper`), against the exported schema.
- Repository, from a real in-memory database: attach on save; replace deletes the old file; deleting
  the expense removes the row and the file; Undo (no commit) keeps both; the orphan sweep removes
  unreferenced and stale staged files and keeps referenced ones.
- Image processing: the long side is capped at 2000 px; the written JPEG has no EXIF GPS.
- UI: the row's empty and filled states, and remove.
- `ScreenRenderCapture`: the sheet with and without a photo.

## Design review (the agent's own, 2026-10-24)

Run with the plan-design-review passes, deciding each fix without the owner, as asked.

| Pass | Before | After | What changed |
|---|---|---|---|
| Information hierarchy | 6 | 9 | Scan bill's notice says the photo was kept, since the row sits at the bottom |
| States | 4 | 9 | the states table: processing, unreadable, cancelled, missing file, save-but-no-photo |
| Journey | 7 | 8 | a feed icon to find bills later; viewing a bill is two taps (row, then thumbnail), accepted |
| Specificity | 5 | 9 | the parts table names the existing components, tokens and sizes |
| Design system | 8 | 9 | only existing pieces: `SheetRow`, the category-menu dropdown, `shimmerLoading`, Coil |
| Accessibility | 6 | 8 | 48 dp targets, labels for every control; the viewer fits the screen first |

Considered and dropped: tapping the feed icon to open the photo directly. A 14 dp icon is no touch
target, and the expense is one tap away anyway.

## Phase 2 (needs the owner)

Upload to Supabase Storage in a private bucket keyed by user, with RLS on `storage.objects`,
`remotePath` filled in and `transactions.receipt_storage_path` set; groupmates see a shared
expense's bill. Decide: whether it's a Plus feature, retention and size limits, and the privacy
policy and Data safety lines ("photos stored in the cloud").

## Left to the builder

Exact spacing and icon, the viewer's zoom limits, and the sweep's staged-file age.
