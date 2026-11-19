#!/usr/bin/env bash
# Is any agent about to run out of work? The Lead runs this at the start of every session, after every merge round,
# and whenever the owner is away. It counts the briefed issues each agent could take right now (open, not blocked,
# not waiting on the owner, not already claimed by someone else) and says which queue to refill.
#
#   scripts/agent_queue.sh            prints the table; exit 1 if a queue is low
#   WORKER_MIN=5 scripts/agent_queue.sh
#
# Low means: Worker (Antigravity) fewer than 4, Builder (Codex) fewer than 6. The owner asked that Antigravity never
# runs dry, so the Worker floor is checked first. Refill by writing issues from docs/agents/briefs/TEMPLATE.md.
set -u
WORKER_MIN="${WORKER_MIN:-4}"
BUILDER_MIN="${BUILDER_MIN:-6}"

count() { # role, agent
  gh issue list -l "for:$1" --state open -L 200 \
    -S "-label:blocked -label:needs-owner -label:wip:claude -label:wip:codex -label:wip:antigravity" \
    --json number --jq 'length'
}
claimed() { gh issue list -l "wip:$1" --state open -L 50 --json number --jq 'length'; }
open_prs() { gh pr list --state open -L 100 --json title --jq "[.[]|select(.title|test(\"^\\\\[$1\\\\]\"))]|length"; }

w=$(count worker); b=$(count builder)
printf '%-12s %-10s %-8s %-8s\n' agent unclaimed working open-PRs
printf '%-12s %-10s %-8s %-8s\n' antigravity "$w" "$(claimed antigravity)" "$(open_prs antigravity)"
printf '%-12s %-10s %-8s %-8s\n' codex "$b" "$(claimed codex)" "$(open_prs codex)"

low=0
if [ "$w" -lt "$WORKER_MIN" ]; then echo "REFILL antigravity: $w unclaimed, want at least $WORKER_MIN (write $((10 - w)) more, to ten)"; low=1; fi
if [ "$b" -lt "$BUILDER_MIN" ]; then echo "REFILL codex: $b unclaimed, want at least $BUILDER_MIN (write $((10 - b)) more, to ten)"; low=1; fi
[ "$low" -eq 0 ] && echo "queues ok"
exit "$low"
