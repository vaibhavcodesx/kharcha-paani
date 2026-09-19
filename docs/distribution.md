# Distribution

How this app gets signed, built and published, and what you have to do by hand.

## What is where

| | |
|---|---|
| Module | `:app` (the only one — `settings.gradle.kts` includes nothing else) |
| applicationId | `com.vaibhav.kharchapaani` — **never change this**, see below |
| versionCode / versionName | `1` / `1.0.0`, in `app/build.gradle.kts` |
| Release build command | `./gradlew :app:assembleRelease` |
| Output | `app/build/outputs/apk/release/app-release.apk` (or `app-release-unsigned.apk`) |

**Why the applicationId can never change:** Android identifies an installed app
by it. Change it and every existing install stops being "this app" — updates
become a separate app with an empty database, and the Google Sign-In client
registered against the old package stops matching.

## 1. Create your release keystore

Once, on your machine. **This key is the app's identity.** Every future update
must be signed with the same one or Android refuses to install it over the
existing app, and there is no recovery — losing it means everyone uninstalling
and reinstalling from scratch.

```bash
keytool -genkeypair -v \
  -keystore kharcha-paani-release.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

`keytool` ships with any JDK; on this machine it is at
`C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe`.

It asks for a store password, a key password and your name/org. Use a real
password and keep it.

**Put the `.jks` somewhere outside the repository.** `*.jks` and `*.keystore`
are gitignored, but the safest arrangement is one where an accident is not
possible.

### Back these up, in a password manager or encrypted drive

1. `kharcha-paani-release.jks` itself
2. the store password
3. the key password
4. the alias (`upload`)

Without all four you cannot ship another update, ever.

## 2. Point the build at it

Create `keystore.properties` in the repo root. It is gitignored.

```properties
storeFile=C:/Users/you/keys/kharcha-paani-release.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

**Use forward slashes even on Windows.** `.properties` files treat a backslash
as an escape character, so `C:\Users\you\...` silently becomes `C:Usersyou...`,
the file is not found, and the build quietly produces an *unsigned* APK. The
build warns and names the path it actually looked at when this happens.

