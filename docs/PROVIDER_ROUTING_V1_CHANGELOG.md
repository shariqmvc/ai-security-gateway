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

## Change set: Provider preference propagation

### Added to ChatRequest / OpenAI-compatible API

The routing request can now carry:

- `preferredProviders`: ordered provider preference
- `excludedProviders`: providers removed before candidate construction
- `allowProviderFallbacks`: request-level fallback intent retained in the canonical routing request

The OpenAI-compatible endpoint maps these fields into the core `ChatRequest`.

### Candidate resolution behavior

Policy-based routing now applies provider preferences after policy provider resolution and before model candidate construction:

1. null/excluded providers are removed;
2. explicitly ordered preferred providers are placed first when eligible;
3. remaining eligible providers retain their existing resolver order;
4. model resolution still occurs per provider, so invalid provider/model pairs are not fabricated.

This keeps policy eligibility and provider preference distinct: preferences do not re-admit a provider excluded by policy.

### Fallback semantics

`allowProviderFallbacks` is now part of the canonical routing request and API contract. This increment does not yet override the execution-layer failover configuration; that will be wired when the routing decision is propagated into provider execution. Existing Mistral/Ollama failover behavior is therefore unchanged.

## Additional commits

- `e4cabfdff146817af11ca00e292065941ee42758` — add provider preferences to ChatRequest
- `c75fab47bff7f16416cbaf08e08ee144f0aea86a` — expose provider routing preferences in OpenAI-compatible API
- `fdd27ce2e12fc1cf921398b788bdb0052b9fb859` — normalize provider preferences in RoutingRequest
- `cb44c8355e3d44d04b9195161893f7cc4178e735` — apply provider preferences to policy candidates

## Change set: RoutingDecision execution integration

### Execution propagation

The selected `RoutingCandidate` list from `RoutingDecision` is now carried through `AIRequest` as `routingCandidates`. The request-level `allowProviderFallbacks` flag is also propagated to the execution layer.

### Failover behavior

When provider failover is enabled and request-level fallback is allowed:

1. The primary routing decision executes first.
2. Additional selected routing candidates are attempted before configured provider failover entries.
3. Selected candidates retain their exact provider/model pair rather than being replaced with the provider's default model.
4. Configured failover entries remain available as an additional fallback layer.
5. Duplicate provider/model candidates are suppressed.
6. Existing health checks, circuit breakers, retry classification, request budgets, and partial-stream safety remain active.

When `allowProviderFallbacks=false`, no fallback candidate is executed after the primary request. Global failover-disabled behavior remains unchanged.

### Streaming

The same candidate propagation and ordering now apply to `StreamingProviderFailoverServiceImpl`. Partial output still prevents provider switching after a stream has emitted content.

### Architectural result

The routing pipeline now has a single decision that survives into execution:

```
RoutingRequest
  -> RoutingStrategy
  -> RoutingDecision
  -> AIRequest.routingCandidates
  -> Provider execution
  -> selected-candidate fallback
  -> configured failover
```

This removes the previous boundary where routing could select multiple candidates but provider execution only knew the primary provider plus an independently configured failover list.

## Execution integration commits

- `38ab4f51c97467e2f43b838b926c8994db6b6026` — carry selected candidates into AIRequest
- `273f1e9b6e3a092f5d508fc459dd3c3897d9e734` — propagate selected candidates from RoutingDecision
- `d85180520628c9e7430cfa4fe625d7abc3b96498` — execute selected synchronous candidates before configured failover
- `c86e629faee1f12dfc485a4a4ebd049af56266bd` — carry fallback policy into AIRequest
- `81ac70788e3df62d9fbf6e43d5ffd64bb21e48fe` — propagate fallback policy to execution
- `cad202735b80372cab2a5255d7bab3d55ad2d72d` — execute selected streaming candidates before configured failover

## Change set: Capability-aware candidate resolution

### Candidate construction

Policy-based candidate construction now validates each resolved provider/model pair against the request's `requiredCapabilities` before the candidate enters eligibility filtering, hard constraints, health filtering, cost evaluation, scoring, or selection.

The registry remains the source of truth for model capabilities. A capability mismatch is treated as candidate rejection rather than a routing failure. If every candidate is rejected, the existing routing error reports that no eligible model/candidate satisfies the request.

This is intentionally an early filter: the routing engine does not spend scoring/health/cost work on candidates that cannot execute the requested workload.

### Capability flow

```
ChatRequest.requiredCapabilities
        |
        v
RoutingRequest
        |
        v
PolicyBasedRoutingStrategy
        |
        +--> provider resolution
        |
        +--> model resolution
        |
        +--> registry capability validation  <-- new
        |
        +--> eligibility
        |
        +--> constraints / health / cost
        |
        +--> scoring / selection
```

### Endpoint architecture note

Provider endpoints remain owned by the provider adapters/configuration at this stage. A separate endpoint registry will be introduced only when endpoint selection is executable (multiple endpoints per provider, endpoint-specific health/cost/capability state). This avoids creating a metadata-only registry that duplicates the existing provider configuration without affecting execution.

## Capability resolution commit

- `03c2e3b0b8262b06078743214c278b83842a51c4` — filter candidates by required model capabilities
