#requires -Version 7

# Runs the local compile gate (and, with -Tests, the unit tests) and prints a
# short JSON verdict instead of Gradle's log: pass/fail, the compiler errors,
# and the failing tests with the top of each stack trace. The full log goes to
# a file under $env:TEMP, outside the worktree.
#
# Uses --no-daemon, so there is no daemon to stop afterwards and nothing is
# registered in the default Gradle home. status is one of:
#   passed        every requested gate passed
#   failed        compile errors or failing tests (errors / failures set)
#   environment   JDK or Android SDK missing; let ci.yml be the gate instead
# Never claim a gate you did not run: gates lists what actually ran.

[CmdletBinding()]
param(
    # Also run :app:testRunTestsUnitTest.
    [switch]$Tests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$Root = (& git rev-parse --show-toplevel).Trim()
Set-Location $Root

function Out-Verdict {
    param([string]$Status, [string[]]$Gates, $Errors, $Failures, [string]$Log, [double]$Seconds, [string]$Note)
    [pscustomobject]@{
        status   = $Status
        gates    = @($Gates)
        seconds  = [math]::Round($Seconds)
        errors   = @($Errors)
        failures = @($Failures)
        log      = $Log
        note     = $Note
    } | ConvertTo-Json -Depth 4
}

$HasJdk = [bool](Get-Command java -ErrorAction SilentlyContinue) -or [bool]$env:JAVA_HOME
$HasSdk = [bool]$env:ANDROID_HOME -or [bool]$env:ANDROID_SDK_ROOT -or (Test-Path (Join-Path $Root 'local.properties'))
if (-not ($HasJdk -and $HasSdk)) {
    Out-Verdict 'environment' @() @() @() $null 0 'JDK or Android SDK not found. Do not install one; ci.yml is the gate. Say so in the PR body.'
    return
}

$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$Log = Join-Path $env:TEMP "dwell-gate-$Stamp.log"
$Tasks = @(':app:compileDebugNoMinifyJavaWithJavac')
if ($Tests) { $Tasks += ':app:testRunTestsUnitTest' }

$Clock = [Diagnostics.Stopwatch]::StartNew()
$Ran = @()
$Errors = @()
$Failures = @()

# Compile first, on its own, so a compile error is not buried under test output.
& "$Root\gradlew.bat" --no-daemon $Tasks[0] *> $Log
$Ran += 'compile'
if ($LASTEXITCODE -ne 0) {
    $Errors = @(Select-String -Path $Log -Pattern '^e: |error: |FAILURE: |What went wrong' -Context 0, 1 |
        ForEach-Object { $_.Line.Trim() } | Select-Object -Unique -First 30)
    Out-Verdict 'failed' $Ran $Errors @() $Log $Clock.Elapsed.TotalSeconds $null
    return
}

if ($Tests) {
    & "$Root\gradlew.bat" --no-daemon $Tasks[1] -PdwellTestBuildType=runTests *>> $Log
    $Ran += 'unit-tests'
    if ($LASTEXITCODE -ne 0) {
        $Results = Join-Path $Root 'app\build\test-results\testRunTestsUnitTest'
        foreach ($File in Get-ChildItem $Results -Filter '*.xml' -ErrorAction SilentlyContinue) {
            $Xml = [xml](Get-Content $File.FullName -Raw)
            foreach ($Case in $Xml.testsuite.testcase) {
                if ($Case.PSObject.Properties['failure'] -and $Case.failure) {
                    $Trace = ($Case.failure.InnerText -split "`r?`n" | Select-Object -First 6) -join "`n"
                    $Failures += [pscustomobject]@{ test = "$($Case.classname).$($Case.name)"; trace = $Trace }
                }
            }
        }
        if (-not $Failures) {
            $Errors = @(Select-String -Path $Log -Pattern 'FAILURE: |What went wrong' -Context 0, 2 |
                ForEach-Object { $_.Line.Trim() } | Select-Object -Unique -First 10)
        }
        Out-Verdict 'failed' $Ran $Errors $Failures $Log $Clock.Elapsed.TotalSeconds $null
        return
    }
}

Out-Verdict 'passed' $Ran @() @() $Log $Clock.Elapsed.TotalSeconds $null
