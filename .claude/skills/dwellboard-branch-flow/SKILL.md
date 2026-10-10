---
name: dwellboard-branch-flow
description: "Dazewell's git / integration / upstream-merge model for DwellBoard (dazewell/DwellBoard; upstream HeliBorg/HeliBoard). Trigger for branches/worktrees, one change = one branch = one session, mandatory #tag, discoverability, merging from upstream, ci.yml/process-rules.yml/staging.yml gates, <YYYY-MM-DD>_<slug> names, no force-push, follow-up commits, merge authority, batch landing, and getting a test APK. Companion to dwellboard-workflow."
---

# DwellBoard branch & integration flow

Current model: **one change = one dated branch = one session**, running start to
finish with subagents. No coordinator branches.

## Topology

Remotes: `origin` is `dazewell/DwellBoard`. `upstream` is
`https://github.com/HeliBorg/HeliBoard.git` (add it once with
`git remote add upstream <url>`). Upstream may move to Codeberg; if it does,
repoint `upstream` and update this file — nothing else depends on the host.

Branches:
- `dev` — default and integration branch, build source, durable history.
  Features and upstream merges land in it. Ruleset: no force-push, no delete,
  required check `Every commit carries a #tag`. Never commit to it directly.
- `base` — tracks upstream `main`. Ruleset: fast-forward only. Only ever moved
  by fast-forwarding to `upstream/main`; never commit to it.
- `main` — frozen legacy mirror. Never commit, never push.

Change branches are short-lived `<YYYY-MM-DD>_<slug>` from `dev`, PR'd to
`dev`, auto-deleted at merge, and recoverable via `refs/pull/<N>/head`.

## Branch names

Branch format is **`<YYYY-MM-DD>` + `_` or `-` + `<slug>`**, lowercase,
date first, hyphens in slug. `process-rules.yml` enforces it on every PR:

```
2026-10-09_clip-pin          2026-10-09-clip-pin          2026-10-10_ci-tag-check
```

Date required; a bare `clip-pin` is wrong. `_` and `-` both valid; tooling may
flatten `_` to `-`; do not flag dated hyphen names. No camelCase, spaces, owner
prefix, or `coord-<slug>`. Slug matches the tag: `2026-10-09_clip-pin` ->
`#clip-pin`. `.github/scripts/test-branch-name.ps1` checks a name.

Locally this is mostly automatic once `core.hooksPath` is set:
`.githooks/post-commit` dates an undated branch whenever it is committed to
(skipped once the branch is on a remote; opt out with `DWB_NO_AUTO_DATE=1`), and
`.githooks/pre-push` refuses to push one. For a name up front:

```powershell
.\.github\scripts\new-branch-name.ps1 -Slug clip-pin     # -> 2026-10-09_clip-pin
```

Wrong name already created? Rename before review history accumulates:

```powershell
git branch -m <old-name> <YYYY-MM-DD>_<slug>
git push origin :<old-name>                              # drop the old remote ref
git push origin -u <YYYY-MM-DD>_<slug>
```

Deleting/re-pushing a branch ref is not history rewrite. Retarget/reopen any
PR. Do not rebase/catch up feature branches; conflicts with upstream are
resolved on the upstream-merge branch, not on features.

## Commit tags

Every authored non-merge commit has inline `#<slug>` in subject/body:
`add long-press clipboard pin #clip-pin`. Later fixes reuse it;
`git log --grep '#clip-pin'` survives branch deletion. A line starting with `#`
may be stripped.

Feature commits use the feature slug. Exempt category tags are exactly `#ci`,
`#docs`, `#build`, `#chore`, `#infra`, `#deps`, `#test`, `#release`, `#slug`,
`#tag`, plus any `*-fix`. Anything else must appear in `FEATURES.md` as
`<!-- #slug -->`. Merge commits are exempt. `.githooks/commit-msg` enforces
locally after `git config core.hooksPath .githooks`; `process-rules.yml`
enforces branch format, tags, and catalogue. The check scans the whole PR range
and full bodies, so a wrong pushed tag is effectively immutable; a real entry
is the costly escape. Pick right first. `--no-verify` only in emergency.

No AI mentions in commit messages, PR titles/bodies, trailers, or app source. No
assistant `Co-Authored-By`.

## Issues and dispatch gates

Issues are for decided, unstarted work. Each records feature tag and unique
branch slug:

```
<!-- tracking -->
**Branch slug:** `clip-pin-limit`  |  **Feature tag:** `#clip-pin`  |  **Status:** deferred

