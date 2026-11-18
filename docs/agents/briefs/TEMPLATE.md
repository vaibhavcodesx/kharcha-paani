<!-- status: live | owner: lead | reviewed: 2026-11-19 -->
# Brief template (the issue body of a Worker issue)

The Worker starts cold and has the weakest judgement of the three agents, so a brief must be doable without
judgement. Only `risk:green` or `risk:yellow`. Never two open Worker issues touching the same file.

```markdown
## Goal
One sentence: what a user or developer gets.

## Files
The exact paths to change. "Nothing else" is the default.

## Do
1. Numbered steps, each one checkable.

## Don't
Out of scope. Always: no renames, no reformatting, no new dependencies, no wording changes beyond the list.

## Done when
The test that must pass, by name, and what it asserts. Or the exact grep/diff that must hold.

## Check on phone
What the owner should look at, or "nothing".

## If stuck
Comment what you found, add `blocked`, remove your `wip:` label, take the next issue.
```

Labels: `for:worker`, `risk:green|yellow`, `p0|p1|p2`, milestone. Add `hot:<name>` if it touches a hot file
(`PLAYBOOK.md` section 6).
