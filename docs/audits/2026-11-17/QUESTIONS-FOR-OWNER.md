# Questions for the owner, 2026-11-17

Every decision, doubt and permission the audit turned up, in one place. Answer on the `Answer:`
line under each one; a word is enough where the options are given. "Rec" is what I would do and why.
Where you don't mind, write "rec" and the recommendation is taken.

How to use it: edit this file, save it, and tell the agent "answers are in". Anything you leave
blank is treated as **not decided**, and the work that depends on it waits.

The ones that block the most work are marked **★**. If you only have ten minutes, answer those.

Sections: A Launch and Play · B Money and services · C Security · D Product and UX ·
E International · F Engineering · G The agent system · H Repository · I Facts only you know ·
J Permissions for the next sessions.

---

## A. Launch and Play Store

**A-01 ★ Personal or organisation developer account?**
Personal: $25, your legal name shown, the 12-tester 14-day rule applies, your address is shown once
you sell anything. Organisation: needs a registered business and a D-U-N-S number, no tester rule.
It can't be switched later without moving the app.
Rec: personal now if you have no registered business; accept the address rule later.
Answer:

**A-02 ★ Will you open the account now and start the closed test with 1.7.0, fixing things while it runs?**
The 14 days are the longest item on the path. Waiting until the app "is ready" adds about three weeks.
Rec: yes.
Answer:

**A-03 ★ Is `com.vaibhav.kharchapaani` the application id forever?**
It is permanent after the first upload. It has your first name and a Hindi product name in it.
Options: keep · change to something neutral (say what).
Rec: decide together with E-03 (the name abroad). If unsure, keep.
Answer:

**A-04 Who are the testers?** Do you have 15–20 people with Google accounts who will opt in and stay
for 14 days? Roughly how many are sure?
Answer:

**A-05 Launch countries on day one.**
Options: India only · India plus others (which).
Rec: India only. Countries can be added later at no cost.
Answer:

**A-06 Which build goes to Play first?**
Options: 1.7.0 as it is · a 1.8.0 with the "before strangers" fixes from the backlog.
Rec: 1.7.0 to internal testing now to prove signing and sign-in; 1.8.0 to the closed test.
Answer:

**A-07 The people using sideloaded 1.6/1.7 today** will have to uninstall and reinstall from Play
(different signing key), after exporting a backup. Are you fine telling them that, and how many
are there?
Answer:

**A-08 The reviewer account.** Will you create a separate Google account with sample data for
Google's reviewers, or should an agent write the seeding steps for you to run?
Answer:

**A-09 Store listing: is Google sign-in required or optional?**
The listing says required; the app has a no-account mode.
Rec: say optional, and that groups and backup need it.
Answer:

**A-10 "Divide by shares" in the listing.** The app has Equal, By amount and By percent, no ratio
mode. Options: add a shares/ratio mode · change the sentence.
Rec: change the sentence now, add the mode later.
Answer:

**A-11 Tablets and landscape.** Options: support properly later · just make sure nothing is cut off.
Rec: the second, for launch.
Answer:

**A-12 Light theme before launch?** Options: yes · after launch.
Rec: after launch.
Answer:

---

## B. Money and services

**B-01 ★ Supabase: move to Pro ($25 a month) before the public launch?**
Free plan: no backups, 1 GB for everyone's bill photos, 500 MB database.
Options: Pro at the closed test · Pro at public launch · stay free and shrink photos to ~200 KB with a per-user cap.
Rec: Pro at public launch; shrink photos now either way.
Answer:

**B-02 ★ Gemini: paid tier before launch?**
On the free tier Google may use and review what's sent (bank statements, voice), and the daily
quota is shared by all users, so a few abusers can switch AI off for everyone.
Options: paid before the closed test · paid before public launch · stay free for the first months (current decision D2).
Rec: paid before public launch; it is required anyway for any EU or UK user.
Answer:

**B-03 What can you spend per month in total** on Supabase, Gemini, Groq and anything else, before
there is revenue? A number lets agents design the AI caps to it.
Answer:

