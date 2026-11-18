<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Testing here: what is true, what costs time

Moved from Claude's private notes into the repo on 2026-11-19, so every agent has them. CI (Linux) is the
authority. Agents run no local Gradle and no emulator; the owner tests on his phone.

## Local failures that are expected (do not chase them)

- **Room under Robolectric on the owner's Windows drive** fails with `SQLiteCantOpenDatabaseException (code 14)`
  (about 6 to 33 tests: Migration tests, `LedgerDatabasesTest`, `GroupSyncEngineTest`, `SharedGroupRepositoryTest`...).
  Green on CI. Check the exception type before blaming a change; any other exception is real.
- **`FileProvider.getUriForFile`** tests fail on Windows (it matches roots with `/`). Linux CI is the authority.
  Do not add reflection hacks to pass locally.

## What to test, and how

- **Test the gatherer, not the format.** For export, backup or a sync payload, build the state in a real
  in-memory Room database, run the real gathering function, assert a value that matters (a balance, a total).
  A hand-built object round-tripped through JSON tests the constructor: in 2026-09 a backup shipped missing
  splits and settings because a default made the omission look deliberate. Reintroduce the defect and confirm
  the new test fails before claiming a fix.
- **Launch crashes need a test that builds the app.** An `init` block placed above the property it used crashed
  every launch with all unit tests green. `AppShellLaunchTest` and several ViewModel tests build the real
  thing; mind declaration order for new properties.
- **Background work** (WorkManager, widgets, reminders) must be checked with the app opened then backgrounded.
  supabase-kt resets its session on `onStop`, so never call `auth.awaitInitialization()` from background code; use
  `BackgroundAuth`.
- **A bug gets its failing test first.** Never delete or loosen an assertion, add `@Ignore`, lengthen a sleep or
  narrow a check to get green. Compose tests use `StandardTestDispatcher` unless a test says otherwise; cancel and
  await the real ViewModel before closing Room.
- **Room schema changes** need a version bump, a `Migration` in `AppDatabase.ALL_MIGRATIONS`, the exported schema
  JSON, and a migration test. Never a destructive fallback. (Red: owner review.)
- **Sample data** seeded by early versions must not upload; match by names, not group.
- Only invented data in fixtures.

## Before a release

Install the release-signed build and test sign-in, sign-out and sign-in again. Sign-in works only if the release
SHA-1 is registered with Google; debug builds hid a total failure in 1.0.0 and 1.1.0. "Merged and green" is not
"verified on a real build". See `docs/engineering/runbooks/release.md`.

## Tooling traps

- Do not write source through a shell heredoc if it contains a backslash: `\b` and `\n` have become real control
  bytes. Use the Edit/Write tool; after scripted edits scan for bytes 0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F.
- Check a command's exit code before committing. A failed inline conflict resolver once let conflict markers be
  committed and pushed. Grep `^<<<<<<<|^>>>>>>>` after any merge.
- A conflicting PR gets **no CI run** (the workflow runs on `pull_request`). If nothing starts, check
  `gh pr view N --json mergeable` and rebase. Bound any wait loop.
- Source is stored with LF line endings. A diff of hundreds of lines for a ten-line change is a line-ending rewrite.
- If Gradle must ever run locally: no idle daemons, capped heap, never alongside an emulator; confirm no
  `java.exe` is left afterwards.
- Live SQL tests (until a staging project exists): one script that runs the migration body and the test, ending in
  a raised error carrying the results, so it always rolls back. Scripts that alter constraints or grants get
  declined; leave the test file in the PR and ask the owner. Nothing persistent is ever applied to live by an agent.
