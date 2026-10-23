#requires -Version 7

# After a PR is merged: waits for the merge, then for the staging.yml build that
# the push to dev produced for the merge commit, downloads that APK and installs
# it on the connected phone via install-apk.ps1 (which polls for a device, never
# uninstalls, and never touches other adb clients).
#
# Prints one JSON object. status is one of:
#   installed | no-device | multiple-devices | signature-mismatch | install-failed
#                    (install-apk.ps1's results, passed through)
#   not-merged       the PR is closed unmerged, or -MergeWaitMinutes ran out
#   no-staging-run   the merge touched only paths staging.yml ignores; nothing to install
#   build-failed     the dev build failed or never finished (run set)
#
# staging.yml cancels a superseded dev run when another push lands. If the merge
# commit's run was cancelled, the newest successful dev build is used instead and
# builtSha says which commit it is.

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$PullRequest,
    [ValidatePattern('^[^/]+/[^/]+$')]
    [string]$Repository,
    [ValidateRange(0, 1440)]
    [int]$MergeWaitMinutes = 240,
    [ValidateRange(1, 120)]
    [int]$BuildWaitMinutes = 30,
    [ValidateRange(0, 240)]
    [int]$DeviceWaitMinutes = 30,
    [string]$OutDir = (Join-Path $env:TEMP 'dwell-apk'),
    [string]$Serial
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

function Out-Result {
    param([string]$Status, [string]$Detail, $Run, [string]$BuiltSha)
    [pscustomobject]@{
        status = $Status; pullRequest = $PullRequest; detail = $Detail
        run = if ($Run) { $Run.html_url } else { $null }; builtSha = $BuiltSha
    } | ConvertTo-Json
}

if (-not $Repository) {
    $Repository = (Invoke-Gh @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner')).Trim()
}

# 1. Wait for the merge.
$MergeDeadline = (Get-Date).AddMinutes($MergeWaitMinutes)
while ($true) {
    $Pr = Invoke-Gh @('api', "repos/$Repository/pulls/$PullRequest") | ConvertFrom-Json
    if ($Pr.merged) { break }
    if ($Pr.state -ne 'open') { Out-Result 'not-merged' "PR is $($Pr.state), not merged." $null $null; return }
    if ((Get-Date) -ge $MergeDeadline) { Out-Result 'not-merged' 'Still open when the wait ran out.' $null $null; return }
    Start-Sleep -Seconds 60
}
$Sha = $Pr.merge_commit_sha

function Get-DevRuns {
    $Result = Invoke-Gh @('api', "repos/$Repository/actions/workflows/staging.yml/runs?event=push&branch=dev&per_page=20") | ConvertFrom-Json
    @($Result.workflow_runs | Sort-Object created_at -Descending)
}

# 2. Find the dev build for the merge commit. staging.yml skips pushes that touch
# only docs, .github, hooks or agent files (its paths-ignore); for those say so
# at once. Otherwise the run is coming, so poll for it until the build deadline
# rather than guessing from a short wait (Actions queues can be slow).
$BuildDeadline = (Get-Date).AddMinutes($BuildWaitMinutes)
$Files = @(Invoke-Gh @('api', '--paginate', "repos/$Repository/pulls/$PullRequest/files", '--jq', '.[].filename'))
$Ignored = '\.md$|^\.github/|^docs/|^\.githooks/|^\.claude/'
if (-not @($Files | Where-Object { $_ -notmatch $Ignored })) {
    Out-Result 'no-staging-run' 'The merge touched only paths staging.yml ignores (docs, .github, hooks, agent files).' $null $Sha
    return
}
$Run = $null
while (-not $Run) {
    $Run = Get-DevRuns | Where-Object { $_.head_sha -eq $Sha } | Select-Object -First 1
    if ($Run) { break }
    if ((Get-Date) -ge $BuildDeadline) { Out-Result 'build-failed' 'No staging.yml run appeared for the merge commit in time.' $null $Sha; return }
    Start-Sleep -Seconds 15
}

while ($Run.status -ne 'completed') {
    if ((Get-Date) -ge $BuildDeadline) { Out-Result 'build-failed' 'Build did not finish in time.' $Run $Sha; return }
    Start-Sleep -Seconds 20
    $Run = Invoke-Gh @('api', "repos/$Repository/actions/runs/$($Run.id)") | ConvertFrom-Json
}

if ($Run.conclusion -eq 'cancelled') {
    # Superseded by a later push to dev: that run's build contains this merge.
    $Cancelled = $Run
    $Run = $null
    while (-not $Run) {
        $Later = @(Get-DevRuns | Where-Object { [datetime]$_.created_at -ge [datetime]$Cancelled.created_at -and $_.id -ne $Cancelled.id })
        $Run = $Later | Where-Object { $_.conclusion -eq 'success' } | Select-Object -Last 1
        if ($Run -or @($Later | Where-Object { $_.status -ne 'completed' }).Count -eq 0 -or (Get-Date) -ge $BuildDeadline) { break }
        Start-Sleep -Seconds 20
    }
    if (-not $Run) { Out-Result 'build-failed' 'Merge-commit run was cancelled and no later dev build succeeded.' $Cancelled $Sha; return }
} elseif ($Run.conclusion -ne 'success') {
    Out-Result 'build-failed' "Dev build concluded $($Run.conclusion)." $Run $Sha
    return
}

# 3. Download the unzipped .apk artifact (same recipe as request-apk-build.ps1).
$List = Invoke-Gh @('api', "repos/$Repository/actions/runs/$($Run.id)/artifacts") | ConvertFrom-Json
$Artifact = @($List.artifacts | Where-Object { $_.name -like '*.apk' }) | Select-Object -First 1
if (-not $Artifact) { Out-Result 'build-failed' 'Build succeeded but has no .apk artifact.' $Run $Run.head_sha; return }

$Dir = Join-Path $OutDir "dev-$($Run.head_sha.Substring(0, 8))"
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
$Apk = Join-Path $Dir $Artifact.name
"header = `"Authorization: Bearer $(gh auth token)`"" | & curl.exe -sfL -K - -o $Apk "https://api.github.com/repos/$Repository/actions/artifacts/$($Artifact.id)/zip"
if ($LASTEXITCODE -ne 0) { Remove-Item $Apk -ErrorAction SilentlyContinue; throw 'artifact download failed.' }
$Magic = [IO.File]::ReadAllBytes($Apk)[0..1]
if ($Magic[0] -ne 0x50 -or $Magic[1] -ne 0x4B) { Remove-Item $Apk; throw 'downloaded file is not an APK.' }

# 4. Install; the result is install-apk.ps1's JSON, with the built commit added.
$InstallArgs = @{ ApkPath = $Apk; DeviceWaitMinutes = $DeviceWaitMinutes }
if ($Serial) { $InstallArgs.Serial = $Serial }
$Install = & "$PSScriptRoot\install-apk.ps1" @InstallArgs | ConvertFrom-Json
$Install | Add-Member -NotePropertyName builtSha -NotePropertyValue $Run.head_sha
$Install | Add-Member -NotePropertyName run -NotePropertyValue $Run.html_url
$Install | ConvertTo-Json