**B-04 The AI daily caps** (quick add 50, bills 10, statements 3, Insights 20 per user). Keep, or change?
Answer:

**B-05 A project-wide AI ceiling** (for example 2,000 reads a day for all users together, with an
alert at 80%). Add it? What number?
Rec: yes; start at ten times a normal day once there is data.
Answer:

**B-06 Groq.** The privacy policy says Groq keeps nothing and doesn't train on it. Do you have that
from Groq's settings or terms, or was it assumed? Is the Groq account on a free or paid plan?
Answer:

**B-07 Firebase Crashlytics.** Is a Firebase project set up and are crash reports arriving from
1.6/1.7? Should crash reporting be on by default for new users (with the off switch) or opt-in?
Answer:

**B-08 A domain.** The site is on `vaibhavcodesx.github.io`. Will you buy a domain? It is needed
for verified invite links (T3.3), looks better in the listing, and can't easily be changed in
printed links later.
Answer:

**B-09 A support email.** Everything points to your personal alt Gmail. Keep it, or create
`support@…`/a dedicated address before it is printed in the store?
Answer:

---

## C. Security

**C-01 ★ Was the September Gemini key (the one inside 1.0–1.5) deleted in Google Cloud?**
Options: yes, on (date) · no · not sure.
Answer:

**C-02 ★ The security report is public** because the repository is public and the PR is open.
Options: leave it · remove `01-security.md` from the branch and keep it only on your laptop until the High items are fixed.
Rec: remove it until S-01 is fixed. The others are low risk to publish.
Answer:

**C-03 Should a group's creator be able to remove a member?** (S-03)
Options: creator only · any member by vote · nobody.
Rec: creator only, with a warning if the member has a balance.
Answer:

**C-04 Invite links.** Today: 7 days, unlimited uses. Options: keep · 7 days and single-use ·
7 days with a cap you choose (say 20) · add "Reset link".
Rec: keep multi-use with a cap of 20 and add Reset.
Answer:

**C-05 Should existing members be told when someone joins?** (a notification on the next sync)
Rec: yes.
Answer:

**C-06 App lock.** Options: before public launch · after. Opt-in or on by default?
Rec: before launch, opt-in, offered once after the first sign-in.
Answer:

**C-07 Hide amounts on home-screen widgets** as an option?
Rec: yes, with the app lock.
Answer:

**C-08 Minimum-version switch.** May the app block syncing (not reading or exporting) on a version
you have marked too old, with an "Update" screen?
Rec: yes; it is your only kill switch.
Answer:

**C-09 Play Integrity / App Check on the AI endpoint** (refuses requests not from the real app).
Options: before launch · when abuse appears.
Rec: when abuse appears.
Answer:

**C-10 A second Supabase project as staging,** so migrations stop being tested on production. Free.
May an agent set it up with you?
Rec: yes.
Answer:

**C-11 The two migrations applied by hand** (account deletion, item split) are missing from the
migration history. May the history be repaired with a reviewed statement that you run?
Answer:

**C-12 Account deletion by email.** Will you do these by hand with a runbook, or should it be an
owner-only function you call with an email address?
Rec: runbook now, function when there are more than a few a month.
Answer:

**C-13 Android Auto Backup** copies the on-phone ledger to the user's Google account. Keep it on
(convenient, must be said in the policy) or turn it off for a finance app?
Rec: keep, and say so.
Answer:

**C-14 Two-factor authentication.** Is it on for your Google, GitHub and Supabase accounts today?
Answer:

**C-15 The signing keystore.** Is there a copy of `kharcha-paani-release.jks` and its passwords
somewhere other than this laptop and GitHub secrets?
Answer:

**C-16 The Supabase dashboard list in S-14** (email/phone/anonymous sign-in off, JWT expiry,
legacy keys). Will you go through it, or share your screen with an agent in a session?
Answer:

---

## D. Product and UX

**D-01 ★ Guests who later sign in.** Today the guest ledger stays separate forever.
Options: offer "move what's on this phone into my account" at sign-in · keep separate but warn clearly · remove guest mode.
Rec: the first.
Answer:

