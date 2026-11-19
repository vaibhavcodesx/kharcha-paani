# Agent brief: fix the working app first, then make it production-ready

Written 2026-10-23 for a fresh agent session. It collects what the previous session
learned: the knowledge graph it built with graphify, the problems that graph helped find
(each checked against the code before being written here), the work in flight, and the
order to tackle things in.

**How to use this brief.** Read it once from top to bottom. Then read `CLAUDE.md`,
`PRD.md`, and the memory index the harness loads for this project. Work through
**Part 3** one item at a time, then **Part 4**. Each item gets its own branch, its
own verification and its own report to the owner, and the next item waits for the
owner's go-ahead.

---

## Part 0: Rules that apply to everything below

These come from `CLAUDE.md` and from the owner's standing feedback.

**Process**
- **Confirm with the owner before you start each item.** Say which item it is,
  what you plan to change, and how you'll verify it. Don't start the next item
  until the owner says so.
- **Record what changed in `CHANGELOG.md`** (Keep a Changelog, under Unreleased).
  Only edit `PRD.md` when a spec or a decision changes.
- **One item, one branch, one PR, cut from `main`.** Don't stack fixes on
  `feature/ask-to-pay`.
- **Don't re-architect things that already work:** the ledger/balance pattern,
  `DebtSimplifier`, the AI ingestion pipeline, and the groups/trips model.
  Improve and extend them.

**Merging**
- You may self-merge a low-risk PR once CI is green and you've confirmed it,
  meaning you've read the per-test results, not only the check badge.
- **Never** self-merge a PR that touches RLS, auth or security, or that adds a
  non-additive Room migration. The owner reviews those.

**Room and tests**
- Every Room schema change needs a `Migration`, the exported schema JSON under
  `app/schemas/`, and a migration test. No destructive fallback, ever.
- **Test the gatherer, not the format.** Start from a real database, run the real
  code path, and assert a balance. A hand-built object only tests its own
  constructor.

**Before you push**
- **UI work:** run the app first. Unit tests never construct `MainViewModel`, and
  an init-order NPE once crashed every launch while 447 tests passed.
- **Anything release-related:** smoke-test the **release** build. Sign-in only
  works when the release SHA-1 is registered, and debug builds hid a total
  sign-in failure for three releases.
- **Background work** (widgets, workers, sync): test it with the app in the
  background. supabase-kt blocks `awaitInitialization` after `onStop`.

**Shell**
- Don't write Kotlin through Bash heredocs. `\b` and `\n` turned into control
  bytes once. Use the Edit and Write tools, and scan for bytes 0x00–0x1F if in
  doubt.

### This laptop is short on RAM

It has 15.7 GB in total. At the end of the last session about 1.8 GB was free, with
Chrome, ChatGPT, Telegram and other Claude sessions open. On 2026-09-15 a Gradle build
crashed the machine with a native out-of-memory error.

- `~/.gradle/gradle.properties` caps Gradle: `-Xmx2g`, 2 workers, no daemon.
  Don't raise those limits.
- **Before any Gradle build or emulator start, check free RAM.** If less than
  about 5 GB is free, stop and ask the owner to close apps. Don't kill their
  processes yourself.
- Run builds and the emulator **one after the other, never together**. After
  each build, confirm no `java.exe` is left running.
- Don't run the full local test suite here; it gets killed. **Linux CI is the
  authority on test results.**
- Known local-only failures:
  - Room tests can fail with `SQLITE_CANTOPEN`. Set
    `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=C:/temp/robo`, and check the exception
    type before blaming your change.
  - One `AskShareTest` FileProvider case always fails on Windows
    ("Failed to find configured root").

### Installing on a device

- **Never uninstall the app on a device.** It holds the owner's real account
  data. Install over it with `adb install -r`.
- The release keystore is `kharcha-paani-release.jks` with `keystore.properties`
  in the repo root.
- `.github/workflows/release.yml` publishes a **public GitHub Release**. Don't
  use it to make test builds.

