# Job Service beta-readiness audit

## Current role

The service validates a limited request, calls enabled Reed, Adzuna and JSearch
adapters concurrently within bounded budgets, maps results to a canonical
model, deduplicates, enriches and caches complete provider snapshots, then
attempts optional Job Matching enrichment before returning.

## Blocking findings

- **Completed provider build control:** Reed, Adzuna and JSearch clients are
  generated with the pinned OpenAPI Generator from exact producer revisions.
  Contract checks fail closed on missing input/provenance, checksum or revision
  drift, and required-operation removal. Job Matching remains the separately
  tracked handwritten contract boundary.
- **Completed SEARCH-07 availability boundary:** concurrent provider fan-out is
  bounded by shared request and per-provider deadlines, a fixed-size executor
  and a bounded queue. One provider failure preserves healthy jobs, stable
  provider categories describe degradation, and all-provider failure remains a
  safe HTTP 503. Optional Job Matching timeout, saturation or failure preserves
  provider jobs and is reported as a partial response.
- **Completed SEARCH-11 persistence boundary:** PostgreSQL/Flyway owns
  owner-scoped saved-job identities and immutable canonical/source snapshots.
  Replays are idempotent, source changes append versions, unsave is soft and
  repeatable, cross-user reads are indistinguishable from missing records, and
  generation consumers can retrieve a server-owned snapshot by `savedJobId`.
  Production startup fails closed without verified TLS, least privilege,
  migration, encryption-at-rest and encrypted-backup declarations.
- **Identity rollout in progress:** Job Service now independently verifies the
  RS256 access token and binds search identity to `sub`. End-to-end closure
  still requires Job Finder to consume the revised contract and forward the
  original Bearer token in its own reviewed release.
- **Completed SEARCH-07 latency controls:** provider and matching HTTP clients
  have explicit connect/read timeouts; provider work is concurrent and
  cancellable under a shared request deadline; saturation fails fast instead of
  growing an unbounded queue. Provider-specific retry, rate-limit and circuit
  policy remains in the existing gateway-owned resilience issues.
- **Completed target-role paging correction:** API 2.2 retains the API 2.1
  aggregate compatibility page while adding independently sliced, counted and
  status-bearing target-role results. Page, page size, sort and role limits are
  validated before fan-out; provider and aggregate results stay bounded; and
  JSearch continuation remains within a hard two-call budget. Job Finder
  contract rollout and end-to-end UI evidence remain consumer-owned.
- **P1 cache policy:** cache keys include target role, provider selection,
  salary and employment filters. Complete provider snapshots are shared
  without user enrichment; partial snapshots are owner-scoped only to keep
  later pages stable, while page-one refresh retries providers. Deep copies
  prevent user-specific application state crossing users. The caches are still
  unbounded in-memory stores with no provider-terms-aware TTL, eviction policy
  or distributed invalidation.
- **Completed SEARCH-04 canonical schema boundary:** additive schema 2.0
  defines stable provider/source identifiers, raw and normalised values,
  explicit unknown employment/contract/workplace taxonomies, decimal salary
  provenance/confidence, offset-safe posting/expiry/deadline timestamps,
  skills, experience and field-level mapping evidence. Legacy aliases remain
  for compatibility, and unsafe non-HTTP(S) links are rejected. SEARCH-05 and
  provider mapping issues still own complete deterministic value mapping.
- **P1 normalisation:** salaries use fixed multipliers without currency
  conversion/provenance; dates lose timezone; external URLs are not explicitly
  limited to safe HTTP(S) schemes.
- **P1 deduplication:** merge rules exist and source URLs are retained, but the
  canonical ID may change when a lexicographically earlier source appears and
  no merge reason/provenance record is retained.
- **Completed SEARCH-07 failure semantics:** deterministic tests cover
  concurrency, partial and all-provider failure, timeouts, cancellation,
  saturation, bounded concurrent load, matching degradation, correlation
  propagation and cache isolation.

## Target orchestration

Return deterministic, bounded and paged canonical results plus provider status
and correlation metadata when at least one provider succeeds. Continue with
tracked work to complete canonical mapping and roll the reviewed paging
contract through Job Finder and the browser.

## Evidence required to close

Clean-clone build/container evidence; source-owned contracts and compatibility
tests; concurrency/deadline/load tests; deterministic mapping, normalisation,
deduplication and failure matrices; persistence migration and cross-user
authorization tests; production PostgreSQL restart/restore evidence; safe-link
tests; cache policy approval against provider terms; and production-like E2E
proof through Job Finder and the client.

This audit is not a beta-readiness approval.
