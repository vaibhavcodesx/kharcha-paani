<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Unreleased changes

One small file per issue, so no two PRs ever edit the same line of `CHANGELOG.md`. Name it
`changes/<issue-number>.md` (the PR number does not exist yet when you write it).

```
kind: added | changed | fixed | removed | security | internal
One or two sentences a user or the owner would understand. Say what changed, not how.
```

`internal` (tests, refactors, tooling) is kept out of the published notes. A change that is **server, staged,
not applied** starts its sentence with "(server, staged, not applied)". Do not describe a vulnerability's
mechanism (the repository is public): say "hardening".

At release the Lead runs `python scripts/fold_changes.py <version>`: it writes the files under the new version in
`CHANGELOG.md` and deletes them. Files that are not named like an issue number are left alone (this README).
