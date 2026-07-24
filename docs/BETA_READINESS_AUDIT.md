# Job Service beta-readiness audit

## Current role

The service validates a limited request, calls Reed, Adzuna and JSearch
adapters sequentially, maps results to a canonical model, deduplicates, enriches
them, caches them in memory, and invokes Job Matching before returning.

## Blocking findings

- **Completed provider build control:** Reed, Adzuna and JSearch clients are
  generated with the pinned OpenAPI Generator from exact producer revisions.
  Contract checks fail closed on missing input/provenance, checksum or revision
  drift, and required-operation removal. Job Matching remains the separately
  tracked handwritten contract boundary.
- **P0 availability boundary:** every search requires the out-of-scope Job
  Matching service; its failure turns provider results into HTTP 503. Matching
  must be optional/asynchronous or otherwise isolated without auditing that
  service in this workstream.
- **P0 product ownership:** no database, repository, entity, saved-job API, or
  save/unsave persistence exists in Job Service.
- **Identity rollout in progress:** Job Service now independently verifies the
  RS256 access token and binds search identity to `sub`. End-to-end closure
  still requires Job Finder to consume the revised contract and forward the
  original Bearer token in its own reviewed release.
- **P1 latency:** provider calls are sequential and have no applied end-to-end
  deadline, cancellation, circuit breaker, bulkhead, rate limiter, or bounded
  concurrency.
- **P1 paging:** `page` and `pageSize` are reported but all results are returned;
  sorting, limits and cursor semantics are client-side or absent.
- **P1 cache correctness:** the unbounded ten-minute in-memory key omits salary
  and employment filters and has no provider-terms-aware TTL or invalidation.
- **P1 canonical model:** provenance, raw values, normalisation confidence,
  robust employment/remote taxonomy, timezone-safe timestamps, skills,
  experience, expiry and deadline semantics are incomplete.
- **P1 normalisation:** salaries use fixed multipliers without currency
  conversion/provenance; dates lose timezone; external URLs are not explicitly
  limited to safe HTTP(S) schemes.
- **P1 deduplication:** merge rules exist and source URLs are retained, but the
  canonical ID may change when a lexicographically earlier source appears and
  no merge reason/provenance record is retained.
- **P1 failure semantics:** partial results are represented only after a
  provider call completes; timeout/deadline behaviour and stable provider error
  categories are not demonstrated.

## Target orchestration

Run provider calls concurrently under a shared deadline and per-provider
budgets. Return deterministic canonical results plus provider status and
correlation metadata when at least one provider succeeds; distinguish healthy
empty, invalid query, authentication/configuration, quota, timeout and upstream
failure. Enforce server-side bounds, sorting and pagination. Persist saved jobs
against a stable canonical identifier and a provider-safe snapshot/provenance
model.

## Evidence required to close

Clean-clone build/container evidence; source-owned contracts and compatibility
tests; concurrency/deadline/load tests; deterministic mapping, normalisation,
deduplication and failure matrices; persistence migration and cross-user
authorization tests; safe-link tests; cache policy approval against provider
terms; and production-like E2E proof through Job Finder and the client.

This audit is not a beta-readiness approval.
