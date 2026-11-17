# The multi-agent system: audit and a proposed replacement, 2026-11-17

How three AI agents (Claude, Codex, Antigravity) and one owner coordinate on this repository today,
what is wrong with it, and a concrete system to replace it. Written for the owner to decide on, and
then for other agents to implement step by step (section 10).

Nothing here is built yet. Several choices are the owner's and are listed in
[QUESTIONS-FOR-OWNER.md](QUESTIONS-FOR-OWNER.md), section G; where this page says "recommended", the
question file has the alternative.

## 1. What exists today

| Thing | Where it lives now |
|---|---|
| Who leads, who may merge | `AGENTS.md:14`, `docs/agents/work-split.md:24`, `docs/agents/codex-lead-2026-11-03.md`, two places in `docs/antigravity/HANDBOOK.md`, Claude's private memory on the laptop |
| What to work on next | `docs/production-readiness/README.md` §0 and §5, `docs/plan-1.6-to-launch.md`, the lanes in `work-split.md`, `HANDBOOK.md` §7 (waves 1–3), `codex-lead-2026-11-03.md` "The queue", `docs/claude-cloud/HANDOFF.md` streams A–C, `TODOS.md` |
| What was done | `CHANGELOG.md` (836 lines), "done in #NN" marks inside the work order, `solo-log.md` (46 KB), `codex-lead-report`, 89 comments on issue #188, PR titles |
| Rules for agents | `CLAUDE.md`, `AGENTS.md`, work order §1, `work-split.md`, `HANDBOOK.md` §2 and §6, `.agents/rules/*.md`, `.agents/hooks/guard.py`, the cloud handoff, Claude's private memory |
| Questions for the owner | Comments on #188, a section in `solo-log.md`, a section in `codex-lead`, `docs/handoffs/` (untracked, on the laptop only), the end of chat sessions |
| Owner decisions | Work order §10, `docs/handoffs/2026-10-24-owner-decisions.md` (untracked), chat history, Claude's memory |

Task ids in use: `T1.x`, `T2.x`, `T3.x`, `N0–N6`, `O1–O13`, `X1–X12`, `Y1–Y16`, `W1–W12`, `M1–M3`,
`F3–F7`, `D3–D4`, `R1–R3`, `SEC-`, `REL-`, `PLAY-`, `NOTIF-`, TODOS 2–10, and now `S-`, `P-`, `I-`,
`UX-`, `ENG-`. That is more than twenty namespaces for one backlog.

## 2. What is wrong with it

**1. The leader is written as prose with a date in it, in five places.** `AGENTS.md` still tells
Codex "you lead until 2026-11-06". Changing the leader means finding and editing every copy, so it
does not get done, so agents read expired orders. This is the root of "I should be able to change
the main agent whenever I want": today you cannot, cheaply.

**2. Rules and state are mixed in the same files.** The Antigravity handbook is 32 KB: permanent
rules (never merge, never touch Supabase), a description of the product, and a dated queue with
three "waves", two of them finished. Every session the agent pays to re-read all of it, and the
permanent parts get edited every time the queue changes.

**3. There is no single list of work.** Seven documents each hold part of the queue. An item is
"done" when someone remembers to write "done in #190" into a 43 KB file. Antigravity's handbook
says it itself: "PR titles are your progress". The pull requests are the only reliable record,
and the documents are a hand-kept copy of them that drifts.

**4. Every agent posts as you.** All PRs, commits and the 89 comments on #188 come from
`vaibhavcodesx`. Attribution depends on a `[claude]` or `[codex]` prefix that an agent has to
remember. GitHub's own protections (required review, code owners) cannot tell an agent from the
owner, so every hard stop is on the honour system.

**5. One issue is the message bus.** #188 is the log, the question box and the handoff channel for
everyone. It cannot be filtered, a question in comment 40 has no state (asked, answered, dropped),
and reading "the latest comment" silently skips anything before it.

**6. The changelog is a merge-conflict generator.** Every PR must add a line to the same section of
the same file. The handbook admits it: "Those conflicts are expected; whoever merges resolves them."
That is why batches of 19 PRs were merged through integration branches, which in turn means PRs
were not tested one by one against `main`.

