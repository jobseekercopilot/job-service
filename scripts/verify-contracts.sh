#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
contract_dir=${1:-"$script_dir/../src/main/openapi"}
checksums="$contract_dir/SHA256SUMS"

test -d "$contract_dir" || {
    echo "contract policy: contract directory is missing" >&2
    exit 1
}

for required_file in \
    adzuna-gateway.yaml \
    adzuna-gateway.SOURCE \
    jsearch-gateway.yaml \
    jsearch-gateway.SOURCE \
    reed-gateway.yaml \
    reed-gateway.SOURCE \
    SHA256SUMS
do
    test -f "$contract_dir/$required_file" && test ! -L "$contract_dir/$required_file" || {
        echo "contract policy: $required_file is missing or symbolic" >&2
        exit 1
    }
done

test "$(wc -l < "$checksums")" -eq 3
grep -Eq '^[a-f0-9]{64}  adzuna-gateway\.yaml$' "$checksums"
grep -Eq '^[a-f0-9]{64}  jsearch-gateway\.yaml$' "$checksums"
grep -Eq '^[a-f0-9]{64}  reed-gateway\.yaml$' "$checksums"
(cd "$contract_dir" && sha256sum --check --strict SHA256SUMS)

verify_source() {
    source_file="$contract_dir/$1.SOURCE"
    contract_file="$contract_dir/$1.yaml"
    expected_repository="$2"
    expected_revision="$3"
    expected_checksum="$4"

    test "$(wc -l < "$source_file" | tr -d ' ')" = 4
    grep -Fx "repository=$expected_repository" "$source_file" >/dev/null
    grep -Fx "revision=$expected_revision" "$source_file" >/dev/null
    grep -Fx 'path=api/openapi.yaml' "$source_file" >/dev/null
    grep -Fx "sha256=$expected_checksum" "$source_file" >/dev/null
    test "$(sha256sum "$contract_file" | cut -d ' ' -f 1)" = "$expected_checksum"
}

verify_source \
    adzuna-gateway \
    jobseekercopilot/adzuna-gateway \
    d57fb6c37c343a8d0bb93ca8f5606978c0fb88a4 \
    31c497be3648f12b97cfc166d17a803a2a0390bb59b0a4220356126b3f30bc1b
verify_source \
    jsearch-gateway \
    jobseekercopilot/jsearch-gateway \
    b7542b74265b9b6fd4cee752b4d9719935ccc3d9 \
    abe1984b48939f3c9820b846098bab5f20ee3c9346fb208df172deb9805382ad
verify_source \
    reed-gateway \
    jobseekercopilot/reed-gateway \
    83440f9a8d61c9ba1c53b8b2c4237f614707c6df \
    de1801bb897990ec2e106023429d11744987929291b49a38ec023154ff3e3506

adzuna_contract="$contract_dir/adzuna-gateway.yaml"
jsearch_contract="$contract_dir/jsearch-gateway.yaml"
reed_contract="$contract_dir/reed-gateway.yaml"

for contract in "$adzuna_contract" "$jsearch_contract" "$reed_contract"; do
    grep -Eq '^openapi: 3\.0\.[0-9]+$' "$contract"
done

grep -F '  /api/v1/adzuna/jobs/search:' "$adzuna_contract" >/dev/null
grep -F '      operationId: search' "$adzuna_contract" >/dev/null
grep -F '    AdzunaSearchRequest:' "$adzuna_contract" >/dev/null
grep -F '    AdzunaSearchResponse:' "$adzuna_contract" >/dev/null

grep -F '  /api/v1/jsearch/jobs/search:' "$jsearch_contract" >/dev/null
grep -F '      operationId: search' "$jsearch_contract" >/dev/null
grep -F '    JSearchSearchRequest:' "$jsearch_contract" >/dev/null
grep -F '    JSearchSearchResponse:' "$jsearch_contract" >/dev/null

grep -F '  /api/jobs/external-search:' "$reed_contract" >/dev/null
grep -F '      operationId: externalSearch' "$reed_contract" >/dev/null
grep -F '  /api/jobs/{jobId}:' "$reed_contract" >/dev/null
grep -F '      operationId: jobDetails' "$reed_contract" >/dev/null
grep -F '  version: 1.1.0' "$reed_contract" >/dev/null
if grep -F '        "422":' "$reed_contract" >/dev/null; then
    echo "contract policy: Reed healthy empty-result 422 response returned" >&2
    exit 1
fi

echo "contract input policy: passed"
