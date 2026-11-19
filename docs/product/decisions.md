<!-- status: live | owner: owner | reviewed: 2026-11-19 -->
# Owner decisions (append only, newest first)

Source for the launch answers: `docs/audits/2026-11-17/OWNER-ANSWERS.md` (read-only; this page is the live list).

- **2026-11-19. Keep the word "IOU"** (not "loan", which sounds too serious); the Log an IOU sheet explains it at the top.
- **2026-11-19. The owner admin page is hosted on Vercel and built with Vite, React and TypeScript** (not plain files on
  GitHub Pages), so it gets real security headers. Design: `docs/designs/admin-page.md`.
- **2026-11-19. Antigravity may have up to 15 open PRs** (was five).
- **2026-11-19. Work like a product company; the owner does the least.** Claude is Lead, Codex Builder,
  Antigravity Worker. The owner says "start" and the agents find their own work (`AGENTS.md`).
- **2026-11-19. Merge permission while the owner sleeps and for 1.8.0.** The Lead merges every PR after checking
  CI and reading the diff, including red PRs under the conditions in `PLAYBOOK.md` 3a (staged, additive, nothing
  applied or deployed live). Live Supabase, signing, Play Console and GitHub settings stay the owner's.
- **2026-11-19. Subagents:** at most two, on a cheaper model, for the Lead; Codex and Antigravity are prompted by the owner.
- **2026-11-17. 1.8.0 ships within two weeks, without multi-currency; no UK for now.** Cut order: hardening and
  free-plan fixes, small UX fixes, guest mode and listing, then budgets, tutorial, app lock only if tested by day ten.
  Currency and country are 1.9.0, built while the closed test runs. After India: US, UAE, Canada.
- **2026-11-17. "AI" on ordinary screens;** Google and Groq stay named in the one-time notice and the privacy policy.
- **2026-11-17. Stay on the free Supabase plan:** photos about 200 KB with a per-user cap, a manual dump before every
  migration, a staging project.
- **2026-11-17. One bot GitHub account for agents** (`vaibhavkp-bot`, collaborator with Write). Questions reach the owner as
  `needs-owner` issues. No Claude cloud sessions. Keep application id `com.vaibhav.kharchapaani`.
- **2026-11-17. Server minimum-version switch:** block syncing, not reading or exporting, below the minimum (C-08).
- **2026-11-17. Per-user plan on the server (`free`, `friends`), not a second app.** A project-wide daily AI ceiling.
- **2026-11-17. Daily reminder off by default;** crash reports on by default with an off switch once the Firebase project is
  confirmed; no licence file ("source visible, all rights reserved").
- **Standing:** the logo is not final, so do not reuse or animate the launcher mark in UI. The repo is public. Keep
  Room migrations non-destructive. Hard stops are listed in `AGENTS.md`.
