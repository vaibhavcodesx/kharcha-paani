#!/usr/bin/env python3
"""Hard limits for Antigravity on this repo. Runs before every shell command and file tool call.

Reads the hook payload on stdin and prints {"decision": ..., "reason": ...} on stdout.
  deny          never runs, whatever the agent says
  auto_approve  a known-safe command, so the agent can work without waiting for the owner
  allow         no opinion: the app's normal permission setting decides

It fails closed: if anything here breaks, the call is denied. It is a seatbelt, not a sandbox. Determined
shell tricks could get past a regex, which is why the rules and code review still exist.
Don't weaken this file. See docs/antigravity/HANDBOOK.md section 2.
"""
import ctypes
import json
import os
import re
import subprocess
import sys

INTEGRATION = "antigravity/integration"
REQUIRED_JOBS = {"unit-tests", "lint", "release-bundle"}
MIN_FREE_GB_FOR_GRADLE = 4.0
OWNER_CHECKOUT = "kharcha paani"  # The owner's own folder holds .env and the signing keys.

SECRET_FILE = re.compile(
    r"(^|[\\/\s\"'=])\.env(?!\.example)(\b|$)|\.jks\b|\.keystore\b|keystore\.properties|keystore-base64"
    r"|google-services\.json|service[-_]?account.*\.json",
    re.I,
)
NO_WRITE = re.compile(
    r"(^|[\\/])(supabase|\.github|\.agents)([\\/]|$)"
    r"|(^|[\\/])(PRD\.md|CLAUDE\.md|AGENTS\.md|GEMINI\.md|gradlew|gradlew\.bat)$"
    r"|docs[\\/]production-readiness[\\/]README\.md$"
    r"|docs[\\/]agents[\\/]work-split\.md$"
    r"|docs[\\/]antigravity[\\/]HANDBOOK\.md$",
    re.I,
)

DENY_COMMANDS = [
    (r"\bsupabase\b|\bpsql\b|\bpg_dump\b", "Nothing touches Supabase or any database server. Claude and the owner do that."),
    (r"\bgh\s+(release|workflow|secret|variable|repo|auth|ssh-key|gpg-key|cache|ruleset|extension|gist)\b", "Releases, workflows, secrets and repository settings belong to the owner."),
    (r"\bgh\s+pr\s+(close|reopen|lock)\b", "Don't close or reopen PRs. Say what you'd do in docs/antigravity/PROGRESS.md."),
    (r"\bgh\s+pr\s+edit\b.*--base\b", "Don't change a PR's base."),
    (r"\bgh\s+run\s+(cancel|delete)\b", "Don't cancel or delete CI runs."),
    (r"\bgh\s+api\b.*(\s-X\s*(?!GET)|--method\s+(?!GET)|\s-f\s|\s-F\s|--field|--raw-field|--input)", "gh api is read-only here."),
    (r"\bgit\s+reset\s+--hard|\bgit\s+clean\b|\bgit\s+branch\s+(-\S*[dD]\b|--delete)|\bgit\s+checkout\s+(\S+\s+)?--\s|\bgit\s+restore\s+(?!--staged)", "This throws work away. Commit it to a branch instead."),
    (r"\bgit\s+(rebase|filter-branch|filter-repo|update-ref|replace|tag|config|remote\s+(add|set-url|remove|rename)|worktree\s+remove|gc\b|reflog\s+(delete|expire)|stash\s+(drop|clear))", "Not needed on this workflow: use new commits and merges."),
    (r"--no-verify|--no-gpg-sign", "Don't skip checks."),
    (r"\badb\b|\bemulator\b|\bavdmanager\b|connected\w*AndroidTest", "No emulator and no device: the owner tests on the phone. CI runs the tests."),
    (r"\b(assemble|bundle)\w*Release\b|\bpublish\w*\b.*gradle|gradle\w*\s+.*\bpublish", "Release builds and their signing belong to the owner."),
    (r"\brm\s+(-\S*[rR]|--recursive)|\brmdir\b|\brd\s+/s|\bdel\s+/s|remove-item\b.*-recurse|\bformat\s+[a-z]:|\bdiskpart\b|\bmkfs\b|\bshutdown\b", "No recursive deletes. Delete single files with git rm, inside this workspace."),
    (r"\b(reg|setx|icacls|takeown)\s|set-executionpolicy|set-itemproperty\s+hk", "No system changes."),
    (r"(curl|wget|iwr|invoke-webrequest|irm|invoke-restmethod)\b.*\|\s*(sh|bash|pwsh|powershell|iex|python)\b|\biex\b", "Don't run downloaded scripts."),
    (r"\bprintenv\b|^\s*env\s*$|get-childitem\s+env:|\bgci\s+env:|\bdir\s+env:", "Don't dump environment variables: they can hold tokens."),
    (r"\.agents[\\/].*(>|sed\s+-i)|(>|set-content|add-content|out-file|copy-item|move-item|\bmv\b|\bcp\b|\bgit\s+rm\b).*(\.agents|\.github|supabase)[\\/]", "These folders are read-only for you."),
    (r"(\bgit\s+add\b).*(\.env\b(?!\.example)|\.jks|\.keystore|keystore\.properties|local\.properties)", "Never commit secrets or local machine files."),
]

