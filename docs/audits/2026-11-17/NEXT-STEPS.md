# Next steps for the agents that pick this up

Written 2026-11-17 at the end of the audit session. Read [README.md](README.md) and
[OWNER-ANSWERS.md](OWNER-ANSWERS.md) first; the answers override the reports where they differ.
Claude is Lead. No Claude cloud sessions. The owner tests on the phone; no local Gradle builds.

Hard stops are unchanged: anything under `supabase/`, RLS, auth, security, Room schema, `.github/`,
signing or releases is opened as a PR and left for the owner. Nothing is applied to the live
Supabase project by an agent.

## Not done in the audit session

- No GitHub repository settings were changed (H-01 to H-05 are approved; see package 0).
- The owner's local `main` was not updated (H-08 approved).
- No issues were filed; the backlog is still the tables in `README.md`.
- The agent-system proposal (`06-agent-system.md`) is a first draft. The owner approved it in
  outline **on condition that it is reviewed before it is built** (G-01).
- Not audited in depth: backup restore, `GroupSyncEngine`, `LocalLedgerSyncEngine`,
  `ReceiptSyncEngine`, `AccountSheet`, the widgets; the deployed Edge Function was not compared
  byte for byte with the repo.

## Owner actions that unblock everything (ask for these first)

1. Delete the September Gemini key (the local security report's S-02 has the steps) and the unused
   `GEMINI_API_KEY` Actions secret.
2. Confirm or correct [OWNER-ANSWERS.md](OWNER-ANSWERS.md) section 3 (the 1.8/1.9 split, UK later)
   and section 4.
3. Create the bot GitHub account (G-03) and add it as a collaborator.
4. Find the release keystore passwords and make an off-laptop copy (C-15). A lost upload key can be
   reset through Play support once Play App Signing is on; the passwords cannot be recovered.

## Work packages, in order

Each is one agent session and one or a few small PRs. Model in brackets.

**0. Housekeeping (Sonnet, 1 session).** Repo settings the owner approved: squash-only, delete
branches on merge, Dependabot alerts and security updates, private vulnerability reporting. Check
whether CI runs on docs-only PRs **before** making the three checks required, or docs PRs will be
stuck forever. Delete branches of merged PRs only. Fix the local `main` (move the untracked
`tools/` and `docs/audits/` aside, pull, restore what isn't upstream). Close #139 and #276 with a
comment and re-file them.

**1. Agent system, reviewed (Opus, 1–2 sessions).** Review `06-agent-system.md` critically against
the owner's additions: token efficiency (measure the start-of-session reading in tokens and set a
budget), Antigravity running sub-agents, unattended sessions, no fixed weekly day. Publish v2, get
the owner's yes, then do migration tasks 1, 2 and 11 from its section 10. All other agent work
stops for that day.

**2. File the backlog (Sonnet).** After package 1: one issue per open finding, labelled, in
milestones `1.8.0`, `1.9.0`, `After launch`.

**3. 1.8.0: hardening and trust (Opus designs and reviews, Codex and Antigravity build).**
- Server, owner review: S-01 AI proxy limits and a project ceiling in the caps table, with a plan
  per user (`free`, `friends`); S-04 column grants; S-05 migration history repair and a staging
  project; S-06 minimum version and a sync catch-all; S-03 remove a member, invite cap of 20,
  "X joined"; S-08 deletion runbook.
- Free-plan mitigations: photos to about 200 KB with a per-user cap; a dump before each migration.
- UX: UX-06 delete in the edit sheet; UX-02; UX-31; UX-21; UX-22; group expenses tappable; UX-19
  and UX-23 edit group and friend; guest mode repair and "move into my account" (D-01, A-09);
  Settings split (D-04); "AI" wording with no vendor line under quick add, vendors kept in the
  notice and policy; UPI reassurance text; rename "IOU"; general default categories; reminder caps.
- New features the owner wants before launch: **budgets per category** (D-18), a **salary/income
  setting** (D-10), a **skippable tutorial** (D-05), app lock and hidden widget amounts (C-06, C-07),
  pull-based notifications N2 (D-17). Each needs a short design doc first.
- Bill reading speed and cancel (D-21): measure where the time goes before changing anything.
- Store listing and README rewrites (P-01, F-10); CI runs the SQL and Deno tests (ENG-21).
- Then: the owner opens the Play account, uploads 1.8.0, starts the closed test.

**4. 1.9.0: country and currency (Opus designs, then build).** `docs/designs/multi-currency.md`
first, approved by the owner: currency per user and per group, country at first run, payment links
(PayPal, Venmo) where there is no UPI, statement import India-only, Hinglish moved to `en-IN`,
AI caps reset per user's day. Update `PRD.md` in the same PR as the design. Released into the
running closed test; US, UAE and Canada are added to the country list after it. UK waits for
funding (paid AI tier and a UK representative).

**5. After launch.** `MainViewModel` split (F-01), test clean-up (F-04, F-05), sync of phone-only
data (D-20, for 1.9 or 1.10), invite links that open the app on a bought domain and add-by-QR
(B-08), the admin page, push notifications.

## A warning about the timeline

The owner wants to be on Play in two to four weeks. Packages 3 and 4 together are more like six to
ten weeks on three subscriptions, and the 14-day closed test starts only after the account is
opened. The split above (1.8.0 without currency, 1.9.0 with it, built during the test) is the
fastest honest path. If the owner insists on currency in the first Play build, say so plainly and
re-plan; do not quietly cut tests or review to make the date.
