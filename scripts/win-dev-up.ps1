#!/usr/bin/env pwsh
<#
Helper for Windows developers (PowerShell) to fetch backend dependencies, seed H2 and bring the stack up with Docker.
Usage: Open PowerShell (preferably PowerShell 7) in repo root and run: .\scripts\win-dev-up.ps1
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Ensure-Command($name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        Write-Error "$name not found on PATH. Please install it before continuing."
        exit 1
    }
    return $cmd
}

Write-Host "Running Windows dev helper (fetch deps, seed H2, docker compose up)"

# Ensure required tools
Ensure-Command mvn | Out-Null
Ensure-Command java | Out-Null
Ensure-Command docker | Out-Null

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Resolve-Path (Join-Path $scriptDir '..')
Push-Location $repoRoot

try {
    Write-Host "1) Fetch backend dependencies (for H2 jar)"
    & mvn -B -DskipTests dependency:copy-dependencies -f backend/pom.xml -DoutputDirectory=backend/target/dependency

    Write-Host "2) Seed H2 external DB"
    # Call the included PowerShell seed helper
    & "$repoRoot\backend\scripts\docker-seed-h2.ps1"

    Write-Host "3) Start the stack with Docker Compose"
    & docker compose up --build
}
catch {
    Write-Error "Error during flow: $_"
    exit 2
}
finally {
    Pop-Location
}

Write-Host "Done. Frontend: http://localhost/  Backend: http://localhost:8005/"
