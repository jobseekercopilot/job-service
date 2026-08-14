# Job Service provider contract policy

## Ownership

Provider gateways own their public service contracts and external-provider
integration details. Job Service owns the canonical job model, provider
selection, fan-out, normalisation, deduplication, and stable aggregate response.

| Producer | Source contract | Consumer snapshot |
| --- | --- | --- |
| `jobseekercopilot/reed-gateway` | `api/openapi.yaml` | `src/main/openapi/reed-gateway.yaml` |
| `jobseekercopilot/adzuna-gateway` | `api/openapi.yaml` | `src/main/openapi/adzuna-gateway.yaml` |
| `jobseekercopilot/jsearch-gateway` | `api/openapi.yaml` | `src/main/openapi/jsearch-gateway.yaml` |
| `jobseekercopilot/nhs-jobs-gateway` | `api/openapi.yaml` | `src/main/openapi/nhs-jobs-gateway.yaml` |
| `jobseekercopilot/apprenticeships-gateway` | `api/openapi.yaml` | `src/main/openapi/apprenticeships-gateway.yaml` |
| `jobseekercopilot/job-matching-service` | `contracts/openapi.json` | `src/main/openapi/job-matching-service.json` |

The exact private repository, immutable producer revision, source path, and
SHA-256 are recorded beside each snapshot in its `.SOURCE` file.

## Generation and compatibility

- Maven runs OpenAPI Generator 7.5.0 for the five provider gateway contracts.
- Each generated client has a distinct API, model, and invoker package.
- Job Matching retains a handwritten DTO boundary, checked against its pinned
  `enrichJobs` request, response, and deterministic match-evidence schemas.
- Generated sources and binaries are build output under `target/`; they are not
  committed or copied between repositories.
- `SHA256SUMS` rejects changed inputs before generation.
- `scripts/verify-contracts.sh` verifies provenance and the required operation
  and request/response schemas for every downstream service.
- `scripts/test-contract-policy.sh` proves that missing inputs, missing
  provenance, checksum drift, revision drift, and required-operation removal
  fail closed.
- Adapter tests compile against the generated request and response models and
  preserve the canonical mapping boundary.

Provider contract changes are compatible only when the pinned generated client
still compiles and the mapping and contract-policy tests pass. A breaking
producer change requires an explicit version review and coordinated consumer
change; branch movement alone is never accepted as provenance.

## Runtime boundary

Job Service calls only these downstream service operations:

- Reed: `POST /api/jobs/external-search`
- Adzuna: `POST /api/v1/adzuna/jobs/search`
- JSearch: `POST /api/v1/jsearch/jobs/search`
- NHS Jobs: `POST /api/v1/nhs-jobs/jobs/search`
- Find an apprenticeship: `POST /api/v1/apprenticeships/jobs/search`
- Job Matching: `POST /api/v1/job-matches/enrich`

Provider credentials, provider API DTOs, fixture/live mode selection, rate
limits, and provider-specific compliance remain inside each gateway. Job
Service maps generated gateway responses into its own canonical response and
does not expose the generated provider models to Job Finder.

The aggregate response makes degradation explicit:

- `searchStatus=COMPLETE` means every attempted provider and optional matching
  enrichment completed.
- `searchStatus=PARTIAL` retains usable jobs when a provider or Job Matching
  degrades.
- `providerResults` reports stable `SUCCESS`, `DISABLED`, `TIMED_OUT`,
  `SATURATED`, `RATE_LIMITED`, `CONFIGURATION_ERROR`, `REJECTED` or
  `UNAVAILABLE` outcomes without raw downstream response detail.
- `matchingStatus` independently reports the optional enrichment outcome.

HTTP 503 is reserved for searches where no requested provider is enabled or
every attempted provider fails. Job Matching failure alone does not discard
provider jobs or turn a successful provider search into HTTP 503. Runtime
budgets and operator guidance are documented in
[`docs/PROVIDER_RESILIENCE.md`](docs/PROVIDER_RESILIENCE.md).

## Canonical Job compatibility

API version 2.3.0 publishes canonical Job schema version 2.0, bounded
target-role Job Search paging/sorting and the owner-scoped saved-job resource.
Each `resultsByTargetRole` item owns its page totals, provider outcomes, search
status and matching status. The legacy top-level fields remain a flattened
compatibility view. The
canonical job model
retains legacy aliases while adding raw and normalised values, explicit unknown
taxonomies, decimal salary evidence, offset-aware timestamps, complete source
records, skills, experience and field-level provenance.

Version 2.3 adds official NHS Jobs and Find an apprenticeship sources. The
canonical job retains a specialist classification, every advertised location,
official source provenance, and structured apprenticeship training details.

Saved-job responses are server-owned records. `savedJobId`, snapshot version,
content digest, capture/retrieval timestamps and source state must never be
accepted from a browser as authoritative input. Generation and Application
Tracking consumers use `savedJobId` to retrieve the canonical snapshot from
Job Service while forwarding the verified end-user Bearer token. The producer
contract policy fails when saved routes, immutable snapshot identity/digest, or
source-state fields disappear.

Provider adapters may populate only evidence present in their pinned producer
contract. Missing values remain absent or explicit `UNKNOWN`; Job Service does
not infer on-site work from `remote=false`, fabricate a timezone/currency, or
invent skills and experience. External links pass the canonical HTTP(S) safety
policy before entering a response.

Job Matching may enrich only match/application-owned fields. Job Service
correlates Matching results to the original canonical jobs and ignores any
replacement provider-owned fields or additional jobs.

The field dictionary, provider mapping matrix and compatibility/migration plan
are authoritative in
[`docs/CANONICAL_JOB_MODEL.md`](docs/CANONICAL_JOB_MODEL.md).
Paging defaults, deterministic tie-breakers, bounded provider traversal and
consumer rollout are defined in
[`docs/JOB_SEARCH_PAGING.md`](docs/JOB_SEARCH_PAGING.md).

Job Matching's producer-owned API 1.1 contract is pinned alongside the gateway
contracts. The policy specifically retains candidate-profile inputs and the
algorithm version, provenance, score components, reasons and hard-gate evidence
that Job Service projects into its canonical response. Application Tracker's
separate consumer boundary remains owned by Job Matching.

## Update and rollback

1. Merge and review the producer-owned contract change.
2. Copy the exact source document from that immutable revision.
3. Update the matching `.SOURCE` file and `SHA256SUMS`.
4. Run the contract policy, clean Maven verification, and source-built
   container verification.
5. Review generated provider API/model differences or handwritten Job Matching
   DTO differences, canonical schema compatibility and mapping tests.
6. Merge the consumer only after producer and consumer evidence is green.

Rollback restores the last reviewed contract snapshot, provenance record, and
compatible adapter change together. Generated output is then recreated from
source; no binary rollback artefact is stored in Git.

Database rollback is independent from API contract rollback. Flyway migrations
are forward-only in production: restore the last encrypted backup into a new
database, validate the schema and snapshot counts, then switch the application
connection. Never run Flyway clean or mutate the schema with Hibernate.
