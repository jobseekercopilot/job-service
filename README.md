# Job Service

## Role in Job Seeker Copilot

| Role | Called by | Calls | Data | Local port |
|---|---|---|---|---:|
| Canonical job search, provider fan-out, normalisation/deduplication and saved-job owner | Job Finder Gateway, Document Generation Gateway | Reed, Adzuna, JSearch, NHS Jobs, Apprenticeships and Job Matching | PostgreSQL for saved jobs; search cache in memory | 8086 |

See the central [job-search journey](https://docs.jobseekercopilot.com/journeys/job-search/), [data ownership](https://docs.jobseekercopilot.com/data/ownership/), and [dependency maps](https://docs.jobseekercopilot.com/architecture/dependency-maps/).

Job Service owns canonical multi-provider Job Search aggregation:
provider fan-out, mapping, normalisation, deduplication, enrichment, warnings,
and stable search responses. It must not own provider credentials or leak
provider-specific DTOs to consumers.

Status: **implemented and composed for the controlled private-beta search
journey**. Provider clients now
build from pinned source-owned contracts and the search API verifies a signed
end-user access token. Provider fan-out now runs concurrently within explicit
request, provider, connection and capacity budgets; healthy provider jobs
survive other provider failures and optional Job Matching degradation. Cached
provider snapshots are isolated from user-specific application state.
Canonical Job schema 2.0 adds lossless raw evidence, explicit unknown
taxonomies, provenance, decimal salary fields, timezone-safe instants, skills
and experience without removing legacy fields.
Job Search API 2.3 applies bounded server-side paging and deterministic sorting
independently to every target role. Role-scoped totals, provider outcomes and
matching state prevent one role's page or failure from appearing as another
role's empty result set. The legacy top-level response remains as a flattened
compatibility view without removing the 2.0 canonical Job fields.
Owner-scoped saved jobs now use PostgreSQL/Flyway and retain immutable,
digest-addressed canonical snapshots through replay, update, unsave and
reactivation. Other tracked beta work remains. See
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Trusted identity boundary

`POST /api/jobs/search` requires an RS256 Bearer access token. Job Service
validates the token independently and uses only its nonblank `sub` claim as the
search identity. `X-User-Id` is not an authentication mechanism and is not part
of the API contract.

Configure the verification boundary with:

- `AUTH_JWKS_URI`
- `JOB_SERVICE_JWT_ISSUER`
- `JOB_SERVICE_JWT_AUDIENCE`

Job Finder must forward the original end-user Bearer token; it must not mint or
forward an unsigned subject header. See
[`docs/SECURITY_BOUNDARY.md`](docs/SECURITY_BOUNDARY.md) for the claim,
rotation and local-test contract.

The same verified JWT subject owns every saved job. The saved-job API is:

- `POST /api/jobs/saved` to create, replay, update or reactivate a canonical
  snapshot;
- `GET /api/jobs/saved` to list the owner's active saved jobs;
- `GET /api/jobs/saved/{savedJobId}` to retrieve the current immutable
  snapshot; and
- `DELETE /api/jobs/saved/{savedJobId}` to idempotently unsave it.

Missing, unsaved and other-user identifiers have the same not-found response.
The generated OpenAPI contract records the status codes and response fields.

The Infrastructure
[Job Search architecture ADR](https://github.com/jobseekercopilot/infrastructure/blob/develop/docs/adr/0001-job-search-architecture-and-ownership.md)
is the approved ownership map for canonical jobs, provider orchestration,
normalisation, deduplication, matching enrichment, and persistence.

## Local verification

```bash
./scripts/test-contract-policy.sh
./scripts/verify-contracts.sh
mvn -B clean verify
docker build -t local/job-service .
```

Persistence integration tests use H2 only as an isolated migration and
repository check. Release evidence must also start the service against a real
PostgreSQL instance, restart it without losing data, and exercise the backup
restore procedure. See
[`docs/SAVED_JOB_PERSISTENCE.md`](docs/SAVED_JOB_PERSISTENCE.md).

Reed, Adzuna, JSearch, NHS Jobs, and Apprenticeships clients are generated during the Maven build from
checksum-protected producer contracts and immutable `.SOURCE` records under
`src/main/openapi`. Generated sources and binaries remain under `target/` and
are never committed. See [`CONTRACT.md`](CONTRACT.md) for the compatibility,
update, and rollback policy.

Provider gateway URLs and request deadlines must be supplied as runtime
configuration. Provider credentials do not belong in this service.
See [`docs/PROVIDER_RESILIENCE.md`](docs/PROVIDER_RESILIENCE.md) for timeout
defaults, partial-result semantics, failure categories and operator actions.
See [`docs/CANONICAL_JOB_MODEL.md`](docs/CANONICAL_JOB_MODEL.md) for the field
dictionary, provider matrix, safe-link rule and compatibility plan.
See [`docs/JOB_SEARCH_PAGING.md`](docs/JOB_SEARCH_PAGING.md) for request
bounds, stable sorting, provider fetch budgets and consumer rollout.

Runtime database configuration is supplied using:

- `JOB_SERVICE_DATABASE_URL`
- `JOB_SERVICE_DATABASE_USERNAME`
- `JOB_SERVICE_DATABASE_PASSWORD`
- `JOB_SERVICE_DATABASE_SSL_MODE=verify-full`
- `JOB_SERVICE_DATABASE_ENCRYPTION_AT_REST_ENABLED=true`
- `JOB_SERVICE_DATABASE_ENCRYPTION_KEY_REFERENCE`
- `JOB_SERVICE_DATABASE_BACKUP_ENCRYPTION_ENABLED=true`
- `JOB_SERVICE_DATABASE_BACKUP_KEY_REFERENCE`

Startup fails closed when PostgreSQL, verified TLS, a dedicated non-root role,
reviewed Flyway migration settings, encryption declarations, or backup
declarations are absent. The production safety check may be disabled only in an
isolated local/test environment.

`develop` is the integration/default branch for beta hardening. See
`CONTRIBUTING.md`, `SECURITY.md`, and `LICENSE`.
