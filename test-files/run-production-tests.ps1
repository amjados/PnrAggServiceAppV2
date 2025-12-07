# ==============================================================================
# Run Production Environment Tests
# ==============================================================================
# Description: Enables performance tests that run only in production environment
# Sets ENV=production to activate @EnabledIfEnvironmentVariable tests
# Includes: testAggregateBooking_PerformanceTest_ProductionOnly
# Usage: .\run-production-tests.ps1
# ==============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running Production Environment Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Setting environment variable: ENV=production" -ForegroundColor Yellow
$env:ENV = "production"

Write-Host "Executing: mvn test" -ForegroundColor Yellow
mvn test

Write-Host ""
Write-Host "Cleaning up environment variable..." -ForegroundColor Yellow
Remove-Item Env:\ENV

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Production Tests Completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
