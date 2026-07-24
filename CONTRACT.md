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

The exact private repository, immutable producer revision, source path, and
SHA-256 are recorded beside each snapshot in its `.SOURCE` file.

## Generation and compatibility

- Maven runs OpenAPI Generator 7.5.0 for all three contracts.
- Each generated client has a distinct API, model, and invoker package.
- Generated sources and binaries are build output under `target/`; they are not
  committed or copied between repositories.
- `SHA256SUMS` rejects changed inputs before generation.
- `scripts/verify-contracts.sh` verifies provenance and the required operation
  and request/response schemas for every provider.
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

Job Service calls only the gateway service operations:

- Reed: `POST /api/jobs/external-search`
- Adzuna: `POST /api/v1/adzuna/jobs/search`
- JSearch: `POST /api/v1/jsearch/jobs/search`

Provider credentials, provider API DTOs, fixture/live mode selection, rate
limits, and provider-specific compliance remain inside each gateway. Job
Service maps generated gateway responses into its own canonical response and
does not expose the generated provider models to Job Finder.

Job Matching is not covered by these provider snapshots. Its authoritative
inbound and Application Tracker consumer contracts are tracked by
[MATCH-02](https://github.com/jobseekercopilot/job-matching-service/issues/2).

## Update and rollback

1. Merge and review the producer-owned contract change.
2. Copy the exact source document from that immutable revision.
3. Update the matching `.SOURCE` file and `SHA256SUMS`.
4. Run the contract policy, clean Maven verification, and source-built
   container verification.
5. Review generated API/model differences and canonical mapping tests.
6. Merge the consumer only after producer and consumer evidence is green.

Rollback restores the last reviewed contract snapshot, provenance record, and
compatible adapter change together. Generated output is then recreated from
source; no binary rollback artefact is stored in Git.
