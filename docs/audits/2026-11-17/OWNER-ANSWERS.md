# Owner answers, 2026-11-17

The owner answered [QUESTIONS-FOR-OWNER.md](QUESTIONS-FOR-OWNER.md) by voice on 2026-11-17. This
page records each answer against the question's id. The spoken numbering drifted from the file's in
places, so every line below is **my reading** of what was meant; anything I was unsure of is in
section 4 for the owner to correct. Where the owner said "you decide", the decision and its reason
are in section 2.

Until the agent-system migration creates `docs/product/decisions.md`, **this page is the decision
record**. It overrides the work order's §10 where they disagree (section 3 lists where).

## 1. Answers

### A. Launch and Play
| Id | Answer |
|---|---|
| A-01 | Personal developer account. |
| A-02 | Opens the account **after 1.8.0**, which is to be the near-production build. Not now. |
| A-03 | "You decide." → section 2. |
| A-04 | Not answered. About 4–5 people use the app today (I-03). |
| A-05 | **India only on day one. US and UAE about two weeks later**, then UK and Canada (E-02). The app must be multi-currency from the first Play release: country and currency chosen from the phone's locale or by asking, and the app adapts. |
| A-06 | 1.8.0 goes up first for testing; the final tested build is the first public release. Also asked how releasing to several countries works → section 5. |
| A-07 | Asked whether friends can simply install the Play version. → section 5. None of them use guest mode. |
| A-08 | "You suggest." → section 2. |
| A-09 | Sign-in stays optional. Guest mode needs work: there is no way to delete the guest ledger and return to the sign-in screen. |
| A-10 | Change the listing's sentence; the three split modes stay. |
| A-11 | Make sure nothing is cut off; proper tablet support after launch. |
| A-12 | Dark only. No light theme and no switch before launch. |

### B. Money and services
| Id | Answer |
|---|---|
| B-01 | **Stay on Supabase free** until there are more users or funding. The owner is a student with no budget. |
| B-02 | **Stay on the Gemini free tier.** Disclosed in the notice and policy; nothing is sold. |
| B-03 | No monthly budget for 2–3 months after launch. Plans to work on marketing, then try to raise funding, then buy the paid tiers. |
| B-04 | Caps are fine; Insights (20 a day) may change. |
| B-05 | Asked why it is needed. "You decide, but make it changeable." → section 2. |
| B-06 | Groq is on the free plan. That it keeps nothing is **assumed, not checked**. |
| B-07 | Firebase not checked. "You decide." → section 2. |
| B-08 | Will buy a domain (or use Vercel/Cloudflare hosting). Wants invite links that open the app and join the group when tapped in WhatsApp. |
| B-09 | Keep the current email for now; log a dedicated support address as a later task. |

### C. Security
| Id | Answer |
|---|---|
| C-01 | Recorded in the local security notes, not here. |
| C-02 | **Yes:** take the security report out of the public repository. Done on 2026-11-17; it is kept in the owner's local folder. |
| C-03 | Creator only can remove a member, with a warning if the member has a balance. |
| C-04 | Recommended: multi-use with a cap of 20, plus "Reset link". |
| C-05 | Yes, tell members when someone joins. |
| C-06 | App lock before public launch, opt-in, offered once after first sign-in. |
| C-07 | Yes, an option to hide amounts on widgets. |
| C-08 | Yes, a minimum-version switch. |
| C-09 | App integrity check only when abuse appears. |
| C-10 | Yes, a staging Supabase project. |
| C-11 | Permission given to repair the migration history. |
| C-12 | Wants deletion-by-email automated. → section 2. |
| C-13 | Keep Auto Backup on and say so in the policy. |
| C-14 | Two-factor is on. |
| C-15 | Recorded in the local security notes. |
| C-16 | Wants an agent to go through the Supabase dashboard on the laptop. → section 2. |

