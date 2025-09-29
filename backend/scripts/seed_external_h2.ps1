<#
.SYNOPSIS
  Seed a local H2 database with the external queue table/sequence used by tests (PowerShell)

Usage:
  .\seed_external_h2.ps1               # creates .\external-h2-db.mv.db and seeds it with backend/src/main/resources/external_h2_seed.sql
  $env:DB_URL='jdbc:h2:./mydb'; .\seed_external_h2.ps1
  $env:DB_FILE='./mydb'; .\seed_external_h2.ps1

This script attempts to locate the H2 jar in the local Maven repository; if not found it will run mvn to copy dependencies.
#>

param()

Set-StrictMode -Version Latest
try {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
    $repoRoot = Resolve-Path "$scriptDir/.."
    $sqlFile = Join-Path $repoRoot 'src/main/resources/external_h2_seed.sql'
    $dbUrl = if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:h2:./external-h2-db' }

    # Try to locate H2 jar in local maven repo (~/.m2/repository)
    $m2 = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2'
    $h2Jar = Get-ChildItem -Path $m2 -Filter 'h2-*.jar' -Recurse -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    if (-not $h2Jar) {
        Write-Host "H2 jar not found in ~/.m2/repository; fetching dependency to $repoRoot/target/dependency..."
        Push-Location $repoRoot
        & mvn dependency:copy-dependencies -DincludeGroupIds=com.h2database -DoutputDirectory=target/dependency
        Pop-Location
        $depDir = Join-Path $repoRoot 'target/dependency'
        $h2Jar = Get-ChildItem -Path $depDir -Filter 'h2-*.jar' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    }

    if (-not $h2Jar) { throw "Failed to locate h2 jar." }
    if (-not (Test-Path $sqlFile)) { throw "SQL seed file not found: $sqlFile" }

    Write-Host "Seeding H2 DB at $dbUrl using SQL $sqlFile"
    & java -cp $h2Jar.FullName org.h2.tools.RunScript -url $dbUrl -script $sqlFile -user SA -password ""

    Write-Host "Seed complete. DB file(s) created in current directory (or in H2's storage)."
    Write-Host "You can inspect it with the H2 console: java -cp $($h2Jar.FullName) org.h2.tools.Console -url $dbUrl -user SA -password ''"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