> Blocked until PR #12 is merged and confirmed on-device.
```

Labels: `status:approved` means dazewell approved work; only he applies it.
`status:in-progress` means active session; `status:blocked` names a dependency;
`status:deferred` is parked. No approved label means proposal. Stop labels
outrank approval. Agents have dazewell's token, so they still must not approve.

Mandatory preflight before issue work:

```powershell
$repo = 'dazewell/DwellBoard'
$n    = 12                # issue number
$slug = 'clip-pin-limit'  # the issue's declared branch slug

# Exact branch match; loose "*$slug*" false-positives and "_" anchoring misses
# names because branch tooling normalises the separator to "-" on push.
$branchRe = "^\d{4}-\d{2}-\d{2}[-_]$([regex]::Escape($slug))$"

$blockers = @()

# 0. APPROVED BY DAZEWELL? No label, no work.
$issue  = gh issue view $n --repo $repo --json state,labels,assignees | ConvertFrom-Json
$labels = @($issue.labels.name)
if ($labels -notcontains 'status:approved') { $blockers += 'NOT APPROVED by dazewell' }

# 1. open, and no stop labels?
if ($issue.state -ne 'OPEN')                 { $blockers += "issue is $($issue.state)" }
if ($labels -contains 'status:in-progress')  { $blockers += 'already claimed (status:in-progress)' }
if ($labels -contains 'status:blocked')      { $blockers += 'blocked (status:blocked)' }
if ($labels -contains 'status:deferred')     { $blockers += 'deferred (status:deferred)' }

# 2. open PR? --limit is mandatory; gh defaults to 30 and can drop the match.
$pr = gh pr list --repo $repo --state open --limit 200 --json number,headRefName |
  ConvertFrom-Json | Where-Object { $_.headRefName -match $branchRe }
if ($pr) { $blockers += "open PR #$($pr.number) on $($pr.headRefName)" }

# 3. remote branch?
$branch = git ls-remote --heads origin |
  ForEach-Object { ($_ -split 'refs/heads/')[-1] } |
  Where-Object { $_ -match $branchRe }
if ($branch) { $blockers += "remote branch $branch" }

if ($blockers) { "DO NOT DISPATCH:`n - " + ($blockers -join "`n - ") } else { 'clear to dispatch' }
```

The block decides: no approval or any blocker means stop. Claim before work:

```powershell
# Hyphen: tooling flattens "_" on push, so "_" would name a branch that never appears.
$claimBranch = "{0:yyyy-MM-dd}-{1}" -f (Get-Date), $slug
gh issue edit $n --repo $repo --add-label "status:in-progress" --remove-label "status:deferred"
gh issue comment $n --repo $repo --body "Started. Branch: ``$claimBranch``"
```

Derive the date. Re-read after claiming; if another claim landed first, back off.
PR body uses `Closes #<n>`. Before reclaiming stale `status:in-progress`, prove
no matching remote branch, open PR, or live session; remove label and explain.

## Start and work

Use sibling worktree `..\DwellBoard-<slug>` for iterative features; main clone
for one-shot CI/workflow tweaks, small fixes, tuning, or docs. When unsure, use
a worktree. Hooks are shared.

```powershell
git switch dev; git pull --ff-only origin dev
git config core.hooksPath .githooks                     # once per clone, if not set

git switch -c <YYYY-MM-DD>_<slug> dev                   # in place: cut the change branch from dev
git worktree add -b <YYYY-MM-DD>_<slug> ..\DwellBoard-<slug> dev   # or a sibling folder on the same fresh branch
cd ..\DwellBoard-<slug>                                 # work here; the main clone stays on dev
# ...dwellboard-workflow steps: design review, hooks, compile, code review...
```

`FEATURES.md` rides in the same branch. Before self-managed worktree removal
(`git worktree remove ..\DwellBoard-<slug>` from the main clone, then
`git worktree prune`), stop/verify processes per `dwellboard-process-lifecycle`;
app-managed worktrees use only `archive_session`.

## PR, builds, and review

User-visible features open non-draft PRs into `dev` by default; CI/bug/chore PRs
are optional.

```powershell
gh pr create --base dev --head <YYYY-MM-DD>_<slug> --title "<title>" --body "<body>"
```

`ci.yml` compiles and unit-tests every PR push and is the no-local-tools gate;
say so in the body. `process-rules.yml` also runs on every PR. For a test APK,
add the `build-apk` label: `staging.yml` builds the APK and uploads it unzipped
as an Actions artifact. Fetch it with (`gh run download` would unpack the APK):

```powershell
gh run list --repo dazewell/DwellBoard --workflow staging.yml --branch <branch> --limit 3
gh api repos/dazewell/DwellBoard/actions/runs/<run-id>/artifacts --jq '.artifacts[] | "\(.id) \(.name)"'
curl.exe -sL -H "Authorization: Bearer $(gh auth token)" -o <dir>\<name>.apk https://api.github.com/repos/dazewell/DwellBoard/actions/artifacts/<artifact-id>/zip
```

