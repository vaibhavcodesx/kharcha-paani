# Kharcha Paani security audit (read-only)

- Repo: `Z:\V\.The_Future\Vibeeee\Kharcha Paani`, branch `main`, commit `7a1342a`
- Date: 2026-10-24
- Scope: MASVS-level basics for a Play Store launch to about 1,000 strangers. Covers the APK's secrets, the Supabase schema and RLS (all 16 migrations), the manifest, data at rest, the network, auth, input validation and dependencies.
- Method: every finding below was checked in the code and cites `file:line`. Nothing was modified. No Gradle, emulator or Supabase calls were made. Read-only `git log` and `gh release` were used, and OSV was queried for dependency versions.
- Not re-reported: anything in open PRs #64, #65, #71, #73, #74, #75, #76, or already on the owner-decision list. Gaps those leave are noted where relevant.
- **OWNER REVIEW** marks anything that touches RLS, auth, privileges, or a non-additive migration.

---

## 0. Answer: "Do we need to change any API keys, databases, or features before launch?"

**Yes.** Concrete list, in order:

### API keys

1. **Rotate the Gemini key (mandatory). Git history is not the reason; distribution is.**
   - The key in `.env` (prefix `AQ.…`) is compiled into `BuildConfig.GEMINI_API_KEY` (`GeminiRepository.kt:675`).
   - All five GitHub releases (v1.0.0 to v1.4.0) were built with it. None has a `-no-ai` asset (`gh release view`; `release.yml:77-81`).
   - The current key's first 14 characters are present verbatim in the dex of `dist/KharchaPaani-debug-2026-09-19.apk`.
   - Anyone holding any of those APKs holds the key.
   - When the Gemini Edge Function lands (already on the owner list):
     - create a **new** key that lives only as an Edge Function secret;
     - restrict it to the *Generative Language API*;
     - set per-minute and per-day quota caps and a billing budget alert in Google Cloud;
     - then **delete** the old key. Alternatively, keep it with a tiny quota until v1.4-and-earlier installs are gone, but note that deleting it breaks AI only in those old builds.
2. **Do not rely on the Android-app restriction for the Gemini key.**
   - `AppIdentity.restrictionInterceptor` (`AppIdentity.kt:64-71`) sends `X-Android-Package` / `X-Android-Cert` headers.
   - Both values are public: the package name, and the signing-cert SHA-1 of any copy of the APK. Anyone who extracted the key can send the same headers.
   - It is a speed bump, not a control, which is why the server-side move is required.
3. **Supabase publishable key (`sb_publishable_…`): safe to ship, no rotation.** Data access is enforced by RLS.
   - In the dashboard (API Keys), if the **legacy** `anon` / `service_role` JWT keys are still enabled and nothing uses them, disable the legacy keys.
   - Verified: no `service_role` / `sb_secret_` value exists anywhere in the source or git history.
4. **Google Web OAuth client ID: safe to ship (public identifier).** Before launch:
   - register an *Android* OAuth client for package `com.vaibhav.kharchapaani` with the SHA-1 of **the Play App Signing key** (Play re-signs the APK) as well as the upload key;
   - publish the OAuth consent screen to "In production" with app branding;
   - keep "Skip nonce check" **off** in Supabase > Auth > Google. The app sends a nonce, `AuthRepository.kt:47,58,139,147`.
5. **Git history is clean.**
   - Searched with `git log --all -S` for: `AIza`, the live Gemini key prefix, the live Supabase project ref, `sb_secret_`, `eyJhbGciOi` (JWT), `BEGIN PRIVATE`, and `storePassword=`.
   - `storePassword=` only matched a `...` placeholder in README.
   - `service_role` only matched role DDL in the local PGlite test harness.
   - `keystore.properties`, `kharcha-paani-release.jks`, `debug.keystore`, `.env` and `local.properties` are gitignored (`.gitignore:9-25`) and were never committed.
   - No rotation is needed because of git.

### Database (Supabase): migrations and dashboard settings