SAFE_PREFIXES = re.compile(
    r"^\s*("
    r"git\s+(status|log|diff|show|fetch|branch(\s+-a|\s+-r|\s+--show-current|\s+--list)?\s*$|switch|checkout|add|commit|merge|pull\s+--ff-only|push|rev-parse|ls-files|grep|merge-base|cherry-pick|remote\s+-v|blame|restore\s+--staged|rm\s+(?!-r))"
    r"|gh\s+(pr\s+(view|list|checks|diff|create|merge|comment)|run\s+(list|view|download|watch|rerun)|api\b)"
    r"|(\./|\.\\)?gradlew(\.bat)?\s"
    r"|ls\b|dir\b|cat\b|type\b|head\b|tail\b|grep\b|rg\b|findstr\b|find\b|wc\b|sort\b|uniq\b|select-string\b|get-content\b|get-childitem\b|select-object\b|where-object\b|measure-object\b|test-path\b|pwd\b"
    r")",
    re.I,
)


def finish(decision, reason=""):
    print(json.dumps({"decision": decision, "reason": reason}))
    sys.exit(0)


def strip_quoted(command):
    return re.sub(r"\"(?:[^\"\\]|\\.)*\"|'[^']*'", "\"\"", command, flags=re.S)


def segments(command):
    return [s.strip() for s in re.split(r"&&|\|\||;|\n|\|", strip_quoted(command)) if s.strip()]


def current_branch(cwd):
    result = subprocess.run(["git", "branch", "--show-current"], cwd=cwd or None, capture_output=True, text=True, timeout=10)
    return result.stdout.strip()


def free_gb():
    if os.name != "nt":
        return 99.0

    class Status(ctypes.Structure):
        _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong), ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong), ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong), ("sullAvailExtendedVirtual", ctypes.c_ulonglong)]

    status = Status()
    status.dwLength = ctypes.sizeof(Status)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))
    return status.ullAvailPhys / (1024 ** 3)


def check_push(segment, cwd):
    args = segment.split()[2:]
    if any(a in ("-f", "-d", "--force", "--force-with-lease", "--delete", "--mirror", "--all", "--tags", "--prune") or a.startswith("--force") or a.startswith("+") for a in args):
        finish("deny", "No force, delete, mirror or tag pushes.")
    if any(re.search(r"(^|[:/])(main|master)$", a) for a in args):
        finish("deny", "Nothing is pushed to main. Open a PR.")
    if current_branch(cwd) in ("main", "master"):
        finish("deny", "You're on main. Create an antigravity/ branch first.")


def check_commit(cwd):
    if current_branch(cwd) in ("main", "master", INTEGRATION):
        finish("deny", "Don't commit directly to main or the integration branch. Create antigravity/<item>-<name>.")


def check_pr_create(command, segment, cwd):
    match = re.search(r"--base[=\s]+\"?([\w./-]+)", command)
    if not match:
        finish("deny", f"Pass --base explicitly: {INTEGRATION}, or main only for release-lane items R1 to R3.")
    base = match.group(1)
    branch = current_branch(cwd)
    if base == "main" and re.match(r"antigravity/r[123]-", branch):
        return
    if base != INTEGRATION:
        finish("deny", f"PRs go to {INTEGRATION}. Only branches named antigravity/r1-, r2- or r3- may target main.")


