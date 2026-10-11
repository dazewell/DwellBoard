#requires -Version 7

# Pushes the current branch and opens a non-draft PR into dev, the standing last
# step of a change. Idempotent: if the branch already has an open PR it only
# pushes and returns that PR. Refuses dev/base/main, an undated branch name and
# an uncommitted tree. The body comes from a file, so backticks and $ in prose
# survive the shell.
#
# Prints JSON: status is created | exists, plus number and url.

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Title,
    [Parameter(Mandatory)]
    [string]$BodyFile,
    [ValidatePattern('^[^/]+/[^/]+$')]
    [string]$Repository
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Invoke-Native {
    param([string]$Command, [string[]]$Arguments)
    $Output = & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command $($Arguments -join ' ') failed with exit code $LASTEXITCODE." }
    $Output
}

if (-not $Repository) {
    $Repository = (Invoke-Native gh @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner')).Trim()
}
$Branch = (Invoke-Native git @('branch', '--show-current')).Trim()
if ($Branch -in @('dev', 'base', 'main', '')) { throw "On '$Branch'; cut a dated feature branch first." }
& "$PSScriptRoot\test-branch-name.ps1" -Branch $Branch | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Branch '$Branch' is not <YYYY-MM-DD>_<slug>." }
if (-not (Test-Path $BodyFile)) { throw "Body file not found: $BodyFile" }
if (Invoke-Native git @('status', '--porcelain')) { throw 'Uncommitted changes; commit them first.' }

Invoke-Native git @('push', '-u', 'origin', 'HEAD') 2>&1 | Out-Null

$Existing = Invoke-Native gh @('pr', 'list', '--repo', $Repository, '--head', $Branch, '--state', 'open', '--json', 'number,url') | ConvertFrom-Json
if (@($Existing).Count -gt 0) {
    [pscustomobject]@{ status = 'exists'; number = $Existing[0].number; url = $Existing[0].url } | ConvertTo-Json
    return
}

$Url = (Invoke-Native gh @('pr', 'create', '--repo', $Repository, '--base', 'dev', '--head', $Branch, '--title', $Title, '--body-file', $BodyFile) | Select-Object -Last 1).Trim()
$Number = [int]($Url -split '/' | Select-Object -Last 1)
[pscustomobject]@{ status = 'created'; number = $Number; url = $Url } | ConvertTo-Json