**D-02 The AI notice when someone declines.** Options: turn AI off quietly and show a small "AI is
off, turn on" line · ask again once per session · keep asking every time (today).
Rec: the first.
Answer:

**D-03 Colour dots on group expenses.** You asked for dots to save space; the review found dots
alone can't be read by colour-blind people or with five or more people.
Options: dots plus the payer's name in words · dots only (as now) · names only.
Rec: the first.
Answer:

**D-04 The Account sheet.** May it be split into You / Money setup / Your data, and be called
Settings with a gear icon?
Answer:

**D-05 First-run help.** Options: three one-time tips (swipe, add a friend, add UPI ID) · a
skippable four-screen tour (TODOS 10) · nothing.
Rec: the tips.
Answer:

**D-06 The "Expenses" filter** hides split expenses. Should it show everything you spent?
Rec: yes.
Answer:

**D-07 Reminders.** Cap "payment waiting for you" nudges at three, and stop the daily reminder
after a week of being ignored?
Rec: yes to both.
Answer:

**D-08 Is the daily "nothing logged today" reminder on or off by default** for new users?
Answer:

**D-09 Default category and payment method** for a new entry are "Food" and "UPI". Options: keep ·
remember the last used · no default.
Rec: remember the last used.
Answer:

**D-10 Built-in names "Team Payment", "PopUPI", "Income (Client)"** look like your own life. Rename
or remove? What should the default list be?
Answer:

**D-11 Default IOU notes "Informal Loan" and "Borrowed Amount"** are stiff and the other person
sees them. Options: leave the note empty · "Lent to Rohan" style.
Rec: empty.
Answer:

**D-12 "You owe" as a label on your own screens.** The tone rule bans it towards others. Allowed
when it faces only the user?
Rec: yes, and write that into the rule.
Answer:

**D-13 The line under quick add that names Groq and Gemini.** Options: keep · "AI fills in typed
lines, bills and voice", with the vendors in the notice and Settings.
Rec: the second.
Answer:

**D-14 A glossary.** May an agent propose one word each for friend/person/member, group/trip,
entry/expense/transaction, settle/record/pay, for you to approve?
Answer:

**D-15 "Edited by X" on shared expenses.** Wanted for launch?
Answer:

**D-16 After paying in a UPI app, ask "Did it go through? Record it"?**
Rec: yes.
Answer:

**D-17 Notifications when a groupmate adds an expense (N2–N4).** You asked for this in October and
it isn't built. Before public launch, or after?
Rec: the pull-based version (N2) before launch, push after.
Answer:

**D-18 Budgets** are the PRD's main retention idea and don't exist. First feature after launch?
Answer:

**D-19 The public split page.** Add a small "Get Kharcha Paani" line for people who open a friend's link?
Rec: yes, once the app is on Play.
Answer:

**D-20 Things that stay on the phone only** (subscriptions, custom categories, settings) are lost
on reinstall unless Auto Backup restores them. Sync them, or leave?
Rec: leave for launch, say it in Help.
Answer:

**D-21 What bothers you most** when you use the app yourself, that isn't in report 4? Anything your
two daily users have complained about?
Answer:

**D-22 Screenshots.** May agents look at the `Screenshots/` folder on your laptop (real names and
amounts, never committed) to review the real UI?
Answer:

---

## E. International

**E-01 ★ Why international, and when?** If it is only for revenue from a paid tier, that tier is at
least three months away by your own rule (D5). Options: start the currency work now in parallel ·
after the India launch is stable · after the paid tier is designed.
Rec: after the India launch is stable.
Answer:

**E-02 Which countries, in order?** You named the US, the UK and Russia.
Rec: UAE, UK or Canada first (large Indian diaspora, English); the US after; not Russia (Play
billing is suspended there as far as I know, and its data-localisation law needs servers in Russia).
Answer:

**E-03 The name abroad.** Options: "Kharcha Paani" everywhere · a different store title per country ·
a new global name (and then A-03 changes too).
Answer:

