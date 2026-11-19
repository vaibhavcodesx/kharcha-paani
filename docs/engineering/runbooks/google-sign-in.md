<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: Google sign-in troubleshooting & external code exchange

Troubleshooting guide for Google OAuth sign-in failures across the owner admin page (`site/admin`) and the Android app. Agents never change live Supabase or Google Cloud settings; the owner applies configuration fixes following these steps.

## What to Check First
When sign-in fails with an error or redirects back with an error code:
1. **Check the Supabase Auth Logs:** Go to Supabase Dashboard > **Logs** > **Auth**, filter by `exchange`.
2. If it says `oauth2 invalid_client` or `The provided client secret is invalid`, the Google Web Client Secret in Supabase does not match the Web Client ID.
3. If it returns `unexpected_failure` with `Unable to exchange external code`, verify the Web Client ID is the **first** entry in Supabase's Client IDs and the redirect URI in Google Cloud matches the Supabase project callback.

---

## The 2026-11-19 Incident
**Symptoms:**
- The admin site redirected back to its address with error parameters in the URL: `error_code=unexpected_failure` and `error_description=Unable+to+exchange+external+code`.
- Users could not sign into the admin portal via Google OAuth.

**Root cause:**
- In Supabase Auth logs (`oauth2` filter / `exchange` events), the log recorded:
  `oauth2 invalid_client: The provided client secret is invalid`
- The Client Secret configured in Supabase Authentication did not match the Google Cloud OAuth 2.0 Web Client ID, or the Web Client ID was not the first ID in the client list.

---

## Core Rule: Client IDs and Secret Matching
Supabase Auth allows multiple client IDs for Google OAuth (e.g. Web + Android clients), but only **one Client Secret**:
- **The rule:** Supabase uses the **FIRST client ID** in its Google provider configuration field with the single **Client Secret**.
- **Both the first Client ID and the Client Secret MUST come from the same Google Web Client** (OAuth 2.0 Client IDs -> Web application in Google Cloud Console).
- Android OAuth clients have **no secret** (they are authenticated via package name and SHA-1 certificate fingerprint).
- In Supabase's **Client IDs** list, additional Android client IDs must come **after** the Web client ID, separated by commas without spaces:
  `<WEB_CLIENT_ID>,<ANDROID_CLIENT_ID_DEBUG>,<ANDROID_CLIENT_ID_RELEASE>`

---

## Step-by-Step Fix Procedure

### 1. Check Google Cloud Console Credentials
1. Open Google Cloud Console > **APIs & Services** > **Credentials**.
2. Locate **OAuth 2.0 Client IDs**.
3. Select your **Web application** client.
4. Verify **Authorized redirect URIs**:
   - Must contain: `https://<supabase-project-id>.supabase.co/auth/v1/callback`
   - If missing or incorrect, add the exact callback URL and click **Save**.
5. Use the copy button to copy the **Client ID** (`...apps.googleusercontent.com`).
6. Use the copy button to copy the **Client secret** (or generate a new secret if the existing secret is lost or compromised).

### 2. Configure Supabase Auth Provider
1. Open Supabase Dashboard > **Authentication** > **Providers** (or **Sign In / Providers**).
2. Expand the **Google** provider.
3. Ensure Google provider is **Enabled**.
4. Set **Client IDs**:
   - Paste the Web Client ID as the **very first** entry.
   - Append any Android client IDs separated by commas (no spaces):
     `web_client_id.apps.googleusercontent.com,android_client_id_1.apps.googleusercontent.com`
5. Set **Client Secret**:
   - Paste the corresponding Web application Client Secret copied from Google Cloud.
6. Click **Save**.

### 3. Verify Admin Site Redirect URLs
1. In Supabase Dashboard > **Authentication** > **URL Configuration**.
2. Under **Redirect URLs**, ensure the admin website URL (e.g. `https://<admin-domain>.vercel.app` or custom domain) is listed.

---

## How to Read Supabase Auth Logs
When debugging sign-in issues:
1. In the Supabase Dashboard, navigate to **Logs** > **Auth**.
2. In the search/filter box, type `exchange` to inspect OAuth token exchange operations.
3. Common errors and meanings:
   - `invalid_client: The provided client secret is invalid`: The secret pasted in Supabase does not match the Web Client ID, or the Web Client ID is not the first one listed.
   - `redirect_uri_mismatch`: Google Cloud Console Authorized redirect URI does not match `https://<project-ref>.supabase.co/auth/v1/callback`.
   - `access_denied`: The user canceled the Google sign-in consent screen.

---

## Security Warning & Token Hygiene
- **Never paste non-Google credentials:** Never paste GitHub tokens, service role keys, Supabase anon keys, or database passwords into Google OAuth fields. A GitHub token was mistakenly pasted in the past.
- **Immediate revocation:** If an OAuth client secret or personal token is ever exposed in logs, git commits, or chat, immediately revoke it in the originating service (Google Cloud Credentials or GitHub Settings > Developer settings > Personal access tokens) and generate a new one.
- **Agents:** Never display or log actual client secrets, service role keys, or tokens.
