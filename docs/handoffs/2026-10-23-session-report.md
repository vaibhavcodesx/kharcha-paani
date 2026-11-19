# Session report: fix-then-harden brief, 2026-10-23

Written for the joint review. It follows the brief's order:
[2026-10-23-fix-then-harden-brief.md](2026-10-23-fix-then-harden-brief.md).
Nothing is merged. Each PR is ready for review, with what is waiting on you listed.

## Status at a glance

| Item | Branch / PR | State | Waiting on |
|---|---|---|---|
| F0 ask-to-pay Phase 1 | `feature/ask-to-pay`, PR #54 (draft) | Placement change committed **locally** (`bee51fb`), not pushed | Free RAM (≥ 5 GB) for the release build + T7 on the Pixel_10 emulator |
| F1 docs: local friends' data | `fix/local-friend-data-docs`, PR #55 | CI green: 669 tests, 0 failed | Your review of the wording (the UPI sheet line is in-app copy) |
| F2 backup restore over synced data | `fix/backup-restore-over-synced`, PR #56 (draft) | Test pushed alone to show it fails first; fix committed locally | CI red on `3779607`, then push the fix; your call on over-inclusion |
| F3 one source for balance maths | `refactor/one-source-person-balance`, PR #57 (draft) | Pins passed on the old loops (`4070d3e`: 3/3, 672 tests, 0 failed); refactor `8cfc7a2` pushed | CI on `8cfc7a2` must keep the same pins green |
| F4 forward-only gap | none | Decision only | Your decision (below) |

## F0: ask-to-pay Phase 1 (PR #54)

Your answers: keep the `cache/asks` FileProvider path (option A), move the ask above the QR, and
I run T7 on the emulator.

- `SettleUpSheet.kt`: the "Ask {name} to pay" preview and button now come before the payment QR.
  Tests find these elements by scrolling, so the order doesn't affect them.
- `docs/designs/ask-to-pay.md`: records the move (the Message spec and the Q1/D6 history) and your
  acceptance of the `cache/asks` scope, with why the alternatives lost: `cache/exports` is emptied
  by every CSV export, and text-only gives the friend nothing to scan.
- **Blocked:** free RAM was 0.3–0.8 GB all session, against the brief's ~5 GB floor. The rule is
  "run the app before pushing UI work", so the commit isn't pushed yet.
- **Next, once RAM allows** (one at a time): build the signed release APK, confirm no `java.exe`
  is left, start Pixel_10, `adb install -r`, run T7, push, take #54 out of draft, read the
  per-test CI results, then merge.
  - T7 is: WhatsApp keeps the caption, `AskSentReceiver` reports the app you picked, the row says
    "Asked today", and cancelling logs nothing.
  - WhatsApp on the emulator needs a signed-in account. Without one I'd test the caption with
    Messages or Gmail, and you'd check WhatsApp on your phone.

## F1: what the docs said about local friends' data (PR #55)

The brief named one false claim. There were three.

1. **Since 1.3.0**, splits and IOUs with friends who aren't on the app back up to
   `local_splits` / `local_settlements`, which only your account can read.
2. **Since 1.4.0**, a local friend added as a guest on a shared group's expense syncs to that
   group **by name**. The brief didn't mention this one.
3. **The UPI sheet** said your UPI ID "stays on this phone". It has been on your profile, readable
   by anyone you share a group with, since 1.0.0. It now says "People in your shared groups can
   see it too."

The uninstall table is rewritten, and it also fixes the UPI row. Groupmates still see your UPI ID
after a reinstall, but the app never reads it back into your own settings: `setUpiPayee` is only
called from the UPI sheet. The doc also states the forward-only catch.

Checked and still true: `CreateGroupSheet` "Only on this phone" (local groups never sync), the
"Back up what's only on this phone" row, and README / distribution.md (neither makes the claim).

Possible follow-up (a behaviour change, so not done): restore your own UPI ID from your profile
after a reinstall.

## F2: restoring a backup over cloud-synced records (PR #56)

The test, `LocalBackupOverCloudRestoreTest`, uses real Room on two phones and the real sync engines:

1. Restoring a backup over records already pulled from the cloud **changes no balance**, and the
   report says "Everything in that backup was already here." This passed against the code as it
   was.
2. **The backup fills the forward-only gap.** A pre-1.3 split settled by a later payment reads
   -200 on a cloud-restored phone, and back to 0 after the restore. This also passed as it was.
