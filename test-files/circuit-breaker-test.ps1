# ============================================================================
# PNR Aggregator Service - Circuit Breaker Test Script
# ============================================================================
# Description: Automated testing of circuit breaker behavior with MongoDB
#              failure simulation and recovery validation
#
# Usage: .\circuit-breaker-test.ps1 [-BaseUrl <url>] [-PNR <pnr>]
#
# Parameters:
#   -BaseUrl : Service endpoint URL (default: http://localhost:8080)
#   -PNR     : Passenger Name Record to test (default: GHTW42)
#
# Test Duration: ~2-3 minutes
# Prerequisites: Docker, docker-compose, service running
#
# Documentation: See md-files/CIRCUIT_BREAKER_TEST_README.md
# ============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$PNR = "GHTW42"
)

$results = @()

# ============================================================================
# Function: Run Test Requests
# Description: Executes HTTP requests to the booking endpoint and captures
#              response status, cache info, and timing metrics
# ============================================================================
function Run-TestRequests {
    param(
        [int]$Start,
        [int]$End,
        [string]$Description
    )
    
    Write-Host "`n$Description" -ForegroundColor Cyan
    for ($i = $Start; $i -le $End; $i++) {
        Write-Host "Request $i/21: " -NoNewline -ForegroundColor Yellow
        
        $startTime = Get-Date
        try {
            $response = Invoke-RestMethod -Uri "$BaseUrl/booking/$PNR" -Method Get -TimeoutSec 10 -ErrorAction Stop
            $endTime = Get-Date
            $duration = ($endTime - $startTime).TotalSeconds
            
            $status = $response.status
            $fromCache = if ($response.fromCache) { "true" } else { "false" }
            
            $color = switch ($status) {
                "SUCCESS" { "Green" }
                "DEGRADED" { "Yellow" }
                default { "Cyan" }
            }
            Write-Host "Status: $status, FromCache: $fromCache, Time: $($duration.ToString('F2'))s" -ForegroundColor $color
            
            $script:results += [PSCustomObject]@{
                Request = $i
                Status = $status
                FromCache = $fromCache
                Duration = $duration
            }
        }
        catch {
            $endTime = Get-Date
            $duration = ($endTime - $startTime).TotalSeconds
            Write-Host $_.Exception.Message -ForegroundColor Red
            
            $script:results += [PSCustomObject]@{
                Request = $i
                Status = "ERROR"
                FromCache = "N/A"
                Duration = $duration
            }
        }
        
        Start-Sleep -Milliseconds 500
    }
}

# ============================================================================
# Function: Check Circuit Breaker Status
# Description: Queries actuator health endpoint for circuit breaker states,
#              failure rates, and call statistics
# ============================================================================
function Check-CircuitBreakerStatus {
    Write-Host "`nChecking Circuit Breaker Status..." -ForegroundColor Cyan
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/circuitBreakers" -Method Get -ErrorAction Stop
        
        if ($health.details) {
            foreach ($cb in $health.details.PSObject.Properties) {
                $cbName = $cb.Name
                $cbState = $cb.Value.details.state
                $failureRate = $cb.Value.details.failureRate
                $failedCalls = $cb.Value.details.failedCalls
                $bufferedCalls = $cb.Value.details.bufferedCalls
                
                $color = switch ($cbState) {
                    "CLOSED" { "Green" }
                    "OPEN" { "Red" }
                    "HALF_OPEN" { "Yellow" }
                    default { "Gray" }
                }
                
                Write-Host "  $cbName : " -NoNewline
                Write-Host "$cbState" -ForegroundColor $color -NoNewline
                Write-Host " (Failure Rate: $failureRate%, Failed: $failedCalls, Buffered: $bufferedCalls)"
            }
        }
    }
    catch {
        Write-Host "  Could not retrieve circuit breaker state" -ForegroundColor Red
    }
}

# ============================================================================
# Main Test Execution
# ============================================================================
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Circuit Breaker Test" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Yellow
Write-Host "PNR: $PNR" -ForegroundColor Yellow
Write-Host ""
Write-Host "Test Phases:" -ForegroundColor Yellow
Write-Host "  Phase 1 (Requests 1-5):   MongoDB UP → Expect SUCCESS" -ForegroundColor Gray
Write-Host "  Phase 2 (Requests 6-15):  MongoDB DOWN → Circuit OPENS → Expect DEGRADED" -ForegroundColor Gray
Write-Host "  Phase 3 (Requests 16-21): MongoDB UP → Circuit RECOVERS → Expect SUCCESS" -ForegroundColor Gray
Write-Host ""
Write-Host "Circuit Breaker States:" -ForegroundColor Yellow
Write-Host "  CLOSED     → Normal operation (Phase 1)" -ForegroundColor Green
Write-Host "  OPEN       → Failure threshold exceeded (Phase 2)" -ForegroundColor Red
Write-Host "  HALF_OPEN  → Testing recovery (Phase 3)" -ForegroundColor Yellow
Write-Host "  CLOSED     → Recovered (Phase 3)" -ForegroundColor Green
Write-Host ""