def check_pr_merge(segment):
    if re.search(r"--admin|--auto|--delete-branch=false", segment):
        finish("deny", "No --admin or --auto merges.")
    number = re.search(r"\bmerge\s+#?(\d+)", segment)
    if not number:
        finish("deny", "Name the PR number: gh pr merge <n> --squash.")
    view = subprocess.run(
        ["gh", "pr", "view", number.group(1), "--json", "baseRefName,title,statusCheckRollup"],
        capture_output=True, text=True, timeout=25,
    )
    if view.returncode != 0:
        finish("deny", "Couldn't read that PR, so it can't be merged.")
    pr = json.loads(view.stdout)
    if pr["baseRefName"] != INTEGRATION:
        finish("deny", f"You only merge into {INTEGRATION}. The owner or Claude merges into main.")
    if re.search(r"\[(owner review|needs claude)\]", pr["title"], re.I):
        finish("deny", "This PR is marked for human or Claude review. Leave it open.")
    results = {c.get("name"): c.get("conclusion") for c in pr["statusCheckRollup"]}
    missing = [job for job in REQUIRED_JOBS if results.get(job) != "SUCCESS"]
    if missing:
        finish("deny", f"CI isn't green on this PR yet: {', '.join(sorted(missing))}.")


def check_command(command, cwd):
    lowered = command.lower()
    if OWNER_CHECKOUT in lowered:
        finish("deny", "Work only in your own clone. The owner's folder holds secrets.")
    if SECRET_FILE.search(command):
        finish("deny", "Never read, print, copy or commit secret files.")
    bare = strip_quoted(command)
    for pattern, reason in DENY_COMMANDS:
        if re.search(pattern, bare, re.I):
            finish("deny", reason)
    for segment in segments(command):
        if re.match(r"git\s+push\b", segment, re.I):
            check_push(segment, cwd)
        elif re.match(r"git\s+commit\b", segment, re.I):
            check_commit(cwd)
        elif re.match(r"gh\s+pr\s+create\b", segment, re.I):
            check_pr_create(command, segment, cwd)
        elif re.match(r"gh\s+pr\s+merge\b", segment, re.I):
            check_pr_merge(segment)
        elif re.search(r"gradlew", segment, re.I) and free_gb() < MIN_FREE_GB_FOR_GRADLE:
            finish("deny", "Under 4 GB of RAM free: a local build has crashed this laptop before. Push and let CI run it.")
    unsafe_syntax = re.search(r"[;&<>`]|\$\(", bare)
    if not unsafe_syntax and all(SAFE_PREFIXES.match(s) for s in segments(command)):
        finish("auto_approve", "Known-safe command.")
    finish("allow")


def inside(path, roots):
    full = os.path.normcase(os.path.abspath(path))
    return any(full == r or full.startswith(r + os.sep) for r in roots)


def check_file(tool, args, payload):
    path = args.get("TargetFile") or args.get("AbsolutePath") or args.get("SearchPath") or ""
    if not path:
        finish("allow")
    if OWNER_CHECKOUT in path.lower():
        finish("deny", "Work only in your own clone. The owner's folder holds secrets.")
    if SECRET_FILE.search(path) and not path.lower().endswith(".env.example"):
        finish("deny", "Never read or write secret files.")
    if tool in ("write_to_file", "replace_file_content", "multi_replace_file_content"):
        workspaces = [os.path.normcase(os.path.abspath(p)) for p in payload.get("workspacePaths", [])]
        artifacts = payload.get("artifactDirectoryPath")
        roots = workspaces + ([os.path.normcase(os.path.abspath(os.path.expanduser(artifacts)))] if artifacts else [])
        if roots and not inside(path, roots):
            finish("deny", "Only write inside this workspace.")
        relative = path
        for root in workspaces:
            if inside(path, [root]):
                relative = os.path.relpath(os.path.abspath(path), root)
        if NO_WRITE.search(relative):
            finish("deny", "This file is read-only for you. See docs/antigravity/HANDBOOK.md section 2.")
    finish("allow")


def session_start():
    message = (
        "Kharcha Paani. Before anything else, read docs/antigravity/HANDBOOK.md section 0, then "
        "docs/antigravity/PROGRESS.md on the antigravity/integration branch, and continue from the first item "
        "marked todo. A guard script blocks merges to main, Supabase, secrets and destructive commands. A denied "
        "command is a limit, not a bug: never work around it. Skip the item and note it in PROGRESS.md."
    )
    print(json.dumps({"injectSteps": [{"systemMessage": {"systemMessage": message}}]}))


def main():
    if "--session" in sys.argv:
        session_start()
        return
    try:
        payload = json.loads(sys.stdin.read())
        call = payload["toolCall"]
        args = call.get("args") or {}
        if call["name"] == "run_command":
            check_command(args.get("CommandLine", ""), args.get("Cwd"))
        else:
            check_file(call["name"], args, payload)
    except SystemExit:
        raise
    except Exception as error:  # Fail closed.
        finish("deny", f"Guard error, so this was blocked: {type(error).__name__}. Note it in PROGRESS.md.")


if __name__ == "__main__":
    main()
