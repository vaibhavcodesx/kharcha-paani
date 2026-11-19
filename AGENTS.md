<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Kharcha Paani: start here (every agent, every session)

Android app (Kotlin, Compose, Room, Supabase, AI via an Edge Function). Package
`com.vaibhav.kharchapaani`. The owner is not an engineer: keep him out of the loop, make safe
choices, and put real questions on the board.

## If the owner says "start" (or "go", "continue", or names an issue)

1. Read `docs/agents/STATE.md` (30 lines). Find your name: that is your role (lead, builder, worker).
2. Read `docs/agents/PLAYBOOK.md`, then `docs/agents/roles/<your role>.md`. Nothing else yet.
2a. Read your inbox, `docs/agents/inbox/<your name>.md` (claude, codex, antigravity). It is the only way the Lead
   messages you: "improve PR 123: ...", "continue issue 45: ...". Do those first. When one is done, delete
   its block and say so in the PR or issue.
3. Take work: run the start query below, take the first line, read its body with
   `gh issue view N --json body --jq .body`. The body is the brief. Never read comments, never run
   `gh issue list` without `-L`.
4. Claim it: `gh issue edit N --add-label wip:<you>` then `gh issue view N --json labels`. If two
   `wip:` labels exist, the alphabetically later agent removes its own and takes the next issue.
5. Work in your own worktree and branch, open a draft PR early, finish, hand over. Loop to step 3.

```bash
# role = lead | builder | worker ; try p0, then p1, then p2 until one prints
gh issue list -l for:<role> -l p0 -m 1.8.0 -S '-label:blocked -label:needs-owner -label:"wip:claude" -label:"wip:codex" -label:"wip:antigravity"' -L 5 --json number,title --jq '.[]|"\(.number) \(.title)"'
```

Empty for every priority: say so in one line and stop. Do not invent work.

## If the owner says "read <file>" or gives a task directly

Do that, still under the rules in `PLAYBOOK.md`. If the task is also an issue, claim it.

## Hard stops (any agent, always)

- Nothing is applied to the live Supabase project, deployed to it, or changed in the Play Console, Google
  Cloud, GitHub settings or secrets by an agent. Staged SQL and function code in a PR is fine.
- No secrets in the repo, chat, PR text or logs. Never open `*.jks`, `keystore.properties`, `.env`.
- Do not weaken a test to get green. Do not reuse or animate the launcher logo (it is not final).
- Red work (`supabase/`, RLS, auth, security, `app/schemas/`, `.github/`, signing, releases, a major
  dependency bump) is only ever a PR; the Lead merges it after review (see `PLAYBOOK.md` section 3).
- This repository is public. Do not write a vulnerability's mechanism in a PR, issue or comment: say
  "hardening" and what is checked, and put the detail in the (private) local security report.

## Rules that save the most time

- No local Gradle builds on the owner's laptop (they have crashed it). CI on the PR is the test run.
- Git stores source with LF endings, but some working copies on Windows are CRLF (`git ls-files --eol <file>`
  shows `i/lf w/crlf`). Before committing run `git diff --stat`; a diff far larger than your change means the
  line endings changed. Fix it: `sed -i 's/\r$//' <file>`. Never rewrite a whole file.
- Use the Edit or Write tool for any text containing a backslash, not a shell heredoc.
- One issue, one branch (`<agent>/<issue>-<slug>`), one PR titled `[<agent>] <ID>: what changes for the user`.
- Add `changes/<issue>.md` (see `changes/README.md`), never edit `CHANGELOG.md` in a feature PR.
- The owner tests on his phone: end every PR body with "Check on the phone: ..." or "nothing".

Questions for the owner go on the board: `gh issue create -l needs-owner` with the options written
out and your safe default. Do not wait for an answer; take the next issue.

Memory: Claude, Codex and Antigravity keep nothing project-related in private memory. Rules go in
`PLAYBOOK.md` or `docs/engineering/testing.md`, decisions in `docs/product/decisions.md`.

Where things are: `PRD.md` (what the product is), `docs/product/decisions.md` (owner decisions),
`docs/engineering/` (testing, runbooks), `docs/audits/2026-11-17/` (the launch audit, read-only),
`docs/history/` (finished or superseded; never take work from it).
