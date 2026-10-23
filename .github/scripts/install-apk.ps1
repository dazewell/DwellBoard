#requires -Version 7

# Installs a staging APK on the one connected device with `adb install -r`: the
# APK of a PR (built first if needed, via request-apk-build.ps1), or an
# already-downloaded one passed as -ApkPath (install-dev-build.ps1 does this).
# -DeviceWaitMinutes polls for a phone instead of returning no-device at once,
# for unattended runs; interactive runs leave it 0 and ask dazewell to connect.
# Never uninstalls: a signature mismatch is reported and left for dazewell to decide. Never runs adb kill-server or touches other adb
# clients. The APK goes to a directory outside the repo.
#
# Prints JSON: status is installed | no-device | multiple-devices |
# signature-mismatch | build-failed | install-failed.

[CmdletBinding()]
param(
    [Parameter(Mandatory, ParameterSetName = 'Pr')]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$PullRequest,
    [Parameter(Mandatory, ParameterSetName = 'Apk')]
    [string]$ApkPath,
    [Parameter(ParameterSetName = 'Pr')]
    [string]$OutDir = (Join-Path $env:TEMP 'dwell-apk'),
    [string]$Serial,
    [ValidateRange(0, 240)]
    [int]$DeviceWaitMinutes = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Out-Result {
    param([string]$Status, [string]$Apk, [string]$Detail)
    [pscustomobject]@{ status = $Status; apk = $Apk; detail = $Detail } | ConvertTo-Json
}

$Adb = (Get-Command adb -ErrorAction SilentlyContinue)?.Source
if (-not $Adb -and $env:ANDROID_HOME) { $Adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }
if (-not $Adb -or -not (Test-Path $Adb)) { throw 'adb not found.' }

if ($ApkPath) {
    if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath" }
    $Build = [pscustomobject]@{ apkPath = $ApkPath }
} else {
    $Dir = Join-Path $OutDir "pr$PullRequest"
    $Build = & "$PSScriptRoot/request-apk-build.ps1" -PullRequest $PullRequest -Wait -OutDir $Dir | ConvertFrom-Json
    if (-not $Build.apkPath) { Out-Result 'build-failed' $null "build status: $($Build.status); run: $($Build.run)"; return }
}

$Deadline = (Get-Date).AddMinutes($DeviceWaitMinutes)
do {
    $Devices = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "	device$" } | ForEach-Object { ($_ -split "	")[0] })
    if ($Serial) { $Devices = @($Devices | Where-Object { $_ -eq $Serial }) }
    if ($Devices.Count -gt 0 -or (Get-Date) -ge $Deadline) { break }
    Start-Sleep -Seconds 15
} while ($true)
if ($Devices.Count -eq 0) { Out-Result 'no-device' $Build.apkPath 'No authorised device connected; APK is downloaded.'; return }
if ($Devices.Count -gt 1) { Out-Result 'multiple-devices' $Build.apkPath "Pass -Serial. Devices: $($Devices -join ', ')"; return }

$Output = (& $Adb -s $Devices[0] install -r $Build.apkPath 2>&1) -join "`n"
if ($Output -match 'Success') { Out-Result 'installed' $Build.apkPath $null }
elseif ($Output -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Out-Result 'signature-mismatch' $Build.apkPath 'A differently-signed build is installed. Ask dazewell before uninstalling it.'
} else { Out-Result 'install-failed' $Build.apkPath $Output }
