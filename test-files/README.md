# PowerShell Test Scripts

This directory contains automated test scripts for the PNR Aggregator Service.

## Available Scripts

### Circuit Breaker Test
**File:** `circuit-breaker-test.ps1`  
**Purpose:** Test circuit breaker pattern with MongoDB failure simulation  
**Duration:** ~2-3 minutes

```powershell
.\circuit-breaker-test.ps1
```

**What it tests:**
- Normal operation (SUCCESS responses)
- Circuit opens on MongoDB failure
- Fallback mechanisms (cached and default data)
- Circuit recovery (OPEN → HALF_OPEN → CLOSED)
- Response time improvements (5s → <0.05s)
---

### Quick Test
**File:** `quick-test.ps1`  
**Purpose:** Fast validation of basic functionality  
**Duration:** ~30 seconds

```powershell
.\quick-test.ps1
```

**What it tests:**
- Basic endpoint connectivity
- Valid PNR responses
- Invalid PNR handling
- Health check endpoints

---

### Load Test
**File:** `load-test.ps1`  
**Purpose:** Performance testing under sustained load  
**Duration:** ~5 minutes

```powershell
.\load-test.ps1
```

**What it tests:**
- Response times under load
- Throughput capacity
- System stability
- Cache effectiveness

**Output:** CSV file with performance metrics

---

### Stress Test
**File:** `stress-test.ps1`  
**Purpose:** System limits and failure modes  
**Duration:** ~10 minutes

```powershell
.\stress-test.ps1
```

**What it tests:**
- High concurrent request handling
- System resource usage
- Recovery from overload
- Circuit breaker under stress

---

### Spike Test
**File:** `spike-test.ps1`  
**Purpose:** Sudden traffic spike handling  
**Duration:** ~3 minutes

```powershell
.\spike-test.ps1
```

**What it tests:**
- Rapid load increase
- System response to spikes
- Quick recovery
- Rate limiting behavior

---

## Test Results

Performance test results are saved in CSV format:
- `load-test-results_YYYYMMDD_HHMMSS.csv`
- Contains timestamps, response times, and status codes

## Prerequisites

All scripts require:
- PowerShell 5.1 or higher
- Service running on `http://localhost:8080`
- Docker and docker-compose (for circuit breaker test)

## Common Parameters

Most scripts accept:
- `-BaseUrl`: Service endpoint (default: `http://localhost:8080`)
- `-PNR`: Test PNR to use (default: `GHTW42`)

## Test Order Recommendation

1. **quick-test.ps1** - Verify basic functionality
2. **circuit-breaker-test.ps1** - Test resilience patterns
3. **load-test.ps1** - Measure baseline performance
4. **spike-test.ps1** - Test burst handling
5. **stress-test.ps1** - Find system limits

## CI/CD Integration

Scripts can be integrated into CI/CD pipelines:

```yaml
# Example: Azure Pipeline step
- task: PowerShell@2
  displayName: 'Run Circuit Breaker Tests'
  inputs:
    filePath: 'ps-files/circuit-breaker-test.ps1'
    failOnStderr: true
```

## Troubleshooting

### Service Not Responding
```powershell
# Check if service is running
curl http://localhost:8080/actuator/health
```

### Docker Issues (Circuit Breaker Test)
```powershell
# Verify Docker is running
docker ps

# Check MongoDB status
docker-compose ps mongodb
```

### Permission Errors
```powershell
# Run PowerShell as Administrator or adjust execution policy
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Best Practices

1. **Clean state**: Restart service before critical tests
2. **Monitor logs**: Keep service logs visible during tests
3. **Sequential runs**: Wait between heavy tests (stress/load)
4. **Verify data**: Ensure test PNRs exist in MongoDB
5. **Resource cleanup**: Stop MongoDB after circuit breaker tests

## Circuit Breaker Test Details

### Test Flow

#### Phase 1: MongoDB Running (Requests 1-5)
- **Purpose**: Establish baseline with healthy system
- **Expected**: All requests return SUCCESS status
- **MongoDB**: Running
- **Circuit State**: CLOSED

#### Phase 2: MongoDB Down (Requests 6-15)
- **Purpose**: Trigger circuit breaker by simulating database failure
- **Expected**: Requests return DEGRADED status with fallback data
- **MongoDB**: Stopped
- **Circuit State**: CLOSED → OPEN (after failure threshold)

#### Phase 3: MongoDB Recovery (Requests 16-21)
- **Purpose**: Test circuit breaker recovery behavior
- **Expected**: Mix of DEGRADED and SUCCESS as circuit tests recovery
- **MongoDB**: Running
- **Circuit State**: OPEN → HALF_OPEN → CLOSED

### Script Features

- Automated MongoDB control (stop/start)
- Detailed request/response logging
- Circuit state monitoring
- Response time measurements
- Summary statistics (success rate, avg response time)
- Color-coded output for easy reading

### Parameters

- **BaseUrl** (optional): Service endpoint URL (default: `http://localhost:8080`)
- **PNR** (optional): Passenger Name Record to test (default: `GHTW42`)

### Output Example

```
=================================================================
Circuit Breaker Test - PNR Aggregator Service
=================================================================

Phase 1 (Requests 1-5): MongoDB UP → Expect SUCCESS
Phase 2 (Requests 6-15): MongoDB DOWN → Circuit OPENS → Expect DEGRADED/ERROR
Phase 3 (Requests 16-21): MongoDB UP → Circuit RECOVERS → Expect SUCCESS

Step 1: Starting MongoDB...
MongoDB started

Step 2: Phase 1 - MongoDB Running (Requests 1-5)
Request 1: SUCCESS (Response time: 0.45s)
Request 2: SUCCESS (Response time: 0.12s)
...

Step 3: Stopping MongoDB...
MongoDB stopped

Step 4: Phase 2 - MongoDB Down (Requests 6-15)
Request 6: ERROR (Response time: 5.23s) - First timeout
Request 7: DEGRADED (Response time: 5.18s) - Still timing out
Request 11: DEGRADED (Response time: 0.04s) - Circuit OPEN - Fast fail!
...
```

## See Also

- [README.md](../README.md) - Project overview and quick start
- [TESTING.md](../docs/out-readme/TESTING.md) - Comprehensive manual testing guide
- [FALLBACK_MESSAGES_IMPLEMENTATION.md](../docs/out-readme/FALLBACK_MESSAGES_IMPLEMENTATION.md) - Technical implementation details
