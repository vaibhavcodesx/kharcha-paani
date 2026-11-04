# Y6 entry-draft recovery implementation plan

Goal: the existing add/edit sheet resumes the same safe unsaved work after process recreation, without AI or automatic ledger changes.
Spec: [entry-draft-recovery.md](../designs/entry-draft-recovery.md).

- [ ] Commit existing-API real Room/VM recreation regressions and positive controls. Observe predicted assertion failures on full CI. No production implementation before that proof.
- [ ] Add a bounded versioned per-ledger private draft store with serialized ownership/clearing and validation tests. No Room schema/dependency/auth change.
- [ ] Wire coherent snapshots and guarded restore into existing MainViewModel mutations, edit identity, Save and receipt lifecycle; retain current AI cancellation behavior and existing financial assertions.
- [ ] Add adversarial real-VM/Room regressions before the corresponding fixes: stale edit/participant/group, old staged photo/removal, account namespace, interrupted Save and late-write resurrection. Freeze/replay old stages only deliberately; do not cancel active verification.
- [ ] Read the full diff, independent review, one Unreleased entry/solo-log/phone list, latest-main merge and exact-head tests/lint/release. Open one [codex] Y6 PR. Merge only if all lead rules allow it; auth/security involvement makes it owner-review/unmerged.

Safe defaults: no automatic Save or AI; corrupt data never mutates Room; stale financial targets block Save while retaining text; failures remain visible; explicit successful save/discard clears recovery. No new feature or visual redesign outside this item. Questions go to #188.
