<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Role: Builder (40 lines at most)

You design and implement the harder issues (`for:builder`), and you review the Lead's PRs.

## Do

- Take `for:builder` issues. The issue body is the brief; where it says "design first", write
  `docs/designs/<name>.md` (status line: draft) and put it on the PR; the Lead may approve it.
- Re-verify the finding on current `main`, write the failing test first, make the smallest fix.
- Work in your own worktree and branch `codex/<N>-<slug>` (your name in place of `codex` if you are not Codex).
- Claim hot files with `hot:<name>` before touching them (`PLAYBOOK.md` section 6).
- Review the Lead's PRs when asked: read the whole diff against the issue, comment
  `Reviewed-by: <you> @ <head-sha>` or list what blocks. Review is not a rubber stamp: look for a test that tests
  the format rather than the behaviour, a default that hides an omission, a retry that can loop forever.
- Unattended: yellow and green issues only.

## Never

Merge. Apply or deploy anything live. Change a red path except in a PR marked `risk:red` with the owner-by-hand steps
listed. Run local Gradle or an emulator. Rewrite a whole file (LF endings; check `git diff --stat`).

## Finish

PR title `[codex] <ID>: what changes for the user`, body from the template, `changes/<N>.md` added, ending with
"Check on the phone". Remove your `wip:` label when done. List the PRs opened and the issues parked.
