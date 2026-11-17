# Review of the agent-system proposal, 2026-11-17

The owner approved [06-agent-system.md](06-agent-system.md) in outline on condition that it be
reviewed critically before it is built (answer G-01). This is that review, done by a second
reviewer acting as a staff engineer. **The agent that writes version 2 applies these changes; where
this page and the proposal disagree, this page wins**, except for the two notes at the end.

GitHub behaviours marked "verify" were stated from memory and not checked.

## Changes to make, most important first

1. **Make the bot account and the ruleset step 0, and drop the `owner-approved` label job.** A
   `pull_request` workflow runs the PR's own copy of the workflow, so a PR can edit the check, and
   any agent on the owner's login can add the label. Replace with: the bot has the Write role and is
   never on the bypass list; a ruleset on `main` requires a PR, the status check, no force-push, and
   Code Owner review; `CODEOWNERS` lists the owner for `supabase/`, `.github/`, `app/schemas/`,
   signing, `AGENTS.md` and `CODEOWNERS` itself. Log the owner's account out of `gh` on every
   agent. Verify that code-owner review works with zero other required approvals.

2. **Say plainly that cross-review is trusted, not enforced.** One bot account cannot approve its
   own PRs, and per-agent tokens share one identity. A GitHub App per agent needs hourly token
   minting, which is too much for a solo owner. Rule: "The reviewer comments
   `Reviewed-by: <agent> @ <head-sha>`; the Lead merges only if that sha is the PR's head."

3. **Derive risk from the diff.** Authors label their own risk and unattended merges trust it.
   Rule: CI maps paths to a minimum risk and fails if the label is lower. Tests, docs and `changes/`
   are green; `app/src/main` is yellow; code-owned paths are red.

4. **Replace the draft-PR lock.** It needs a commit before it exists, the start query's
   `no:assignee` cannot see it, and all agents are one assignee. Rule: "Claim with
   `gh issue edit N --add-label wip:<agent>`, then `gh issue view N --json labels`. If two `wip:`
   labels exist, the alphabetically later agent removes its own. A `wip:` label with no push in 48
   hours may be removed by anyone."

5. **Fix the start command.** It has no limit, no priority order and prints full rows. Replace with:

   ```bash
   gh issue list -l for:<role> -l p0 -S "-label:blocked -label:wip:claude -label:wip:codex -label:wip:antigravity sort:created-asc" -L 5 --json number,title --jq '.[]|"\(.number) \(.title)"'
   ```

   then `p1` if that is empty, then `gh issue view N --json body --jq .body`. Add: "Never read
   comments, and never run `gh issue list` without `-L`. The body is the brief; the Lead edits the
   body, not the comments."

6. **Prove CI mechanically.** "Green" is otherwise a claim, and a conflicting PR gets no run at all.
   Rule: "Merge only after `gh pr checks N --required` exits 0, then
   `gh pr merge N --squash --match-head-commit <sha>`." Add
   `concurrency: {group: ci-${{ github.ref }}, cancel-in-progress: true}` to CI.

7. **One cut-over PR.** Step 1 of the migration rewrites `AGENTS.md` while `.agents/rules`, the
   guard, the handbook, the work split and the cloud handoff stay live until steps 6 and 10, so for
   a while there are two sets of instructions. Merge steps 1 and 10, the PR template and the
   archiving of those files as one PR. Create the labels and file the issues (step 3) before it
   merges. Agents resume only when the board is not empty.

8. **Add a rule for when the Lead is absent.** Worker merges, briefs and yellow reviews all stall.
   Text: "If the Lead has not acted in 72 hours, the Builder is acting Lead for green merges and
   Worker briefs. A yellow PR with no second main agent available gets `needs-owner`; the owner
   merges it. The Lead keeps at least ten briefed Worker issues ready."

9. **Hot files, for every role.** The same-file rule covers Worker issues only. Text:
   "`strings.xml`, `MainViewModel`, `MainActivity`, `libs.versions.toml`, the Room database class:
   one open PR each, held by a `hot:<name>` label on the issue."

10. **Worker parallelism.** Text: "One worktree per sub-agent:
    `git worktree add ../kp-wt/N -b antigravity/N-slug origin/main`. The parent claims issues one
    at a time. At most three sub-agents and five open Worker PRs; at the cap, fix your own red or
    conflicting PRs first. No local Gradle; CI is the test run." Parallel builds have crashed this
    laptop.

11. **Stop rules for unattended sessions.** Text: "The same PR red twice: add `blocked` and move on.
    Never retry a rejected push. End by listing the PRs opened and merged."

12. **Replace the Monday and Friday meetings.** The owner has no fixed schedule. Text: "Each Lead
    session starts with the up-front questions and ends with a five-line digest in chat. The sweep
    runs every ten merges and at each release."

13. **Cap what a cold agent reads.** The playbook and role files have no limit. Put `STATE.md`'s
    lines at the top of `AGENTS.md`; the playbook at most 150 lines and a role file at most 40,
    enforced by `check_docs.py`.

14. **Name fragments `changes/<issue>.md`.** The PR number does not exist before the PR is opened.

15. **Bring it in line with the owner's answers.** Remove cloud sessions (G-13), the licence
    question (F-11) and the per-task model tags.

## Cut list

- The postmortem and runbook templates, `adr/`, `CONTRIBUTING.md`. Keep `decisions.md`.
- The design doc for changes over 400 lines. Keep it for red-risk work only.
- The four Friday numbers, the `this-week` label, the per-release retro.
- The `reviewed:` header and its 45-day warning.
- The `nit:`, `suggestion:` and `question:` prefixes, the kind labels, `roles/builder.md`. Keep `blocking:`.

## Two places where the Lead disagrees with the review

1. **Keep three runbooks**, even though the template is cut: cutting a release, applying a
   migration, and deleting an account by email. The owner is not an experienced engineer and each
   of these has already gone wrong once or has never been done. They are procedures, not ceremony.
2. **Keep a design doc for multi-currency and budgets** whatever their size. The owner asked for
   the process to look like a product company's, and those two change the data model.

The owner decides if he disagrees with either.