---

## Part 1: Where things stand (2026-10-23)

**Releases and branches**
- Latest release: **v1.4.0** (`759ba22` on `main`).
- Branch checked out: `feature/ask-to-pay`, pushed. It contains:
  - `29bd411`: the design doc and TODOS
  - `4a21ab8`: ask-to-pay Phase 1
  - `7856cad`: `FileProviderScopeTest` now allows the `asks/` folder
- The local branch `release-1.4.0` is stale; it was squash-merged as #53.

**PR #54 (ask-to-pay Phase 1)**
- https://github.com/vaibhavcodesx/kharcha-paani/pull/54, **open as a draft**.
- CI passed on `7856cad`: 702 tests, 0 failed, 0 skipped. The screenshot is
  `screens/ask_to_pay.png` in the `unit-test-results` artifact of run
  38476709467.

**Ask-to-pay docs**
- Spec and decision log: [docs/designs/ask-to-pay.md](../designs/ask-to-pay.md)
  (D1–D10, the gate before Phase 2, and the review report).
- Deferred work: [TODOS.md](../../TODOS.md).

**Not committed:** `Screenshots/` and `graphify-out/` (see Part 2).

---

## Part 2: The knowledge graph (graphify)

The previous session ran `/graphify` on the repo root.

| Fact | Value |
|---|---|
| Output | `graphify-out/` (`graph.html`, `GRAPH_REPORT.md`, `graph.json`, `manifest.json`, `cost.json`, `cache/`) |
| Snapshot of | `feature/ask-to-pay` at `7856cad` |
| Size | 4,302 nodes, 13,130 edges, 196 labelled communities |
| Corpus | 328 code files (`.kt`, `.sql`, `.json`, `.kts`, `.mjs`, `gradlew`), extracted from the AST with no LLM, plus 23 docs extracted by 3 subagents |
| Cost | about 470k subagent tokens (input and output combined) |
| Python | `C:\Users\VAIBHAV\AppData\Roaming\uv\tools\graphifyy\Scripts\python.exe` (also saved in `graphify-out/.graphify_python`) |

### How to use it

Query the graph before reading code broadly:

```bash
graphify query "how does a local friend's split reach the server?"
```

```bash
graphify path "LocalBackup" "LocalLedgerSyncEngine"
```

```bash
graphify explain "HisaabItemiser"
```

After you change code, refresh it with `/graphify . --update`. That re-extracts only
the changed files, and docs come from the cache. Open `graphify-out/graph.html` in a
browser to explore it visually.

### Known limits

Don't treat these as bugs in the app.

- **Images were left out on purpose.** 88 PNG/WebP files (mostly duplicated app
  screenshots and launcher icons) and the 3 font licences are excluded by
  `graphify-out/.graphify_build.json`:
  `{"excludes": ["*.png", "*.webp", "docs/licenses/*"]}`.
  Each image would have needed its own vision subagent.
- **6 Kotlin files were only partly parsed** because the tree-sitter grammar
  can't handle them:
  - `LedgerDatabases.kt`
  - `AccountSheet.kt`
  - `CategoriesSheet.kt`
  - `ConfirmBottomSheet.kt`
  - `SubscriptionsSheet.kt`
  - one more

  These files have no BOM and compile fine on CI.
- **4 files yielded no symbols:** `Migrations.kt`, `Color.kt`,
  `build.gradle.kts`, `settings.gradle.kts`. So migrations are under-represented
  in the graph.
- **Health check:** 3,075 dangling-endpoint edges, 41 self-loops, 352 edges
  collapsed into duplicates. Most dangling edges probably point at library
  symbols that have no node (`assertEquals`, `Flow`, `StateFlow`). This hasn't
  been checked.
- **Duplicate nodes:** a class named in a doc (e.g. `DebtSimplifier`,
  `HisaabItemiser`) is a separate node from the same class in code. Treat the
  doc and code versions as linked by name only.
- **`graphify-out/` is not in `.gitignore`.** Ask the owner whether to ignore it
  (recommended) or commit it.

