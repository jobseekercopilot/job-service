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
    cp "$source_dir/reed-gateway.yaml" "$source_dir/SHA256SUMS" "$fixture/"
}

missing_contract="$temporary_root/missing-contract"
copy_fixture "$missing_contract"
rm "$missing_contract/reed-gateway.yaml"
if "$script_dir/verify-contracts.sh" "$missing_contract" >/dev/null 2>&1; then
    echo "contract policy test: missing contract was accepted" >&2
    exit 1
fi

checksum_drift="$temporary_root/checksum-drift"
copy_fixture "$checksum_drift"
printf '%s\n' '# unreviewed drift' >> "$checksum_drift/reed-gateway.yaml"
if "$script_dir/verify-contracts.sh" "$checksum_drift" >/dev/null 2>&1; then
    echo "contract policy test: checksum drift was accepted" >&2
    exit 1
fi

missing_operation="$temporary_root/missing-operation"
copy_fixture "$missing_operation"
sed -i '/operationId: externalSearch/d' "$missing_operation/reed-gateway.yaml"
(cd "$missing_operation" && sha256sum reed-gateway.yaml > SHA256SUMS)
if "$script_dir/verify-contracts.sh" "$missing_operation" >/dev/null 2>&1; then
    echo "contract policy test: required operation removal was accepted" >&2
    exit 1
fi

echo "contract policy tests: passed"
