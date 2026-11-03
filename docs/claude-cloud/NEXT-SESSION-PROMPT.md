# Prompt for the next Claude Code cloud session

Copy everything inside the fence into a new cloud session on `vaibhavcodesx/kharcha-paani`, fill in
the placeholder, and attach your screenshots to the same message.

```text
You're the Claude Code cloud session for Kharcha Paani: senior engineer and designer on this
project. Read, in order: CLAUDE.md, docs/claude-cloud/HANDOFF.md (standing permissions and hard
stops), docs/claude-cloud/session-log-2026-11-03.md (what the last session did, PR states, how to
render screens without a phone, the rules Codex enforces), PRD.md, and
docs/production-readiness/audits/ui-audit-2026-11.md. Then read issue #188 and the open PRs.

I've been using v1.6.0 on my phone. These are the improvements I want, in priority order. Each
attached screenshot is named or numbered to match:

<<PLACEHOLDER: owner's list of improvements, one per line, each with its screenshot number and
what's wrong / what you want instead>>

How to work:
1. First, finish anything the last session left open that blocks these (session log, "Open item on
   #212"), and check what Codex and Antigravity have open so you don't overlap.
2. For each improvement: look at my screenshot, find the screen in code, render it with the audit
   harness (./gradlew :app:testDebugUnitTest --tests "*.ui.audit.*") so you see what I see, then
   fix it. Before/after renders go in the PR. Anything new gets a short docs/designs/<name>.md
   first. Extend the design system (HANDBOOK section 4); don't replace it.
3. Group related fixes into small [claude] PRs, one theme each. Claim on #188 first and list shared
   files (MainActivity, MainViewModel, shared sheets) there before editing. Codex merges; you keep
   each PR green, answer its review, and add the solo-log row and CHANGELOG line.
4. Money or sync changes need a real-Room test shown failing on pre-fix code. Tests stop their
   ViewModels before closing Room. No fixed sleeps.
5. Don't stop to ask: take the safe, reversible choice and note it. Anything you can't decide, or
   that a screenshot leaves unclear, goes into one list of questions on #188 at the end.
6. Also approved and not yet built: the Home hero in whole rupees (no paise), and a more compact
   Home top on short phones so entries show on the first screen (audit U27, U17).

Hard stops (no exceptions): nothing applied to Supabase; RLS, auth, security or non-additive
migrations open as [owner review] and stay unmerged; no pricing or paywall work; no releases; never
write @claude on GitHub. The largest system font (200%) is deferred until after launch.

When you stop: comment on #188 with what's open, what I need to do (exact taps), what wasn't
verified on a phone, and your questions.
```

## Notes for the owner

- **Screenshots:** attaching them to the first message is simplest. If there are many, put them in
  `docs/feedback/1.6/` (numbered `01-…png`) on a branch and say so in the placeholder.
- One line per issue works best: "03 · Splits · the Settle button is too close to the amount",
  "07 · quick add · I want it to remember the last group".