**7. Knowledge lives where other agents can't see it.** Claude's memory folder on the laptop holds
real project rules (the merge policy, "smoke-test the release build", "the logo isn't final", the
low-memory Gradle rule). Codex, Antigravity and cloud sessions never see them. The owner-decisions
file is untracked. A fresh clone is missing part of the constitution.

**8. Nothing notices when a document goes stale.** `CLAUDE.md` says 1.0–1.4 shipped (it is 1.7).
`docs/architecture.md` says Gemini only. A migration file says "NOT APPLIED" and is live. The store
listing says sign-in is required. No check, human or automatic, compares docs with reality.

**9. No templates.** There is no PR template, no issue template, no labels in use, no CODEOWNERS.
Each agent's PR body follows whichever handbook it read.

**What is good and should be kept:** the hard stops are the same everywhere and have held; the
guard hook for Antigravity is a real control; "failing test first" and "say what to check on the
phone" are in every PR; risk colours (GREEN, YELLOW, ⚠) on Antigravity's items; small PRs; the
habit of making the safe choice and listing questions for later.

## 3. Design rules for the replacement

1. **One fact, one place.** If two files can disagree, one of them is deleted or generated.
2. **Rules are written for roles, never for a named agent.** Only one small file says which agent
   holds which role today.
3. **State lives where it updates itself.** The task list is GitHub Issues, because merging a PR
   closes its issue without anyone editing a document.
4. **Documents describe what is true now; history is archived, not edited.** A dated plan is
   moved to `docs/history/` when it is finished.
5. **Every rule that can be checked by a script is checked by a script.** The rest go in one
   playbook short enough to read every session.
6. **An agent starts cold.** The start page must get any agent, any model, to the right task in
   under five minutes of reading.

## 4. Roles

Three roles. An agent is assigned to a role in `STATE.md` (section 5) and nowhere else.

| Role | What it does | Who can hold it |
|---|---|---|
| **Lead** | Keeps the board in order, writes task briefs, reviews and merges, cuts releases, keeps the docs true, talks to the owner. One at a time. | Claude or Codex. The owner switches it by changing one line. |
| **Builder** | Takes its own issues from the board, designs and implements, opens PRs, reviews the Lead's PRs. | The main agent that is not Lead. |
| **Worker** | Takes only issues labelled for it, exactly as briefed. Never merges, never touches the protected paths, never designs. | Antigravity. |

The owner is not a role in the table. The owner decides product questions, approves anything marked
owner review, tests on the phone, and is the only one who changes `STATE.md`'s lead line (or tells
an agent to).

**Why Antigravity is never Lead:** it has the largest quota and the least judgement of the three.
Use it for volume: tests, mechanical refactors, string moves, doc sweeps, accessibility passes,
first drafts of large mechanical changes that a main agent then reviews. Use the stronger of its
two models for anything that touches product code and the faster one for tests and docs.

**Who merges what**

| Risk | What it covers | Who merges |
|---|---|---|
| `risk:green` | Tests, docs, comments, string resources with no wording change | The author's role, once CI is green. The Lead for Worker PRs. |
| `risk:yellow` | Product code with no schema, sync, auth or server change | The **other** main agent reviews, the Lead merges. Never the author alone. |
| `risk:red` | Anything under `supabase/`, RLS, auth, security, a Room schema change, `.github/`, signing, releases, dependencies with a major bump | Owner only, after a review by the other main agent. |

Cross-review between Claude and Codex is the cheapest quality control you have: two different
models rarely make the same mistake.

## 5. The files

```
AGENTS.md                 The one start page. Every agent reads it first. 80 lines at most.
CLAUDE.md                 Three lines: "read AGENTS.md", plus anything true only for Claude Code.
README.md                 For people on GitHub: what the app is, how to build, where the docs are.
PRD.md                    What the product is and the rules that hold. Changes only with an owner decision.
CHANGELOG.md              Released versions only. Generated at release time.
changes/                  One small file per PR for unreleased changes (see section 7).

docs/
  agents/
    STATE.md              Who is Lead, Builder, Worker; the current goal; frozen areas. 30 lines at most.
    PLAYBOOK.md           The rules for everyone: hard stops, the task loop, PR rules, testing rules.
    roles/lead.md         The Lead's duties and checklists.
    roles/builder.md
    roles/worker.md       What replaces the Antigravity handbook's rules sections.
    briefs/TEMPLATE.md    How the Lead writes a task for the Worker.
  product/
    roadmap.md            Now / Next / Later, one line each, linking to milestones.
    decisions.md          Every owner decision, numbered and dated, newest first. Append-only.
    glossary.md           The words the app uses (friend, group, entry, settle up).
  engineering/
    architecture.md       How the code works now.
    testing.md            How to test here, including the known local failures.
    release.md            The release checklist.
    operations.md         Supabase, secrets, backups, incident plan.
  designs/                One file per feature, with a status line: draft, approved, shipped.
  audits/                 Dated audits. Read-only once merged.
  legal/  store-listing/  As now.
  history/                Everything finished or superseded. Agents do not take tasks from it.
```

