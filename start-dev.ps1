<#
.SYNOPSIS
    Starts the backend and frontend dev servers together, each in its own window.

.DESCRIPTION
    Convenience wrapper around the two commands documented in README.md's "Start, stop and verify
    each part" table:
        backend\mvnw.cmd spring-boot:run   (http://localhost:8080)
        npm start                          (http://localhost:4200, run from frontend\)

    Each runs in its own new PowerShell window so their output doesn't interleave and either can
    be stopped independently (close the window, or Ctrl+C inside it).

    This script does NOT start the database. Run `docker compose up -d` first if it isn't already
    running — the backend still starts without it, it just reports the health check as `DOWN`
    until the database is reachable.

.PARAMETER IncludeDb
    Also run `docker compose up -d` before starting the two dev servers.

.EXAMPLE
    .\start-dev.ps1

.EXAMPLE
    .\start-dev.ps1 -IncludeDb
#>

param(
    [switch]$IncludeDb
)

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

if ($IncludeDb) {
    Write-Host "Starting database (docker compose up -d) ..." -ForegroundColor Cyan
    Push-Location $repoRoot
    try {
        docker compose up -d
    } finally {
        Pop-Location
    }
}

Write-Host "Starting backend (http://localhost:8080) ..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-Command',
    "Set-Location '$repoRoot\backend'; .\mvnw.cmd spring-boot:run"
)

Write-Host "Starting frontend (http://localhost:4200) ..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-Command',
    "Set-Location '$repoRoot\frontend'; npm start"
)

Write-Host "Both dev servers are starting in separate windows. Close a window (or Ctrl+C inside it) to stop that part." -ForegroundColor Green
