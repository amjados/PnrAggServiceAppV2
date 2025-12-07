# ==============================================================================
# Run Tests in CI Simulation Mode
# ==============================================================================
# Description: Simulates CI/CD environment to skip heavy/resource-intensive tests
# Sets CI=true environment variable to activate @DisabledIf tests
# Skips: testAggregateBooking_HeavyLoadTest_SkipOnCI
# Usage: .\run-ci-simulation-tests.ps1
# ==============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running Tests in CI Simulation Mode" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Setting environment variable: CI=true" -ForegroundColor Yellow
Write-Host "Note: Heavy load tests will be SKIPPED" -ForegroundColor Magenta
$env:CI = "true"

Write-Host "Executing: mvn test" -ForegroundColor Yellow
mvn test

Write-Host ""
Write-Host "Cleaning up environment variable..." -ForegroundColor Yellow
Remove-Item Env:\CI

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "CI Simulation Tests Completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
