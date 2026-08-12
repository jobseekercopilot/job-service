# Pinned downstream contracts

Job Service keeps exact producer-owned OpenAPI sources for every downstream
HTTP boundary. The five provider gateway contracts generate clients; the Job
Matching contract guards the separately implemented enrichment DTO boundary.

| Contract | Producer revision | SHA-256 | Required boundary |
| --- | --- | --- | --- |
| `adzuna-gateway.yaml` | `d57fb6c37c343a8d0bb93ca8f5606978c0fb88a4` | `31c497be3648f12b97cfc166d17a803a2a0390bb59b0a4220356126b3f30bc1b` | `search` |
| `apprenticeships-gateway.yaml` | `82e9db4ef317c6e4e90009e46fb9f6205d6d30dd` | `94605132443e2dcc5872c2cc62df39ec7575960845211d6277f3c5f7b3ef9b2e` | apprenticeship search |
| `jsearch-gateway.yaml` | `b7542b74265b9b6fd4cee752b4d9719935ccc3d9` | `abe1984b48939f3c9820b846098bab5f20ee3c9346fb208df172deb9805382ad` | `search` |
| `job-matching-service.json` | `7a6e99b1273afc1183479a160307ffc70cd74570` | `f2576c6e83f6aa500b9a3072f14553a6757cec80cb30473ce02d7337bf1db4d1` | `enrichJobs` and deterministic match evidence |
| `nhs-jobs-gateway.yaml` | `b2f4d254f14b0acc656063a5bfe1e5388e7fec31` | `a797a2abd467efe7716ef31cae7320793d27dc84e6a5a3d23740667267608165` | NHS job search |
| `reed-gateway.yaml` | `83440f9a8d61c9ba1c53b8b2c4237f614707c6df` | `de1801bb897990ec2e106023429d11744987929291b49a38ec023154ff3e3506` | `externalSearch`, `jobDetails` |

Each `.SOURCE` file records the private producer repository, immutable
revision, source path, and exact contract checksum. `SHA256SUMS` protects all
six inputs locally.

The OpenAPI Generator version and provider package names are pinned in
`pom.xml`. Generated source and binaries remain under `target/` and must not be
committed. The Job Matching snapshot is verification input only; no copied JAR
or `systemPath` dependency is permitted.

To update a contract:

1. Merge and review the producer contract change.
2. Replace the matching YAML with that exact producer-owned file.
3. Update its `.SOURCE` record and the table above.
4. Recalculate all entries in `SHA256SUMS`.
5. Run `./scripts/test-contract-policy.sh` and `mvn -B clean verify`.
6. Review generated provider API/model compatibility or the handwritten Job
   Matching DTO compatibility, as applicable; do not commit `target/`.
