#requires -Version 7

# Requests the installable APK for a pull request's current head, the one thing
# to call whenever dazewell is about to be asked to test something. It applies
# the build-apk label (staging.yml starts, then removes the label) and reuses a
# build that already exists for the same head instead of starting another.
#
# Prints one JSON object. status is one of:
#   requested       the build was started; nothing waited for (run set if found)
#   already-built   a successful build for this head exists (artifact set)
#   in-progress     a build for this head is already running; nothing re-sent
#   built           the build finished while waiting (artifact set)
#   failed          the build finished unsuccessfully (run set)
#   timed-out       the build did not finish in time (run set)
# Refusals and API failures throw instead.
#
# -Wait blocks until the build ends. -OutDir (needs -Wait or an existing build)
# also downloads the .apk into that directory; keep it outside the repo.

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$PullRequest,
    [ValidatePattern('^[^/]+/[^/]+$')]
    [string]$Repository,
    [switch]$Wait,
    [ValidateRange(1, 120)]
    [int]$TimeoutMinutes = 30,
    [string]$OutDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Invoke-Gh {
    param([string[]]$Arguments)
    $Output = & gh @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gh $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
    $Output
}

if (-not $Repository) {
    $Repository = (Invoke-Gh @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner')).Trim()
}

$Pr = Invoke-Gh @('api', "repos/$Repository/pulls/$PullRequest") | ConvertFrom-Json
if ($Pr.state -ne 'open') { throw "PR $PullRequest is $($Pr.state); only an open PR can be built." }
if ($Pr.head.repo.full_name -ne $Repository) { throw "PR $PullRequest is from a fork; staging.yml never builds those." }
$Sha = $Pr.head.sha

function Get-Runs {
    # Newest first. Runs for the PR head started by the label event.
    $Result = Invoke-Gh @('api', "repos/$Repository/actions/workflows/staging.yml/runs?event=pull_request&head_sha=$Sha&per_page=20") | ConvertFrom-Json
    @($Result.workflow_runs | Sort-Object created_at -Descending)
}

function Get-Artifact {
    param($Run)
    $List = Invoke-Gh @('api', "repos/$Repository/actions/runs/$($Run.id)/artifacts") | ConvertFrom-Json
    @($List.artifacts | Where-Object { $_.name -like '*.apk' }) | Select-Object -First 1
}

function Save-Apk {
    param($Artifact)
    if (-not $OutDir) { return $null }
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $Zip = Join-Path $OutDir "$($Artifact.name).zip"
    # gh run download would unpack the APK; the artifact API gives the file.
    & curl.exe -sL -H "Authorization: Bearer $(gh auth token)" -o $Zip "https://api.github.com/repos/$Repository/actions/artifacts/$($Artifact.id)/zip"
    if ($LASTEXITCODE -ne 0) { throw 'artifact download failed.' }
    Expand-Archive -Force -Path $Zip -DestinationPath $OutDir
    Remove-Item $Zip
    (Join-Path $OutDir $Artifact.name)
}

function Out-Result {
    param([string]$Status, $Run, $Artifact)
    $Apk = $null
    if ($Artifact) { $Apk = Save-Apk $Artifact }
    [pscustomobject]@{
        status      = $Status
        pullRequest = $PullRequest
        headSha     = $Sha
        run         = if ($Run) { $Run.html_url } else { $null }
        artifact    = if ($Artifact) { "https://github.com/$Repository/actions/runs/$($Run.id)/artifacts/$($Artifact.id)" } else { $null }
        apkPath     = $Apk
    } | ConvertTo-Json
}

function Wait-Run {
    param($Run, [datetime]$Deadline)
    while ($Run.status -ne 'completed') {
        if ((Get-Date) -gt $Deadline) { return $Run }
        Start-Sleep -Seconds 20
        $Run = Invoke-Gh @('api', "repos/$Repository/actions/runs/$($Run.id)") | ConvertFrom-Json
    }
    $Run
}

$Deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$Existing = @(Get-Runs | Where-Object { $_.conclusion -in @($null, 'success') })

# One build per head: reuse rather than burn another run.
foreach ($Run in $Existing) {
    if ($Run.conclusion -eq 'success') {
        $Artifact = Get-Artifact $Run
        if ($Artifact) { Out-Result 'already-built' $Run $Artifact; return }
    } else {
        if ($Wait) {
            $Run = Wait-Run $Run $Deadline
            if ($Run.status -ne 'completed') { Out-Result 'timed-out' $Run $null; return }
            if ($Run.conclusion -ne 'success') { Out-Result 'failed' $Run $null; return }
            Out-Result 'built' $Run (Get-Artifact $Run); return
        }
        Out-Result 'in-progress' $Run $null; return
    }
}

$Started = (Get-Date).ToUniversalTime().AddSeconds(-30)
Invoke-Gh @('api', '-X', 'POST', "repos/$Repository/issues/$PullRequest/labels", '-f', 'labels[]=build-apk') | Out-Null

# The run appears a few seconds after the label lands.
$Run = $null
for ($i = 0; $i -lt 12 -and -not $Run; $i++) {
    Start-Sleep -Seconds 5
    $Run = Get-Runs | Where-Object { [datetime]$_.created_at -ge $Started } | Select-Object -First 1
}

if (-not $Wait -or -not $Run) { Out-Result 'requested' $Run $null; return }

$Run = Wait-Run $Run $Deadline
if ($Run.status -ne 'completed') { Out-Result 'timed-out' $Run $null; return }
if ($Run.conclusion -ne 'success') { Out-Result 'failed' $Run $null; return }
Out-Result 'built' $Run (Get-Artifact $Run)
