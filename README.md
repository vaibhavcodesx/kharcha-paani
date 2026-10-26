<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/07d9b3ea-e5c5-4bbc-a9b9-e35c13895442

## Download

**[Latest release](https://github.com/vaibhavcodesx/kharcha-paani/releases/latest)** &nbsp;·&nbsp; [All releases](https://github.com/vaibhavcodesx/kharcha-paani/releases)

Direct link to the newest APK, which always points at whatever the current release is:

```
https://github.com/vaibhavcodesx/kharcha-paani/releases/latest
```

### Installing on Android

1. Open the link above on your phone and download the `.apk` file.
2. Tap the downloaded file. Android will ask whether to allow installs from
   this source — that prompt is normal for an app that doesn't come from the
   Play Store. Allow it for your browser or file manager.
3. Tap **Install**, then **Open**.

If your phone blocks it, the setting is under **Settings > Apps > Special app
access > Install unknown apps**.

Updates appear on the same Releases page. Download the newer APK and install it
over the old one — your data stays, because every release is signed with the
same key.

> **A note on sending the APK around:** WhatsApp usually refuses `.apk`
> attachments. Send the Releases link instead of the file.

### Two kinds of build

Some releases are published without a Gemini API key. Those hide the AI
features (Ask about your money, Read this trip, statement scanning) and work
normally otherwise; they are marked in the release notes and their filename
ends `-no-ai`. Everything else — expenses, splits, groups, settling up, UPI
QR codes, CSV export — is the same in both.

## Releasing (maintainer)

Full reasoning in `docs/distribution.md`. The short version:

1. Bump `versionCode` **and** `versionName` in `app/build.gradle.kts`.
   `versionCode` must strictly increase or Android refuses the update.
2. Commit and push to `main`.
3. GitHub > **Actions** > **Release APK** > **Run workflow**. Enter the tag
   (`v1.1.0` — it must match `versionName`).
4. The workflow builds, signs, verifies the signature, creates the release and
   attaches the APK.

Building one locally instead:

```bash
./gradlew :app:assembleRelease
```

No build carries a Gemini key: the app reaches Gemini through the `gemini-proxy` Edge Function,
which holds the key as a server secret (see `supabase/functions/gemini-proxy/README.md`).

This needs a `keystore.properties` at the repo root (gitignored) — see
`docs/distribution.md`. Without it the build still succeeds but the APK is
left **unsigned** and will not install.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory with the Supabase project's URL and keys (see `.env.example`). AI features go through that project's `gemini-proxy` Edge Function, so no Gemini key goes in `.env`
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
