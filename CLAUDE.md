\# Kharcha Paani — Project Context



This is an existing, working native Android app (Kotlin + Jetpack

Compose + Room + Gemini API), originally built in Google AI Studio — no

Claude Code work has been done on it yet.



Read PRD.md before starting any work — it covers the cloud migration to

Supabase, correctness/testing priorities, and a UI/UX premium-polish

pass, in priority order. Also read docs/original-architecture-dossier.md

for the full original technical writeup of what already exists.



Don't re-architect things that already work well (the ledger/balance

pattern, the debt simplification algorithm, the AI ingestion pipeline,

the groups/trips model) — improve and extend them, per the PRD's "Out of

Scope" section.



Confirm with me before starting each numbered priority area in the PRD.

Record what changed in CHANGELOG.md (Keep a Changelog format), not in PRD.md.
PRD.md is the stable spec: update it only when a spec or decision changes.