3. **A real double count, in a narrow case:**
   - A payment typed to a fraction of a paisa (₹100.555) and **backed up before it synced** keeps
     ₹100.555 in the file.
   - The push rounds it to ₹100.56, so the restore didn't recognise it and added it again.
   - The fix compares settlement amounts to the paisa.

A correction on my side: my first version of case 3 backed up *after* a full sync. That passed
without the fix, because a phone pulls its own row back already rounded. I rewrote it for the case
that can actually happen, and pushed it alone so CI shows it failing before the fix goes up.

**Your decision:** keep the backup over-including (today's behaviour, so the file can stand in for
the cloud), or narrow it to `LOCAL_ONLY` rows (a smaller file)? The PR keeps it as it is.

**Found, not fixed:** restore matches people by name across everyone, groupmates included. A
local friend and a groupmate with exactly the same name can get mixed up. The backup format
doesn't record which kind a name was, so fixing it needs a format change. It's worth its own item.

## F3: one source for the per-friend balance maths (PR #57)

- `PersonBalanceOneSourceTest` pins every friend's net and both totals exactly. It also pins each
  history's rows and order, on a ledger that has: splits either way, a three-way split to the
  paisa, guests I host, a groupmate's own guest, payments and IOUs both ways, and a pending and a
  rejected payment. It was pushed alone, so CI checks it against the old loops.
- The refactor: one private builder of signed entries, read by both `personBalances` and
  `getLedgerForPerson`. It does the same sums in the same order, and the history keeps its
  pre-sort order. Unlike the brief's sketch, the balance doesn't build the history's display rows
  (with their formatted strings) for every friend on every change. Both read the same entries
  instead.
- It should merge cleanly with PR #54: ask-to-pay only adds code beside these loops.

## F4: the forward-only gap (needs your decision)

On a phone restored from the cloud alone, a post-1.3 payment can settle a pre-1.3 split that never
uploaded, so that friend's balance reads backwards. F2's case 2 shows the JSON backup repairs this
today.

| | A. Build the opt-in "Back up older splits…" row | B. Accept the gap and document it |
|---|---|---|
| What | The design in `local-friend-sync-v2.md` "Later, not built": tap-only, lists the names, one transaction, `local_history_opt_in_at` in `sync_meta` | Already done in F1's doc ("One catch on a restored phone"). The remedy is the JSON backup |
| Privacy | Uploads names you chose to keep on the phone, but only after you tap and see the list | Nothing new leaves the phone |
| Risk | Touches sync and the forward-only guardrail; needs a migration-free client change plus tests | None |
| Effort | M (about a day with tests) | Done |

My recommendation is **B for now, A if anyone actually hits it.** Only phones restored from the
cloud without the JSON backup are affected, and the backup's restore is now tested for exactly this
case.

## Part 4: proposals for later (ordered; you pick)

| # | Proposal | Effort | Risk | Notes |
|---|---|---|---|---|
| 1 | Gemini key behind a Supabase Edge Function | M–L | Security, owner review | The key ships in every APK unless `-PomitAiKey`. No `supabase/functions/` yet and no Supabase CLI here; the Supabase MCP needs `/mcp` authorisation first |
| 2 | Release smoke-test checklist + `adb` script | S | Low | Sign-in (SHA-1), sync while backgrounded, widgets, ask-to-pay share, backup export/restore |
| 3 | Real on-device tests | M | Low | `ExampleInstrumentedTest` is a stub. Candidates: sign-in, local-friend split, settle up, sync after restore. CI emulator or by hand? |
| 4 | Ask-to-pay Phase 2 | L | RLS hard stop | Only after the gate in `docs/designs/ask-to-pay.md`, then `/plan-eng-review` first |
| 5 | `MainViewModel` slices | M per slice | Medium | Only if you want it; one cohesive slice at a time, behind `MainViewModelConstructionTest` and a run of the app |
| 6 | Questions | n/a | n/a | Distribution channel; privacy policy (F1's facts feed it); crash reporting; **R8: `isMinifyEnabled = false` on release today**; backing up subscriptions, categories and settings across an uninstall |

## Housekeeping

- `graphify-out/` is now in `.gitignore` (in PR #55, so it takes effect once that merges).
- The graph is out of date after these changes. Re-run `/graphify . --update` once the PRs land.
- The merges will need small CHANGELOG conflict fixes: #55, #56 and #57 each add to *Unreleased*.