**`STATE.md` in full** (this is the whole design of "change the leader any time"):

```markdown
# State
lead: claude            # claude | codex
builder: codex
worker: antigravity
since: 2026-11-18
goal: India closed test. Milestone "Closed test".
frozen: supabase/, .github/ (owner review only)
release: 1.7.0 is on phones; next is 1.8.0
notes:
- Codex is out of quota until 2026-11-22: Lead also builds.
```

To switch leaders the owner says "Codex leads from now" to either agent. That agent opens one PR
that changes `lead:` and `builder:` and `since:`, and adds a handover comment to the open
milestone: PRs in flight, what it was in the middle of, anything it does not trust. Nothing else in
the repository changes, because nothing else names an agent.

**Every live document starts with three lines**, so staleness can be checked:

```markdown
<!-- status: live | owner: lead | reviewed: 2026-11-18 -->
```

## 6. The board: GitHub Issues

One issue per task. The board is the issue list; nobody maintains a queue document.

**Labels**

| Group | Values |
|---|---|
| Who | `for:lead`, `for:builder`, `for:worker`, `for:owner` |
| Risk | `risk:green`, `risk:yellow`, `risk:red` |
| Priority | `p0` (blocks the launch), `p1`, `p2` |
| Kind | `bug`, `feature`, `chore`, `docs`, `security` |
| State | `blocked`, `needs-owner` |

"Ready", "in progress" and "done" need no label: an open issue with no linked PR is ready, one with
a draft PR is in progress, a closed one is done.

**Milestones** are the plan: "Closed test", "Production launch", "After launch", "International".
`docs/product/roadmap.md` is ten lines pointing at them.

**The owner's inbox** is one saved search: `is:open label:needs-owner`. A question is an issue with
that label and the options written out. Answering it and removing the label is the whole process.
No more questions buried in comment 61.

**What each agent runs at the start of a session**

```bash
gh issue list --label for:worker --state open --search "sort:created-asc -label:blocked no:assignee"
```