6. **OWNER REVIEW:** enforce "shares add up to the amount" in the database itself. Today only the `save_group_expense` RPC checks it, and direct table writes bypass the RPC. See finding H-2.
7. **OWNER REVIEW:** revoke the project's default privileges from `anon`.
   - Migration comments confirm that every new table and function in `public` is granted to `anon` by default (`20260923000000_owner_private_local_ledger.sql:141-144`, `20260914033124_groups_invites_rls.sql:656-657, 675-678`).
   - Every migration so far has remembered to revoke. The next one might not. See M-4.
8. Fix the hue-clash trigger. A groupmate with no display name disables the check. See L-9.
9. Dashboard checks that can't be seen from the repo (**OWNER REVIEW**, auth):
   - **Auth > Providers:** disable Email, Phone and Anonymous sign-ins if Google is the only intended path. Otherwise anyone with the publishable key can mint unlimited accounts over REST, which compounds the invite brute-force item already on the list.
   - **API > Exposed schemas:** must be `public` (and `graphql_public` only if GraphQL is used). The `private` helper functions are granted to `authenticated` (e.g. `private.is_member_of(group, profile)`), so exposing `private` would turn them into membership oracles.
   - Disable `pg_graphql` if unused. It lets `anon` introspect table and column names.
   - **Realtime settings:** disable "Allow public access" / require private channels. The app only uses `postgres_changes` (`SyncDoorbell.kt:71-73`), so nothing breaks.
   - Run the Security Advisor and fix every finding.

### Features before launch

10. **Gemini data-use terms (OWNER REVIEW, privacy):** receipts, bank-statement PDFs and ledger summaries go to Google. See H-1.
    - On the **unpaid** Gemini API tier, Google may use prompts and outputs to improve its products.
    - Make sure the key's project has billing enabled (paid tier), and disclose "financial info, photos, files shared with Google for processing" in the privacy policy and the Play Data safety form.
11. **Block non-finite amounts (NaN / Infinity).** One such entry stops all cloud sync for the account. See H-3.
12. Already on the list and still blockers: account deletion, the Gemini Edge Function with per-user quota, invite brute-force limits, and `char_length` limits.

---

## 1. Secrets in the APK: classification

