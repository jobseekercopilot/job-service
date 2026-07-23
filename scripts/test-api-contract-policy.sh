#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
source_contract="$script_dir/../api/openapi.yaml"
temporary_root=$(mktemp -d)
trap 'rm -rf "$temporary_root"' EXIT INT TERM

"$script_dir/verify-api-contract.sh" "$source_contract" >/dev/null

missing_home="$temporary_root/missing-home-location.yaml"
sed '/^        homeLocation:$/,/^          \\$ref: "#\\/components\\/schemas\\/HomeLocation"$/d' \
    "$source_contract" > "$missing_home"
if "$script_dir/verify-api-contract.sh" "$missing_home" >/dev/null 2>&1; then
    echo "producer contract policy test: missing homeLocation was accepted" >&2
    exit 1
fi

missing_providers="$temporary_root/missing-selected-providers.yaml"
sed '/^        selectedProviders:$/,/^          type: array$/d' \
    "$source_contract" > "$missing_providers"
if "$script_dir/verify-api-contract.sh" "$missing_providers" >/dev/null 2>&1; then
    echo "producer contract policy test: missing selectedProviders was accepted" >&2
    exit 1
fi

missing_coordinate="$temporary_root/missing-longitude.yaml"
sed '/^        longitude:$/,/^          type: number$/d' \
    "$source_contract" > "$missing_coordinate"
if "$script_dir/verify-api-contract.sh" "$missing_coordinate" >/dev/null 2>&1; then
    echo "producer contract policy test: incomplete HomeLocation was accepted" >&2
    exit 1
fi

echo "producer API contract policy tests: passed"
