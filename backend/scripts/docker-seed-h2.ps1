<#
.SYNOPSIS
  Seed a local H2 database using external_h2_seed.sql (PowerShell version)

.DESCRIPTION
  Attempts to locate an h2-*.jar in the user's Maven repository (%USERPROFILE%\.m2\repository).
  If not found it will attempt to run `mvn -f backend/pom.xml dependency:copy-dependencies`
  to fetch dependencies into backend/target/dependency. Then it runs org.h2.tools.RunScript
  to apply the SQL file.

  This script is intended for Windows/PowerShell developers. It does not modify production
  databases; the default target is a file-backed H2 DB in the `backend` directory.

.PARAMETER DbUrl
  JDBC URL for H2. Default: jdbc:h2:./external-h2-db

.EXAMPLE
  .\backend\scripts\docker-seed-h2.ps1

.EXAMPLE
  $env:DB_URL='jdbc:h2:./mydb'; .\backend\scripts\docker-seed-h2.ps1
#>

param(
  [string]$DbUrl = $(if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:h2:./external-h2-db' })
)

Set-StrictMode -Version Latest
Write-Host "Seed H2 DB: $DbUrl"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Resolve-Path (Join-Path $scriptDir '..')
$seedSql = Join-Path $repoRoot 'src\main\resources\external_h2_seed.sql'

# Try to find h2 jar in local maven repo
$h2Jar = $null
$m2Repo = Join-Path $env:USERPROFILE '.m2\repository'
if (Test-Path $m2Repo) {
  $h2Jar = Get-ChildItem -Path $m2Repo -Filter 'h2-*.jar' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 | ForEach-Object { $_.FullName }
}

if (-not $h2Jar) {
  Write-Host "H2 jar not found in %USERPROFILE%\.m2\repository. Attempting to fetch dependencies with Maven..."
  $mvn = Get-Command mvn -ErrorAction SilentlyContinue
  if ($null -eq $mvn) {
    Write-Error "Maven (mvn) not found on PATH. Please install Maven or place an H2 jar and set H2_JAR environment variable."
    exit 2
  }
  Push-Location $repoRoot
  & mvn -f backend/pom.xml dependency:copy-dependencies -DoutputDirectory=target/dependency
  Pop-Location
  $depDir = Join-Path $repoRoot 'target\dependency'
  if (Test-Path $depDir) {
    $h2Jar = Get-ChildItem -Path $depDir -Filter 'h2-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1 | ForEach-Object { $_.FullName }
  }
}

if (-not $h2Jar) {
  Write-Error "Unable to locate h2 jar. Please ensure Maven dependencies are available or set H2_JAR environment variable."
  exit 3
}

Write-Host "Using H2 jar: $h2Jar"

$java = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $java) {
  Write-Error "Java not found on PATH. Please install a JDK/JRE."
  exit 4
}

if ($DbUrl -like 'jdbc:h2:./*') {
  $dataDir = Join-Path $repoRoot 'external-h2-data'
  if (-not (Test-Path $dataDir)) { New-Item -ItemType Directory -Path $dataDir | Out-Null }
}

Write-Host "Running RunScript against $DbUrl using $seedSql"

& java -cp $h2Jar org.h2.tools.RunScript -url $DbUrl -script $seedSql -user sa -password ''
if ($LASTEXITCODE -ne 0) {
  Write-Error "RunScript failed with exit code $LASTEXITCODE"
  exit $LASTEXITCODE
}

Write-Host "Seed completed. H2 DB at: $($DbUrl -replace '^jdbc:h2:','')"
