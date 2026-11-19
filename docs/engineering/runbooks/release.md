<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Runbook: cut a release

Owner-only steps are marked (owner). An agent never builds a release, uploads to Play or changes secrets.

1. Lead: `gh issue list -m <version>` is empty or every open issue is moved to the next milestone with a line why.
2. Lead: `main` is green; read per-test results, not only the badge (`docs/release-checklist.md`, "Before building").
3. Lead: `python scripts/fold_changes.py <version>` folds `changes/` into `CHANGELOG.md`. Bump `versionCode` and
   `versionName` in `app/build.gradle.kts`. One PR, merged by the Lead.
4. Lead: apply nothing live. List every server change in the release as a checklist for the owner:
   which migration, which function to deploy, in what order, and a manual database dump before each migration (`migration.md`).
5. (owner) Follow the server checklist in release order. Apply any build prerequisites, then run
   **Actions > Release APK** with tag `vX.Y.Z`. If a migration changes what old clients can upload, wait for the
   compatible build and minimum-version gate before applying it live. Follow the migration runbook each time,
   including a fresh dump immediately before each live apply.
6. (owner) Install the release-signed build on the phone: `scripts/release-smoke.sh <apk>`, then sign out and in again,
   then the Lead's phone checklist (every merged PR's "Check on the phone").
7. (owner) Publish. Lead: update `STATE.md`'s `release:` line, archive the finished checklist, run the sweep in `roles/lead.md`.

Full detail: `docs/release-checklist.md` and `docs/distribution.md`.