| Item | Where | Classification | Restriction to set |
|---|---|---|---|
| `GEMINI_API_KEY` | `.env` → Secrets plugin → `BuildConfig` (`app/build.gradle.kts:151-157`, `GeminiRepository.kt:674-678`) | **Must move server-side** (owner list). Already exposed in 5 releases and the staged APK, so **rotate**. | New key: API restriction = Generative Language API only. Stored as an Edge Function secret. Quotas plus a budget alert. No Android restriction (server caller). |
| `SUPABASE_URL` | `BuildConfig`, `SupabaseClientProvider.kt:35` | Public | none |
| `SUPABASE_PUBLISHABLE_KEY` | `BuildConfig`, `SupabaseClientProvider.kt:36` | Safe to ship (RLS is the control) | Disable the legacy anon/service_role JWT keys if unused. Never ship `sb_secret_`. |
| `GOOGLE_WEB_CLIENT_ID` | `BuildConfig`, `AuthViewModel.kt:28`, `MainViewModel.kt:135` | Safe to ship (public) | Android OAuth client with package + SHA-1 for the debug, upload and **Play app-signing** keys. Consent screen in production. |
| Release keystore and passwords | `keystore.properties`, `kharcha-paani-release.jks` (repo root, gitignored, never committed) | Secret, never shipped | Enroll in Play App Signing so this becomes the upload key. Keep an offline backup outside the working tree. |
| `debug.keystore` | repo root, gitignored; `build.gradle.kts:106-111` | Debug only | none |
| `google-services.json` | absent (`googleServices.missing…=WARN`, `build.gradle.kts:159`) | n/a (Firebase removed by PR #64) | none |
| strings.xml | no keys or tokens found | n/a | none |

---

## 2. Findings

Severity scale:
- **Blocker:** must fix before Play launch.
- **High:** fix before launch unless the owner accepts the risk.
- **Medium:** fix soon after launch.
- **Low:** hardening.

### H-1 (High, OWNER REVIEW: privacy/compliance): users' financial documents go to Gemini under unknown data-use terms

**Evidence:**
- Receipts are sent as `inlineData` image/jpeg (`GeminiRepository.kt:174-177`).
- Full bank-statement PDFs are sent base64 (`GeminiRepository.kt:396-409`).
- Ledger summaries, including friends' names, go out in `askForAnswer` (`GeminiRepository.kt:243-250`).
- `metadata.json` declares `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`, which is AI Studio provenance.

**Failure scenario:** the key belongs to an unbilled (free-tier) project, so Google's terms allow human review and use of the content to improve its products. A Play reviewer or user finds that bank statements were shared on those terms without disclosure, which violates the Play User Data policy and puts the app at risk of removal.

**Fix:**
1. Owner confirms the Gemini key's Google Cloud project has billing enabled (paid tier).
2. The privacy policy (required, linked in the Play listing and in-app) states that receipts, statements and ledger text are sent to Google Gemini and to Supabase.
3. The Play Data safety form declares Financial info, Photos and Files as collected and shared for app functionality.
4. Show a one-time in-app notice before the first statement import.

The Edge Function (owner list) should not log request bodies.

### H-2 (High, OWNER REVIEW: RLS/trigger): the share-sum invariant is enforced only inside an RPC; direct table writes bypass it

**Evidence:**
- `save_group_expense` checks `sum(shares) = amount = split.total` (`20260926000100_group_expense_guests.sql:61-68`).
- But `transactions_update_owner_or_payer` lets the owner **or the payer** PATCH `transactions.amount` directly (`20260918073251_payer_can_edit_group_expenses.sql:53-60`).
- `split_shares_update` lets either of them PATCH `share_amount` directly (`20260926000100_group_expense_guests.sql:217-239`).
- `splits_update` allows `total_amount` (`20260918073251…:104-114`).
- No table constraint or trigger re-checks the sum.

**Failure scenario:** a group member who paid an expense sends `PATCH /rest/v1/split_shares?id=eq.<share>` with `{"share_amount": 50000}` on a ₹600 dinner, using their own session and the publishable key. Every groupmate's device pulls a share that no longer adds up, and balances are wrong for the whole group. The app's own guard (`LocalLedgerSyncEngine.kt:94` logs "shares don't add up" for local splits) shows the client assumes the server guarantees this.

**Fix (new migration, additive):**
1. Add `private.check_group_expense_sums(p_transaction_id uuid)`. It compares `transactions.amount`, `splits.total_amount` and `sum(share_amount) where deleted_at is null`.
2. Call it from `CONSTRAINT TRIGGER … DEFERRABLE INITIALLY DEFERRED` on `transactions` (after update of amount, only when `group_id is not null and deleted_at is null`), on `splits` and on `split_shares`. Deferred, so `save_group_expense`'s multi-statement upsert still passes at commit.
3. Raise `23514` so the app marks the row FAILED.
4. Add tests in `supabase/tests/`: a direct PATCH of `share_amount` is refused; a direct PATCH of `amount` is refused; `save_group_expense` still passes; a soft-delete still passes.

### H-3 (High, input validation, causes sync-wide failure): a NaN or Infinity amount is accepted and then breaks every sync pass

**Evidence:**
- The amount field has no input filter, only a keyboard hint (`Sheets.kt:318-322`, `KeyboardType.Decimal`). A hardware keyboard, paste or an autofill suggestion can enter `NaN`, `Infinity` or `1e400`.
- `saveCurrentTransaction`: `val total = _amount.value.toDoubleOrNull() ?: 0.0; if (total <= 0) return` (`MainViewModel.kt:1211-1212`). `"NaN".toDoubleOrNull()` is `NaN`, and `NaN <= 0` is **false**, so NaN is saved.
- The settle-up and IOU sheets gate on `value > 0` (`SettleUpSheet.kt:78,87`; `IouSheet.kt:68`). `Infinity > 0` is true, so an Infinity settlement or IOU is saved.
- Upload conversion: `BigDecimal.valueOf(amount)` (`RemoteTransaction.kt:56`, `RemoteGroups.kt:174`) throws `NumberFormatException` for NaN or Infinity.
- `SyncWorker.syncIfSignedIn` catches only `IOException`, `RestException` and `IllegalStateException` (`SyncWorker.kt:72-86`). The exception escapes the pass.
- The push loop (`TransactionSyncEngine.kt:40-50`) re-reads the same pending batch every time, so every later pass dies on the same row.
- Locally, NaN also poisons every sum (balances and charts show NaN).

**Failure scenario:** a user pastes "Infinity" or types "NaN". From then on nothing syncs for that account (no new uploads, no pulls after the failing push), silently.

**Fix:**
1. Add one `Money.parseInput(text): Double?` in `domain/Money.kt`. It returns null unless the text matches `^\d{1,10}(\.\d{0,2})?$` after removing `,`, and the value is finite and `<= 9_999_999_999.99` (`numeric(12,2)`).
2. Use it in `MainViewModel` (lines 1114, 1128, 1211), `ConfirmBottomSheet.kt:240`, `SettleUpSheet.kt:78`, `IouSheet.kt:68`, `SubscriptionsSheet.kt:106`, `SplitEditor.kt:207,214,271`.
3. Filter keystrokes in `BigAmountField` (`Sheets.kt:319`): reject a change that fails the regex.
4. Guard the repository layer as well: `require(amount.isFinite() && amount > 0)` in `LedgerRepository.recordSettlement` / insert paths.
5. In the sync engines, map a row whose `toUpload` throws to `markFailedIfUnchanged`, so one bad row can't stop the queue.
6. Tests: a NaN or Infinity input is refused at save; a seeded NaN row in Room is marked FAILED while other rows still sync.

### M-1 (Medium, OWNER REVIEW: auth/deep links): invite links use an unverifiable custom scheme

**Evidence:** `AndroidManifest.xml:66-71` declares `kharchapaani://join/CODE`, and `InviteLink.kt:10-18` builds it.

**Failure scenario:** any installed app can register the same scheme. When a user taps an invite in WhatsApp, Android shows a chooser, or opens the other app if this one isn't installed. That app reads the 8-character code, joins the group through `join_group`, and from then on reads every group expense and every member's UPI ID and display name (`profiles_select_self_or_groupmate`). The invite stays valid for 7 days and is multi-use (`20260914033124…:50-57`).

**Fix:**
1. Move to verified App Links: `https://<owned-domain>/join/CODE` with `android:autoVerify="true"` and `/.well-known/assetlinks.json` listing the Play app-signing SHA-256.
2. Keep parsing the bare code (`InviteLink.parse`) so codes pasted as text still work.
3. Until then, accept the risk, or shorten invite expiry to 48 h (server default `expires_at`). Owner decision, since it touches the brute-force item.

### M-2 (Medium, prompt injection into ledger writes): AI output has no ceilings and statement rows are pre-selected

**Evidence:**
- Statement rows come back `isSelected = true` (`GeminiRepository.kt:524`) with no row cap and no amount ceiling.
- `AiParse.amountField` only takes `abs()` of any finite value (`LocalHeuristicParser.kt:175-182`).
- `merchant` and `note` lengths are unbounded (`GeminiRepository.kt:520-523`, `107-111`).
- `confirmImportStatementTransactions` inserts everything ticked (`MainViewModel.kt:910-931`).
- Mitigating: text and receipt reads always go through the confirm sheet (`MainViewModel.kt:817-823, 830-833`).

**Failure scenario:** a crafted PDF (a "statement" sent by a scammer, or text hidden in white font) instructs the model to emit 2,000 rows or a ₹9,99,99,999 "salary". The user taps Import on a pre-ticked list, and the ledger is polluted. Amounts above `numeric(12,2)` are then rejected by the server on every sync and stay FAILED.

**Fix:**
1. In `statementItems`, drop rows with `amount > 10_000_000` (or the H-3 ceiling).
2. Truncate `merchant` to 80 and `note` to 200 characters.
3. Cap the list at 1,000 rows and say so in the UI.
4. Pre-tick only rows whose date falls inside the parsed `statementPeriod`.
5. Apply the same truncation to `parseText` / `readReceipt` results.
6. When the Edge Function lands, validate the response schema there too.

### M-3 (Medium): the financial UI has no screenshot or recents protection, and there is no app lock

**Evidence:** no `FLAG_SECURE`, no `setRecentsScreenshotEnabled`, no `BiometricPrompt` anywhere in `app/src/main` (grep).

**Failure scenario:** balances, UPI IDs and bank-statement rows show in the Recents thumbnail. Anyone holding the unlocked phone opens the full ledger.

**Fix:**
1. On API 33+, call `setRecentsScreenshotEnabled(false)` in `MainActivity.onCreate`. This is low cost and doesn't block user screenshots.
2. Add an opt-in Settings toggle, "Lock with fingerprint / screen lock". It uses `androidx.biometric` `BiometricPrompt` with `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` and runs on `ON_START` after more than 60 s in the background.
3. Do not force `FLAG_SECURE`. Users share screenshots of splits on purpose.

### M-4 (Medium, OWNER REVIEW: privileges): defaults grant `anon` ALL on every new object

**Evidence:** migration comments say so (`20260923000000…:141-144`; `20260914033124…:656-657, 675-678`). The core tables still carry `anon` INSERT, UPDATE and SELECT grants. They are safe today only because every policy is `to authenticated`.

**Failure scenario:** a future migration adds a table or a `public` function and forgets the revoke. The function becomes callable without signing in, and any `security definer` function becomes a hole.

**Fix (new migration):**

```sql
alter default privileges in schema public revoke all on tables from anon;
alter default privileges in schema public revoke all on functions from anon, public;
alter default privileges in schema public revoke all on sequences from anon;
```

Then `revoke all on all tables in schema public from anon;`. Add a test asserting that `anon` has no privileges on any `public` table or function (query `information_schema.role_table_grants` / `routine_privileges`).

### M-5 (Medium): no server-side write rate limits

**Evidence:** no rate-limit tables or triggers in any migration. PostgREST has no per-user limits. Text columns are unbounded (owner list).

**Failure scenario:** one malicious account inserts millions of transactions or settlement requests (each settlement request also notifies a groupmate). Database size and costs grow, and pulls slow down for everyone in that person's groups.

**Fix:**
1. With the `char_length` migration (owner list), add a `before insert` trigger on `transactions`, `settlements` and `local_settlements` that refuses more than N rows per `auth.uid()` per hour (e.g. 500), tracked in `private.write_counters(profile_id, hour, n)`.
2. Cap `group_members` per group (e.g. 50) inside `join_group`.
3. Set a database-size alert in Supabase.

### L-1 (Low): Room DB, DataStore and session are unencrypted at rest; Auto Backup includes the ledger

**Evidence:**
- `allowBackup="true"` (`AndroidManifest.xml:22`). Only `supabase_auth.xml` is excluded (`backup_rules.xml:9`, `data_extraction_rules.xml:9,12`), so `ledger-<uid>.db` and `app_settings` DataStore (UPI ID keys, `AppSettings.kt:123-124`) go to Google Drive backup and device transfer.
- The session sits in plain `SharedPreferences` (`SupabaseClientProvider.kt:31-33`).

**Assessment:** acceptable under MASVS-STORAGE for this threat model. The app sandbox, file-based encryption, and E2E-encrypted Drive backups when a screen lock is set together cover it. EncryptedSharedPreferences is deprecated. PR #76 already excludes receipts.

**Fix:** none required. Optionally exclude `database/` from `device-transfer` if the owner prefers re-sync from the cloud over copying. **Do not** add SQLCipher pre-launch.

### L-2 (Low): sign-out leaves the account's ledger on the phone

**Evidence:** `LedgerDatabases.close()` keeps the file, as its own comment says (`LedgerDatabases.kt:50-54`). `AuthRepository.signOut` clears only the session and Credential Manager (`AuthRepository.kt:184-198`).

**Failure scenario:** on a shared or sold phone, the previous person's ledger file stays until uninstall. It isn't visible in the UI to another Google account, but an extraction would show it.

**Fix:** on sign-out, offer "Also remove this account's data from this phone". It is enabled only when no rows are pending sync (`SyncDao` pending counts). It deletes `ledger-<uid>.db*`, the per-user DataStore keys, `cache/exports`, `cache/asks` and `filesDir/receipts`. Account deletion (owner list) must do the same.

### L-3 (Low): shared-file handlers accept any URI scheme and authority

**Evidence:** `MainActivity.kt:251-272` passes `EXTRA_STREAM` straight to `SharedFiles.decodeImage` / `readBytes` (`SharedFiles.kt:37,45,73`). There is no check for `content://`, or for a URI that points at the app's own FileProvider or files.

**Failure scenario:** a malicious app sends `ACTION_SEND` with `file:///data/data/com.vaibhav.kharchapaani/…` (StrictMode disabled on its side) or `content://com.vaibhav.kharchapaani.fileprovider/exports/…`. The app reads its own private file and uploads it to Gemini. The attacker doesn't get the content back, so impact is limited, but it is a MASVS-PLATFORM finding.

**Fix:**
- In `routeIntent`, accept only `uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority != "${packageName}.fileprovider"`. Otherwise show "Couldn't open that file".
- Unit test with a `file://` URI and a self-authority URI.

### L-4 (Low): the Gemini key travels in the URL query string, and response bodies are logged

**Evidence:**
- `?key=$apiKey` (`GeminiRepository.kt:321, 426`).
- `Log.e` with `text.take(400)` of Google responses (`:336, 346, 446, 468`, via `failedCall` `:362` and `failed` `:535`).
- `logging-interceptor` is an unused `implementation` dependency (`build.gradle.kts:209`). If anyone adds `HttpLoggingInterceptor` at BODY, the key and the documents go to logcat.

**Fix:** moot once the Edge Function lands. Until then:
- send the key as the `x-goog-api-key` header;
- drop the body excerpts from the release logs;
- remove `libs.logging.interceptor` (check whether PR #64 already does);
- with R8 (PR #71), add `-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }` to `proguard-rules.pro`.

Note: supabase-kt 3.6.0 masks `Authorization` and `apikey` in `RestException.message` (checked in the AAR: `StringMasking.SENSITIVE_HEADERS`), so the sync logs (`GroupSyncEngine.kt:73,99,180,219,242`, etc.) don't leak tokens.

### L-5 (Low): settlement notifications show names and amounts on the lock screen

**Evidence:** `NotificationHelper.kt:64-75` sets no `setVisibility` or `setPublicVersion`.

**Fix:** `.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(<builder with "A payment is waiting for your answer">)`.

### L-6 (Low): exported MainActivity, default launchMode

**Evidence:** `AndroidManifest.xml:40-73`. It is exported because of the launcher and SEND filters. The internal actions are fixed by PR #74.

**Gap PR #74 leaves:** the `ACTION_SEND text/plain` path (`MainActivity.kt:275-279`) lets any app push arbitrary text into `parseInputText`, which spends a Gemini call. There's no harm to data because the confirm sheet guards it, but it burns quota.

**Fix:** after the Edge Function quota lands this is self-limiting. Optionally debounce identical shared text.

### L-7 (Low): a payer can rewrite a groupmate-added expense directly, outside the RPC

This is by design (backlog item 11), but combined with H-2 there is no audit trail. Once H-2 is enforced, it is fine. Consider an `edited_by` column stamped by trigger, so groupmates can see who changed what.

### L-8 (Low): profile fields any user can set

**Evidence:**
- `profiles_update_own` allows any column on your own row (`20260913144006…:29-33`).
- `display_name` and `avatar_url` are unbounded. `display_name` is already on the `char_length` list.
- A member can rename themselves to match another groupmate's name to confuse settle-ups. The UPI link is still built from the payee's own profile (`UpiLink.kt:33-34`), so money can't be redirected that way.
- `avatar_url` is never loaded (Coil commented out, `build.gradle.kts:188`).

**Fix:** with the `char_length` migration:
- add `check (avatar_url is null or avatar_url ~ '^https://[a-z0-9.-]*googleusercontent\.com/')` or drop the column from client updates;
- add `display_name` `1..60`.

### L-9 (Low, OWNER REVIEW: trigger): the hue-clash check is skipped when the clashing groupmate has no display name

**Evidence:** `20260927000000_profile_hue.sql:79-95` runs `select p.display_name into v_clash … limit 1`, then `if v_clash is not null`. A clashing profile with `display_name is null` (possible: `handle_new_user` coalesces Google metadata that may be missing, `20260913144006…:46`) yields a null `v_clash`, and the clash is allowed.

**Fix:** `select coalesce(p.display_name, 'a groupmate') into v_clash`, or use `if found`. Add a test with a null-name groupmate.

### L-10 (Low): backup and CSV exports are plaintext and leave via the share sheet

**Evidence:** `file_paths.xml:6-7`, `LocalBackupRepository.kt`. Backups contain the whole ledger and UPI IDs.

**Fix:** add a line in the export sheet ("This file contains your full ledger; share it only with yourself"). Optional password encryption later.

---

## 3. Area-by-area results (including what is fine)

### (2) Supabase schema, RLS, functions, storage

- **RLS is enabled on all 10 tables:** `profiles`, `groups`, `group_members`, `transactions`, `splits`, `split_shares`, `settlements`, `group_invites`, `local_splits`, `local_settlements` (the `enable row level security` lines in the core, invites and private-ledger migrations).
- **Every function sets `search_path = ''`.** All 27 SECURITY DEFINER functions plus all INVOKER ones were checked mechanically across the migrations.
- RPCs that write as the caller are SECURITY INVOKER: `save_group_expense`, `save_local_split`, `respond_to_settlement`.
- Every `public` RPC revokes `anon` / `public` and grants `authenticated`.
- `handle_new_user` is revoked from all client roles (`20260913144030`).
- DELETE and TRUNCATE are revoked from client roles (`20260914033124…:679-690`). The private-ledger tables `revoke all` and grant back only select, insert, update (`20260923000000…:146-147, 268-269`).
- Profiles are readable only by self and current groupmates (`20260914033124…:202-205`). Memberships are created only by RPCs (`:224`). Settlements are immutable except for status and retraction (`20260926000000…:104-167`). A group expense can't leave its group (`20260914142135…:122-146`). The owner is locked (`20260918073251…:62-82`).
- `upi_id` is format-constrained so it can't inject `upi://` parameters (`20260918210646…:18-21`), and the client re-validates (`UpiLink.kt:21-34`).
- Realtime publishes only `transactions`, `settlements` and `groups`, all RLS-filtered (`20260914034325…:66`).
- **No storage buckets** are defined anywhere. `receipt_storage_path` is unused, and receipts are local-only (PR #76).
- Gaps: H-2, M-4, M-5, L-8, L-9, plus the owner-list items.

### (3) Manifest

- **Cleartext:** `usesCleartextTraffic` is not set. With targetSdk 36, cleartext is off by default. No `network_security_config`, so only system CAs are trusted (user CAs are ignored). Good.
- **`android:debuggable`:** not set (release default false). Good.
- **FileProvider:** `exported=false`, with only `cache/exports` and `cache/asks` exposed (`file_paths.xml:6-7`). Good.
- **Receivers:** `AskSentReceiver` is not exported. Its mutable PendingIntent is explicit (component plus `setPackage`; `AskShare.kt:76-91`). Widget receivers are exported only for `APPWIDGET_UPDATE`. Fine.
- **Other PendingIntents:** all `FLAG_IMMUTABLE` (`NotificationHelper.kt:64,105`, `WidgetIntents.kt:30-32`).
- **Permissions:** INTERNET, POST_NOTIFICATIONS, and RECORD_AUDIO (removed by PR #64). `<queries>` limited to `upi`.
- **taskAffinity:** default. No StrandHogg-relevant configuration.
- Deep link: M-1. Backup: L-1.

### (4) Local data at rest

- Logging: no `println` or `printStackTrace` anywhere.
- No ID token, access token or Gemini key is logged.
- Supabase errors are masked by the library.
- Gaps: L-1, L-2, L-4, L-5, M-3.

### (5) Network

- TLS goes to Google and Supabase through OkHttp 5.3.2 with system trust.
- **Certificate pinning is not recommended.** Supabase (Cloudflare) and Google rotate intermediates. A pin failure would brick sync and sign-in for every user, and pinning adds little over system-CA TLS for this threat model.
- OkHttp timeouts are set per call: connect, read and write at 30 s; text 25 s, receipt 45 s, ask 60 s, statement 180 s; call timeouts at +15 s or +30 s (`GeminiRepository.kt:324-328, 429-433, 664-669`).
- The Supabase client uses the default Ktor/OkHttp engine timeouts (`SupabaseClientProvider.kt:38`). Acceptable.
- Prompt injection: M-2.

### (6) Auth

- **Nonce:** 32 random bytes from SecureRandom. The SHA-256 goes to Google and the raw value to Supabase (`GoogleNonce.kt:11-31`, `AuthRepository.kt:47-59`). Correct.
- **Session:** supabase-kt auto-refresh (default on). The session lives in a dedicated prefs file excluded from backup. Background callers re-import it (`BackgroundAuth.kt:39-52`).
- **Sign-out:** server `signOut`, local `clearSession` as fallback, Credential Manager cleared (`AuthRepository.kt:184-198`). Background sync is cancelled per account (`AccountSessionHolder.kt:51`), and widgets and workers check the signed-in id (`SyncWorker.kt:71`, `AskSentReceiver.kt:50`).
- Gaps: L-2, the dashboard provider checks in item 9 of section 0, and account deletion (owner list).

### (7) Input validation

- **Good:**
  - server CHECKs on `amount > 0`, `numeric(12,2)`, group name 1-60 characters, avatar emoji 1-8, `upi_id` format, local names 1-64, response note 200, hue range;
  - JSON share validation in `private.local_shares_are_valid`;
  - the client UPI regex;
  - invite codes checked against the alphabet before any RPC (`InviteLink.kt:21-27`).
- **Gaps:** H-3 (NaN, Infinity, no ceiling), M-2 (AI fields), L-8, plus the `char_length` items on the owner list (`transactions.merchant/note/category/payment_method`, `settlements.note`, `groups.description/emoji/color_hex`, `profiles.display_name`).
- A malformed `color_hex` from a groupmate can't crash the app: `PersonAvatar.kt:72` wraps `parseColor` in `runCatching`.

### (8) Dependencies

- An OSV batch query (2026-10-24) returned **no known vulnerabilities** for: ktor-client 3.4.3, okhttp 5.3.2, retrofit 2.12.0, moshi 1.15.2, zxing 3.5.4, supabase-kt 3.6.0, room 2.7.0, datastore 1.1.7, work 2.11.2, coroutines 1.10.2, credentials 1.6.0, googleid 1.2.0, multiplatform-settings 1.3.0, firebase-bom 34.17.0, activity-compose 1.10.1, lifecycle 2.8.7, haze 1.7.0.
- This covers direct dependencies only; transitive ones are covered by Dependabot (PR #65).
- Unused catalog entries (camera, location, accompanist, coil, navigation) are not on the classpath.
- Remove `logging-interceptor` (L-4).
- `isMinifyEnabled = false` (`build.gradle.kts:117`) is fixed by PR #71.

---

## 4. Owner review queue

1. H-1: Gemini paid tier, privacy policy, Data safety.
2. H-2: the deferred constraint trigger for share sums (new trigger in the RLS area).
3. M-1: App Links domain, invite expiry.
4. M-4: revoking default privileges from anon.
5. M-5: write rate limits (triggers).
6. L-9: the hue trigger fix.
7. Dashboard checks: auth providers, exposed schemas, GraphQL, Realtime public access, legacy JWT keys, Security Advisor.
8. Key rotation sequencing with the Edge Function rollout.
