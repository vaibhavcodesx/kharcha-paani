<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Playbook: the rules for everyone (150 lines at most)

Rules are written for roles. `STATE.md` says which agent holds which role. Nothing else names one.

## 1. Roles

- **Lead** keeps the board in order, writes briefs, reviews and merges, cuts releases, keeps docs true,
  talks to the owner. One at a time.
- **Builder** takes `for:builder` issues, designs and implements, opens PRs, reviews the Lead's PRs.
- **Worker** takes `for:worker` issues exactly as briefed: tests, docs, strings, accessibility,
  mechanical refactors, and `risk:yellow` product code only from a complete brief. Never merges, never
  designs, never touches red paths. May run up to three sub-agents, one worktree each, on issues that
  touch different files.
- The **owner** decides product questions, tests on the phone, and is the only one who changes `STATE.md`'s lead.

## 2. The loop

1. Take and claim an issue (`AGENTS.md`). The issue body is the brief. If it is unclear, make the safe
   choice, write it in the PR, and file a `needs-owner` issue for the rest. Do not stall.
2. Re-verify the finding on current `main` before changing anything. If it is already fixed, close the
   issue with the commit that fixed it and take the next one.
3. `git worktree add ../kp-wt/<N> -b <agent>/<N>-<slug> origin/main`. Work there, never in the owner's checkout.
4. A bug gets its failing test first. Tests are on real data paths (real in-memory Room, real gathering
   function, assert a balance), not on a hand-built object. See `docs/engineering/testing.md`.
5. Open a **draft PR** as soon as there is a first commit; mark ready when you believe CI is green.
6. Size: under 400 changed lines outside tests. Larger: split, or stack with "merge after #N".
7. When done: `gh pr checks N`; if red, read the log, fix, push. The same PR red twice for the same
   reason: add `blocked` to the issue with a comment saying what you found, and move on.
8. Remove `wip:<you>` when the PR is merged or you give up. A `wip:` label with no push for 48 hours
   may be removed by anyone.

## 3. Risk, review, merge

The author labels the PR `risk:green|yellow|red` (the Lead corrects it from the diff: tests, docs,
`changes/` are green; `app/src/main` is yellow; the red paths below are red).

| Risk | Covers | Review | Merged by |
|---|---|---|---|
| green | tests, docs, comments, string resources with no wording change | none beyond CI | the Lead (a Worker or Builder PR) or the author if Lead |
| yellow | product code with no schema, sync-contract, auth or server change | the other main agent, or the Lead if it is the author's equal | the Lead |
| red | `supabase/`, RLS, auth, security, `app/schemas/`, `.github/`, signing, releases, major dependency bump, `STATE.md` lead line | the Lead reads the whole diff, plus the other main agent when available | the Lead, only when all of 3a hold |

**3a. Red merge conditions** (owner's permission of 2026-11-19, while he sleeps and for 1.8.0): CI green;
the Lead read every line; the change is **staged** (a migration or function in the repo is not applied or
deployed by merging it, and the PR says so); no secret, no destructive or non-additive migration, no
loosened RLS policy; the PR body lists what the owner must still do by hand (apply, deploy, test). If any
condition is unclear, the PR stays open with `needs-owner`. Anything that would reach live Supabase,
signing, the Play Console or GitHub settings is never done by an agent.

Cross-review is trusted, not enforced: the reviewer comments `Reviewed-by: <agent> @ <head-sha>` and
the Lead merges only if that sha is the PR's head. No second reviewer free: the Lead reviews alone and
says so in the merge comment.

## 4. Merging (Lead only)

```bash
gh pr checks N                      # every check passes, read it, don't assume
gh pr view N --json mergeable,mergeStateStatus   # a conflicting PR gets NO ci run; rebase onto main
gh pr merge N --squash --delete-branch --match-head-commit <sha>
```

Merge one at a time and let the next rebase. No integration branches except for a release. After
merging, `git fetch` and confirm the issue closed; close it by hand if not.

## 5. Pull requests

Title `[agent] ID: what changed for the user`. Body from `.github/pull_request_template.md`: what and why,
how tested, "Check on the phone", risk, docs. One change per PR. Squash merge only. Never push to `main`.
Never force-push over a reviewed commit. A PR must not rename or reformat what it does not need to change.

## 6. Hot files: one open PR at a time

`strings.xml`, `MainViewModel.kt`, `MainActivity.kt`, `libs.versions.toml`, the Room database class,
`CHANGELOG.md`. Claim one with the label `hot:<name>` on your issue (`hot:strings`, `hot:mainviewmodel`,
`hot:mainactivity`, `hot:versions`, `hot:db`). If it is already taken, take another issue. `CHANGELOG.md`
is never edited in a feature PR: use `changes/<issue>.md`.

## 7. Unattended sessions (overnight, scheduled)

Worker: any time. Builder: yellow and green issues only. Lead: may review and merge green and yellow,
and red under 3a. Never: releases, anything live, settings, secrets, a non-additive migration. Never
retry a rejected push. End by listing the PRs opened and merged, and the issues parked.

## 8. When the Lead is absent

If the Lead has not acted on a PR for 72 hours, the Builder is acting Lead for green merges and Worker
briefs. A yellow PR with no second main agent gets `needs-owner`. The Lead keeps at least ten briefed
Worker issues ready; a Worker PR waiting more than three days is the Lead's problem.

## 9. Worker briefs

The Lead writes them in the issue body with the headings in `docs/agents/briefs/TEMPLATE.md`: Goal, Files,
Do, Don't, Done when, Check on phone, If stuck. Only green or yellow. No two open Worker issues touch the
same file. At most ten open at once. A Worker who finds the brief wrong comments once, adds `blocked`, and
takes the next issue.

## 10. Keeping docs true

Every PR answers "what document does this make false?" and fixes it in the same PR. A finished plan or
superseded page goes to `docs/history/` with one line at the top saying what replaced it; nothing is deleted.
Limits: `AGENTS.md` 80 lines, `STATE.md` 30, this file 150, a role file 40. A new rule that came from a
mistake goes here or in `docs/engineering/testing.md`, with the date and the cost.

## 11. Questions and the owner

One list: `gh issue list -l needs-owner`. Ask everything that blocks planned work at the start of a Lead
session, in one go; after that run on autopilot. New questions go on the board with a safe default.
Each Lead session ends with a five-line digest in chat: merged, open, blocked, needs-owner, next.

## 12. Parallel machine limits

Several Gradle builds crashed the owner's laptop. Agents run no local Gradle, no emulator. At most three
sub-agents, at most fifteen open Worker PRs; at the cap, fix your own red or conflicting PRs first.
