# Provider Routing V1 Change Document

## Branch

- `feature/provider-routing-v1`
- Source: `alroute-perosonal-v1`

## Change set: Canonical routing domain primitives

### Objective

Establish a stable routing-domain vocabulary for AIRouter without replacing the existing provider adapters, routing strategies, scoring engine, selection engine, or failover pipeline.

### Added

#### `ProviderEndpoint`

Represents an executable provider endpoint independently of the provider adapter implementation.

Fields:

- provider
- endpointId
- baseUrl
- enabled

This establishes the distinction between a provider and an executable endpoint.

#### `ProviderPreferences`

Represents provider-level routing controls:

- ordered provider preferences
- excluded providers
- fallback permission

The default behavior is an empty preference set with fallbacks enabled.

#### `RoutingRequest`

Canonical routing input derived from the existing `ChatRequest`. It contains only routing-relevant state:

- model
- provider
- required capabilities
- provider preferences
- optimization profile
- selection mode
- top-N
- escalation profile
- maximum request cost
- remaining workflow budget

This prevents routing code from progressively coupling itself to the full gateway request DTO.

### Updated

#### `RoutingContext`

Added `routingRequest()`, which derives the canonical `RoutingRequest` from the existing request. Existing constructors and the current routing pipeline remain compatible.

#### `RoutingCandidate`

Added a stable `candidateKey()` in the form:

``
PROVIDER/model
```

This gives routing metadata, diagnostics, and future telemetry a deterministic candidate identity without changing the existing two-field candidate contract.

## Architecture after this change

``
OpenAI-compatible request
        |
        v
   ChatRequest
        |
        v
   RoutingContext
        |
        v
   RoutingRequest  <-- canonical routing domain
        |
        +--> ProviderPreferences
        |
        +--> candidate resolution
        |
        +--> eligibility / constraints
        |
        +--> scoring
        |
        +--> selection
        |
        v
   RoutingDecision
        |
        v
   Provider adapter
        |
        v
     Failover
```

## Compatibility

No provider adapter was replaced. No existing routing strategy was removed. Existing `RoutingCandidate(provider, model)` construction remains valid. The OpenAI-compatible endpoint continues to produce `ChatRequest` and the existing `GatewayService` remains the execution boundary.

## Next planned increment

Adapt candidate resolution and provider preference handling to consume the canonical routing primitives, then expose the resulting routing decision consistently to the execution and observability layers.

## Commits

- `232795e84753f829c42f60e93ad314dad956782b` — add `ProviderEndpoint`
- `05fab38f527701b86b6a3496aedcec776857dcf9` — add `ProviderPreferences`
- `677b3ca33592c3f19bb86992d3bd937b670cb631` — add `RoutingRequest`
- `d9d4b0b54a753ffcd14671adf07af15969292596` — expose canonical routing request from context
- `e2ad9c2215ea9914d5081d7b302732baf0c2f3e1` — add stable candidate identity