If labelling produces **no run at all** — not a skipped one — check
`mergeable_state`: `dirty` means there is no merge ref to build. Merge
`origin/dev` **into the feature branch**, push, re-label. The one case that
overrides "do not catch up feature branches" above: nothing builds until it is
resolved. A manual `workflow_dispatch` builds the ref you name, as-is; report
which trigger and ref produced the APK. A workflow that never ran is not a pass.

Request behaviour verification only after review is clean. A UI-facing change
also gets one earlier smoke build after compile, for reachability only.

Copilot review is **billed and never automatic**. Request it once architect
round 2 has cleared and `ci.yml` is green on the head SHA, via the
`request_copilot_review` tool or its script,
`.github/scripts/request-copilot-review.ps1 -PullRequest <n> -Wait`. **Two per
PR**, the second only after fixing an Important-or-above finding; none on doc-
or process-only PRs unless dazewell asks. The script enforces this; never
hand-roll the request.

Close every review point: fix or reply why not, resolve, verify none remain
(`.github/scripts/get-review-threads.ps1`).

## Follow-up commits

Review fixes, on-device bugs, and later improvements are new commits, not amends:
re-run the compile gate, then commit and push to the same branch.

Name the fix, not "address review". Push re-runs `ci.yml`; a fresh APK needs
`build-apk` again. Rewriting is off by default even on feature branches; use it
only on dazewell's request or to erase a real bad object, then only
`--force-with-lease`, never `--force`. `dev`, `base` and `main` are never
force-pushed.

## Landing

**Default merge authority is dazewell.** A session may press merge only under a
named approval, and the holder is whichever session holds it — not a role. Every
condition below must hold; if any fails, hand back the PR URL and the decision.

- **Explicit in-session approval naming the PR.** A bare "go ahead" is not it.
  Approval authorises **the button, never the evidence**: it waives no review,
  no hard-line grep, no `#slug` check. It binds to the **reviewed head SHA** —
  a later commit makes it stale, so re-verify and re-ask.
- **Non-transferable.** Held by *this* session, closed per PR when it ends. A
  successor re-asks for anything still unmerged.
- **`--admin` and `--auto` are forbidden for sessions, always.** `--admin`
  forces past branch protections and you hold the token that makes it
  available; `--auto` merges on a future state you never verified. The repo
  allows auto-merge for dazewell's own use; that is not a licence for agents.
- **Not while an upstream merge PR is open** unless dazewell orders the two.

Know the backstop's edge: the `dev` ruleset requires the `Every commit carries a
#tag` check, so an untagged commit cannot land. That is the **only** part the
platform enforces — the named approval, the fresh re-verify and the
`--admin`/`--auto` ban are process-only. These bind because you follow them,
not because GitHub stops you.

Squash PRs only: `allow_merge_commit: false`, `allow_squash_merge: true`,
`delete_branch_on_merge: true`. GitHub makes one `dev` commit from PR title plus
branch messages; the push to `dev` triggers `staging.yml`. Never pass
`--delete-branch`; deletion is automatic and safe:

```powershell
git fetch origin refs/pull/<N>/head:<local>
```

Preflight settings live:
`gh api repos/dazewell/DwellBoard --jq '{squash:.allow_squash_merge, msg:.squash_merge_commit_message, title:.squash_merge_commit_title}'`.
Stop unless `allow_squash_merge` is `true` and `squash_merge_commit_message` is
`COMMIT_MESSAGES`; otherwise `#slug` tags can vanish with CI green.
`squash_merge_commit_title` is `PR_TITLE`, so the PR title is permanent history.

Merge-time gate:
- Poll `.github/scripts/test-merge-preflight.ps1` with a deadline. It requires an
  open, non-draft PR into `dev`, the exact branch and approved SHA, `CLEAN`
  merge state, and no unresolved review threads. Any failure or timeout is
  stop-and-report. Never update/resolve while landing.
- Re-read the PR head through that script every poll; a new commit aborts and
  reopens non-CI gates.

```powershell
$deadline = (Get-Date).AddMinutes(10)
do {
  $LASTEXITCODE = 1
  .\.github\scripts\test-merge-preflight.ps1 -Repository dazewell/DwellBoard `
    -PullRequest $n -ExpectedHead $approvedHead -ExpectedBranch $branch
  if ($LASTEXITCODE -eq 0) { break }
  if ($LASTEXITCODE -ne 1) { throw 'Merge preflight has a terminal blocker; re-review and re-approve before landing.' }
  Start-Sleep -Seconds 15
} while ((Get-Date) -lt $deadline)
if ($LASTEXITCODE -ne 0) { throw 'Merge preflight did not become clean before the deadline.' }
```

- Current head needs `ci.yml` `success` for code, or if path-ignored, the
  required `Every commit carries a #tag` green. Classify by live
  `.github/workflows/ci.yml`; absence alone is ambiguous.
