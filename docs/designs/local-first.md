# Local first: optional account, optional AI, complete recovery

Owner request: 2026-11-04. Target: 1.7. Implementation is tracked on issue #188.

## Product contract

The welcome screen offers Google sign-in and **Use without an account**. Local mode supports the personal ledger, local friends/groups/splits, IOUs, subscriptions, categories, ordinary Insights and backups. It does not create an anonymous Supabase account. Shared groups, invitations, account sync and Gemini need sign-in. Explain that at the action rather than presenting a failed network request.

Settings identifies **On this phone** or the signed-in account. Local data survives relaunch and remains separate when signing in or switching back. No automatic upload, merging, deleting or claiming an old account database. A cancelled sign-in returns to the unchanged local ledger. Reuse the existing theme, rows and sheets.

**Use AI (Google Gemini)** is a device-wide switch, separate from disclosure consent. Preserve the current enabled default for existing installations; consent is still required before the first upload. Off means no Gemini requests from Quick Add, bill photos, statements, Insights, trip reviews, widget or shared-file routes. Cancel running requests and ignore late answers. Quick Add uses the local parser, bill photos can remain attachments on manual entries, statements explain that automatic PDF reading needs AI. External speech recognition is also unavailable while off because its provider can send speech for processing. The app cannot control an external keyboard's dictation. Switching on never accepts the AI notice automatically. AI actions clearly identify Gemini before sending data, including after consent has been accepted.

**Export everything** is a complete portable archive, distinct from a CSV report and the old local-data backup. It includes all locally held expenses and their latest edits, splits/guest shares, friends including registered friends, groups/memberships, settlement states, subscriptions/checkpoints, categories/payment methods, preferences, saved asks and bill photos. There is no server edit-history table today: export the actual stored latest edit metadata; never invent earlier versions. Do not include login credentials, tokens, notification/AI/crash consent, remote-write queues or sync cursors.

Restore previews counts and explains the destination. Complete archives restore only into an empty local ledger, with stable references so duplicate names or identical purchases remain distinct. Importing shared history creates an offline copy, never membership or a new cloud upload. Preserve confirmation status and balances; do not replay queued settlement answers. An occupied destination remains unchanged. Legacy v1/v2 additive imports remain supported and separately labelled. A failed/corrupt/newer/oversized file changes no ledger rows. No automatic replacement or deletion. Show a warning that an unencrypted export contains the full financial history.

## Implementation order and ownership

1. AI switch and dispatch/cancellation guards, labelled capture actions, existing Settings row; own PR.
2. Complete archive gather/validate/restore core with real Room round-trip tests; own PR, then connect export/restore UI and settings to local sessions.
3. Explicit local/cloud session capability, persistent local identity, isolated database/receipts/settings, lazy remote dependencies, worker/widget/reminder guards and welcome/settings transitions. **Auth owner-review PR, left unmerged.** No Supabase changes.
4. Integration: archive picker/progress/preview, guest backup reminder, phone checklist; fresh main and one PR per item. No version bump or release until the approved set is green and review gates are met.

The owner asked for autonomous implementation, so this design records safe choices without another approval round. The repository's auth/security merge hard stop still applies. Claude keeps the existing creative/UI queue; Antigravity keeps routine work. MainViewModel/MainActivity/AccountSheet changes are coordinated on #188.

## Verification

- Real MainViewModel/Room plus counting HTTP boundary: AI off blocks every route despite prior consent; local/manual saves remain correct; off persists; a pending notice cannot bypass off; late results and fallback calls are suppressed.
- Full Room snapshot -> encode -> decode -> fresh Room restore: compare every promised column, relationship, photo bytes and balances; duplicate names/purchases; pending/rejected settlements; subscription checkpoint; malformed references and money; oversized/path-traversal inputs; occupied-target refusal and legacy import compatibility.
- Real app/session tests: no-login cold launch/recreation, local/Alice/Bob isolation, no local remote construction or scheduled sync, unchanged local split rounding, blocked shared actions/deep links, stale queued workers/callbacks, cancelled sign-in, VM cancellation before Room close.
- CI unit-tests, lint and release-bundle on the exact final head containing current main. No local Gradle below 4 GB free RAM; no emulator. Phone checks cover installed release sign-in, local restart/upgrade, airplane mode, both account transitions, every AI entry point and export/restore on a fresh local ledger.

## Release boundaries

This is implementation scope, not proof of phone acceptance, Play approval or live server operation. 1.6.0 remains the published phone-test build until a separately prepared 1.7 release is authorized and built. Owner console/legal/security checks remain in the production-readiness work order.
