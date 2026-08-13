# Provider Failover Implementation

Implemented against the uploaded `ai-security-gateway` source snapshot.

## Production changes
- Added `ProviderFailoverService`
- Added `ProviderFailoverServiceImpl`
- Added `FailoverProperties` bound to `gateway.routing.failover`
- Wired `GatewayServiceImpl` to execute providers through the failover boundary
- Added bounded ordered fallback using the provider/model registry
- Added failover attempt/success metrics
- Added failover configuration with failover disabled by default

## Default configuration
```yaml
gateway:
  routing:
    failover:
      enabled: false
      max-attempts: 2
      providers:
        GEMINI:
          - OPENAI
        OPENAI:
          - GEMINI
        OLLAMA:
          - GEMINI
```

## Tests
Added `ProviderFailoverServiceImplTest` covering:
- primary success
- primary failure -> fallback success
- failover disabled
- no fallback configured
- primary + fallback failure
- maximum attempts
- duplicate fallback providers

## Verification
Maven is not installed in the execution environment used for this rebuild, so compilation/test execution could not be performed here. The code should be verified locally with:

`mvn -DskipTests compile`

`mvn -Dtest=ProviderFailoverServiceImplTest,GatewayServiceImplTest test`

Do not enable failover in production until the local test suite passes.
