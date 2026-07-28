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
        sha256sum adzuna-gateway.yaml jsearch-gateway.yaml nhs-jobs-gateway.yaml reed-gateway.yaml > SHA256SUMS
    )
}

for provider in adzuna-gateway jsearch-gateway nhs-jobs-gateway reed-gateway; do
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

missing_adzuna_operation="$temporary_root/missing-adzuna-operation"
copy_fixture "$missing_adzuna_operation"
sed -i '/operationId: search/d' "$missing_adzuna_operation/adzuna-gateway.yaml"
recalculate_manifest "$missing_adzuna_operation"
if "$script_dir/verify-contracts.sh" "$missing_adzuna_operation" >/dev/null 2>&1; then
    echo "contract policy test: Adzuna operation removal was accepted" >&2
    exit 1
fi

missing_jsearch_operation="$temporary_root/missing-jsearch-operation"
copy_fixture "$missing_jsearch_operation"
sed -i '/operationId: search/d' "$missing_jsearch_operation/jsearch-gateway.yaml"
recalculate_manifest "$missing_jsearch_operation"
if "$script_dir/verify-contracts.sh" "$missing_jsearch_operation" >/dev/null 2>&1; then
    echo "contract policy test: JSearch operation removal was accepted" >&2
    exit 1
fi

missing_nhs_jobs_operation="$temporary_root/missing-nhs-jobs-operation"
copy_fixture "$missing_nhs_jobs_operation"
sed -i '/operationId: searchNhsJobs/d' "$missing_nhs_jobs_operation/nhs-jobs-gateway.yaml"
recalculate_manifest "$missing_nhs_jobs_operation"
if "$script_dir/verify-contracts.sh" "$missing_nhs_jobs_operation" >/dev/null 2>&1; then
    echo "contract policy test: NHS Jobs operation removal was accepted" >&2
    exit 1
fi

missing_reed_operation="$temporary_root/missing-reed-operation"
copy_fixture "$missing_reed_operation"
sed -i '/operationId: externalSearch/d' "$missing_reed_operation/reed-gateway.yaml"
recalculate_manifest "$missing_reed_operation"
if "$script_dir/verify-contracts.sh" "$missing_reed_operation" >/dev/null 2>&1; then
    echo "contract policy test: Reed operation removal was accepted" >&2
    exit 1
fi

echo "contract policy tests: passed"
