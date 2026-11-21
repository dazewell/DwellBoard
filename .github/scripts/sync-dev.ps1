#requires -Version 7

# Start a change from the newest dev. Session tooling cuts its worktree branch
# from whatever dev it last saw, and a session can sit for hours before its
# first commit. Fetches origin/dev and fast-forwards the current branch onto it,
# but only while the branch is still empty: no commits of its own, never
# pushed, clean tree. Anything else is reported and left alone; bringing dev
# into a branch with work on it is a merge-forward (dwellboard-branch-flow).
#
# Runs as the Claude Code SessionStart hook; other tools run it by hand before
# the first edit. Never fails: a hook that errors blocks nothing, but noise.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Out-Result([string]$Status, [string]$Detail) {
    [pscustomobject]@{ syncDev = $Status; detail = $Detail } | ConvertTo-Json -Compress
    exit 0
}

& git fetch -q origin dev 2>$null
if ($LASTEXITCODE -ne 0) { Out-Result 'offline' 'could not fetch origin/dev; the branch was not checked' }

$Branch = & git symbolic-ref --short -q HEAD
if (-not $Branch) { Out-Result 'skipped' 'detached HEAD' }
if ($Branch -in 'dev', 'base', 'main') { Out-Result 'skipped' "on $Branch" }

# Not '@{upstream}': a branch cut from origin/dev tracks it without ever being pushed.
& git rev-parse -q --verify "refs/remotes/origin/$Branch" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { Out-Result 'skipped' "$Branch is already pushed" }

$Head = & git rev-parse HEAD
$Dev = & git rev-parse origin/dev
if ($Head -eq $Dev) { Out-Result 'up-to-date' "$Branch is at origin/dev $($Dev.Substring(0, 8))" }

& git merge-base --is-ancestor HEAD origin/dev
if ($LASTEXITCODE -ne 0) {
    $Behind = & git rev-list --count HEAD..origin/dev
    Out-Result 'has-commits' "$Branch has its own commits and is $Behind behind origin/dev; merge dev in if you need it"
}

if (@(& git status --porcelain).Count -gt 0) {
    Out-Result 'skipped' "$Branch has uncommitted changes; not fast-forwarding over them"
}

$Behind = & git rev-list --count HEAD..origin/dev
& git merge -q --ff-only origin/dev 2>$null
if ($LASTEXITCODE -ne 0) { Out-Result 'failed' "fast-forward of $Branch to origin/dev failed" }
Out-Result 'fast-forwarded' "$Branch moved $Behind commits to origin/dev $($Dev.Substring(0, 8))"
