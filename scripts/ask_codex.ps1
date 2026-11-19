<#
Sends one task to Codex from the owner's laptop, non-interactively, in its own git worktree so it never touches the
folder anyone else is working in. Used by the Lead to say "improve this PR" or "continue that issue" while the owner
is away. Codex starts by reading AGENTS.md like any other session, so the task text only needs the new instruction.

  scripts/ask_codex.ps1 "Improve PR 480: see docs/agents/inbox/codex.md"
  scripts/ask_codex.ps1 -Branch codex/480-fix "Fix the failing test in PR 480"

It runs on the owner's Codex login and usage; if the usage limit is reached it fails with Codex's own message and
the task stays in the inbox. It never needs a token of its own. Output goes to scratch/codex-<time>.log.
#>
param(
  [Parameter(Mandatory = $true, Position = 0)][string]$Task,
  [string]$Branch = ""
)

$ErrorActionPreference = "Stop"
$repo = (git rev-parse --show-toplevel).Trim()
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $repo "scratch/codex-$stamp.log"
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null

$work = $repo
if ($Branch -ne "") {
  $work = Join-Path (Split-Path $repo) ("kp-codex-" + $stamp)
  git -C $repo fetch origin | Out-Null
  git -C $repo worktree add -B $Branch $work origin/main | Out-Null
}

$prompt = "You are Codex, the Builder for this repository. Read AGENTS.md first and follow it, including your inbox at docs/agents/inbox/codex.md. New instruction from the Lead: $Task"
Write-Host "Codex started in $work. Log: $log"
Push-Location $work
try {
  codex exec --full-auto $prompt *> $log
  Write-Host "Codex finished with exit code $LASTEXITCODE. Log: $log"
} finally {
  Pop-Location
}