### What the graph says about the code

**Most-connected nodes:**

| Node | Edges |
|---|---|
| `MainViewModel` | 165 |
| `PersonEntity` | 140 |
| `TransactionEntity` | 130 |
| `FullTransaction` | 107 |
| `SplitShareEntity` | 93 |
| `GroupEntity` | 91 |
| `SplitFlowTheme()` | 90 |
| `LedgerRepository` | 88 |
| `SettlementEntity` | 84 |
| `AppDatabase` | 68 |

**`MainViewModel` is the main bridge.** It has the highest betweenness centrality
(0.095) and connects 35 communities. Its own community has a cohesion score of
0.035, the lowest in the graph. It is the obvious candidate to split up, but that
counts as re-architecture under `CLAUDE.md`, so it's a Part 4 decision for the
owner, not a fix.

**`PersonEntity` is the second bridge** (betweenness 0.082, 40 communities). A
change to how people are modelled ripples almost everywhere.

**Weakly connected nodes:** `Loading`, `SignIn` and `LedgerFailed` are states of
the sealed `AuthGate` class. That's a graph artifact, not dead code.

**Hyperedges** (groups of nodes that work together) that are worth knowing:
- offline-first cloud sync (Room as cache, hand-rolled sync, last-write-wins, RLS
  via `group_members`)
- the per-person colour system
- the ask-to-pay Phase 1 share pipeline
- syncing friends who aren't on the app to the cloud
- the layered protection for the Gemini API key

---

## Part 3: Fix the current app, one item at a time

The order is deliberate. For each item: confirm with the owner → branch from `main` →
fix → verify → CHANGELOG → PR → report back.

### F0: Finish or park PR #54 (ask-to-pay Phase 1)

PR #54 is already in flight, so settle it first. Then later fixes won't conflict with it.

This needs the owner's answers to three questions:

1. **FileProvider scope.** `res/xml/file_paths.xml` now exposes `cache/asks` as
   well as `cache/exports`, which changes what other apps can be granted. Does
   the owner accept that?
2. **Ask section position.** On the settle sheet it sits below the QR and needs a
   scroll. Should it move up?
3. **Who does the device check (T7)?** You, on the Pixel_10 emulator (only if
   RAM allows, see Part 0), or the owner, on their phone.

**T7 checklist**, run on a **release** build installed with `adb install -r`:
- [ ] WhatsApp keeps the caption alongside the image.
- [ ] On API 31+, the app you pick is reported back (`AskSentReceiver`).
- [ ] The friend row shows "Asked today".
- [ ] Closing the share sheet without picking an app logs nothing.

**Done when:** T7 has passed, the owner has signed off on the scope and placement,
the PR is out of draft, CI is green, and the PR is merged.

### F1: Fix the docs' wrong claim about local friends' data

The only contradiction the graph found that is real and affects users.

**What the docs say:** [docs/splitting-with-people-not-on-the-app.md](../splitting-with-people-not-on-the-app.md)
says a local friend "lives **only on this phone**" (line 90) and that "a local
friend's name never leaves your device at all" (line 134). Its table says local
friends and what's owed to or by them do **not** survive an uninstall or a new phone.

**What's actually true:** [docs/local-friend-sync-v2.md](../local-friend-sync-v2.md)
is **LIVE in 1.3.0**, and `LocalFriendSync.ENABLED = true`.
- Splits and IOUs **created after 1.3.0** upload to `local_splits` and
  `local_settlements`, with names stored in `local_name` / `from_local_name` /
  `to_local_name`.
- They come back after you clear the app's data and sign in.
- Older records stay `LOCAL_ONLY`. This is the forward-only guardrail.

So both the privacy claim and the uninstall table are out of date.

**Do:**
- Rewrite the doc's "what 'not on the app' means for your data" section and its
  table. Separate records from before 1.3.0 (phone only) from records after
  (account-private on the server, invisible to other accounts under RLS).
