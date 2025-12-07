# ==============================================================================
# Run Integration Tests
# ==============================================================================
# Description: Enables integration tests that require external services
# Sets integration.tests=enabled system property
# Includes: testGetBookingsByCustomerId_IntegrationTest
# Usage: .\run-integration-tests.ps1
# ==============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running Integration Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Setting system property: integration.tests=enabled" -ForegroundColor Yellow
Write-Host "Executing: mvn test -Dintegration.tests=enabled" -ForegroundColor Yellow
mvn test -Dintegration.tests=enabled

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Integration Tests Completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
