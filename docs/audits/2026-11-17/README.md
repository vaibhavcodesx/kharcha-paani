# Launch audit, 2026-11-17

A full review of Kharcha Paani before its first Play Store release, asked for by the owner: security,
feature gaps, UX, maintainability, and (added mid-audit) readiness for an international launch.

**What was audited:** `origin/main` at `8839b3f` (1.7.0 plus the item-split PR #289), the live
Supabase project (read-only), the Edge Function, the public site pages, the GitHub repository
settings and the last 40 pull requests.

**What was not:** the app was never run. There is no emulator on the owner's machine and no Gradle
build was made, so everything here comes from reading code, querying the live database's catalog and
checking settings. Each report ends with what needs a phone or a build to confirm.

## The reports

| # | Report | Findings |
|---|---|---|
| 1 | Security (`01-security.md`) | **Not in the public repo** (owner answer C-02). It is in the owner's local folder `docs/audits/2026-11-17/` and goes back in once its High items are fixed. Ids `S-01` to `S-15`. |
| 2 | [Play Store readiness](02-play-store-readiness.md) | 5 blockers for a first review, 7 High |
| 3 | [International readiness](03-international-readiness.md) | 5 build items, legal notes by market |
| 4 | [UX, UI and feature gaps](04-ux-and-feature-gaps.md) | 10 High, 28 Medium, 21 Low |
| 5 | [Engineering and maintainability](05-engineering-and-maintainability.md) | 7 High, 18 Medium, 10 Low |
| 6 | [The multi-agent system](06-agent-system.md) | 9 problems, a replacement system, 13 migration tasks |
| | [Questions for the owner](QUESTIONS-FOR-OWNER.md) | Every open decision and permission |
| | [Owner answers](OWNER-ANSWERS.md) | The answers of 2026-11-17, the decisions made on the owner's behalf, and the conflicts to resolve. **The current decision record.** |
| | [Next steps](NEXT-STEPS.md) | The work packages for the next agents, in order |

**Next step:** the owner answers `QUESTIONS-FOR-OWNER.md` (the starred ones first). Work that
depends on an unanswered question waits.

Ids are stable and unique across the folder: `S-` security, `P-` Play, `I-` international, `UX-`,
`ENG-`. Use them in branch names and PR titles.

## Verdict

**Is it ready for the Play Store? Not yet, and the reasons are mostly not code.**

The app is better built than its history suggests. Data access is properly locked down, the money
maths is exact, the copy is good, and nothing found here leaks one user's data to another. A
stranger installing it today would not lose money or data because of a bug this audit found.

What stands between it and a public launch:

1. **No developer account exists.** Play requires a closed test with 12 testers for 14 days before
   production. That clock has not started, and it is longer than any fix below.
2. **The app cannot be operated safely yet.** It runs on a free database plan with no backups and
   1 GB for everyone's bill photos, every migration is tested on production, there is no way to
   force an old version to update, and the AI endpoint needs tighter limits
   (see the security report).
3. **A new user will not find half the app,** and can create things they cannot later change:
   groups, friends, and an expense they cannot delete from where they edit it.
4. **The codebase is at the point where it starts to slow down.** Two files carry everything, and
   the instructions agents read contradict each other.

**Is it ready for an international launch? No.** It is an India-only product by design: rupees,
UPI, Hinglish, Indian bank statements. That is several weeks of feature work plus legal work, and
Russia in particular is a poor choice for revenue. See report 3.

## The backlog, in order

Sizes: S = hours, M = a day or two, L = a week or more. "Owner" means only the owner can do it.
**OR** = owner-review hard stop (RLS, auth, security, `supabase/`): open the PR, do not merge.

### Now (owner, this week, none of it is code)

| Do | Why | Ref |
|---|---|---|
| Open the Play developer account; decide personal vs organisation first | Starts the 14-day clock | Play report |
| Confirm the September Gemini key is deleted at Google; delete the unused `GEMINI_API_KEY` GitHub secret | It shipped inside 1.0–1.5 | S-02 |
| Protect `main` (ruleset, required checks), turn on Dependabot alerts, auto-delete branches | Public repo, several agents merge | S-07 |
| Go through the Supabase dashboard list | SQL can't see these | S-14 |
| Decide: Supabase Pro now, or smaller photos and a cap until then | No backups, 1 GB storage | S-15 |
| Decide what "international" means and update `PRD.md` | It contradicts two standing rules | Report 3 |

### Before the closed test goes to strangers

| Id | What | Size | Notes |
|---|---|---|---|
| S-01 | AI proxy: server decides the feature, per-feature limits, a global ceiling | M | **OR** |
| S-06 | Minimum-version switch, and a catch-all in `SyncWorker` | M | Needed before S-01 changes the contract |
| S-04 | Column-level grants on `profiles` | S | **OR** |
| S-05 | Repair the migration history, fix the stale "NOT APPLIED" header, staging project | S + M | **OR** |
| ENG-21 | CI runs the SQL and Deno tests | M | Unblocks every later server change |
| P-01 | Store listing: sign-in is optional, Groq, voice, "shares" wording | S | Docs only |
| UX-06 | Delete button in the edit sheet | S | |
| UX-02 | Remember a declined AI notice | S | |
| UX-31 | Confirm before switching to the local ledger | S | |
| UX-21 | Show the balance when leaving a group | S | |
| UX-22 | "Only on your phone" / "Everyone in X sees this" on the entry sheet | S | |
| ENG-28 | One `docs/START-HERE.md`; archive the rest | M | Do this before handing work to any agent |

### During the 14-day closed test

| Id | What | Size | Notes |
|---|---|---|---|
| S-03 | Remove a member; invite attempt limit; "X joined" notice | M | **OR** |
| S-08 | Account deletion in one Edge Function; runbook for the email path | M | **OR** |
| S-09 | Opt-in app lock; hide amounts on widgets | M | Also UX-32 |
| UX-19, UX-23 | Edit group, edit friend | M | Remove-member is S-03 |
| UX-01 | Move a guest ledger into an account | M | |
| UX-05 | Split the Account sheet; name it Settings | M | |
| UX-03 | Three first-run tips | M | |
| UX-20 | Payer's name in words on group rows | S | |
| UX-33 | "Not synced" mark on feed rows | S | T2.4's remaining half |
| UX-39 | Cap reminders | S | |
| P-03 | Microphone rationale before the system dialog | S | |
| P-11 | Clear the pre-launch report: accessibility scanner, #139 and #276 | M | |
| ENG-19 | Regenerate the lint baseline and read what is left | S | |
| ENG-22 | Certificate fingerprint check and release lint in `release.yml` | M | |
| ENG-23, ENG-32 | Fresh-clone debug build; rewrite the README | S | |

### After launch

- **Maintainability first:** ENG-01 steps 1–2 (extract controllers, then the entry flow), ENG-02,
  ENG-16, ENG-17, ENG-07, ENG-03. Each makes every later feature cheaper.
- **Then product:** notifications N2–N4 (UX-57), budgets and trends (UX-29), light theme, the rest
  of the Medium and Low UX items.
- **Then international,** starting with a design note for currency (I-01). Not before the India
  launch is stable.

## For the agent picking this up

1. Read `CLAUDE.md` and `PRD.md`, then this page. Treat the work order
   (`docs/production-readiness/README.md`) as history for anything this audit supersedes; its §10
   owner decisions still hold, except where the owner changes them for the international plan.
2. One finding, one branch, one PR, named with the finding's id (`claude/s-04-profile-grants`).
3. Re-verify the finding on current `main` before changing anything. Every finding says how it was
   checked. Line numbers drift.
4. Bugs get a failing test first. Anything marked **OR** is opened and left for the owner.
5. Nothing under `supabase/` is applied to the live project by an agent.
6. The owner tests on a phone. Each PR says what to check there.

## Questions for the owner

The full list, with options and a line for each answer, is in
[QUESTIONS-FOR-OWNER.md](QUESTIONS-FOR-OWNER.md). The eight that came out of the first five reports:

1. **Personal or organisation Play account?** It cannot be switched later, and a personal account
   shows your address once you sell anything.
2. **Supabase Pro at launch ($25 a month), or cap photos until then?**
3. **Move Gemini to the paid tier before launch?** Recommended in the work order already; required
   if any user is in the EU or UK.
4. **Which countries first?** The recommendation here is India only at launch, then one
   English-speaking market with a large Indian diaspora, and not Russia.
5. **Is `com.vaibhav.kharchapaani` the application id you want forever?** It is permanent after the
   first upload.
6. **Should group creators be able to remove members?** It is a product decision as well as a
   security one (S-03).
7. **May the earlier coordination docs be archived** into `docs/history/` (ENG-28)?
8. **Was the September Gemini key deleted?** (S-02)

## Housekeeping found along the way

- The owner's local `main` is 143 commits behind GitHub, and its untracked `tools/` folder overlaps
  files that now exist upstream, so `git pull` there will probably refuse until `tools/` is moved
  aside.
- Open PRs: #252 (Codex draft, conflicts), #139 (Codex draft, conflicts), #276 (Antigravity,
  conflicts), four Dependabot PRs of which two fail CI. Decide each: rebase or close.
- Issue #188 is the only open issue and is used as a progress log.