- Then find every other place that makes the same promise: `README.md`,
  `docs/distribution.md`, any privacy text, and in-app copy.
- Check these strings (from a grep of string literals), and add any others you
  find:
  - `CreateGroupSheet.kt:154` ("Only on this phone…"). This is probably still
    true, because local-only *groups* aren't part of v2. Confirm.
  - `UpiSheet.kt:79`
  - `AccountSheet.kt:390`
  - `MainViewModel.kt:1748`
- Don't change behaviour in this item; it's about telling the truth.
- If the owner would rather the old promise stay true, that's a product and
  privacy decision. Raise it, don't decide it.

**Done when:** no doc or string claims local friends' names never leave the
phone, the uninstall table matches what 1.3.0+ actually does, and CHANGELOG
records the correction.

### F2: Bring the backup's comments in line with sync v2, and test a restore over synced data

**Evidence:** the KDoc in `domain/LocalBackup.kt` (around lines 15–31) and the
`isSplit -> true` branch of `isUnsyncable` (line 114) still say "every split
expense is excluded from upload". Since v2, new personal splits and local IOUs do
sync. As a result, `LocalBackupRepository.collect` (around line 55) **over-includes**
them in the JSON backup.

**Why it's probably safe, and what to prove:** over-inclusion doesn't lose data,
and `restore` is additive.
- Transactions are de-duplicated by `remoteId`, or by shape.
- Settlements are de-duplicated by `(fromPersonId, toPersonId, amount, date)`
  (around line 289).

What hasn't been tested is **restoring a backup onto a phone that has already
pulled the same v2 records from the cloud.** People are matched by name, so a
mismatch in name or id would double a balance.

**Do:**
1. Write the test first. Start from a real Room database, seed v2-synced local
   splits and IOUs, collect a backup, simulate the cloud restore, run `restore`,
   and **assert the per-friend balance is unchanged and the report says
   "skipped"**.
2. If it passes, only fix the comments, and decide with the owner whether the
   backup should keep over-including (safer) or narrow to `LOCAL_ONLY` rows.
3. If it fails, you've found a real double-counting bug. Fix it, keeping
   `LocalBackupRoundTripTest` and `LocalBackupTest` green.

### F3: One source for the per-friend balance maths (TODOS.md, TD1)

**Evidence:**
- `LedgerRepository.personBalances` (around line 88) and `getLedgerForPerson`
  compute the same contributions in two separate loops.
- Only `PersonLedgerTest` keeps the two equal.
- Ask-to-pay's itemised message promises its lines add up to the balance, so a
  future change to just one loop would break that promise without any test
  failing.

**Do:**
- Make `personBalances` sum the signed rows that `getLedgerForPerson` produces.
- Keep these green: `PersonLedgerTest`, `SettlementConfirmationTest`,
  `GuestSharesTest`, `HisaabItemiserTest`.
- This is an internal refactor of a pattern that works. Keep the observable
  behaviour identical, and write a test that compares old and new results on a
  seeded real database before you delete the old loop.

### F4: The forward-only gap (needs a decision; don't fix without one)

