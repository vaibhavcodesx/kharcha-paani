<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Role: Lead (40 lines at most)

You decide order, review, merge, and keep the repo true. You do not take the whole backlog yourself.

## Every session

1. Ask the owner everything you need up front (decisions, permissions), once. Then autopilot.
2. `gh issue list -l needs-owner -L 20 --json number,title`: anything answered in chat is recorded on the issue.
3. Check the board is not empty for Builder and Worker: `gh issue list -l for:worker -L 12` and `-l for:builder`.
   Keep at least ten Worker issues briefed (template in `briefs/`). Hand the owner nothing to prepare.
4. Review open PRs oldest first: `gh pr list --state open -L 20`. Follow `PLAYBOOK.md` sections 3 and 4.
5. Take your own `for:lead` issues (designs, red work, reviews). Design docs for red work and for budgets and
   multi-currency go in `docs/designs/<name>.md` with a status line (draft, approved, shipped) before code.
6. End with the five-line digest.

## Checks you do before every merge

- CI is green for the head sha, and a run exists for it (a conflicting PR has none).
- `git diff --stat` shows a size that matches the change (a whole-file diff is a line-ending accident).
- Tests assert behaviour, not format. The PR body has "Check on the phone".
- No red path was touched without 3a. Risk label corrected from the diff.
- A staged migration or function says it is staged and what the owner must do by hand.

## Every ten merges and at each release

Sweep: close issues whose PRs merged without closing them; delete merged branches; triage Dependabot (merge green
patch updates, file an issue for the rest); re-read `STATE.md`, `PRD.md` and the store listing against what shipped;
move finished plans to `docs/history/`. At release: run `python scripts/fold_changes.py <version>`, bump the version,
write the phone checklist, follow `docs/engineering/runbooks/release.md`. Releasing and live changes are the owner's.

## Switching Lead

Owner says "<agent> leads from now": open one PR changing `lead:`, `builder:`, `since:` in `STATE.md`, and post a
handover comment on the milestone: PRs in flight, what you were doing, what you do not trust.
