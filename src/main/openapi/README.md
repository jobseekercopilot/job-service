# Pinned producer contract

`reed-gateway.yaml` is the reviewed source contract used to generate the only
external client currently imported by Job Service.

- Owner: `jobseekercopilot/reed-gateway`
- Source: `api/openapi.yaml`
- Producer revision: `c1f90dcfe14360d8eb5b1665ab8009ed135c421e`
- Contract version: `1.0.0`
- Required operation: `externalSearch`

The OpenAPI Generator version and Java package names are pinned in `pom.xml`.
Generated source and binaries remain under `target/` and must not be committed.
The Adzuna, JSearch and Job Matching integrations currently use repository-owned
adapters or HTTP DTOs rather than generated clients, so their unused copied-JAR
dependencies are deliberately absent.

To update the contract:

1. Merge and review the producer contract change.
2. Replace `reed-gateway.yaml` with that exact producer-owned file.
3. Record the new immutable producer revision above.
4. Recalculate `SHA256SUMS`.
5. Run `./scripts/test-contract-policy.sh` and `mvn -B clean verify`.
6. Review generated API/model compatibility; do not commit `target/`.
