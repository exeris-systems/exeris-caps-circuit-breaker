---
title: "exeris-caps-circuit-breaker — Roadmap"
type: roadmap
visibility: public
owning-repo: exeris-caps-circuit-breaker
status: active
last-verified: 2026-10-15
---

# exeris-caps-circuit-breaker — Roadmap

Status: **poc**. Version line: **0.1.x**. Licence: Exeris Commercial License (ADR-023), entitlement id
`exeris-caps-circuit-breaker`.

Milestones are versions, not dates. Dates appear only on what has shipped; nothing has shipped yet.

> **`1.0.0` is not on this roadmap.** It is a gate, described in the last section. Status moves
> `poc` → `snapshot` → `released`.

---

## Where this sits

Layer 3 gateway policy cap. Provides `CircuitBreakerRegistry`: named `CircuitBreaker`s created through
`CircuitBreakers.newRegistry(config)`, with `CLOSED` / `OPEN` / `HALF_OPEN` states, a count-based sliding
window, a failure-rate threshold, an open-state wait and a half-open permit budget (`LockFreeCircuitBreaker`
over atomics). Zero runtime dependencies; no `@Requires`. 9 tests across two classes.

Gaps between the inventory and the code, found in the code:

- HLA §3.2 names the service `CircuitBreakerPolicy` and lists `@Requires policy-chain`; the code provides
  `CircuitBreakerRegistry` and requires nothing. The policy itself lives in the SKU
  (`CircuitBreakerGatewayPolicy`), not in this cap.
- Every outcome call (`onSuccess`, `onError`, `onOutcome`) already carries `durationNanos`, but it is not
  used: there is no slow-call detection. Adding it later needs no contract change.
  `CircuitBreakerRegistry` is `AutoCloseable` but there is no `@CapabilityLifecycle`.

**Upstream constraints this repo does not own:**

| Constraint | Effect here | Owner |
|---|---|---|
| Kernel licence gate (ADR-088, ADR-089) in kernel `0.13.0-SNAPSHOT` | Entitlement resolvable only from that line | `exeris-kernel` |
| `RequestFilterChain` contract (ADR-099) | 0.2.0 binds to it | `exeris-caps-service-boundary-core` |
| ADR on the response-side seam (gateway-core 0.2.0) | Entry condition: no policy cap starts its 0.2.0 before it is accepted | `exeris-caps-gateway-core`, `exeris-docs` |
| HLA §3.2 row differs from the code | Registry names `CircuitBreakerPolicy` and a `policy-chain` requirement | `exeris-docs` |

---

## 0.1.0 — the code PR

- [ ] Defects found in the pre-import review are fixed in the import, each with a test
- [ ] Code imported; repository files and ADR link stubs added
- [ ] `@Provides(CircuitBreakerRegistry)` and `@Provides(CircuitBreakerAdmin)` at contract `0.1.0`
- [ ] Outcomes bound to a `Permit` carrying the state generation; an outcome from an earlier generation
      is counted in metrics only and never drives a transition
- [ ] Manual control (`forceOpen`, `disable`, `release`) only through the separate `CircuitBreakerAdmin`
      service, with distinct `FORCED_OPEN` / `DISABLED` states and an admin listener for audit
- [ ] `FailureClassifier` in the contract; default: transport error, timeout, HTTP 500, 502, 503, 504.
      No 4xx counts as a failure, 429 included: back-pressure is handled by retry / rate limiting, not by the breaker
- [ ] Bounded registry: configurable limit, default 1024; exceeding it is an explicit error, never eviction
- [ ] Kernel `0.13.0-SNAPSHOT`; SDK `0.12.0` and tooling `0.10.0` pins aligned
- [ ] Artefacts stay `0.1.0-SNAPSHOT` until kernel `0.13.0` is released; a non-snapshot `0.1.0` is cut only after that
- [ ] Licence enforcement: `CircuitBreakerEntitlement` (id `exeris-caps-circuit-breaker`), `META-INF/services`
      registration, TCK test and boot test against the kernel's Phase 0 gate
- [ ] `build.yml` green on JDK 25 and 26; `cap-manifest.json` committed through the two-pass build

**Exit gate:** `mvn verify` green on both JDK rows; entitlement test fails without the id.

## 0.2.0 — bind to `RequestFilterChain`

Follows the gateway-core PR that replaces its `PolicyChain`.

- [ ] Optional `@Requires(RequestFilterChain, "[0.1.0,0.2.0)")` and a `CircuitBreakerServiceBoundaryBinding`
      in `api`, on the pattern of `exeris-caps-rate-limiting`; replaces the SKU-side `CircuitBreakerGatewayPolicy`
- [ ] Outcome recording (`onOutcome(Permit, CallOutcome, durationNanos)` from the upstream result) happens after the
      handler runs; `AdmissionFilter` is pre-handler only, so this depends on the response-side ADR
      (entry condition: accepted before this milestone starts; see gateway-core 0.2.0)
- [ ] The same upstream outcome feeds both this breaker and `UpstreamPoolService.recordResult`; whether both
      use one `FailureClassifier` or differ on purpose is written down
- [ ] Align `HLA §3.2` (service name, requirements) through an `exeris-docs` PR

## 0.3.0 — Independent security review

- [ ] Covered by the API Gateway SKU review (one review; scope: the SKU and every composed cap); this
      repository's public API and documented behaviour are listed in that scope
- [ ] Threat model written and committed alongside the code
- [ ] Findings handled through `SECURITY.md` private reporting
- [ ] Fixes released, each with a regression test

## 0.4.0 — re-measurement and snapshot

- [ ] Permission check and outcome recording measured in the `exeris-benchmarks` harness on the reference profile, published there; no figure stated here
- [ ] Decision recorded on slow-call and time-based windows: add or leave out
- [ ] Snapshot artefact on GitHub Packages; status `poc` → `snapshot`

---

## 1.0.0 — a gate, not a milestone

Entry condition: the API Gateway SKU has driven a breaker through open, half-open and recovery against
real failing upstreams, and the review is closed. Then `CircuitBreakerRegistry` freezes, a semver gate is
added, and the status becomes `released`.

## Deliberately out of scope

- Retries, timeouts and bulkheads: not in HLA §3.2 for this cap.
- Per-route failure classification: the consuming binding supplies its own `FailureClassifier`; this cap owns only the default.
