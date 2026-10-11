#requires -Version 7

# Gets the staging APK for a PR (building it first if needed, via
# request-apk-build.ps1) and installs it on the one connected device with
# `adb install -r`. Never uninstalls: a signature mismatch is reported and left
# for dazewell to decide. Never runs adb kill-server or touches other adb
# clients. The APK goes to a directory outside the repo.
#
# Prints JSON: status is installed | no-device | multiple-devices |
# signature-mismatch | build-failed | install-failed.

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$PullRequest,
    [string]$OutDir = (Join-Path $env:TEMP 'dwell-apk'),
    [string]$Serial
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

$Dir = Join-Path $OutDir "pr$PullRequest"
$Build = & "$PSScriptRoot\request-apk-build.ps1" -PullRequest $PullRequest -Wait -OutDir $Dir | ConvertFrom-Json
if (-not $Build.apkPath) { Out-Result 'build-failed' $null "build status: $($Build.status); run: $($Build.run)"; return }

$Devices = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
if ($Serial) { $Devices = @($Devices | Where-Object { $_ -eq $Serial }) }
if ($Devices.Count -eq 0) { Out-Result 'no-device' $Build.apkPath 'No authorised device connected; APK is downloaded.'; return }
if ($Devices.Count -gt 1) { Out-Result 'multiple-devices' $Build.apkPath "Pass -Serial. Devices: $($Devices -join ', ')"; return }

$Output = (& $Adb -s $Devices[0] install -r $Build.apkPath 2>&1) -join "`n"
if ($Output -match 'Success') { Out-Result 'installed' $Build.apkPath $null }
elseif ($Output -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Out-Result 'signature-mismatch' $Build.apkPath 'A differently-signed build is installed. Ask dazewell before uninstalling it.'
} else { Out-Result 'install-failed' $Build.apkPath $Output }