# ============================================================================
# Step 1: Start MongoDB
# ============================================================================
Write-Host "Step 1: Starting MongoDB..." -ForegroundColor Cyan
docker-compose start mongodb | Out-Null
Start-Sleep -Seconds 3
Write-Host "MongoDB started" -ForegroundColor Green

# ============================================================================
# Step 2: Requests 1-5 (MongoDB Running - Expect SUCCESS)
# ============================================================================
Run-TestRequests -Start 1 -End 5 -Description "Step 2: Requests 1-5 (MongoDB Running - Expect SUCCESS)"

# ============================================================================
# Step 3: Stop MongoDB
# ============================================================================
Write-Host "`nStep 3: Stopping MongoDB..." -ForegroundColor Cyan
docker-compose stop mongodb | Out-Null
Start-Sleep -Seconds 2
Write-Host "MongoDB stopped" -ForegroundColor Green

# ============================================================================
# Step 4: Check Circuit Breaker Status
# ============================================================================
Write-Host "`nStep 4: Circuit Breaker Status (Before Failures)" -ForegroundColor Cyan
Check-CircuitBreakerStatus

# ============================================================================
# Step 5: Requests 6-15 (MongoDB Down - Expect DEGRADED)
# ============================================================================
Run-TestRequests -Start 6 -End 15 -Description "Step 5: Requests 6-15 (MongoDB Down - Expect DEGRADED)"

# ============================================================================
# Step 6: Check Circuit Breaker Status
# ============================================================================
Write-Host "`nStep 6: Circuit Breaker Status (After Failures - Expect OPEN)" -ForegroundColor Cyan
Check-CircuitBreakerStatus

# ============================================================================
# Step 7: Start MongoDB
# ============================================================================
Write-Host "`nStep 7: Starting MongoDB..." -ForegroundColor Cyan
docker-compose start mongodb | Out-Null
Start-Sleep -Seconds 3
Write-Host "MongoDB started" -ForegroundColor Green

# ============================================================================
# Step 8: Requests 16-21 (MongoDB Recovered - Circuit Testing Recovery)
# Description: Tests circuit transition from OPEN → HALF_OPEN → CLOSED
#              MongoDB is running, circuit should gradually recover
# ============================================================================
Run-TestRequests -Start 16 -End 18 -Description "Step 8a: Requests 16-18 (Circuit Recovery - Part 1)"
Check-CircuitBreakerStatus
Start-Sleep -Seconds 5
Check-CircuitBreakerStatus
Run-TestRequests -Start 19 -End 21 -Description "Step 8b: Requests 19-21 (Circuit Recovery - Part 2)"
Check-CircuitBreakerStatus
Start-Sleep -Seconds 5
Check-CircuitBreakerStatus

# ============================================================================
# Test Summary
# ============================================================================
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

# Calculate metrics
$totalRequests = $script:results.Count
$totalSuccess = ($script:results | Where-Object { $_.Status -eq "SUCCESS" }).Count
$totalDegraded = ($script:results | Where-Object { $_.Status -eq "DEGRADED" }).Count
$totalError = ($script:results | Where-Object { $_.Status -eq "ERROR" }).Count
$avgDuration = ($script:results | Measure-Object -Property Duration -Average).Average

$phase1 = $script:results | Where-Object { $_.Request -le 5 }
$phase2 = $script:results | Where-Object { $_.Request -ge 6 -and $_.Request -le 15 }
$phase3 = $script:results | Where-Object { $_.Request -ge 16 }
$phase2AvgTime = ($phase2 | Measure-Object -Property Duration -Average).Average

Write-Host "Results:" -ForegroundColor Yellow
Write-Host "  Phase 1 (1-5):    SUCCESS: $(($phase1 | Where-Object { $_.Status -eq 'SUCCESS' }).Count)/5" -ForegroundColor $(if ((($phase1 | Where-Object { $_.Status -eq 'SUCCESS' }).Count) -eq 5) { "Green" } else { "Red" })
Write-Host "  Phase 2 (6-15):   DEGRADED: $(($phase2 | Where-Object { $_.Status -eq 'DEGRADED' }).Count)/10, Avg: $($phase2AvgTime.ToString('F2'))s" -ForegroundColor $(if ($phase2AvgTime -lt 1.0) { "Green" } else { "Yellow" })
Write-Host "  Phase 3 (16-21):  SUCCESS: $(($phase3 | Where-Object { $_.Status -eq 'SUCCESS' }).Count)/6" -ForegroundColor $(if ((($phase3 | Where-Object { $_.Status -eq 'SUCCESS' }).Count) -ge 4) { "Green" } else { "Yellow" })

Write-Host "`nOverall:" -ForegroundColor Yellow
Write-Host "  Total: $totalRequests | SUCCESS: $totalSuccess | DEGRADED: $totalDegraded | ERROR: $totalError" -ForegroundColor White
Write-Host "  Average Duration: $($avgDuration.ToString('F2'))s" -ForegroundColor White

if ($phase2AvgTime -lt 1.0 -and $totalSuccess -ge 10) {
    Write-Host "`n  [PASS] Circuit Breaker Test PASSED" -ForegroundColor Green
} else {
    Write-Host "`n  [WARN] Review results - Some issues detected" -ForegroundColor Yellow
}

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "Test Complete" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan
