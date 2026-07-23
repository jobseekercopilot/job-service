#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
contract_dir=${1:-"$script_dir/../src/main/openapi"}
contract="$contract_dir/reed-gateway.yaml"
checksums="$contract_dir/SHA256SUMS"

test -d "$contract_dir" || {
    echo "contract policy: contract directory is missing" >&2
    exit 1
}
test -f "$contract" && test ! -L "$contract" || {
    echo "contract policy: Reed Gateway contract is missing or symbolic" >&2
    exit 1
}
test -f "$checksums" && test ! -L "$checksums" || {
    echo "contract policy: checksum manifest is missing or symbolic" >&2
    exit 1
}
test "$(wc -l < "$checksums")" -eq 1
grep -Eq '^[a-f0-9]{64}  reed-gateway\.yaml$' "$checksums"
(cd "$contract_dir" && sha256sum --check --strict SHA256SUMS)

grep -Eq '^openapi: 3\.0\.[0-9]+$' "$contract"
grep -F '  /api/jobs/external-search:' "$contract" >/dev/null
grep -F '      operationId: externalSearch' "$contract" >/dev/null

echo "contract input policy: passed"
