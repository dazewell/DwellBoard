#requires -Version 7

# Re-reads everything the handback "State" block needs, from GitHub, so the
# report never rests on memory: the PR head SHA, whether the local branch is
# that head, the ci.yml / process-rules / staging runs pinned to that SHA, the
# unresolved review thread count, and the last commits. An absent run is
# reported as absent (null), never as a pass.

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$PullRequest,
    [ValidatePattern('^[^/]+/[^/]+$')]
    [string]$Repository
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Invoke-Gh {
    param([string[]]$Arguments)
    $Output = & gh @Arguments
    if ($LASTEXITCODE -ne 0) { throw "gh $($Arguments -join ' ') failed with exit code $LASTEXITCODE." }
    $Output
}

if (-not $Repository) {
    $Repository = (Invoke-Gh @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner')).Trim()
}

$Pr = Invoke-Gh @('api', "repos/$Repository/pulls/$PullRequest") | ConvertFrom-Json
$Sha = $Pr.head.sha

function Get-RunFor {
    param([string]$Workflow)
    $Runs = (Invoke-Gh @('api', "repos/$Repository/actions/workflows/$Workflow/runs?head_sha=$Sha&per_page=10") | ConvertFrom-Json).workflow_runs
    $Run = @($Runs | Sort-Object created_at -Descending) | Select-Object -First 1
    if (-not $Run) { return $null }
    [pscustomobject]@{ status = $Run.status; conclusion = $Run.conclusion; url = $Run.html_url }
}

$Threads = @(& "$PSScriptRoot\get-review-threads.ps1" -Repository $Repository -PullRequest $PullRequest | ConvertFrom-Json)
$Unresolved = @($Threads | Where-Object { -not $_.isResolved }).Count
$LocalHead = (& git rev-parse HEAD).Trim()
$Commits = @(Invoke-Gh @('api', "repos/$Repository/pulls/$PullRequest/commits?per_page=100") | ConvertFrom-Json |
    Select-Object -Last 5 | ForEach-Object { "$($_.sha.Substring(0, 8)) $(($_.commit.message -split "`n")[0])" })

[pscustomobject]@{
    pullRequest       = $PullRequest
    url               = $Pr.html_url
    state             = $Pr.state
    draft             = $Pr.draft
    mergeableState    = $Pr.mergeable_state
    headSha           = $Sha
    localHeadMatches  = ($LocalHead -eq $Sha)
    ci                = Get-RunFor 'ci.yml'
    processRules      = Get-RunFor 'process-rules.yml'
    staging           = Get-RunFor 'staging.yml'
    unresolvedThreads = $Unresolved
    lastCommits       = $Commits
} | ConvertTo-Json -Depth 4
