# Pinned producer contracts

Job Service generates its provider clients exclusively from exact
producer-owned OpenAPI sources:

| Contract | Producer revision | SHA-256 | Required operation |
| --- | --- | --- | --- |
| `adzuna-gateway.yaml` | `a10115b0ab60da26469504d964819d44b2d0d550` | `10391e68dcec30e4fb765bcb3ddaf2150c0cc8b8adc529a11c8bf38fe7ae9331` | `search` |
| `jsearch-gateway.yaml` | `b7542b74265b9b6fd4cee752b4d9719935ccc3d9` | `abe1984b48939f3c9820b846098bab5f20ee3c9346fb208df172deb9805382ad` | `search` |
| `reed-gateway.yaml` | `c1f90dcfe14360d8eb5b1665ab8009ed135c421e` | `fa72da3d2f1115c6845db075a65715b24ba40df8619d9a363e21f6dc9e11e21f` | `externalSearch` |

Each `.SOURCE` file records the private producer repository, immutable
revision, source path, and exact contract checksum. `SHA256SUMS` protects all
three inputs locally.

The OpenAPI Generator version and Java package names are pinned in `pom.xml`.
Generated source and binaries remain under `target/` and must not be committed.
Job Matching remains a separately tracked contract dependency; no copied JAR
or `systemPath` dependency is permitted.

To update a contract:

1. Merge and review the producer contract change.
2. Replace the matching YAML with that exact producer-owned file.
3. Update its `.SOURCE` record and the table above.
4. Recalculate all entries in `SHA256SUMS`.
5. Run `./scripts/test-contract-policy.sh` and `mvn -B clean verify`.
6. Review generated API/model compatibility; do not commit `target/`.
