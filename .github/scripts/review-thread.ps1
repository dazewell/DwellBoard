#requires -Version 7

# Review-thread chores in one place, so nobody hand-writes the GraphQL or the
# shell quoting.
#   -Action list     unresolved threads: threadId, commentId, path, line, body
#   -Action reply    reply in-thread (-CommentId, and -Body or -BodyFile)
#   -Action resolve  resolve a thread (-ThreadId, the PRRT_... node id)
# Every thread needs a fix or a reply saying why not, then a resolve.

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('list', 'reply', 'resolve')]
    [string]$Action,
    [ValidateRange(1, [int]::MaxValue)]
    [int]$PullRequest,
    [ValidatePattern('^[^/]+/[^/]+$')]
    [string]$Repository,
    [long]$CommentId,
    [string]$ThreadId,
    [string]$Body,
    [string]$BodyFile
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

switch ($Action) {
    'list' {
        if (-not $PullRequest) { throw '-PullRequest is required for list.' }
        $Open = @(& "$PSScriptRoot\get-review-threads.ps1" -Repository $Repository -PullRequest $PullRequest | ConvertFrom-Json |
            Where-Object { -not $_.isResolved })
        ConvertTo-Json -InputObject $Open -Depth 5
    }
    'reply' {
        if (-not $PullRequest -or -not $CommentId) { throw '-PullRequest and -CommentId are required for reply.' }
        if ($BodyFile) { $Body = [IO.File]::ReadAllText($BodyFile) }
        if (-not $Body) { throw 'Give -Body or -BodyFile.' }
        $Temp = Join-Path $env:TEMP "dwell-reply-$PID.md"
        [IO.File]::WriteAllText($Temp, $Body, [Text.UTF8Encoding]::new($false))
        try {
            Invoke-Gh @('api', "repos/$Repository/pulls/$PullRequest/comments/$CommentId/replies", '-F', "body=@$Temp", '--jq', '{id: .id, url: .html_url}')
        } finally { Remove-Item $Temp -ErrorAction SilentlyContinue }
    }
    'resolve' {
        if (-not $ThreadId) { throw '-ThreadId is required for resolve.' }
        Invoke-Gh @('api', 'graphql', '-f', 'query=mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}', '-F', "id=$ThreadId")
    }
}
