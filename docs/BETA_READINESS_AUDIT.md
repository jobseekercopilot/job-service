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
- **P0 product ownership:** no database, repository, entity, saved-job API, or
  save/unsave persistence exists in Job Service.
- **Identity rollout in progress:** Job Service now independently verifies the
  RS256 access token and binds search identity to `sub`. End-to-end closure
  still requires Job Finder to consume the revised contract and forward the
  original Bearer token in its own reviewed release.
- **Completed SEARCH-07 latency controls:** provider and matching HTTP clients
  have explicit connect/read timeouts; provider work is concurrent and
  cancellable under a shared request deadline; saturation fails fast instead of
  growing an unbounded queue. Provider-specific retry, rate-limit and circuit
  policy remains in the existing gateway-owned resilience issues.
- **P1 paging:** `page` and `pageSize` are reported but all results are returned;
  sorting, limits and cursor semantics are client-side or absent.
- **P1 cache policy:** cache keys now include provider selection, salary and
  employment filters; partial provider results are not cached; deep provider
  snapshots prevent user-specific application state crossing users. The cache
  is still an unbounded in-memory store with no provider-terms-aware TTL,
  eviction policy or distributed invalidation.
- **P1 canonical model:** provenance, raw values, normalisation confidence,
  robust employment/remote taxonomy, timezone-safe timestamps, skills,
  experience, expiry and deadline semantics are incomplete.
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

Return deterministic canonical results plus provider status and correlation
metadata when at least one provider succeeds. Continue with tracked work to
enforce server-side bounds, sorting and pagination, complete canonical mapping,
and persist saved jobs against a stable canonical identifier and a
provider-safe snapshot/provenance model.

## Evidence required to close

Clean-clone build/container evidence; source-owned contracts and compatibility
tests; concurrency/deadline/load tests; deterministic mapping, normalisation,
deduplication and failure matrices; persistence migration and cross-user
authorization tests; safe-link tests; cache policy approval against provider
terms; and production-like E2E proof through Job Finder and the client.

This audit is not a beta-readiness approval.
