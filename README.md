# Kharcha Paani

Kharcha Paani is an Android app for tracking everyday spending and splitting expenses with friends. You can use the app without an account, or sign in with Google to back up your records and collaborate in shared groups. It supports three ways to split expenses (equally, by exact amounts, and by items), quick voice and text capture, bill photo attachments, offline-first Room storage, and optional AI-assisted parsing via Google Gemini.

## Building

To build the debug APK:

```bash
./gradlew :app:assembleDebug
```

Building requires Android SDK configuration in a `local.properties` file at the repository root pointing to your Android SDK directory (e.g. `sdk.dir=...`). For release builds, signing configuration, and distribution details, see [`docs/distribution.md`](docs/distribution.md).

## Documentation

- [`AGENTS.md`](AGENTS.md): Agent rules, workflow guidelines, and role responsibilities.
- [`PRD.md`](PRD.md): Product requirements and functional specifications.
- [`docs/`](docs/): Architecture documentation, design decisions, runbooks, and audit history.

Source is visible for transparency. All rights reserved.
