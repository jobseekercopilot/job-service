# Job Service

Job Service owns canonical multi-provider Job Search aggregation:
provider fan-out, mapping, normalisation, deduplication, enrichment, warnings,
and stable search responses. It must not own provider credentials or leak
provider-specific DTOs to consumers.

Status: **beta hardening in progress; not beta-ready**. Provider clients now
build from pinned source-owned contracts and the search API verifies a signed
end-user access token. Provider calls remain sequential, cache semantics remain
unsafe, and the out-of-scope Job Matching service is still a hard availability
dependency. Saved-job persistence is not implemented. See
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

Reed, Adzuna, and JSearch clients are generated during the Maven build from
checksum-protected producer contracts and immutable `.SOURCE` records under
`src/main/openapi`. Generated sources and binaries remain under `target/` and
are never committed. See [`CONTRACT.md`](CONTRACT.md) for the compatibility,
update, and rollback policy.

Provider gateway URLs and request deadlines must be supplied as runtime
configuration. Provider credentials do not belong in this service.

`develop` is the integration/default branch for beta hardening. See
`CONTRIBUTING.md`, `SECURITY.md`, and `LICENSE`.
