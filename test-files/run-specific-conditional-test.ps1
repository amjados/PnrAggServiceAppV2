# ==============================================================================
# Run Specific Conditional Test
# ==============================================================================
# Description: Runs a specific test method with custom conditions
# Allows targeting individual test cases with conditional execution
# Usage: .\run-specific-conditional-test.ps1 -TestName "testName" -Condition "env|sysprop|both"
# ==============================================================================

param(
    [Parameter(Mandatory=$false)]
    [string]$TestName = "testAggregateBooking_PerformanceTest_ProductionOnly",
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("production", "integration", "circuitbreaker", "ci", "all")]
    [string]$Condition = "production"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running Specific Conditional Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Test Name: $TestName" -ForegroundColor White
Write-Host "Condition: $Condition" -ForegroundColor White
Write-Host ""

# Build Maven command
$mavenCmd = "mvn test -Dtest=BookingAggregatorServiceTest#$TestName"

# Apply conditions
switch ($Condition) {
    "production" {
        Write-Host "Enabling: ENV=production" -ForegroundColor Yellow
        $env:ENV = "production"
    }
    "integration" {
        Write-Host "Enabling: integration.tests=enabled" -ForegroundColor Yellow
        $mavenCmd += " -Dintegration.tests=enabled"
    }
    "circuitbreaker" {
        Write-Host "Enabling: circuitbreaker.test.enabled=true" -ForegroundColor Yellow
        $mavenCmd += " -Dcircuitbreaker.test.enabled=true"
    }
    "ci" {
        Write-Host "Enabling: CI=true (skip heavy tests)" -ForegroundColor Yellow
        $env:CI = "true"
    }
    "all" {
        Write-Host "Enabling: ALL conditions" -ForegroundColor Yellow
        $env:ENV = "production"
        $env:CI = "true"
        $mavenCmd += " -Dintegration.tests=enabled -Dcircuitbreaker.test.enabled=true"
    }
}

Write-Host ""
Write-Host "Executing: $mavenCmd" -ForegroundColor Yellow
Invoke-Expression $mavenCmd

# Cleanup
if ($env:ENV) { Remove-Item Env:\ENV }
if ($env:CI) { Remove-Item Env:\CI }

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Test Execution Completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
