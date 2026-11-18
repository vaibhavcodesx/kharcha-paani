<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# State

The only file that names which agent holds which role. To change the Lead, the owner tells any
agent "Codex leads from now"; that agent opens one PR changing `lead:`, `builder:` and `since:`,
and leaves a handover comment on the open milestone (PRs in flight, what it was in the middle of).

```
lead: claude
builder: codex
worker: antigravity
since: 2026-11-19
goal: Release 1.8.0 (India closed test). Milestone 1.8.0. Ship in about two weeks, no multi-currency.
cut order: hardening and free-plan fixes, then small UX fixes, then guest mode and listing, then budgets/tutorial/app lock only if tested by day ten
frozen: supabase/, .github/ (red: author opens a PR, the Lead reviews and merges, nothing is applied or deployed live)
release: 1.7.0 is on phones; next is 1.8.0
```

Board: `gh issue list -m 1.8.0`. Owner's inbox: `gh issue list -l needs-owner`.
