# TEMP-only private draft store contract tests

Frozen approved API: API-proposal.md. Mandatory DraftEditTarget.createdAt (>=0, epoch0 allowed) included.

entry-draft-atomic-store-tests.patch adds exactly one EntryDraftAtomicStoreTest.kt, 13 tests. Not for the central existing-API red branch: this patch depends on the approved future model/store API and is intended to accompany its eventual implementation AFTER central recovery regression proof. No production source/stub implementation is included.

Tests use real TemporaryFolder files and Robolectric's Android AtomicFile environment, no Room/VM/Compose. StandardTestDispatcher controls store-owned IO queue so acceptance, caller cancellation, retire/new-owner ordering are tested without timing races/sleeps/reflection. Every normal path retires handles. No payload/cache mocks. 512KiB limits, corrupt raw bytes and invalid identity in otherwise valid serialized JSON checked against actual disk; invalid saves must retain last known-good snapshot. Full DTO roundtrip includes createdAt, edit baseline/fingerprint, raw amount, guest locked shares, staged photo metadata and frozen SavingEntry distinct from newer fields. File metadata validation never claims a referenced photo actually exists (receipt integration checks bytes/hash separately).

Input validation intentionally permits finite but currently invalid editing data (-1 share, percentage125, amount '.') so interrupted typing is not silently discarded; ledger Save performs financial validation. Reject NaN/infinity, duplicate ShareInput participant identities, invalid UUID/edit target/birth/hash and path/dimensions, oversized encoded data. File identity test uses actual cloud/local ledger leaf names sharing same UUID.

Isolated git apply --check only; no Gradle, compiler, emulator, repository edits or network mutations. Tests are a proposed contract for review, not a claim of execution/green. More exhaustive decode mutation cases can be added once the concrete codec wire layout exists; no arbitrary JSON key/schema names assumed here.
