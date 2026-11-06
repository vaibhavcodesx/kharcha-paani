# Y6 MainVM/receipt integration — TEMP only

Base source: main 9b465654df3a94d85b051e8072d767ad401b2b32; current root e92f730 changes only docs/tests. No checkout/remote mutation, local Gradle or emulator was used.

Central red proof supplied by root: CI41892168487, artifact13354892352, 1108 tests exactly3 intended missing-sheet assertions (exact/percentage/edit), controls3 and old1102 passed, lint/release green. This proves baseline missing recovery, not every additional adversarial boundary.

## Ordered application / new receipt boundary

1. 00-receipt-retention-api-scaffold.patch: adds ignored optional preserveForDraftRecovery flag and recoverStaged null stub. Original attach cleanup unchanged.
2. Add x12 ReceiptDraftRetentionTest and obtain expected new-feature red (retained photo absent) BEFORE applying01.
3. 01-mainvm-receipt-recovery.patch applies against base+00. It contains exactly4 files: MainViewModel, new EntryDraftRecoveryLedger, ReceiptStore, ReceiptRepository. Requires F2 final domain DTOs +EntryDraftAtomicStore/DraftLoadResult including quarantineUnreadable(). It does not include those files or tests.
4. Root owns strings/UI: draftRecoveryProblem:StateFlow<String?>, isRestoringDraft:StateFlow<Boolean>. Resources: draft_recovery_unreadable, draft_recovery_write_failed, draft_recovery_stale_edit, draft_recovery_missing_person, draft_recovery_group_unavailable, draft_recovery_photo_missing, draft_recovery_save_changed.

## Behavior / source reasoning

- Capture after complete external field/split changes, AI result merges, new/edit opening, photo completion/removal and new-friend split updates. touched() is unchanged and never persists pre-mutation state. No raw StateFlow observer captures partially updated fields.
- File-backed Room identity keys the private store; in-memory DBs have no persistence. Explicit new manual/typed/shared-receipt actions and discard queue quarantine of unreadable originals. Startup/load/edit opening never quarantine. Save awaits queued quarantine and durable captured-save journal before money.
- Load actual file before receipt maintenance. Restore fields directly; never parse, invoke AI, accept consent or save money. A user mutation/open wins over delayed loading. Private draft pins latest AND saving-checkpoint receipt leaves, including old staged/committed files. Unreadable snapshots skip maintenance entirely.
- Original edits retain id+remoteId+createdAt+exact content fingerprint/baseline and locked guest shares. Direct DAO reads validate current row, actual people/group/member state and permissions. Missing/stale edits remain edits and are blocked. Repository Unit return is not treated as success: actual written row/splits/shares are re-read inside the same Room transaction, with mismatch rolling back.
- Save freezes entry/edit/locked shares/payer/split mode/photo operation. Its checkpoint is independent of later typing and persisted before the money transaction. Stable new transaction remoteId reconciles committed rows after recreation; committed saves restore as explicit edits, never another insert. Photo intent resolves against strict same-ledger UUID.jpg leaves, finite dimensions/length and SHA256, from staged or committed directory.
- Successful saved money closes/clears only unchanged fields with completed photo work. Later fields/photos remain an explicit edit of the saved row. Before clearing, a fresh continuation draft identity is installed, so typing during clear IO cannot reuse a now-closed store identity. Conditional clear names old ID+revision; newer edits serialize after it under a distinct ID.
- Receipt retention is opt-in ONLY for a MainVM with a durable file journal. Default attach callers preserve prior discard/delete behavior. Opt-in metadata failure retains the checkpoint photo; cancellation propagates only for this journaled path. Retrying an already committed leaf does not delete the newly referenced same-name file.

## Verification / remaining gates

Only static source inspection and patch construction have run here. No Kotlin compilation or test-pass claim. Root/x12 must review full diff and execute central+adversarial real-Room tests on exact integration head. Root owns the UI busy/error gating and its actual-shell red proof. Test stage needs originals unchanged, photo trigger retention, deleted/reused/stale edit, missing members, old staging pins, interrupted committed money/photo, latest edits during save and clear-acceptance races. Author root must document any additional intended red stage separately.

Pending #230 guest and #227 complete-backup startup ordering is NOT integrated in this standalone-main patch. Before merge on combined owner-review bases, test backup-journal recovery finishes before draft validation/sweep, local/cloud database AND receipt namespaces remain distinct, and session shutdown awaits actual VM/save/restore work before closing Room. Do not apply this patch to those branches or assume source-only compatibility proves that lifecycle integration. Keep #252 draft until required combined-base integration and exact-head unit/lint/release gates are satisfied. Signed phone/process-death checks remain separate.

Final source review: x12 independently read actual after/ sources and found no additional concrete blocker after the duplicate original onCleared was combined and checkpoint/rebase revision increments aligned with the immutable store contract. One final line preserves the existing explicit photo-not-kept notice when settlement fails. This is static review, not execution proof.

Frozen01 SHA256: 6a2e7b39d772163803458055b4d6e2bdfca11735b19511104277133439c19b44
