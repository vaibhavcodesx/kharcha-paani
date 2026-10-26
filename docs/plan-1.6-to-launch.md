# From 1.6 to the Play Store: the plan

Written 2026-10-27. The work order (`docs/production-readiness/README.md`) is still the source for each
item's detail; this page says where things stand and in what order the rest goes. Who builds what is
in `docs/agents/work-split.md`.

## 1. Where things stand

**Tier 1 (before the closed test):** done except **T1.1 account deletion** (#103). Its migration drops a
foreign key, so its SQL test has to run on live (rolled back) before the owner approves it, and that
run needs the owner's permission.

**Tier 2 (during the closed test):**

| Item | State |
|---|---|
| T2.1 shares add up | done (#106, live) |
| T2.3 database hygiene | #107, waiting for its SQL test (same permission as #103) |
| T2.5 confirm or undo | Codex, #120 |
| T2.6 privacy on the device | Codex parts 1–2, #115; part 3 (sign-out data) Claude, not started |
| T2.8 in-app updates | Codex, next |
| T2.9 store listing text | done (#111); screenshots and the feature graphic still to make |
| T2.10 accessibility | Codex, last |
| T2.2 abuse limits | Claude, not started, owner review |
| T2.4 sync problems visible | Claude, not started (first Room migration since v10) |
| T2.7 notifications phase 2 | Claude, not started, the biggest item left |

**Beyond the plan, done this week:** the repo is public (free CI), the legal pages are live, Say the
split (#114), the crash-report switch (#118), three new categories (#119), the paisa fix (#117).

## 2. Release 1.6.0

**Goes in:** everything on `main` once #117, #118, #119 and Codex's open PRs (#115, #120, and X5/X6 if
they're ready) are merged. It's the first build with the Gemini proxy and crash reports.

**Before building:** all of the above green on CI, and the CHANGELOG's Unreleased section read through
once as a whole (many PRs added to it).

**The owner's phone test:** a `docs/testing/1.6.0-device-checks.md` like 1.5.0's, built from each PR's
"Check on the phone" list. The important ones: quick add works through the proxy (that proves the
server key), "Send crash reports" is there, and Say the split from the widget.

**After the test passes:** delete the old Gemini key (owner step O2). From then on 1.5.0 and older can't
use AI; nothing else about them changes.

## 3. The path to the closed test

The calendar is set by **O10: 12+ testers opted in for 14 days in a row** before Play lets the app apply
for production. Recruiting is the long pole, so start it now, not after 1.6:

- The two friends who use the app daily are the first testers; each of them knows others who split
  money. Aim for 15–20 so the count stays above 12 for the whole 14 days.
- Each tester needs a Google account added to the closed-test list, and has to opt in through the link.

Owner steps, in order (from the work order's §4, with status):

1. **O2** new server key: done; delete the old one after the 1.6 test.
2. **O3** Play Console: Play App Signing, upload the 1.6 AAB, register the Play signing SHA-1 on the
   Android OAuth client. Sign-in fails without the last part (it did once before, see the release
   smoke test note).
3. **O4** OAuth consent screen to "In production", with branding.
4. **O6** legal pages: done (`vaibhavcodesx.github.io/kharcha-paani-site`).
5. **O7** App content answers: drafted in `docs/legal/play-console-answers.md`; paste them in.
6. **O8** a reviewer Google account with sample data (a group, a split, a settlement).
7. **O9** device verification in the Play Console app.
8. **O12** 2FA on Google, Play Console, Supabase and GitHub.
9. Store listing: text is ready (`docs/store-listing/`); the screenshots come from the 1.6 build on the
   phone, following its capture plan.

**O1 (paid Gemini) is not needed now:** decision D2 keeps the free tier for the first months, with the
caps already live (50/10/3/20 a day).

## 4. Engineering after 1.6, in order

**Claude:**
1. T1.1 account deletion (#103) and T2.3 (#107), as soon as their SQL tests can run.
2. T2.4 sync problems visible: people should see when something didn't sync, before testers hit it.
3. T2.2 abuse limits (owner review): needed before strangers can join groups.
4. T2.6 part 3: sign-out offers to remove the account's data from the phone.
5. T2.7 notifications phase 2: the Updates inbox ("Priya added an expense").
6. TODOS: Say the split's follow-ups (below), 6 (20+ friends), 3b (saved cards), 10 (walkthrough).

**Codex:** X5's TODOS items, X6 in-app updates, then X7 accessibility. TODOS 9 (preselect a group) is
free to start now that #114 has merged.

## 5. Product: what office hours decided

Capture is the product. The two friends who use the app daily do it from the home-screen widget, and
"Say the split" (#114) removes the step where they fixed every split afterwards. Next, in order:

1. **The owner's assignment:** watch one of them add a shared expense from the widget without helping;
   count taps, note whether they open the split editor, write down what they type or say.
2. **Measure:** four weeks after 1.6, rerun the baseline query in `docs/designs/say-the-split.md`
   (group expenses edited within 30 minutes: 16% before, target under 5%).
3. **If repeat splits dominate:** the widget remembers the split (approach A, small).
4. **If "Rohan paid" comes up:** who paid, by voice.
5. Then the §9 list in the work order: seeing where the money went, budgets with friendly nudges. Not
   before capture is solid: a budget is only as good as what got logged.

No pricing or paid plans until the free period ends (D5).

## 6. Waiting on the owner

- Permission to run the rolled-back SQL tests for #103 and #107 on live (or run them in the SQL editor).
- Permission to delete the ~100 branches of merged or closed PRs from GitHub (the safety check
  stopped it; nothing was deleted).
- Turn on GitHub secret scanning and push protection (free for public repositories): Settings → Code
  security.
- Merge the Dependabot PRs (#82, #86, #97) once 1.6 is tested; #71 (R8) needs a signed-release smoke test.