### D. Product and UX
| Id | Answer |
|---|---|
| D-01 | Offer "move what's on this phone into my account" at sign-in. |
| D-02 | Declining the AI notice turns AI off quietly, with a small "turn on" line. |
| D-03 | **Dots only stay** for now. Separately: expenses inside a group can't be tapped; fix that. |
| D-04 | "Do what's best for the user." → split the sheet, call it Settings. |
| D-05 | **Wants a tutorial** explaining the main features (skippable). Also: next to the UPI ID field, say plainly that it does not connect to a bank account and is safe to enter; friends asked. |
| D-06 | Yes, and improve the whole expenses/splits filtering. |
| D-07 | Cap nudges at three; stop the daily reminder after a week ignored. |
| D-08 | Doesn't matter. → section 2. |
| D-09 | Recommended: remember the last used category and method. |
| D-10 | Remove the personal names; make the defaults general. **New:** a salary/income setting (amount and the day it arrives). |
| D-11 | Keep default notes simple and informal. |
| new | **Rename "IOU".** Friends don't know the word and so don't use the feature. |
| D-12 | "You owe" is allowed on the user's own screens. |
| D-13 | **No line under quick add at all. Say "AI" everywhere in the app, not Gemini or Groq.** → see the caveat in section 3. |
| D-14 | Yes, propose a glossary. |
| D-15 | "Recommended"; none was given. → section 2. |
| D-16 | Yes, ask "did it go through?" after a UPI payment. |
| D-17 | Pull-based notifications (N2) before launch, push after. |
| D-18 | **Budgets before launch**, set per category in Settings. |
| D-19 | Yes to a "Get the app" line on the split page; until the app is on Play it may link to the GitHub release. |
| D-20 | Don't leave phone-only data unsynced: fix it properly in 1.9 or 1.10. |
| D-21 | Reading a bill photo takes too long, and some things can't be cancelled. Quick add is fast. |
| D-22 | Agents may look at `Screenshots/`; they are old. |

### E. International
| Id | Answer |
|---|---|
| E-01 | The motive is that the app is useful to students and people living on their own, not only revenue. The paid tier comes later. |
| E-02 | **US, UAE, UK, Canada. Not Russia.** |
| E-03 | A global name, decided later. "Kharcha Paani" (KP) until then. |
| E-04 | One currency per user and one per group, no conversion. |
| E-05 | Add PayPal, Venmo and similar as payment links. |
| E-06 | English only. |
| E-07 | Yes: Hinglish for India, plain English elsewhere. |
| E-08 | Statement import India-only at first, unless it is legally safe elsewhere. Statements must not be stored. |
| E-09 | **No legal budget.** |
| E-10 | Yes, update `PRD.md`. |
| E-11 | "Recommended"; taken as: design docs may note "could be a paid feature", nothing more. |

### F. Engineering
All "recommended": split `MainViewModel` after the closed test starts (F-01); close #139 and #276
and re-file, rebase #252 (F-02); the Lead handles Dependabot weekly (F-03); replace the 39 string
tests (F-04); move screenshot-only tests out of the unit suite (F-05); longer CI is fine (F-06);
releases every two weeks during the closed test (F-09). **README: rewrite it and remove the AI
Studio text**; the app has been rebuilt in Claude Code since (F-10). F-07, F-08, F-11, F-12 were not
answered individually → section 2.

