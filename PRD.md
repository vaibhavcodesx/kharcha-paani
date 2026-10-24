# Kharcha Paani: product spec (current)

Last reviewed 2026-10-24. This page is short on purpose: it says what the product is and which
rules hold. **The work to do next lives in [docs/production-readiness/README.md](docs/production-readiness/README.md)**,
and what has changed lives in [CHANGELOG.md](CHANGELOG.md).

The original September brief (cloud migration, robustness, premium UI pass) is archived at
[docs/history/prd-2026-09-cloud-migration.md](docs/history/prd-2026-09-cloud-migration.md). All of
it shipped in v1.0–v1.4. Read it only for history: its backlogs, status logs and "out of scope" list
are out of date (for example, friends who aren't on the app are now supported).

## What the app is

A native Android app (Kotlin, Jetpack Compose, Room, Supabase, Gemini) for Indian friends to log what
they spend and split it with each other. It is offline-first: everything is saved on the phone and
synced when there's a connection.

- **Your own spending:** quick add by typing or voice ("chai 40 cash"), bill photos, bank or UPI
  statement import, categories, subscriptions, Insights with AI questions, CSV export and a JSON
  backup.
- **Splitting:** friends, including friends who aren't on the app; shared groups and trips with
  invite codes; equal, exact and share-based splits with guests; IOUs; debt simplification.
- **Settling up:** UPI QR and links, settlements the other person confirms, and ask-to-pay messages
  shared to WhatsApp.
- **Around it:** Google sign-in, home-screen widgets, a daily reminder, a colour per person.

## Where it's going

1. **Production-ready on the Play Store** (now): see the plan's tiers and the owner's decisions (§10).
2. **Free for at least the first 3 months.** The goal is that people rely on it daily: seeing where
   the money went, budget limits with friendly nudges, easy splits and settle-ups, AI quick add.
   Paid plans come only after user feedback. Until then, no pricing, paywall or billing work.

## Rules that still hold

- **Don't re-architect what works.** That covers the ledger/balance pattern, the debt simplification
  algorithm, the AI ingestion pipeline and the groups/trips model. Improve and extend them instead.
- **Out of scope:** self-hosting Supabase; PowerSync (revisit only if hand-rolled sync becomes
  persistently painful); non-Google sign-in; moving money (the app records who owes what, and UPI
  hands off to the user's own payment app); iOS.
- **Tone:** anything one person sends or shows another must sound casual and friendly, never formal,
  never like begging, never "you owe".
- **Quality bar:** it must feel hand-crafted, not AI-generated. The original "AI slop" checklist is in
  the archived brief, Part B.

## Keeping this page current

Update this page when what the product is, or a rule, changes. Plans and progress go in the plan file;
changes go in CHANGELOG.md.