**E-04 Currency model.** Options: one currency per user and one per group, no conversion (simple) ·
mixed currencies in a group with exchange rates (large, a natural paid feature).
Rec: the first.
Answer:

**E-05 Settling up outside India** (no UPI). Options: "mark as paid" only · plus a payment-link
field on the profile (PayPal.me, Venmo, Revolut).
Rec: the first, then the second.
Answer:

**E-06 Languages.** English only abroad at first? Is Hindi wanted for India, and before or after
going abroad? Is Russian still wanted given E-02?
Answer:

**E-07 The Hinglish copy** ("hisaab", "chai"). Keep it for India and write plain English for
everyone else?
Rec: yes.
Answer:

**E-08 Bank statement import abroad.** India-only at first?
Rec: yes.
Answer:

**E-09 Legal budget.** GDPR needs an EU and a UK representative (paid services, roughly €100–300 a
year each) and a policy rewrite. Are you willing to pay for that, and for a lawyer's one-time review?
Answer:

**E-10 May `PRD.md` be changed** from "for Indian friends" to say India first, then international,
once you have answered E-01 to E-04?
Answer:

**E-11 The paid tier (parked by D5).** No work is proposed, but two facts shape choices now: Play
takes 15% and shows a seller's address. Do you want agents to keep D5 exactly as it is (no
discussion at all), or may they note "this could be a paid feature" in design docs?
Answer:

---

## F. Engineering

**F-01 ★ May `MainViewModel` be split after launch** following the map in report 5, starting with
the mechanical extractions? It touches the file every agent edits, so it needs a quiet week.
Rec: yes, first thing after the closed test starts.
Answer:

**F-02 Stuck PRs.** #252 (Codex, draft recovery), #139 (Codex, Home at large fonts), #276
(Antigravity, charts at large fonts): rebase and finish, or close and re-file as issues?
Rec: close #139 and #276 and re-file; rebase #252, it is the fix for a real gap.
Answer:

**F-03 Dependabot.** Four open, two failing (Compose BOM jump, a minor group). Who handles them and
how often?
Rec: the Lead, weekly; the Compose jump as its own PR with a release smoke test on your phone.
Answer:

**F-04 The 39 `*StringResourcesTest` files** will all break when wording changes or a second
language arrives. May they be replaced by one parameterised test per screen?
Answer:

**F-05 Tests that only render screenshots** (about 50) count as tests and assert nothing. Move them
out of the unit suite?
Answer:

**F-06 CI minutes.** The repo is public so Actions are free. Adding a Supabase test job and a
docs check makes runs longer. Fine?
Answer:

**F-07 Your laptop.** How much RAM does it have? The no-local-builds rule shapes how agents work;
if cloud sessions or a second machine exist, say so.
Answer:

**F-08 An emulator or a spare phone** for agents to drive? Today only you can see the app run.
Even one cheap Android phone left plugged in would change what agents can verify.
Answer:

**F-09 Release cadence.** Options: weekly · every two weeks · when ready.
Rec: every two weeks during the closed test.
Answer:

**F-10 `README.md` is still the AI Studio template** on a public repo. Rewrite it? Should it invite
outside contributors, or say "source available, not accepting contributions"?
Answer:

**F-11 A licence.** The public repo has none, which legally means nobody may reuse the code.
Options: leave it (all rights reserved) · MIT/Apache · make the repo private again (CI then costs money).
Answer:

**F-12 `tools/ai-bench/`** commits 1.8 MB of sample bills and results. Keep in the repo or move out?
Answer:

---

## G. The agent system (report 6)

**G-01 ★ Do you approve the proposed system in outline?** Roles (Lead, Builder, Worker), one
`STATE.md`, GitHub Issues as the board, one PR template, `changes/` files replacing the shared
changelog section.
Options: yes · yes with changes (say which) · no, I want (what).
Answer:

**G-02 ★ Who is Lead right now?** Claude or Codex. And who is Builder?
Answer:

