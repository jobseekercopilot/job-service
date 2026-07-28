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
    nhs-jobs-gateway.yaml \
    nhs-jobs-gateway.SOURCE \
    reed-gateway.yaml \
    reed-gateway.SOURCE \
    SHA256SUMS
do
    test -f "$contract_dir/$required_file" && test ! -L "$contract_dir/$required_file" || {
        echo "contract policy: $required_file is missing or symbolic" >&2
        exit 1
    }
done

test "$(wc -l < "$checksums")" -eq 4
grep -Eq '^[a-f0-9]{64}  adzuna-gateway\.yaml$' "$checksums"
grep -Eq '^[a-f0-9]{64}  jsearch-gateway\.yaml$' "$checksums"
grep -Eq '^[a-f0-9]{64}  nhs-jobs-gateway\.yaml$' "$checksums"
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
    a10115b0ab60da26469504d964819d44b2d0d550 \
    10391e68dcec30e4fb765bcb3ddaf2150c0cc8b8adc529a11c8bf38fe7ae9331
verify_source \
    jsearch-gateway \
    jobseekercopilot/jsearch-gateway \
    b7542b74265b9b6fd4cee752b4d9719935ccc3d9 \
    abe1984b48939f3c9820b846098bab5f20ee3c9346fb208df172deb9805382ad
verify_source \
    nhs-jobs-gateway \
    jobseekercopilot/nhs-jobs-gateway \
    0b0d63ccdfffc4767359223bbc696b442962ffbb \
    4ffd3e1fb5f8e91930ffdc8abf8a796faf40a539e42398cb76ffbe48bc98ae2d
verify_source \
    reed-gateway \
    jobseekercopilot/reed-gateway \
    85f3d5ad0117576b0baab08fa0430673a9ba7155 \
    7bc588e36fa7ec1b264420f6dc0a278edb933f0ad403eac6473f961dec099568

adzuna_contract="$contract_dir/adzuna-gateway.yaml"
jsearch_contract="$contract_dir/jsearch-gateway.yaml"
nhs_jobs_contract="$contract_dir/nhs-jobs-gateway.yaml"
reed_contract="$contract_dir/reed-gateway.yaml"

for contract in "$adzuna_contract" "$jsearch_contract" "$nhs_jobs_contract" "$reed_contract"; do
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

grep -F '  /api/v1/nhs/jobs/search:' "$nhs_jobs_contract" >/dev/null
grep -F '      operationId: searchNhsJobs' "$nhs_jobs_contract" >/dev/null
grep -F '    NhsJobsSearchRequest:' "$nhs_jobs_contract" >/dev/null
grep -F '    NhsJobsSearchResponse:' "$nhs_jobs_contract" >/dev/null

grep -F '  /api/jobs/external-search:' "$reed_contract" >/dev/null
grep -F '      operationId: externalSearch' "$reed_contract" >/dev/null
grep -F '  version: 1.1.0' "$reed_contract" >/dev/null
if grep -F '        "422":' "$reed_contract" >/dev/null; then
    echo "contract policy: Reed healthy empty-result 422 response returned" >&2
    exit 1
fi

echo "contract input policy: passed"
