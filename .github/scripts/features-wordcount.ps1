#requires -Version 7

# Counts the words in each FEATURES.md entry the way the 70-word ceiling is
# meant: prose under a "### " heading, excluding the heading, HTML comments,
# images and tables. Prints entries over the ceiling (all of them with -All;
# one with -Slug).

[CmdletBinding()]
param(
    [string]$Slug,
    [switch]$All,
    [int]$Ceiling = 70
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$Root = (& git rev-parse --show-toplevel).Trim()
$Entries = @()
$Current = $null
foreach ($Line in Get-Content (Join-Path $Root 'FEATURES.md')) {
    if ($Line -match '^#{1,3} ') {
        if ($Current) { $Entries += $Current }
        $Current = $null
        if ($Line -match '^### ') {
            $Current = [pscustomobject]@{
                heading = ($Line -replace '<!--.*?-->', '' -replace '^### ', '').Trim()
                slugs   = @([regex]::Matches($Line, '<!--\s*#([a-z0-9-]+)\s*-->') | ForEach-Object { $_.Groups[1].Value })
                words   = 0
            }
        }
        continue
    }
    if (-not $Current) { continue }
    if ($Line -match '^\s*\|' -or $Line -match '^\s*!\[') { continue }
    $Text = $Line -replace '<!--.*?-->', '' -replace '!\[[^\]]*\]\([^)]*\)', ''
    $Current.words += @($Text -split '\s+' | Where-Object { $_ -match '\w' }).Count
}
if ($Current) { $Entries += $Current }

if ($Slug) { $Entries = @($Entries | Where-Object { $_.slugs -contains $Slug }) }
elseif (-not $All) { $Entries = @($Entries | Where-Object { $_.words -gt $Ceiling }) }

[pscustomobject]@{
    ceiling = $Ceiling
    entries = @($Entries | ForEach-Object { [pscustomobject]@{ heading = $_.heading; slugs = $_.slugs; words = $_.words; over = ($_.words -gt $Ceiling) } })
} | ConvertTo-Json -Depth 4
