# Codex restart — solo batch paused 27 October 2026

Start with the [short owner progress report](../production-readiness/solo-progress-2026-10-27.md). Product snapshot: **50 merged, 6 prepared, 4 parked**. The user explicitly requested this wrap/pause to conserve the weekly limit. No feature work should continue just because an old watcher was running.

## Read and verify first

1. Read root [AGENTS.md](../../AGENTS.md), then [work-split.md](work-split.md) **in full**, then its Read first documents and relevant audits. The work split wins; current saved file hash is `9e55b6168bbcdffb19b592ed204633496fe1a4d7`, but never assume it stayed unchanged.
2. The solo self-merge exception is dated **2026-10-27 to 2026-10-30**. Recheck the current date and authorization before merging. Outside that window, follow the normal no-self-merge rule unless the owner changes it.
3. Inspect actual GitHub PR states and latest main. Do not trust the static CI snapshot below as a current merge permission.

## Workspace and saved state

- Actual managed checkout used here: `C:\Users\VAIBHAV\.codex\worktrees\x1-store-listing\Kharcha Paani`.
- The user-facing original `Z:\V\.The_Future\Vibeeee\Kharcha Paani` was unavailable/empty during this run. Do not overwrite it or reset/clean any checkout. Verify paths and preserve user changes.
- Documentation branch at wrap: `codex/solo-handoff-2026-10-27`; docs are committed/pushed and linked in its draft PR. It is not a product merge and is intentionally left for resumption/review.
- Last confirmed product main: `1b5e13a9cb02272bc3ca4f225528d56662c2ef27` ([178](https://github.com/vaibhavcodesx/kharcha-paani/pull/178), widgets).
- No active local CI watcher or feature mutation remains. GitHub's normal CI for 179 can finish independently. No automation/goal/background agent was created.
- Previous helpers in the model's tool store are **not durable**. Use this document and the repository/GitHub as truth.

## Resume queue: one PR at a time

All are X12 / T3.11. Do not combine them or recreate their branches. Final main integration and log row are already present for 179. Its branch has a planned merged log row that must land only when its merge succeeds. For 180–184, add their own merged/parked log row before the final checked commit.

| PR | Branch | Head at pause | CI snapshot (not a future gate) |
|---|---|---|---|
| [#179](https://github.com/vaibhavcodesx/kharcha-paani/pull/179) | `codex/x12-notification-content-strings` | `829cad41f89057bc40d0800f97346e816a73ee4f` | unit-tests: IN_PROGRESS, lint: IN_PROGRESS, release-bundle: IN_PROGRESS |
| [#180](https://github.com/vaibhavcodesx/kharcha-paani/pull/180) | `codex/x12-payment-pill-strings` | `56270274fa176d700f14f299018e53a59e7c0395` | unit-tests: SUCCESS, lint: SUCCESS, release-bundle: SUCCESS |
| [#181](https://github.com/vaibhavcodesx/kharcha-paani/pull/181) | `codex/x12-support-draft-strings` | `ba7b234a291b69f9e637e6e4ae42ea564aa9e61c` | unit-tests: SUCCESS, lint: SUCCESS, release-bundle: SUCCESS |
| [#182](https://github.com/vaibhavcodesx/kharcha-paani/pull/182) | `codex/x12-split-notice-strings` | `2316c66f079512ae7ff53de2bb21aa12f99b9e35` | unit-tests: SUCCESS, lint: SUCCESS, release-bundle: SUCCESS |
| [#183](https://github.com/vaibhavcodesx/kharcha-paani/pull/183) | `codex/x12-statement-warning-strings` | `2078213052ed2014bc03e59161b3ebcbb4d7ae3f` | unit-tests: SUCCESS, lint: SUCCESS, release-bundle: SUCCESS |
| [#184](https://github.com/vaibhavcodesx/kharcha-paani/pull/184) | `codex/x12-split-self-label` | `3b80236fa07982a3211017c6b838bca4e720c9cb` | unit-tests: SUCCESS, lint: SUCCESS, release-bundle: SUCCESS |

179 already contains the last main shown above. PRs 180–184 have initial green CI on older bases and still need current main merged into them, conflict resolution, a log row, and **new** CI. No failed Android CI run was observed for these six at this snapshot; refresh run histories before counting any newer failure. Two failed Android CI runs on an item mean park it and move on; fixture failures count too.

After these six, perform a final source/resource/scope/log audit. The last audit found only intended data/branding/animation labels and protected/parked Home/account text, plus the two follow-ups now covered by 183 and 184. Do not claim that Hindi translations or all accessibility acceptance are finished.

## Mandatory merge sequence

```powershell
git status --short
git fetch origin main
git switch codex/<existing-item-branch>
git merge origin/main --no-commit --no-ff
# Resolve only allowed files; preserve incoming main plus this PR's changes.
# Add/update this PR's solo-log line and retain exactly one Unreleased entry.
git diff --check
git commit --no-edit
git push origin codex/<existing-item-branch>
```

If main is already included and there is no MERGE_HEAD, do not invent a merge commit; commit only actual authorized changes. Check the latest main again immediately before merging.

- Wait for **unit-tests, lint, release-bundle** to succeed on the exact pushed head. Mark drafts ready only when concrete. An old-base green result is insufficient.
- Review the PR's triple-dot file diff, tests, one Unreleased entry, solo-log row and Check on the phone list. Never touch protected files or owner-review work.
- Fetch main; `git merge-base --is-ancestor origin/main origin/<branch>` must succeed. If main moved, merge it and repeat CI.
- Announce the newly observed green result, then squash-merge with the PR title as the commit subject and the **full verified expected head SHA**. Do not guess a SHA or enable an indirect bypass.
- **Verify the merge response actually says merged=true, or re-read GitHub state MERGED.** A tool being called is not proof of success. Never advance to the next merge on a rejected/error result.

An automatic approval review once rejected 175 using stale pending-check context. Fresh GitHub checks proved all three jobs green; the same native merge tool then succeeded. 176 was re-integrated with the confirmed 175 merge and re-tested. Do not repeat the earlier orchestration mistake of treating an error response as a successful merge.

## Expected conflicts and safe choices

- `CHANGELOG.md` and `solo-log.md`: keep both branches' entries and the Questions/handoff section. Add one line per merged or parked PR; never duplicate an existing row.
- `strings.xml`: merge **complete string/plurals blocks**, not line fragments. Shared closing plurals tags can otherwise corrupt XML. Assert common resources are unchanged and final keys are unique. The helper below is for an active merge only, after each side's own resources are committed.
- 180: PaymentPill spoken label only. 181: Support draft text only. 182: UI split-notice formatter and one MainViewModel call; retain the matching algorithm and protected prompts. 183: known statement-warning display only; do not resume X8. 184: reuse entry_you for self by ID; preserve canonical participant names, guest/friend names and callbacks.
- Avoid duplicate imports in shared MainViewModel merges; do not reformat or reorder the class. English, money calculations, field IDs, JSON/CSV schemas and user-entered text must remain unchanged.

```python
from pathlib import Path
import subprocess,re,xml.etree.ElementTree as ET
for f in ['CHANGELOG.md','docs/agents/solo-log.md']:
 p=Path(f);s=p.read_text();s='\n'.join('' if l=='=======' else l for l in s.split('\n') if not l.startswith(('<<<<<<<','>>>>>>>')));p.write_text(s)
file='app/src/main/res/values/strings.xml'
ours=subprocess.check_output(['git','show','HEAD:'+file]).decode('utf-8')
theirs=subprocess.check_output(['git','show','MERGE_HEAD:'+file]).decode('utf-8')
ourkeys={(e.tag,e.attrib.get('name')) for e in ET.fromstring(ours)}
known={(e.tag,e.attrib.get('name')):e for e in ET.fromstring(theirs)}
expected=ourkeys|set(known)
add=[]
for m in re.finditer(r'    <(?P<tag>string|plurals)\b[^>]*name="(?P<name>[^"]+)"[^>]*>.*?</(?P=tag)>',ours,re.S):
 key=(m['tag'],m['name']);e=ET.fromstring(m.group())
 if key in known:
  assert ET.tostring(e,encoding='unicode').strip()==ET.tostring(known[key],encoding='unicode').strip(),key
 else:add.append(m.group());known[key]=e
merged=theirs if not add else theirs.replace('</resources>','    <!-- Additional screen strings. -->\n'+'\n'.join(add)+'\n</resources>')
r=ET.fromstring(merged);keys=[(e.tag,e.attrib.get('name')) for e in r]
assert len(keys)==len(set(keys)) and set(keys)==expected
Path(file).write_text(merged)
print('Valid complete blocks: '+str(len(keys))+' unique resources')

```

Before staging, review any other unmerged source file manually; the helper deliberately handles only the two docs and XML. If there are no new resource blocks, keep incoming XML unchanged. Run XML parsing, unique-key checks and git diff --check before committing.

## Do not resume the four parked items in this solo pass

- [130](https://github.com/vaibhavcodesx/kharcha-paani/pull/130), `codex/x8-statement-reimport`, head `ef8b5975d03ef52adbfc5ca6342dc9e1880f43eb`: failures 40403130740 and 40404218629. Reserve every exact match before near-date matching.
- [132](https://github.com/vaibhavcodesx/kharcha-paani/pull/132), `codex/x10-home-loading`, head `531f2298616096e25823de0b4aafdfcc3e011ce4`: failures 40407939041 and 40408765164. Derive readiness from a complete direct-Room snapshot, not default-empty public StateFlows.
- [139](https://github.com/vaibhavcodesx/kharcha-paani/pull/139), `codex/x7-home-accessibility`, head `fe48b6bdea789fcb0d6d72ae324b3c1fc4ddb7b6`: failures 40412344087 and 40413157415. Whole-balance fitting fallback needed when animated digits cannot fit.
- [143](https://github.com/vaibhavcodesx/kharcha-paani/pull/143), `codex/x12-home-strings`, head `b916668a6376dc22fcee9436d0824f8993c80d2f`: failures 40415903485 and 40418934304 (an intermediate corrected run passed). Likely fixture follow-up: await both feed and analytics before Today assertions.

Do not infer that an incidental later green run cancels the stop rule. Their PR bodies and the solo log record what happened.

## Boundaries and verification

- No Supabase/server/database operations, releases, repository settings, owner-review PRs, #71, #103 or Dependabot work. Never push to main.
- Do not edit `supabase/**`, `data/db/**`, `app/schemas/**`, `data/sync/**`, GeminiRepository/AI prompts, AccountSheet, AppSettings, `.github/**`, PRD or the protected readiness README/release headings. Re-read the full lane contract for exact paths.
- X11 search is merged in 142; Edited by identity still needs protected DB/sync/server work. Do not fake an editor from original owner plus updatedAt.
- Outgoing AskMessage.build has an existing tone conflict and remains a Claude handoff; 177 extracts only status/shared-code labels. Account/auth/reminder copy and parked Home remain outside completed X12.
- Recent separate Claude-review jobs fail execution with is_error:true and no posted findings. This was logged; the solo contract explicitly names the three Android CI gates. Do not call the review successful or change workflows/settings to repair it.
- Local Gradle was not run below the repo's 4 GB free-RAM floor (last observed free RAM about 0.7 GB). Recheck available RAM and SDK before attempting it. If runnable: `./gradlew :app:testDebugUnitTest :app:lintDebug`. **Never run an emulator.**
- UI/VM tests construct real MainViewModel/Room (or app shell), use StandardTestDispatcher and cancel/await VM jobs before resetting Main. Do not replace real flows with fake StateFlows. Explicit Unit-returning runBlocking tests avoid non-void JUnit failures.
- Existing money/role/direction/privacy tests remain meaningful. Phone/Play-track delivery, real review display, TalkBack/large text and Scanner/pre-launch reports remain owner checks; no live acceptance was claimed.

## Finish after resumption

1. Process 179–184 in order under freshly read rules.
2. Verify every merged product PR has its own test change, one Unreleased entry and log row, with no forbidden-file diff. Check that parked entries are still open/unmerged and resources have unique keys.
3. Update the [owner progress report](../production-readiness/solo-progress-2026-10-27.md) and this handoff with actual results. Keep the master readiness README unchanged unless the owner reallocates it.
4. Give the owner a concise outcome and links. Leave all protected/phone/release handoffs visible; do not equate CI with production activation.