**Evidence:** `docs/local-friend-sync-v2.md` (the "What the owner should know about
forward-only" section). On a phone restored from the cloud, a post-1.3 "Rohan paid
you ₹500" can settle a pre-1.3 split that never uploaded. **The balance then reads
backwards.** Today only the JSON backup covers this.

**Options:**
- Build the opt-in "Back up older splits…" row exactly as designed in that doc's
  "Later, not built" section: tap-only, lists the names, one transaction,
  `local_history_opt_in_at` recorded in `sync_meta`.
- Or accept the gap and say so in the docs from F1.

**This is owner territory:** it uploads names the owner chose to keep on the
phone, and it touches sync. Present the trade-off and wait.

### Checked and not a bug: don't spend time on these

- **The `c0efe0f` citation.** The graph had an AMBIGUOUS edge between the
  statement-timeout fix (PR #40) and the AI voice rules.
  `docs/ai-prompt-set-design.md:116` cites `c0efe0f`, which really is "Never
  show invented data when reading a statement, bill or summary fails". The
  citation is correct; the edge was the extractor's mistake.
- The tree-sitter parse failures, the dangling edges, and the weakly connected
  `AuthGate` states (see Part 2).

---

## Part 4: Then make it production-ready

Start this only once Part 3 is done or explicitly parked. **Each bullet is a
proposal to raise with the owner, not a decision.** `CLAUDE.md` requires
confirmation before each PRD priority area, and several of these touch auth,
RLS or security.

Present them as a short ordered plan with effort and risk, and let the owner pick.

1. **Move the Gemini key behind a Supabase Edge Function** (PRD "Backlog: move
   Gemini behind a Supabase Edge Function", around line 630; and the layered key
   protection in `docs/distribution.md`). Today the key ships in the APK (unless
   built with `-PomitAiKey`). This is the biggest security gap for wider
   distribution.
   - The Supabase CLI isn't installed on this laptop, and there's no
     `supabase/functions/` directory yet. Decide between the CLI and the
     Supabase MCP deploy tool.
   - This is security, so it gets owner review.
2. **A release-build smoke-test checklist**, so every release is checked on the
   signed APK: Google sign-in (SHA-1), sync while backgrounded, widgets,
   ask-to-pay share, and backup export and restore. Write it as a doc, plus a
   short script for the `adb` parts.
3. **Real on-device tests.** `ExampleInstrumentedTest` is a stub. Pick the 3–5
   flows that would hurt most if they broke: sign-in, adding a split with a
   local friend, settling up, syncing after a restore. Decide with the owner
   whether those run on CI emulators or by hand per release.
4. **Ask-to-pay Phase 2** (the public token request page, RPCs and RLS) only once
   the gate in `docs/designs/ask-to-pay.md` passes. The gate is:
   - UPI test results for each path
   - 3 of 4 users have sent asks
   - half were repaid within 7 days

   Then run `/plan-eng-review` on it first (TODOS.md). It's the project's first
   public read path, which makes it an RLS hard stop.
5. **`MainViewModel` size and cohesion** (Part 2). Only if the owner wants it.
   Propose extracting one cohesive slice at a time (e.g. subscriptions, or
   statement import) behind the existing behaviour, with
   `MainViewModelConstructionTest` and a run of the app after each step.
6. **Questions to raise, not assume:**
   - distribution channel (Play Store or GitHub Releases)
   - a privacy policy, which F1's corrected facts feed into
   - crash reporting
   - whether R8/minify settings are production-grade
   - a backup and restore story for subscriptions, categories and settings,
     which still don't survive an uninstall

Re-run `/graphify . --update` after significant changes, so the next session's map
is current.

---

## Key files

| Area | Path |
|---|---|
| Spec and priorities | `PRD.md`, `CLAUDE.md`, `CHANGELOG.md`, `TODOS.md` |
| Original architecture | `docs/original-architecture-dossier.md` |
| Ask-to-pay | `docs/designs/ask-to-pay.md`; code in `app/src/main/java/com/vaibhav/kharchapaani/ask/`, `domain/HisaabItemiser.kt`, `ui/format/AskMessage.kt` |
| Local-friend sync | `docs/local-friend-sync-v2.md`, `docs/staged-local-participant-sync.md`, `data/sync/LocalLedgerSyncEngine.kt`, `LocalLedgerRemote.kt` |
| Backup | `domain/LocalBackup.kt`, `data/repository/LocalBackupRepository.kt` |
| Balances | `data/repository/LedgerRepository.kt` (`personBalances`, `getLedgerForPerson`) |
| Sync | `data/sync/SyncWorker.kt`, `TransactionSyncEngine.kt`, `GroupSyncEngine.kt`, `SyncDoorbell.kt` |
| Graph | `graphify-out/GRAPH_REPORT.md`, `graphify-out/graph.html` |
| History | `docs/history/prd-status-log-2026-09.md` |