### G. Agent system
| Id | Answer |
|---|---|
| G-01 | **Approved in outline, but it must be reviewed first and not built from a first draft.** |
| G-02 | **Claude is Lead.** Builder: "you decide" → Codex. |
| G-03 | Asked what real companies do. → section 2. |
| G-04 | Recommended: GitHub Issues. |
| G-05 | The Lead merges. Only the critical (red) changes are the owner's. Yellow as recommended. |
| G-06 | Will tell any agent in chat to change the Lead. |
| G-07 | The weekly rhythm is fine, but there is no fixed schedule (G-19). |
| G-08 | Whatever is most efficient and hard to forget. → section 2. |
| G-09 | Antigravity has close to unlimited usage and can run sub-agents. Use it for volume. |
| G-10 | "You decide." → section 2. |
| G-11 | Not measured. **The system must be token-efficient**: short start pages, small context. |
| G-12 | Opus on Claude; the current Codex model on Codex. |
| G-13 | **No Claude cloud sessions any more**: too expensive. |
| G-14 | Yes, move Claude's shared project memories into the repo. |
| G-15 | Nothing private in `docs/handoffs/`; use it. |
| G-16–18 | Yes: retire old ids, close #188, archive the old coordination docs. |
| G-19 | India. No fixed day. |
| G-21 | Overnight and scheduled sessions are used often. |
| G-22 | The main agent may approve design docs. |
| G-23 | Keep "ask up front, then autopilot". |

### H. Repository
H-01 to H-05 yes (protect `main`, delete merged branches, Dependabot alerts, private vulnerability
reporting, squash only). H-06 stay public, and keep it tidy and safe. H-07 no APKs on GitHub once
the app is on Play. H-08 yes, fix the local `main`. H-09, H-10: "do what's best".

### I. Facts
1.7.0 works; not every checklist item was run (I-01). Sign-in was tested on a fresh release install
(I-02). 4–5 users, none in guest mode (I-03). No lost data or wrong balances known (I-04). The OAuth
consent screen is still in **Testing** (I-05). No billing on the Google Cloud project (I-06). Legal
site: "check it" (I-07). Publishing as an individual, in India (I-08, I-09). No third-party code
(I-10). Final logo before 1.8 or the Play launch (I-11). **Target: on Play within about a month, two
weeks if possible** (I-12).

### J. Permissions
"You decide what is safest." → section 2.

## 2. Decided on the owner's behalf

| Id | Decision | Why |
|---|---|---|
| A-03 | **Keep `com.vaibhav.kharchapaani`.** | An application id never has to match the brand (many well-known apps keep old ids), users almost never see it, and changing it means redoing the Google sign-in client, Firebase app and file-provider authority with a launch four weeks away. Revisit only if the global name and domain exist before the first upload. |
| A-08 | An agent writes the reviewer-account seeding steps; the owner creates the Google account. | Agents must not create accounts. |
| B-05 | Add a project-wide daily AI ceiling, stored in the same caps table so it can be changed without a release. | On the free tier the quota is shared by everyone. Without a ceiling, a few scripted accounts use the day's allowance and AI stops for all real users. |
| B-07 | Crash reports **on by default with the off switch**, once the owner confirms the Firebase project receives them. | With 4–5 users, opt-in means no crash data at all at launch. It is already declared in the policy. |
| C-12 | A runbook first; an owner-only function when there is an admin page (see "admin" below). | One or two requests a month don't justify a public-facing automation that deletes accounts. |
| C-16 | The owner goes through the dashboard list with an agent reading the screen, in one session. | An agent should not change auth settings on its own; it can tell the owner what to click. |
| D-08 | Daily reminder **off by default**, offered after the first week of use. | A reminder nobody asked for is the fastest way to get notifications blocked. |
| D-15 | "Edited by X" after launch. | Not needed by 4–5 users who know each other. |
| F-07, F-08 | Assume no emulator and no spare phone; CI stays the authority. | Nothing was said. |
| F-11 | No licence file; add a line to the README: "Source is visible for transparency. All rights reserved." | Matches a product the owner intends to monetise. |
| F-12 | Move `tools/ai-bench` results out of the repo; keep the scripts. | |
| G-03 | **One bot GitHub account for all agents**, created by the owner, added as a collaborator. | It is what companies do: automation acts through a machine account or a GitHub App, never through a person's login, so reviews can be required and actions are attributable. One account is enough for a solo owner. |
| G-08 | Questions reach the owner as GitHub issues labelled `needs-owner`, with the GitHub mobile app's notifications on. A voice answer in chat is fine; the agent records it on the issue. | One list, a notification per question, nothing to remember. |
| G-10 | Antigravity (Worker): tests, docs, string and accessibility work, mechanical refactors, and product code that is `risk:yellow` **only from a complete brief**. It may run sub-agents on separate issues that touch different files. Never merges, never touches `supabase/`, auth, schema, `.github/`, releases. | Unlimited usage is worth using; its judgement is still the weakest of the three. |
| G-20 | The switch-over day is the first working session after the agent-system review is approved. | |
| G-21 | Unattended sessions: Worker any time; Builder on yellow issues only; the Lead may review and merge green overnight. Nothing red, no releases, no Supabase, no settings while unattended. | |
| J-01, J-02 | Read-only Supabase access: yes. Rollback-only SQL on live: yes, until staging exists, then never. | |
| J-03 | Additive migrations to **staging** without asking: yes. To live: never by an agent. | |
| J-04, J-07 | Creating issues, labels, milestones: yes. Deleting branches of **merged** PRs: yes. | |
| J-05 | The Lead merges green and reviewed yellow after CI. Red is the owner's. | From G-05. |
| J-06 | Closing or rebasing stale agent PRs: yes, with a comment saying why. | |
| J-08 | Up to two cheaper sub-agents per Claude session, asked first. | The owner's cost rule. |
| J-09 | Repository settings: the Lead may change the ones listed in H-01 to H-05. | |
| J-10, J-11 | Editing `CLAUDE.md`/`AGENTS.md` in the migration: yes. Publishing the audit except the security report: yes. | |

