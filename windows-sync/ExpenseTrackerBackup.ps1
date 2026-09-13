param(
    [string]$Token = "CHANGE-ME",
    [int]$Port = 8765
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backupDir = Join-Path $root "backups"
$historyDir = Join-Path $backupDir "history"
New-Item -ItemType Directory -Force -Path $backupDir, $historyDir | Out-Null

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:$Port/")
$listener.Start()

Write-Host "ExpenseTracker backup server running on port $Port"
Write-Host "Backups are stored in $backupDir"
Write-Host "Press Ctrl+C to stop."

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    try {
        if ($ctx.Request.HttpMethod -ne "POST" -or $ctx.Request.Url.AbsolutePath -ne "/backup") {
            $ctx.Response.StatusCode = 404
            $ctx.Response.Close()
            continue
        }

        $provided = $ctx.Request.Headers["X-Expense-Token"]
        if ([string]::IsNullOrWhiteSpace($provided) -or $provided -ne $Token) {
            $ctx.Response.StatusCode = 401
            $bytes = [Text.Encoding]::UTF8.GetBytes("Unauthorized")
            $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
            $ctx.Response.Close()
            continue
        }

        $reader = New-Object IO.StreamReader($ctx.Request.InputStream, $ctx.Request.ContentEncoding)
        $body = $reader.ReadToEnd()
        $reader.Close()
        $null = $body | ConvertFrom-Json

        $latest = Join-Path $backupDir "latest.json"
        $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $history = Join-Path $historyDir "backup-$stamp.json"
        [IO.File]::WriteAllText($latest, $body, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($history, $body, [Text.UTF8Encoding]::new($false))

        $ctx.Response.StatusCode = 200
        $bytes = [Text.Encoding]::UTF8.GetBytes("OK")
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $ctx.Response.Close()
        Write-Host "$(Get-Date -Format s) Backup received."
    } catch {
        try {
            $ctx.Response.StatusCode = 400
            $bytes = [Text.Encoding]::UTF8.GetBytes("Bad backup payload")
            $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
            $ctx.Response.Close()
        } catch {}
    }
}