Environment variables work too, and are what CI uses: `KEYSTORE_PATH`,
`STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. `keystore.properties` wins if
both are present.

**No credentials at all is not an error.** The release build still runs and
produces `app-release-unsigned.apk`, which will not install on a phone. That is
deliberate: it lets anyone check that the release variant compiles without
holding the private key.

## 3. Build

```bash
./gradlew :app:assembleRelease              # AI features included
./gradlew :app:assembleRelease -PomitAiKey  # no Gemini key in the APK
```

Confirm it is actually signed before sending it anywhere:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

The SHA-1 it prints is the fingerprint you need in step 5.

## 4. Versioning

Both numbers live in `app/build.gradle.kts`, and both move together:

```kotlin
versionCode = 2        // any strictly larger integer; 1 → 2 → 3
versionName = "1.1.0"  // what people see; match the git tag, minus the "v"
```

- **`versionCode`** is what Android compares to decide whether an APK is an
  update. It must increase. If it does not, installing over the old app fails
  with no useful message.
- **`versionName`** is a label. Keep it equal to the release tag without the
  `v`, because the release workflow refuses to run if they disagree — a check
  that exists so a release can never be named something the APK contradicts.

Rough guide: bug fixes `1.0.0 → 1.0.1`, new features `1.0.0 → 1.1.0`, something
that breaks or resets data `1.0.0 → 2.0.0`.

## 5. The Gemini API key

**The problem.** The key is compiled into `BuildConfig` from `.env` by the
Secrets Gradle Plugin, so it is a string inside every APK that carries it, and
anyone holding the file can extract it and spend against your quota.

Three layers, in order of how much they actually help.

### a. Restrict the key to this app (do this)

Google Cloud Console → **APIs & Services → Credentials** → click the key →
**Application restrictions** → **Android apps** → **Add**:

| Field | Value |
|---|---|
| Package name | `com.vaibhav.kharchapaani` |
| SHA-1 certificate fingerprint | from `apksigner verify --print-certs` in step 3 |

Add **two** entries — one for the release keystore and one for the debug
keystore (`keytool -list -v -keystore debug.keystore -storepass android`) —
or debug builds stop being able to reach Gemini.

Under **API restrictions** on the same page, choose **Restrict key** and select
only **Generative Language API**, so an extracted key cannot be spent on
anything else in your project.

> **This only works because the app sends the right headers.** A key restricted
> to Android apps is checked against `X-Android-Package` and `X-Android-Cert`
> on each request. The Maps SDK adds these for you; a plain OkHttp call does
> not, so turning the restriction on without them makes every Gemini request
> fail with 403. `AppIdentity` adds them, reading the fingerprint from the
> installed package at runtime so debug and release each report their own.
> If the AI features break right after you enable the restriction, the
> fingerprint in the console does not match the one that signed the APK.

Google's own documentation says bypassing this restriction is "straightforward"
— the headers are just headers, and someone determined can send them. Treat it
as raising the cost, not as a wall.

### b. Cap the quota (do this too)

Google Cloud Console → **APIs & Services → Generative Language API →
Quotas & System Limits** → filter for the per-day request limits → edit and set
a ceiling you would not mind paying for. This is the layer that bounds the
damage if the first one is bypassed, and it is the only one that does.

Also set a **budget alert**: Billing → Budgets & alerts → a budget with email
alerts at 50/90/100%. It does not stop spend, but you find out in hours rather
than at the end of the month.

### c. Ship without the key (per build, your choice)

```bash
./gradlew :app:assembleRelease -PomitAiKey
```

`BuildConfig.GEMINI_API_KEY` is emitted as `""`, `GeminiRepository.hasApiKey()`
returns false, and `MainViewModel.aiAvailable` hides the whole AI section, so
the app shows no buttons that cannot work. Verified: with the flag the
generated `BuildConfig.java` contains `GEMINI_API_KEY = ""`; without it, the
real key.

The release workflow exposes this as the `omit_ai_key` tick box, and names the
APK `-no-ai` so the two builds are never confused.

### Backlog, not now

The real fix is to stop shipping the key at all — move the Gemini calls behind
a Supabase Edge Function, so the app calls your backend and the backend holds
the key. Logged in PRD.md. It is a genuine piece of work (an Edge Function,
auth on it, rate limiting per user, and rewriting five call sites), not
something to bundle into a distribution pass.

## 6. GitHub Releases

### One-time setup

GitHub → the repo → **Settings → Secrets and variables → Actions** →
**New repository secret**, for each of:

| Secret | What it is |
|---|---|
| `KEYSTORE_BASE64` | the `.jks`, base64-encoded (below) |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | the key password |
| `GEMINI_API_KEY` | from your `.env` |
| `SUPABASE_URL` | from your `.env` |
| `SUPABASE_PUBLISHABLE_KEY` | from your `.env` |
| `GOOGLE_WEB_CLIENT_ID` | from your `.env` |

To encode the keystore:

```bash
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:/Users/you/keys/kharcha-paani-release.jks")) | Set-Clipboard

# macOS / Linux
base64 -w0 kharcha-paani-release.jks | pbcopy
```

Paste the result as the value of `KEYSTORE_BASE64`.

### Publishing a release

1. Bump `versionCode` and `versionName`, commit, push to `main`.
2. **Actions → Release APK → Run workflow.**
3. Fill in the tag (`v1.0.0`), tick `omit_ai_key` if you want the AI-free
   build, and run it.

The workflow checks the tag against `versionName`, restores the keystore,
writes `.env` from the secrets, builds, **verifies the APK is actually signed**,
deletes the keystore and `.env`, then creates the release with the APK attached.

It is `workflow_dispatch` only. Pushing to `main` never publishes anything.

## 7. Android Developer verification

Google's developer-verification requirement begins **30 September 2026**, and
only in Brazil, Indonesia, Singapore and Thailand. India is not affected until
the global rollout in 2027. For sideloading to a few friends in India now,
nothing is required.

If you want to register early anyway, there is a free hobbyist tier — no
government ID, no fee, up to 20 devices — at the Android Developer Console.
Optional, and not a blocker for anything in this document.