**Two apps and an admin site (asked under "A-10").** The owner wants himself and his friends on
better AI limits, unaffected by a later paid tier, and a place to manage limits. Do **not** ship a
second app: two listings, two signing identities and two code paths double the work for no gain.
Instead, a **plan per user on the server** (`free`, `friends`, later `paid`), with the caps table
keyed by plan. The owner's and friends' accounts are set to `friends` once and stay there. For
managing it: yes, an internal admin page is normal. Stage one is the Supabase table editor (free,
today). Stage two is a small owner-only page on the project site (plans, caps, the AI ceiling,
minimum version, account deletion). Filed as backlog, after launch.

## 3. Where these answers conflict with each other or with the rules

The owner should read this section. Each is a real trade-off, not a detail.

1. **A launch in two to four weeks versus the 1.8.0 scope.** The answers put into 1.8.0: multiple
   currencies, country detection, budgets, a tutorial, the security hardening, the UX fixes, guest
   mode repair, pull notifications and app lock. Currency alone touches the database on both sides,
   sync, backup, AI prompts and every screen that shows money. On three subscriptions this is
   realistically **six to ten weeks**, and then the 14-day closed test only begins after the account
   is opened (A-02). Suggested: split it. **1.8.0** = hardening, UX fixes, budgets, tutorial, India
   only, opened account, closed test starts. **1.9.0** = currency and country, released to the
   running test. The two weeks of testing are then spent building 1.9 instead of waiting.
2. **Free Gemini versus the UK.** Google's terms require the paid tier for users in the UK (and the
   EU). With no budget (B-02, B-03) the UK cannot have AI features. Options: launch the UK with AI
   off, or leave the UK until there is funding. The US, UAE and Canada are not affected by that
   rule, but the policy must still say plainly what the free tier means.
3. **No legal budget versus the UK.** UK law expects a UK representative for a foreign service with
   UK users. Same conclusion: **US, UAE and Canada first; UK when funded.**
4. **Free Supabase versus bill photos and safety.** No backups means a bad migration is
   unrecoverable, and 1 GB holds roughly a thousand to three thousand photos in total. Required
   mitigations while free: photos shrunk to about 200 KB with a per-user cap, a manual database dump
   before every migration, and the staging project (C-10).
5. **"Say AI, not Gemini or Groq" versus disclosure.** In ordinary screens: done, "AI" only. But the
   one-time notice and the privacy policy **must keep naming Google and Groq**. Play's user-data
   rules and the owner's own decision D2 require saying who receives bank statements and voice. Hiding
   the names there is the thing most likely to get the app removed.