(with its own role's label). It takes the top `p0`, then `p1`. Claiming is opening a **draft PR**
that says `Closes #N` in its body, straight away, before the work is finished. That is the lock:
two agents cannot both open a PR for the same issue without seeing each other's.

**The audit backlog moves here.** Each finding in this folder that is to be fixed becomes an issue
titled with its id ("S-04: column-level grants on profiles") and a link to the report. The old ids
(`T2.4`, `W6`, `X7`…) are not migrated: what is still open is re-filed under the new ids, and the
rest is history.

### Task briefs for the Worker

The Worker's model is weaker and starts cold, so a brief has to be complete enough to be done
without judgement. The issue template enforces the headings:

```markdown
## Goal            One sentence, in terms of what a user or developer gets.
## Files           The exact files to change. "Nothing else" is the default.
## Do              Numbered steps.
## Don't           What is out of scope. Always includes: no renames, no reformatting.
## Done when       The test that must pass, by name, and what it asserts.
## Check on phone  What the owner should look at, or "nothing".
## If stuck        Park it: comment what you found, add `blocked`, take the next issue.
```

Rules for the Lead when writing them: only `risk:green` and `risk:yellow`; never two open Worker
issues that touch the same file; no more than ten open at once; a Worker PR waiting more than three
days is the Lead's problem, not the Worker's.

## 7. The loop, and the rules for pull requests

**One issue, one branch, one PR.**

- Branch: `<agent>/<issue-number>-<short-name>`, for example `codex/312-delete-in-edit-sheet`.
- Title: `[agent] ID or #N: what changed for the user`, for example `[codex] UX-06: delete an expense from its edit sheet`.
- Open as a **draft** when the work starts. Mark ready when CI is green.
- Size: aim for under 400 changed lines outside tests. Larger changes are split, or stacked with
  "merge after #N".
- A bug gets its failing test in the first commit, and CI is allowed to go red for the stated reason.
- Squash merge only. The branch is deleted on merge.
- No integration branches except for a release. If PRs conflict, the Lead merges them one at a
  time, rebasing the next; this is what the changelog fix below makes possible.

**The PR template** (`.github/pull_request_template.md`):

```markdown
Closes #

## What changed and why
## How it was tested          Test names. CI link if it went red first on purpose.
## Check on the phone         Steps, or "nothing to check".
## Risk                       green | yellow | red, and why.
## Docs
- [ ] `changes/<pr>.md` added
- [ ] No live doc is made false by this change (or it is updated here: which one)
- [ ] No new rule for agents (or `PLAYBOOK.md` is updated here)
## Questions for the owner    Each also filed as a `needs-owner` issue.
```

**The changelog without conflicts.** Each PR adds one file, `changes/<pr-number>.md`:

```markdown
kind: added | changed | fixed | removed | security
A group's creator can remove a member.
```

Only changes a user would notice get one; tests and refactors get `kind: internal` and are left out
of the published notes. At release the Lead runs a script that folds the files into `CHANGELOG.md`
under the new version and deletes them. No two PRs ever edit the same line again.

**Protected paths, enforced.** A CI job fails any PR that touches `supabase/`, `.github/`,
`app/schemas/`, signing or `STATE.md`'s lead line unless it carries the label `owner-approved`,
which only the owner adds. This turns the hard stops from a sentence in a handbook into a red
check. If the agents get their own GitHub account (question G-03), this becomes a real CODEOWNERS
review instead.

**When not to open a PR:** never for a question (file an issue), never to "save progress" on
something half-working without the draft flag, never to change two unrelated things.

## 8. Keeping everything true

**In every PR** the author answers one question from the template: "what document does this change
make false?" and fixes it in the same PR. This is the main mechanism; the rest are nets.

**A docs check in CI** (`scripts/check_docs.py`, about a hundred lines):
- every relative link in a live document resolves;
- `AGENTS.md` is under 80 lines and `STATE.md` under 30;
- `STATE.md` parses and its roles are known agents;
- no live document outside `STATE.md` contains "Claude leads", "Codex leads" or a date followed by
  "until";
- every live document has the status line, and a warning (not a failure) when `reviewed:` is more
  than 45 days old;
- nothing in `docs/history/` is linked from `AGENTS.md` or the playbook as a thing to read.

**The Lead's weekly sweep** (a recurring issue, `for:lead`, 20 minutes):
1. Close issues whose PRs merged without closing them; re-label anything stuck.
2. Read `STATE.md`, `roadmap.md` and the PRD against the last week's merges; fix what drifted.
3. Move finished designs to `shipped`; move superseded pages to `docs/history/`.
4. Triage Dependabot: merge green patch updates, file an issue for anything else.
5. Delete merged branches the setting missed.

**At every release** the Lead: folds `changes/` into `CHANGELOG.md`, bumps the version, updates
`STATE.md`'s release line, writes the phone checklist, re-reads `architecture.md` and the store
listing against what shipped, and archives the previous release's checklist.

**Archiving rule.** A document is moved to `docs/history/` when its job is done: a plan whose items
all closed, a handoff older than the newest one, an audit whose findings are all filed as issues or
closed. It gets one line at the top saying what replaced it. Nothing is deleted. `docs/history/`
has an index and agents are told not to take work from it.

**Shared memory.** Anything an agent learns that the next session needs goes into the repository:
a rule into `PLAYBOOK.md` or `testing.md`, a decision into `decisions.md`. An agent's private memory
may keep only what is about the owner's machine or preferences. Claude's current project memories
are moved across as part of the migration (step 4 below).

## 9. Working like a product company

The owner's instruction (2026-11-17): the review system, the document formats and the rest should
be what real software companies use. Sections 4 to 8 already are, under plain names. This section
makes the mapping explicit and adds the pieces that were missing.

| A software company has | Here |
|---|---|
| A product manager who owns the "what" and "why" | The owner. `PRD.md`, `roadmap.md`, `decisions.md`. |
| A tech lead | The Lead role. |
| Engineers | Builder and Worker. |
| A ticket tracker (Jira, Linear) | GitHub Issues, labels and milestones (section 6). |
| Sprints | One-week cycles (below). |
| Trunk-based development, short-lived branches, CI gates | Section 7. |
| Code review with required approvals | The risk table in section 4, plus the standards below. |
| Design docs or RFCs before large work | `docs/designs/`, with the template below. |
| Architecture decision records | `docs/product/decisions.md` and `docs/engineering/adr/`. |
| A definition of done | Below, and in the PR template. |
| Release notes, semantic versions, a release checklist | `changes/`, `CHANGELOG.md`, `engineering/release.md`. |
| Runbooks and blameless postmortems | `engineering/operations.md`, `docs/postmortems/`. |
| `CONTRIBUTING.md`, `SECURITY.md`, `CODEOWNERS` | Added in the migration. |

### Documents and their formats

Five kinds of document, each with one template in `docs/templates/`. An agent never invents a new
kind; if something doesn't fit, it is probably an issue, not a document.

**1. PRD** (`PRD.md`, one for the product; large features get a short one inside their design doc).
Headings: Problem · Who it is for · Goals and how we'll know · Non-goals · Requirements (must,
should, won't) · Open questions. Changed only by a PR that cites a decision number.

**2. Design doc / RFC** (`docs/designs/NNN-name.md`). Required before any `risk:red` work and any
change over roughly 400 lines. Headings: Status (draft, in review, approved, shipped, dropped) ·
Author and reviewers · Context · Goals and non-goals · Proposal · Alternatives considered · Data and
migration · Security and privacy · Testing · Rollout and rollback · Open questions. It is reviewed
as a PR of its own; code starts only when the status is `approved`.

**3. Decision record** (ADR). One short entry per decision, numbered, never edited afterwards; a
changed decision is a new entry that says which one it replaces. Product decisions go in
`docs/product/decisions.md` (the current D1–D5 become its first entries); technical ones in
`docs/engineering/adr/NNNN-title.md`. Headings: Date · Status · Context · Decision · Consequences.
This is the fix for decisions living in chat history and one agent's memory.

**4. Runbook** (`docs/engineering/operations.md` and one file per procedure). Headings: When to use
it · Before you start · Steps (numbered, copy-pasteable) · How to check it worked · How to undo it.
Needed now for: cutting a release, applying a migration, deleting an account by email, rotating a
key, restoring from backup.

**5. Postmortem** (`docs/postmortems/YYYY-MM-DD-title.md`), written after anything that reached a
user: a broken release, lost data, a sign-in outage. Headings: Summary · Impact · Timeline · Root
cause · What went well · What didn't · Actions (each one an issue). Blameless: it names the gap in
the system, not the agent. The three releases that shipped with sign-in broken deserved one; the
lesson exists today only as a line in Claude's memory.

### Code review standards

Written into `PLAYBOOK.md` so every reviewing agent applies the same bar.

**What a reviewer checks, in this order:** does it do what the issue asked and nothing else; is it
correct, including the failure paths; is there a test that would fail without the change; does it
touch a protected path or leak anything; is it readable; only then style.

**How comments are written.** Each comment starts with its weight, so the author knows what must
change:
- `blocking:` must be fixed before merge.
- `question:` the reviewer needs an answer before deciding.
- `suggestion:` better if done, the author decides.
- `nit:` trivial; never a reason to hold a PR.

**How a review ends.** One of: Approve · Approve with suggestions · Request changes (at least one
`blocking:`). The reviewer says what they did **not** check (for example "not run on a phone").

**Rules.** The author never reviews their own yellow or red PR. A review is done within one
working session of being asked, or the reviewer says it can't. Disagreements between the two main
agents go to the owner as a `needs-owner` issue with both positions in three lines each, not into a
long thread. A reviewer does not rewrite the PR; it comments, and the author fixes.

### Definition of done

A task is done when all of these are true, and not before:

1. The acceptance test named in the issue passes, and CI is green on the PR's last commit.
2. It was reviewed at the level its risk requires.
3. `changes/<pr>.md` exists if a user would notice the change.
4. No live document is made false by it.
5. The PR says what the owner should check on the phone.
6. It is merged, and the issue is closed by the merge.

"The PR is open" is not done. "CI was green two commits ago" is not done.

### The weekly cycle

Solo developers skip this and lose track; it takes half an hour a week.

- **Monday, planning (owner and Lead, 15 minutes).** The Lead proposes the week from the top of the
  milestone: at most what the three quotas can finish. The owner agrees or reorders. The week's
  issues get the milestone's `this-week` label.
- **During the week.** Agents work the board. Questions become `needs-owner` issues, never a stop.
- **Friday, review (Lead writes, owner reads, 15 minutes).** A short note as a comment on the
  milestone: shipped, slipped and why, what the owner needs to test, the answers still needed. This
  replaces every "session report" and "handoff" file.
- **After each release, a retro of five lines:** what to keep, what to change in the playbook. A
  playbook change is a PR like any other.

Four numbers the Lead puts in the Friday note, because they show whether the system is working:
PRs merged, median hours from "ready" to merged, PRs that needed a follow-up fix, and open
`needs-owner` issues older than three days.

### Files a public repository is expected to have

`CONTRIBUTING.md` (for agents and people: points at `AGENTS.md` and the playbook), `SECURITY.md`
(how to report a vulnerability privately: turn on GitHub's private vulnerability reporting, since
the repo is public), `CODEOWNERS`, `LICENSE` (the repo is public with no licence, which means
nobody may legally reuse it; decide whether that is what you want), issue and PR templates, and a
README written for a visitor.

## 10. Migration, as tasks other agents can do

Each step is one small PR. Steps 1–3 need the owner's answers in section G of the questions file
first. Suggested doer in brackets.

| # | Task | Risk |
|---|---|---|
| 1 | Create `docs/agents/STATE.md`, `PLAYBOOK.md`, `roles/*.md` from the rules that already exist (work order §1, work-split, handbook §2 and §6, the cloud handoff's hard stops). No new rules, only de-duplication. Rewrite `AGENTS.md` to 80 lines and `CLAUDE.md` to a pointer. [Opus] | green |
| 2 | Labels, milestones, issue templates (task, bug, question for owner), PR template. [Sonnet] | green |
| 3 | File the open audit findings and anything still open in the old queues as issues with the new labels. Close #188 with a pointer. [Sonnet, reviewed by the Lead] | green |
| 4 | Move Claude's shared project memories and the untracked owner-decisions file into `docs/product/decisions.md`, `PLAYBOOK.md` and `engineering/testing.md`. [Opus, with the owner] | green |
| 5 | `changes/` directory, the fold script, a CI check that each PR has a fragment. Convert the current Unreleased section once. [Sonnet] | yellow |
| 6 | Restructure `docs/` as in section 5; move finished pages to `docs/history/` with an index. One PR, moves only, no edits. [Sonnet] | green |
| 7 | `scripts/check_docs.py` and its CI job. [Sonnet] | green |
| 8 | Protected-paths CI job and the `owner-approved` label; branch ruleset on `main` (owner clicks). [Opus] | red |
| 9 | Rewrite `README.md` for people; refresh `architecture.md`. [Opus] | green |
| 10 | Update Antigravity's `.agents/rules` and guard to the new names and the issue-based queue. [Opus] | red |
| 11 | `docs/templates/` (PRD, design doc, decision record, runbook, postmortem); move D1–D5 into `decisions.md`; add the review standards and the definition of done to `PLAYBOOK.md`. [Opus] | green |
| 12 | `CONTRIBUTING.md`, `SECURITY.md`, `CODEOWNERS`, a licence decision, private vulnerability reporting (owner clicks). [Sonnet] | green |
| 13 | Runbooks for: release, applying a migration, account deletion by email, key rotation, restore from backup. [Opus, with the owner] | green |

Do steps 1 and 2 first, in that order, and stop all other agent work until they merge: for one day
there is exactly one set of instructions being written, and nobody is reading the old ones.

## 11. What this does not solve

- **Quota.** It makes each session cheaper to start (one short page and a filtered list, in place
  of 200 KB of handbooks) but it cannot make three subscriptions into a team of ten.
- **Judgement.** A good brief limits a weak model's damage; it does not make its design choices
  good. Keep design with the main agents and the owner.
- **The phone.** Nothing here replaces the owner testing on a device. The template only makes sure
  each PR says what to look at.
- **A lead that goes wrong.** If the Lead merges something it should not, the protections in
  section 7 catch the protected paths only. The cross-review rule for yellow changes is the net
  for the rest, and it depends on the other agent having quota that week.
