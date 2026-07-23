# Job Service

Job Service owns canonical multi-provider Job Search aggregation:
provider fan-out, mapping, normalisation, deduplication, enrichment, warnings,
and stable search responses. It must not own provider credentials or leak
provider-specific DTOs to consumers.

Status: **migration candidate; not beta-ready**. The current implementation
uses four excluded local generated-client JARs, calls providers sequentially,
has unsafe cache semantics, and makes the out-of-scope Job Matching service a
hard availability dependency. Saved-job persistence is not implemented. See
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Local verification

```bash
mvn -B clean verify
docker build -t local/job-service .
```

Provider gateway URLs and request deadlines must be supplied as runtime
configuration. Provider credentials do not belong in this service.

`develop` is the integration/default branch for beta hardening. See
`CONTRIBUTING.md`, `SECURITY.md`, and `LICENSE`.