6. **Dots only (D-03).** Kept as the owner wants. It will be flagged by Play's accessibility
   pre-launch report and it fails people who can't tell the colours apart. Making group expenses
   tappable (also asked) softens it: a tap shows the names.
7. **Decision D5 (no paid-plan work) still stands.** The per-user plan in section 2 is for the
   friends tier and AI limits only.

## 4. Unsure readings: please confirm

- "A-11: I don't know, you suggest" was taken as the reviewer account (A-08).
- "D-10 recommended" was taken as D-09 (remember the last category and method).
- "E-11 recommended" and "D-15 recommended": no recommendation existed; see section 2.
- G-09's models and G-12's Codex model name were unclear in the recording; nothing depends on them.
- A-04 (testers): still open. Twelve people for 14 days are needed, and there are 4–5 users today.

## 5. Two things the owner asked to have explained

**Can friends just install the Play version? (A-07)** Almost. The Play build is signed with a
different key from the sideloaded one, so Android will not install it over the old app: each
person uninstalls the old one first, installs from Play, and signs in. Everything in their account
comes back from the cloud. What does not come back is what lives only on the phone: subscriptions,
custom categories, settings. Ask them to use "Export everything" before uninstalling. This is one
more reason for D-20.

**How does releasing to several countries work? (A-06)** There is one app and one build for every
country; you do not publish separate versions.
- In the Play Console each release track has a **country list**. Day one: India only. Later you add
  the US, UAE and Canada to the same app, and it appears there with no new upload.
- The **store listing** can be translated and can differ by country (title, description,
  screenshots), so the name and the rupee screenshots can change per market.
- **Inside the app**, differences come from the user's country and currency setting, chosen at
  first run from the phone's locale and changeable in Settings: currency and number format, whether
  UPI or a payment link is offered, whether statement import is shown, which examples appear.
  Nothing is decided by a separate build.
- For things that must be switchable without a release (AI on or off per country, minimum
  version), a small config table on the server that the app reads at start.
- Updates can be rolled out to a percentage of users first (staged rollout).
- Prices for a paid tier are set per country in the Console.

These are as I know Play to work; the agent doing the launch should confirm each on the current
Play Console help pages.

## 6. Section 3 resolved by the owner (later on 2026-11-17)

These settle conflicts 1 to 4 of section 3 and override anything above that disagrees.

| Conflict | The owner's decision |
|---|---|
| 1. Timeline | **No multi-currency in 1.8.0. 1.8.0 is released within two weeks.** Currency and country are 1.9.0, built while the closed test runs. |
| 2 and 3. UK | **No UK for now.** After India: the US, UAE and Canada. |
| 5. Naming the AI vendors | Agreed: "AI" on ordinary screens; Google and Groq stay named in the one-time notice and the privacy policy. |
| 4. Free Supabase | Stay on the free plan and do what is needed: shrink bill photos to about 200 KB, a per-user photo cap, a manual database dump before every migration, and a staging project. |

**What "1.8.0 in two weeks" means for scope.** Two weeks does not fit everything listed for 1.8.0
in `NEXT-STEPS.md` package 3. The Lead cuts 1.8.0 to what protects users and passes review, in this
order, and moves the rest to 1.9.0 without asking again:

1. Server hardening that is owner review (S-01, S-04, S-05, S-06), and the free-plan mitigations above.
2. The small UX fixes (UX-06, UX-02, UX-31, UX-21, UX-22, tappable group expenses, "AI" wording,
   UPI reassurance text, renamed "IOU", general default categories, reminder caps).
3. Guest mode repair, the store listing and README.
4. Then, only if time remains, in the owner's stated priority: budgets, the tutorial, app lock.

Anything in 4 that is not finished and tested by day ten goes to 1.9.0. The date wins over the
feature list; tests and review are never what gets cut.