**G-03 ★ A separate GitHub account for the agents.** Today every agent posts as you, so GitHub
can't tell an agent's merge from yours and can't require your review. A free second account
(for example `kharcha-paani-bot`) added as a collaborator fixes that properly.
Options: yes, one shared bot account · yes, one per agent · no, keep one account and use a label check.
Rec: one shared bot account.
Answer:

**G-04 GitHub Issues as the task list, or a file?** Issues close themselves when a PR merges and
can be filtered; a `BOARD.md` file is readable offline but goes stale and causes conflicts.
Rec: Issues.
Answer:

**G-05 Who may merge?** The proposal: green = author after CI; yellow = the other main agent
reviews, Lead merges; red = you only. Is that what you want, or should you merge everything during
the closed test?
Answer:

**G-06 How do you want to change the Lead?** Options: tell any agent in chat and it opens the
`STATE.md` PR · edit `STATE.md` yourself on GitHub.
Rec: either; both end in the same one-line change.
Answer:

**G-07 How often will you look?** The proposal assumes a 15-minute planning on Monday and a
15-minute review on Friday, plus answering `needs-owner` issues. Realistic? If not, what is?
Answer:

**G-08 How do you want questions to reach you?** Options: GitHub issues labelled `needs-owner` (you
get email/app notifications) · one file like this one, updated weekly · chat only.
Rec: issues, and this kind of file only for big audits.
Answer:

**G-09 Antigravity's models.** Which exactly do you have ("Gemini 4 Hydrogen", "3.8 Flash"), and
are the limits per day or per week? This decides how many Worker issues the Lead keeps open.
Answer:

**G-10 What may Antigravity do?** Today: tests, docs, mechanical changes, some product code, never
merge, never Supabase. Keep exactly that, or widen/narrow it?
Answer:

**G-11 Quotas.** Roughly how many hours a week does each subscription give you before it runs out
(Claude Pro, ChatGPT Plus, Antigravity Pro)? The weekly plan should fit them.
Answer:

**G-12 Which Claude and Codex models** do you run for Lead work and for building (Opus, Sonnet,
Fable; the Codex model)? You said you will implement this with Opus and Sonnet.
Answer:

**G-13 Claude cloud sessions.** Still used? They can't see your laptop's files, `.env` or Claude's
memory, which is one reason shared knowledge must live in the repo.
Answer:

**G-14 Claude's private memory.** It holds about twenty project rules that other agents can't see.
May they be copied into the repository (`PLAYBOOK.md`, `testing.md`, `decisions.md`)? Anything in
there you would not want public? (The repo is public.)
Answer:

**G-15 `docs/handoffs/` on your laptop** (untracked) includes your decisions of 2026-10-24 that the
work order cites. May the decisions be copied into `docs/product/decisions.md`? Is there anything
private in that folder?
Answer:

**G-16 Old task ids** (T2.4, X7, W6, Y5…). May they be retired, with open work re-filed under the
audit's ids and issue numbers?
Rec: yes.
Answer:

**G-17 Issue #188.** Close it with a pointer to the new board?
Answer:

**G-18 May the old coordination docs be moved to `docs/history/`** (the handbooks, lead reports,
solo log, cloud handoff, plan-1.6, the October audits)? Nothing is deleted.
Answer:

**G-19 The weekly cycle.** Which day is planning day for you, and in which time zone do you work?
Answer:

**G-20 During the one-day switch-over, all other agent work stops.** Which day suits?
Answer:

**G-21 Should agents ever work without you watching** (scheduled or overnight sessions)? If yes,
which roles and with what limits?
Answer:

**G-22 Design docs before big changes.** The proposal requires an approved design doc before any
red-risk work or anything over about 400 lines. Do you want to approve those yourself, or let the
other main agent approve and only tell you?
Answer:

**G-23 The CLAUDE.md rule "ask everything up front, then autopilot".** Keep as it is under the new
system (questions become `needs-owner` issues and the agent moves on)?
Answer:

---

## H. Repository and GitHub

**H-01 ★ May `main` be protected?** No force pushes, PRs only, the three CI checks required. It
means even you can't push straight to `main`.
Rec: yes.
Answer:

