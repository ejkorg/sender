<#
PowerShell port of predeploy_check.sh
Usage: .\predeploy_check.ps1 -dbtype <oracle|postgres> -user <user> -host <host> -db <db> [-dedupe] [-connect <connect-string>]
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('oracle','postgres')][string]$dbtype,
    [string]$user,
    [string]$host,
    [string]$db,
    [switch]$dedupe,
    [string]$connect
)

Set-StrictMode -Version Latest
try {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
    if ($dbtype -eq 'postgres') {
        if (-not $user -or -not $host -or -not $db) { throw "For postgres provide -user, -host and -db" }
        $findSql = Join-Path $scriptDir 'find_duplicate_sender_queue.sql'
        $dedupeSql = Join-Path $scriptDir 'dedupe_sender_queue_keep_lowest_id.sql'
        Write-Host "Running duplicate check (postgres)..."
        & psql -h $host -U $user -d $db -f $findSql
        if ($dedupe) {
            Write-Host "Running dedupe (postgres) — ensure you have a backup and run in maintenance window..."
            $confirm = Read-Host "Type YES to continue"
            if ($confirm -eq 'YES') {
                & psql -h $host -U $user -d $db -c "BEGIN;" -f $dedupeSql -c "COMMIT;"
                Write-Host "Dedupe completed."
            } else { Write-Host "Aborted by user."; exit 1 }
        }
    } elseif ($dbtype -eq 'oracle') {
        $findSql = Join-Path $scriptDir 'find_duplicate_sender_queue_oracle.sql'
        $dedupeSql = Join-Path $scriptDir 'dedupe_sender_queue_oracle.sql'
        if (-not $connect) { throw "For oracle provide -connect user/password@//host:port/SID via -connect" }
        Write-Host "Running duplicate check (oracle)..."
        & sqlplus -s $connect "@${findSql}"
        if ($dedupe) {
            Write-Host "Running dedupe (oracle) — ensure you have a backup and run in maintenance window..."
            $confirm = Read-Host "Type YES to continue"
            if ($confirm -eq 'YES') {
                & sqlplus -s $connect "@${dedupeSql}"
                Write-Host "Dedupe completed."
            } else { Write-Host "Aborted by user."; exit 1 }
        }
    } else {
        throw "Unsupported dbtype: $dbtype"
    }
    Write-Host "Pre-deploy check completed."
} catch {
    Write-Error $_.Exception.Message
    exit 2
}
