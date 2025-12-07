# ==============================================================================
# Run Circuit Breaker Tests
# ==============================================================================
# Description: Enables circuit breaker simulation tests
# Sets circuitbreaker.test.enabled=true system property
# Includes: testAggregateBooking_CircuitBreakerSimulation
# Usage: .\run-circuit-breaker-tests.ps1
# ==============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running Circuit Breaker Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Setting system property: circuitbreaker.test.enabled=true" -ForegroundColor Yellow
Write-Host "Executing: mvn test -Dcircuitbreaker.test.enabled=true" -ForegroundColor Yellow
mvn test -Dcircuitbreaker.test.enabled=true

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Circuit Breaker Tests Completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
