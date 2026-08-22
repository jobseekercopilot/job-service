#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
source_dir="$script_dir/../src/main/openapi"
temporary_root=$(mktemp -d)
trap 'rm -rf "$temporary_root"' EXIT INT TERM

"$script_dir/verify-contracts.sh" "$source_dir" >/dev/null

copy_fixture() {
    fixture="$1"
    mkdir "$fixture"
    cp \
        "$source_dir/adzuna-gateway.yaml" \
        "$source_dir/adzuna-gateway.SOURCE" \
        "$source_dir/jsearch-gateway.yaml" \
        "$source_dir/jsearch-gateway.SOURCE" \
        "$source_dir/job-matching-service.json" \
        "$source_dir/job-matching-service.SOURCE" \
        "$source_dir/apprenticeships-gateway.yaml" \
        "$source_dir/apprenticeships-gateway.SOURCE" \
        "$source_dir/nhs-jobs-gateway.yaml" \
        "$source_dir/nhs-jobs-gateway.SOURCE" \
        "$source_dir/reed-gateway.yaml" \
        "$source_dir/reed-gateway.SOURCE" \
        "$source_dir/SHA256SUMS" \
        "$fixture/"
}

recalculate_manifest() {
    fixture="$1"
    (
        cd "$fixture"
        sha256sum adzuna-gateway.yaml apprenticeships-gateway.yaml \
            jsearch-gateway.yaml job-matching-service.json \
            nhs-jobs-gateway.yaml reed-gateway.yaml > SHA256SUMS
    )
}

for provider in adzuna-gateway apprenticeships-gateway jsearch-gateway \
        nhs-jobs-gateway reed-gateway; do
    missing_contract="$temporary_root/missing-$provider-contract"
    copy_fixture "$missing_contract"
    rm "$missing_contract/$provider.yaml"
    if "$script_dir/verify-contracts.sh" "$missing_contract" >/dev/null 2>&1; then
        echo "contract policy test: missing $provider contract was accepted" >&2
        exit 1
    fi

    missing_source="$temporary_root/missing-$provider-source"
    copy_fixture "$missing_source"
    rm "$missing_source/$provider.SOURCE"
    if "$script_dir/verify-contracts.sh" "$missing_source" >/dev/null 2>&1; then
        echo "contract policy test: missing $provider provenance was accepted" >&2
        exit 1
    fi

    checksum_drift="$temporary_root/$provider-checksum-drift"
    copy_fixture "$checksum_drift"
    printf '%s\n' '# unreviewed drift' >> "$checksum_drift/$provider.yaml"
    if "$script_dir/verify-contracts.sh" "$checksum_drift" >/dev/null 2>&1; then
        echo "contract policy test: $provider checksum drift was accepted" >&2
        exit 1
    fi

    provenance_drift="$temporary_root/$provider-provenance-drift"
    copy_fixture "$provenance_drift"
    sed -i 's/^revision=.*/revision=0000000000000000000000000000000000000000/' \
        "$provenance_drift/$provider.SOURCE"
    if "$script_dir/verify-contracts.sh" "$provenance_drift" >/dev/null 2>&1; then
        echo "contract policy test: $provider provenance drift was accepted" >&2
        exit 1
    fi
done

missing_matching_contract="$temporary_root/missing-job-matching-service-contract"
copy_fixture "$missing_matching_contract"
rm "$missing_matching_contract/job-matching-service.json"
if "$script_dir/verify-contracts.sh" "$missing_matching_contract" >/dev/null 2>&1; then
    echo "contract policy test: missing Job Matching contract was accepted" >&2
    exit 1
fi

missing_matching_source="$temporary_root/missing-job-matching-service-source"
copy_fixture "$missing_matching_source"
rm "$missing_matching_source/job-matching-service.SOURCE"
if "$script_dir/verify-contracts.sh" "$missing_matching_source" >/dev/null 2>&1; then
    echo "contract policy test: missing Job Matching provenance was accepted" >&2
    exit 1
fi

matching_checksum_drift="$temporary_root/job-matching-service-checksum-drift"
copy_fixture "$matching_checksum_drift"
printf '%s\n' ' ' >> "$matching_checksum_drift/job-matching-service.json"
if "$script_dir/verify-contracts.sh" "$matching_checksum_drift" >/dev/null 2>&1; then
    echo "contract policy test: Job Matching checksum drift was accepted" >&2
    exit 1
fi

matching_provenance_drift="$temporary_root/job-matching-service-provenance-drift"
copy_fixture "$matching_provenance_drift"
sed -i 's/^revision=.*/revision=0000000000000000000000000000000000000000/' \
    "$matching_provenance_drift/job-matching-service.SOURCE"
if "$script_dir/verify-contracts.sh" "$matching_provenance_drift" >/dev/null 2>&1; then
    echo "contract policy test: Job Matching provenance drift was accepted" >&2
    exit 1
fi

for provider in adzuna-gateway apprenticeships-gateway jsearch-gateway \
        nhs-jobs-gateway; do
    missing_search_operation="$temporary_root/missing-$provider-search-operation"
    copy_fixture "$missing_search_operation"
    sed -i '/operationId: search/d' "$missing_search_operation/$provider.yaml"
    recalculate_manifest "$missing_search_operation"
    if "$script_dir/verify-contracts.sh" "$missing_search_operation" >/dev/null 2>&1; then
        echo "contract policy test: $provider search operation removal was accepted" >&2
        exit 1
    fi
done

missing_reed_operation="$temporary_root/missing-reed-operation"
copy_fixture "$missing_reed_operation"
sed -i '/operationId: externalSearch/d' "$missing_reed_operation/reed-gateway.yaml"
recalculate_manifest "$missing_reed_operation"
if "$script_dir/verify-contracts.sh" "$missing_reed_operation" >/dev/null 2>&1; then
    echo "contract policy test: Reed operation removal was accepted" >&2
    exit 1
fi

missing_matching_operation="$temporary_root/missing-job-matching-operation"
copy_fixture "$missing_matching_operation"
sed -i 's/"operationId":"enrichJobs"/"operationId":"removedEnrichJobs"/' \
    "$missing_matching_operation/job-matching-service.json"
recalculate_manifest "$missing_matching_operation"
if "$script_dir/verify-contracts.sh" "$missing_matching_operation" >/dev/null 2>&1; then
    echo "contract policy test: Job Matching operation removal was accepted" >&2
    exit 1
fi

echo "contract policy tests: passed"
