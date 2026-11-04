# Entry draft recovery (Y6 / REL M2)

The existing add/edit sheet loses its unsaved work when Android ends the process. Preserve the visible draft on this phone, separately for each ledger, and reopen it without an AI request or a money write. This is the already-authorized recovery item in the Codex lead queue. Safe choices are recorded here instead of asking the owner to design the implementation.

## Scope and storage

One versioned, bounded private AtomicFile per ledger database identity, outside the ledger schema and outside global settings. Cloud and local databases with the same UUID must have distinct keys. No migration, Supabase change, new dependency, network replay or automatic account merge. Missing/corrupt/unsupported files leave Room untouched and show a recoverable notice; never log their contents.

The durable snapshot contains raw EntryDraft fields and all ShareInput values, edit target identity and an original-content fingerprint, explicit group-choice state, locked guest shares, photo intent and a stable new-entry save identity. The current EntryDraft class alone is insufficient: it omits edit and photo semantics. Preserve incomplete amount text exactly. Exclude jobs, callbacks, AI consent, in-flight reads and parsing flags. Use bounded input decoding and safe receipt leaf names; preserve the existing file if a newer snapshot cannot be written rather than truncating it.

## Ordering and ownership

Capture coherent state after complete user/AI mutations and share recalculation, never from touched() before a mutation. Serialize private-file writes; clearing after explicit discard/successful save uses the same ordering. A superseded writer cannot resurrect a cleared draft or overwrite a replacement owner. Scope ownership is per ledger and may not access another ledger's file. Observe restoration readiness before ordinary draft capture; a delayed load cannot overwrite a newly opened/edited entry.

The original visible draft must survive ordinary ViewModel retirement/account changes. Explicit dismiss/discard and successful Save clear it. Flush the latest coherent snapshot on background/retirement using existing lifecycle patterns; disk writes stay off Main. Surface write failures without claiming persistence succeeded. No generic new UI or new design system is needed.

## Restore and validation

Load before receipt maintenance, then restore fields directly. No parser, AI request, Save or consent acceptance runs during recovery. Use direct Room reads, not initial-empty screen StateFlows, to resolve persisted identities. Recover an edit only against its exact original row/remote identity and baseline; changed/deleted targets, changed membership, missing participants or archived/read-only groups retain the text and require review before Save. Never silently turn an edit into a new expense, replace a payer or drop a guest/share. The existing manual controls allow correction/discard; the actual Save path also enforces the recovery guard.

A staged photo is retained in the account's receipt staging directory and protected from the 24-hour sweep while referenced by a recoverable draft. Preserve unchanged-existing, replacement-staged and explicit-removal intent. No cross-account paths or external URIs go into the snapshot. Coordinate startup ordering with #227's pending restore journal before that PR is merged; do not edit its branch or duplicate recovery jobs.

## Interrupted Save

Persist the save identity before committing new money. Reuse the existing unique transaction remoteId, without a schema change. If Room contains that identity after restart, do not insert it again. Reconcile pending photo work against the saved row; an unfinished photo may be shown for explicit review as an edit. For existing edits, preserve the original transaction identity and block a stale baseline rather than overwriting newer content. A draft is cleared only after successful required work; a failed save keeps recoverable text. No token or pending network response is restored.

## Verification and limits

First commit real file-backed Room/MainViewModel recreation tests using existing public APIs, and observe predicted missing-recovery assertions on CI before writing production implementation. Include positive no-draft/discard/saved controls. Then cover exact/percentage/equal shares, edit identity and locked guests, stale references, retained old staged photos/removal, account/ledger isolation, ordered clearing and interrupted-save no-duplication. Own/clear/await actual ViewModel jobs before database close. No arbitrary sleeps, removed assertions, emulator or timeout changes. Current Room remains v10. Local Gradle only above the repository's4GB free-RAM floor; CI tests/lint/release are the exact-head gate. Signed phone/process-death testing remains separate.

Unsaved working drafts are not saved ledger rows and are not added to the existing complete-ledger archive format in this item. Record that boundary in the PR. #230/#227 remain owner-review PRs; this work does not approve or merge them. Implement against latest main and report any protected integration requirement in the PR rather than changing another agent's branch.
