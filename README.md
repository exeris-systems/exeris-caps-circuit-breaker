# exeris-caps-circuit-breaker

Circuit breaking as an Exeris capability. `CircuitBreakerRegistry` holds named `CircuitBreaker` instances, each a state machine over `CLOSED`, `OPEN` and `HALF_OPEN` that decides whether a call may proceed. Manual control is a separate service, `CircuitBreakerAdmin`, with its own states `FORCED_OPEN` and `DISABLED`.

Status: proof of concept, built against `exeris-kernel` 0.13.0-SNAPSHOT on JDK 25 LTS. The API is not stable and no artefact has been published.

## Where it sits

- **Layer:** Layer 3 — Gateway policy, in the Tier 2 capability map of [`high-level-architecture.md`](https://github.com/exeris-systems/exeris-docs/blob/main/high-level-architecture.md) §3.2.
- **Depends on:** `exeris-kernel-spi` (the entitlement requirement) and `exeris-sdk-annotations` (`provided`).
- **Provides** (`@Provides`, contract version 0.1.0): `CircuitBreakerRegistry` (the call-site service) and `CircuitBreakerAdmin` (manual control).
- **Requires:** no other capability.
- **Wall (ADR-055):** `eu.exeris.caps.breaker.api` is the cross-cap surface; `eu.exeris.caps.breaker.internal` is private, and a sibling cap reaching into it fails the build. No Spring, servlet, Netty or reactive type may appear (ADR-006).

## What it provides

### Call site: `CircuitBreakerRegistry` and `CircuitBreaker`

- `acquirePermission()` returns a permit as a primitive `long`, so admitting a call allocates nothing; `Permits.permitted(permit)` and `Permits.generation(permit)` read it. A permit records whether the call was admitted, which breaker admitted it and the state generation it was admitted in. Every state transition increments the generation.
- The outcome is reported with that permit: `onSuccess(permit, nanos)`, `onError(permit, nanos)`, or `onOutcome(permit, outcome, nanos)`, which asks the breaker's `FailureClassifier`. An outcome whose permit belongs to an earlier generation is counted in `metrics()` (`staleOutcomes`) and never drives a transition, so a call admitted in `CLOSED` that finishes after the breaker went `OPEN` and `HALF_OPEN` neither closes nor reopens it, and only permits issued in a `HALF_OPEN` generation count as probes. A permit completes once; a second outcome, a rejected permit and a permit of another breaker are ignored the same way. A breaker remembers its latest 1024 admissions for this: an outcome reported for an admission displaced by 1024 newer ones is counted as stale. The CLOSED path (acquire, then `onSuccess`, `onError` or `onOutcome`) and the refusal path allocate nothing; a state transition allocates the state of the new generation. Both are covered by tests that use the kernel allocation TCK.
- There is no outcome method without a permit and no state-changing method on this interface.

### Manual control: `CircuitBreakerAdmin`

`forceOpen`, `disable`, `release`, `state` and `states`. `FORCED_OPEN` refuses every call; `DISABLED` permits every call and outcomes never change the state. Automatic transitions never enter or leave either state; only `release` does, returning the breaker to `CLOSED` with fresh counters.

Every action takes an `actor` string, is reported to the `AdminListener` as an `AdminEvent` (breaker name, action, previous state, new state, timestamp, actor) so a SKU can write it to an audit trail, and is logged through `System.Logger` (`eu.exeris.caps.breaker.admin`). A listener that throws is logged and does not undo the action.

The service is created with `CircuitBreakers.newAdmin(registry, listener)`. Whoever is not handed it cannot change a breaker's state: the SKU grants `CircuitBreakerAdmin` to the operator-facing surface and to nothing else.

### Failure classification

A breaker counts an outcome as a failure as its `FailureClassifier` decides; it is set per breaker through `CircuitBreakerConfig.Builder#failureClassifier`, which is per route in a SKU that names breakers after routes. The default, `FailureClassifier.defaults()`, counts exactly:

| Outcome | Failure |
|---|---|
| Transport error | yes |
| Timeout | yes |
| HTTP 500, 502, 503, 504 | yes |
| Any other 5xx (501, 505, ...) | no |
| Any 4xx, 429 included | no |
| 1xx, 2xx, 3xx | no |

A 429 never counts: backpressure belongs to retry and rate limiting.

### Bounded registry

A registry holds at most 1024 named breakers by default (`CircuitBreakers.newRegistry(config, maxBreakers)` changes it). Creating one beyond the limit throws `CircuitBreakerLimitExceededException` and increments `limitRejections()`. Breakers are never evicted, because evicting one loses the state of an open circuit. Breaker names must come from configuration or route definitions, never from request data.

`LockFreeCircuitBreaker` keeps one phase object per state generation and swaps it with a compare-and-set, so state changes take no lock; `CircuitBreakers` is the factory.

## Build & test

Requires JDK 25 and Maven 3.9+.

```bash
mvn compile -Dexeris.codegen.skip=true   # pass 1 — the processor writes capability metadata
mvn verify                               # pass 2 — generate cap-manifest.json, validate the graph, scan the Wall
```

Both passes are needed after a `clean`: `exeris:generate` runs before `compile`, and `compile` is when the processor writes the metadata it reads. `src/main/generated/java/cap-manifest.json` is committed, so a clean `git status` after `mvn verify` shows the manifest matches the declaration.

The upstreams are not on Maven Central. `exeris-sdk` and `exeris-tooling` are built from source, and `exeris-kernel` 0.13.0-SNAPSHOT is resolved from GitHub Packages, which needs a token with `read:packages`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — the repository's operating contract and invariants.
- [Capability Author Guide](https://github.com/exeris-systems/exeris-docs/blob/main/cap-author-guide.md) — how a cap declares `@CapabilityModule`, `@Provides`, `@Requires` and `@CapabilityLifecycle`.
- [Capability licence registry](https://github.com/exeris-systems/exeris-docs/blob/main/cap-license-registry.md) — the licence value of every cap.

Where they disagree, the ADRs below and the HLA outrank this README.

## Governing ADRs

- [ADR-006](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-006-spring-free-kernel-boundary.md) — Spring-free kernel boundary.
- [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md) — capability licensing taxonomy.
- [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) — capability composition model.
- [ADR-053](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-053-sku-composition-manifest-format.md) — composition manifest format.
- [ADR-055](https://github.com/exeris-systems/exeris-tooling/blob/main/docs/adr/ADR-055-cap-tier-wall-guard.md) — cap-tier Wall guard.
- [ADR-088](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-088-cryptographic-license-manifest-and-offline-verification.md) — signed licence manifest.
- [ADR-089](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-089-capability-entitlement-enforcement-and-runtime-contract.md) — capability entitlement enforcement.

Registry: [`adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md).

## License

Exeris Commercial License 1.1 — see [`LICENSE`](LICENSE). `exeris-caps-circuit-breaker` is a `commercial` capability under [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md): the source is available to read, build, test and evaluate, and production use requires a commercial licence.

Its entitlement id is `exeris-caps-circuit-breaker`, the artifactId ([ADR-088](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-088-cryptographic-license-manifest-and-offline-verification.md), [ADR-089](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-089-capability-entitlement-enforcement-and-runtime-contract.md)).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Report a suspected vulnerability as described in [`SECURITY.md`](SECURITY.md), never in a public issue.
