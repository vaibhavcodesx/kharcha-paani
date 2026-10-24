# Kharcha Paani: Project Context

A native Android app (Kotlin + Jetpack Compose + Room + Supabase + Gemini API), originally built in
Google AI Studio. Since then it has been through many Claude Code sessions; v1.0–v1.4 have shipped.

Before starting any work, read:
1. **PRD.md**, a short page on what the product is and which rules hold.
2. **docs/production-readiness/README.md**, the current work order. Read §0 "Start here" and §10
   "Owner decisions" first: §10 overrides anything else in it.

docs/history/ is archive only: the old September PRD and its status log. Don't take tasks from it.
docs/original-architecture-dossier.md describes the app as it was before any of this work, so treat
it as background.

Don't re-architect things that already work well (the ledger/balance pattern, the debt
simplification algorithm, the AI ingestion pipeline, the groups/trips model). Improve and extend them.

## How to work with me

- **At the start of a session, ask everything up front.** Collect every question, decision and
  permission you'll need for the session's planned work (merges, migrations, choices between
  approaches, anything in the plan marked as an owner decision) and ask them together, once, before
  you start. Then work on autopilot without stopping for me.
- **Questions that come up later go on a list, not to me.** When something new needs my decision
  mid-session, make the safe, reversible choice if there is one (and note it), or skip that item and
  move on to the next. Then ask me the whole list together at the end of the session.
- **Hard stops stay hard stops.** Anything touching RLS, auth or security, or a non-additive migration,
  is opened as a PR and left for my review, never merged by you.
- Confirm with me before starting each tier of the plan; that confirmation belongs in the up-front
  questions.

Record what changed in CHANGELOG.md (Keep a Changelog format). Update PRD.md only when what the
product is, or a rule, changes.