**H-02 Delete the ~170 merged or abandoned branches** and turn on automatic deletion? (Asked before
in the 1.6 plan; nothing was deleted.)
Answer:

**H-03 Turn on Dependabot alerts and security updates?** Free on a public repo.
Rec: yes.
Answer:

**H-04 Private vulnerability reporting and a `SECURITY.md`?**
Rec: yes.
Answer:

**H-05 Squash merges only?** One commit per PR on `main`, the PR title as its message.
Rec: yes.
Answer:

**H-06 The repository stays public?** It gives free CI and makes the audit, the security notes and
all the agent docs readable by anyone. Options: stay public · go private and pay for CI (or use fewer minutes).
Answer:

**H-07 GitHub releases** currently carry the APK, AAB and mapping file as public pre-releases. Keep
publishing APKs there once the app is on Play?
Rec: no; Play only, to avoid two signing keys in the wild.
Answer:

**H-08 Your local `main`** is 143 commits behind and `git pull` will be blocked by your untracked
`tools/` folder. May an agent move `tools/` aside, pull, and put back whatever isn't upstream?
Answer:

**H-09 The audit worktree** sits in a temp folder with the branch `claude/audit-2026-11-17`. Remove
it once PR #291 is merged?
Answer:

**H-10 PR #291.** Merge it as it is, or wait until C-02 is decided?
Answer:

---

## I. Facts only you know

**I-01** Did the 1.7.0 phone checklist pass? Which items failed or weren't run?
Answer:

**I-02** Has sign-in been tested on a release build installed fresh on a second phone?
Answer:

**I-03** How many people use the app daily right now, and do any of them use guest mode?
Answer:

**I-04** Has anyone lost data or seen a wrong balance since 1.6? If yes, what happened?
Answer:

**I-05** Is the OAuth consent screen "In production" in Google Cloud, or still "Testing"?
Answer:

**I-06** Which Google Cloud project holds the Gemini key, and does it have a budget alert?
Answer:

**I-07** Are the legal pages on `vaibhavcodesx.github.io/kharcha-paani-site` the same as `site/` in
the repo today? Who publishes them and how?
Answer:

**I-08** Is there a registered business, or will the app be published by you as an individual?
(Affects A-01, the privacy policy's "who we are", and tax on a paid tier.)
Answer:

**I-09** Your country of residence for the Play account and tax (India, I assume).
Answer:

**I-10** Is anything in the app copied from, or built on, someone else's code or design that needs
a licence or credit beyond the three fonts in `docs/licenses/`?
Answer:

**I-11** The logo "isn't final" (a note in Claude's memory). Is there a final one coming before the
listing is made?
Answer:

**I-12** Is there a deadline: a date you want to be on the Play Store by?
Answer:

---

## J. Permissions for the next sessions

For each: yes, no, or "ask each time".

**J-01** Read-only access to the live Supabase project in future sessions (as in this audit).
Answer:

**J-02** Running rollback-only SQL tests on the live project until staging exists.
Answer:

**J-03** Applying **additive** migrations to staging without asking, once staging exists.
Answer:

**J-04** Creating GitHub issues, labels and milestones from the audit backlog.
Answer:

**J-05** Merging `risk:green` PRs after green CI without asking (the current merge policy in
Claude's memory says yes for low-risk PRs).
Answer:

**J-06** Closing or rebasing other agents' stale PRs.
Answer:

**J-07** Deleting merged remote branches.
Answer:

**J-08** Using up to two cheaper-model subagents per session for wide reading or mechanical work.
Answer:

**J-09** Changing GitHub repository settings (rulesets, Dependabot, auto-delete) for you, or will
you click them yourself from a checklist?
Answer:

**J-10** Editing `CLAUDE.md` and `AGENTS.md` as part of the agent-system migration.
Answer:

**J-11** Publishing this folder's reports (other than the security one) in the public repo.
Answer:

**J-12** Anything agents must never do that isn't written down yet?
Answer:
