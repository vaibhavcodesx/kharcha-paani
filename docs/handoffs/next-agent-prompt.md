Read CLAUDE.md, then PRD.md (a short current page; ignore docs/history/, which is archive only), then docs/production-readiness/README.md: start with §0 "Start here", then §10 "Owner decisions", which override everything else in it. That file is your work order for making Kharcha Paani production-ready for the Play Store.

Context:
- PRs #61–#77 are open and waiting for my device tests. #74, #75 and #77 also need my security/auth review. Check `gh pr list` to see what has merged since, and build on current main.
- The app will be free for at least the first 3 months (decision D5). Do NOT work on or discuss paid plans, pricing, paywalls or billing. Only features and production readiness.

How we work this session:
1. FIRST, before any code, plan the session's items and ask me ALL your questions, decisions and permissions in one batch: the go-ahead for Tier 1, which PRs you may merge, migrations, approach choices, and any open owner decisions those items need. After I answer, work on autopilot and don't stop to ask me anything.
2. When a new question comes up mid-session, make the safe, reversible choice and note it, or skip that item and move on to the next. Keep a list of every new question and ask them all together at the END of the session, with a summary of what was done and what's next.
3. Hard stops stay hard: anything touching RLS, auth or security, or a non-additive migration, is opened as a PR and left for my review, never merged by you.

What to do:
1. Work through Tier 1 in the order §0 gives: T1.4 amounts, T1.5 status bar and splash, T1.7 double saves, T1.8 crash safety net, T1.9 statement cancel, N0, N1, T1.10 AI notice (use the wording in §10 D2, which is honest and calm), T1.2 privacy policy and Help & about, T1.6 crash reporting, T1.3 Gemini proxy with the caps in D2, T1.1 account deletion as in D1. Then Tier 2.
2. Give each item its own branch, PR, tests and CHANGELOG entry. Check every finding in the current code before changing it; line numbers have drifted. For a bug, commit the failing test first and let CI prove it fails, then push the fix.
3. Follow the rules in §1:
   - Don't run the emulator: I test on my phone, so tell me what to check on the device for each PR.
   - Check free RAM before any Gradle run and stop leftover java.exe afterwards. CI is the authority for tests.
   - You may merge low-risk PRs once CI is green.
   - Room is at v10 after #76, so new migrations are v11 and up, never destructive.
   - Any text one person sends or shows another (notifications, reminders, ask-to-pay) must sound casual and friendly: never formal, never begging, never "you owe".
4. Mark items "done in #NN" in docs/production-readiness/README.md as they merge, so the next session can pick up where you stop.

When Tier 1 and Tier 2 are done (or blocked on me), run the office-hours skill with me on §9 of the plan: how to make Kharcha Paani a daily habit and a necessity, not pricing. The focus is seeing where the money went, a new budget-limits feature with friendly nudges as a budget is approached or crossed, easy splits and settle-up recovery, and AI quick add.