- Re-verify same head: attribution greps clean and the missing-`#slug` query
  clean. The preflight script owns PR state and review-thread checks.
- Merge with `gh pr merge <n> --squash --match-head-commit <headRefOid>`.
- Pass neither `--body` nor `--subject`; either can override `COMMIT_MESSAGES` /
  `PR_TITLE` and land an untagged `dev` commit.

### Batch landing

Order: declared blocker (`status:blocked` + `> Blocked until PR #<n> ...`),
stacked PR after base, same upstream file/hook point, feature before its
`*-fix`. Registry files (`FEATURES.md`, `strings_dwell.xml`, `Settings.java`,
`Defaults.kt`) are textual risk unless the hunk changes behaviour; classify by
hunk.

Before the first and each later merge: re-read blockers and confirm no upstream
merge PR is open. Between merges, wait for `dev`'s post-merge `ci.yml` on the
just-merged commit to finish `success`. `staging.yml` may cancel superseded
`dev` runs; that is expected. After the batch, if any merged commit touched a
path `staging.yml` does not ignore, require a successful `staging.yml` run on
the final such SHA with its artifact present. Record "no staging run expected"
only after proving every commit ignored.

### Rare local landing

Manual chore path only, never approval-based agent landing. The `dev` ruleset
still requires the tag check, so this works only for dazewell:

```powershell
git switch dev; git pull --ff-only origin dev
git merge --squash <YYYY-MM-DD>_<slug>      # stage the change, no commit yet
git commit -m "<summary> #<slug>"           # one commit; carry the slug so it stays greppable
git push origin dev                          # -> staging.yml builds the APK artifact
git branch -d <YYYY-MM-DD>_<slug>            # local only
```

A never-PR'd branch has no `refs/pull`. Worktree cleanup still requires
process-lifecycle checks.

## Later fix

Use a new dated fix branch and the same feature tag:

```powershell
git switch dev; git pull --ff-only origin dev
git switch -c <YYYY-MM-DD>_<slug>-fix dev
# ...implement, compile gate (local, else `ci.yml` on the PR), review...
git commit -m "<what the fix does> #<slug>"   # SAME slug as the feature
```

PR to `dev`, merge, delete. If user-visible, update the existing `FEATURES.md`.

## Merging from upstream

Manual and attended; there is no sync automation.

```powershell
git fetch upstream
git fetch origin
git switch base; git merge --ff-only upstream/main      # refuses if base ever diverged: stop and report
git push origin base                                     # ruleset: fast-forward only

$b = .\.github\scripts\new-branch-name.ps1 -Slug upstream-merge
git switch -c $b origin/dev
git merge base                                           # a real merge commit; exempt from the #tag rule
```

Resolve conflicts: our side is the `// DwellBoard:` marked lines and
`helium314.keyboard.dwell.**`; keep upstream's change around them and re-inject
the marked lines at the equivalent point. Never drop a marker silently — if
upstream removed the hook point, say so in the PR. Any fix-up commit after the
merge carries `#infra`. Then run the compile gate and
`.\gradlew.bat :app:testRunTestsUnitTest -PdwellTestBuildType=runTests` (or let `ci.yml` be the gate, and say
which), and PR into `dev` like any change. Never merge `upstream/main` into
`dev` directly, and never commit to `base`.

Changes are **never** offered upstream from this fork (HeliBoard's
`AI_USAGE.md`).

## Automation notes

`ci.yml` is the push/PR-to-`dev` gate: `:app:compileDebugNoMinifyJavaWithJavac`
plus `:app:testRunTestsUnitTest -PdwellTestBuildType=runTests`, and an R8 deep job when build files change.
Docs/hooks/agent/skill-only changes are path-ignored; workflow edits are not.

`process-rules.yml` runs on every PR: branch name and commit-tag checks. It
produces the required `Every commit carries a #tag` check.

`staging.yml` builds the APK and uploads it unzipped as an Actions artifact — on
push to `dev`, on the `build-apk` PR label, or on `workflow_dispatch`. A push to
`dev` also replaces `DwellBoard.apk` on the rolling `staging` pre-release, which
the README links. Nothing else is published. Verify pipeline edits by dispatch.

## Keeping this current

When flow changes, edit this file in the same session and keep `AGENTS.md`,
`CLAUDE.md` and `dwellboard-workflow` in sync.
