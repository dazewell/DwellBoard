#requires -Version 7

# Read-only end-of-session check for this worktree: uncommitted files, commits
# not pushed, and processes whose command line references the worktree path
# (anything holding it open is a risk before archiving). Kills nothing; stop
# what is yours by exact PID per dwellboard-process-lifecycle. Run it before the
# handback and paste its result into the process ledger section.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$Root = (Resolve-Path (& git rev-parse --show-toplevel)).ProviderPath.TrimEnd('\')
$Dirty = @(& git status --porcelain)
$Upstream = (& git rev-parse --abbrev-ref '@{u}' 2>$null)
$Unpushed = @(if ($LASTEXITCODE -eq 0 -and $Upstream) { & git log --oneline "$Upstream..HEAD" } else { '<no upstream: branch never pushed>' })

$All = @(Get-CimInstance Win32_Process)
# Skip only this checker and its own ancestors (the shell that launched it), so
# a lingering PowerShell watcher on the worktree is still reported.
$Self = [System.Collections.Generic.HashSet[uint32]]::new()
$Next = [uint32]$PID
while ($Next -and $Self.Add($Next)) {
    $Parent = $All | Where-Object { $_.ProcessId -eq $Next } | Select-Object -First 1
    $Next = if ($Parent) { [uint32]$Parent.ParentProcessId } else { 0 }
}
$Holders = @($All |
    Where-Object { -not $Self.Contains([uint32]$_.ProcessId) -and $_.CommandLine -and $_.CommandLine.IndexOf($Root, [StringComparison]::OrdinalIgnoreCase) -ge 0 } |
    ForEach-Object { [pscustomobject]@{ pid = $_.ProcessId; name = $_.Name; started = $_.CreationDate.ToString('s'); command = $_.CommandLine.Substring(0, [math]::Min(160, $_.CommandLine.Length)) } })

[pscustomobject]@{
    worktree      = $Root
    uncommitted   = $Dirty
    unpushed      = $Unpushed
    holdingProcs  = $Holders
    clean         = ($Dirty.Count -eq 0 -and $Unpushed.Count -eq 0 -and $Holders.Count -eq 0)
} | ConvertTo-Json -Depth 4
