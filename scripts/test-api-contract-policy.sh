#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
source_contract="$script_dir/../api/openapi.yaml"
temporary_root=$(mktemp -d)
trap 'rm -rf "$temporary_root"' EXIT INT TERM

"$script_dir/verify-api-contract.sh" "$source_contract" >/dev/null

stale_version="$temporary_root/stale-canonical-version.yaml"
sed 's/^  version: 2\.1\.0$/  version: 2.0.0/' \
    "$source_contract" > "$stale_version"
if "$script_dir/verify-api-contract.sh" "$stale_version" >/dev/null 2>&1; then
    echo "producer contract policy test: stale canonical version was accepted" >&2
    exit 1
fi

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

missing_page_size="$temporary_root/missing-page-size.yaml"
sed '/^        pageSize:$/,/^          type: integer$/d' \
    "$source_contract" > "$missing_page_size"
if "$script_dir/verify-api-contract.sh" "$missing_page_size" >/dev/null 2>&1; then
    echo "producer contract policy test: missing pageSize was accepted" >&2
    exit 1
fi

missing_sort="$temporary_root/missing-sort.yaml"
sed '/^        sort:$/,/^          type: string$/d' \
    "$source_contract" > "$missing_sort"
if "$script_dir/verify-api-contract.sh" "$missing_sort" >/dev/null 2>&1; then
    echo "producer contract policy test: missing sort contract was accepted" >&2
    exit 1
fi

missing_coordinate="$temporary_root/missing-longitude.yaml"
sed '/^        longitude:$/,/^          type: number$/d' \
    "$source_contract" > "$missing_coordinate"
if "$script_dir/verify-api-contract.sh" "$missing_coordinate" >/dev/null 2>&1; then
    echo "producer contract policy test: incomplete HomeLocation was accepted" >&2
    exit 1
fi

missing_response_field="$temporary_root/missing-provider-results.yaml"
sed '/^        providerResults:$/,/^          type: array$/d' \
    "$source_contract" > "$missing_response_field"
if "$script_dir/verify-api-contract.sh" "$missing_response_field" >/dev/null 2>&1; then
    echo "producer contract policy test: incomplete search response was accepted" >&2
    exit 1
fi

missing_partial_status="$temporary_root/missing-partial-status.yaml"
sed '/^        searchStatus:$/,/^          type: string$/d' \
    "$source_contract" > "$missing_partial_status"
if "$script_dir/verify-api-contract.sh" "$missing_partial_status" >/dev/null 2>&1; then
    echo "producer contract policy test: missing partial status was accepted" >&2
    exit 1
fi

missing_matching_status="$temporary_root/missing-matching-status.yaml"
sed '/^        matchingStatus:$/,/^          type: string$/d' \
    "$source_contract" > "$missing_matching_status"
if "$script_dir/verify-api-contract.sh" "$missing_matching_status" >/dev/null 2>&1; then
    echo "producer contract policy test: missing matching status was accepted" >&2
    exit 1
fi

missing_failure_category="$temporary_root/missing-rate-limited-category.yaml"
sed '/^          - RATE_LIMITED$/d' \
    "$source_contract" > "$missing_failure_category"
if "$script_dir/verify-api-contract.sh" "$missing_failure_category" >/dev/null 2>&1; then
    echo "producer contract policy test: incomplete provider failure taxonomy was accepted" >&2
    exit 1
fi

missing_job_identity="$temporary_root/missing-canonical-job-id.yaml"
awk '
    $0 == "    Job:" { in_job = 1 }
    in_job && $0 == "      properties:" { in_properties = 1 }
    in_job && in_properties && $0 == "        canonicalJobId:" {
        print "        canonicalJobIdentity:"
        next
    }
    $0 == "    CanonicalLocation:" { in_job = 0 }
    { print }
' "$source_contract" > "$missing_job_identity"
if "$script_dir/verify-api-contract.sh" "$missing_job_identity" >/dev/null 2>&1; then
    echo "producer contract policy test: incomplete job identity was accepted" >&2
    exit 1
fi

missing_schema_version="$temporary_root/missing-canonical-schema-version.yaml"
sed '/^        canonicalSchemaVersion:$/,/^          type: string$/d' \
    "$source_contract" > "$missing_schema_version"
if "$script_dir/verify-api-contract.sh" "$missing_schema_version" >/dev/null 2>&1; then
    echo "producer contract policy test: missing canonical schema version was accepted" >&2
    exit 1
fi

missing_provenance="$temporary_root/missing-field-provenance.yaml"
sed '/^        fieldProvenance:$/,/^          type: array$/d' \
    "$source_contract" > "$missing_provenance"
if "$script_dir/verify-api-contract.sh" "$missing_provenance" >/dev/null 2>&1; then
    echo "producer contract policy test: missing field provenance was accepted" >&2
    exit 1
fi

missing_unknown="$temporary_root/missing-workplace-unknown.yaml"
awk '
    $0 == "    WorkplaceTypeCode:" { in_schema = 1 }
    in_schema && $0 == "      - UNKNOWN" { next }
    in_schema && $0 == "    ExperienceLevelCode:" { in_schema = 0 }
    { print }
' "$source_contract" > "$missing_unknown"
if "$script_dir/verify-api-contract.sh" "$missing_unknown" >/dev/null 2>&1; then
    echo "producer contract policy test: workplace taxonomy without UNKNOWN was accepted" >&2
    exit 1
fi

missing_saved_route="$temporary_root/missing-saved-route.yaml"
sed '/^  \/api\/jobs\/saved:$/,/^  \/api\/jobs\/saved\/{savedJobId}:$/d' \
    "$source_contract" > "$missing_saved_route"
if "$script_dir/verify-api-contract.sh" "$missing_saved_route" >/dev/null 2>&1; then
    echo "producer contract policy test: missing saved-job route was accepted" >&2
    exit 1
fi

missing_saved_digest="$temporary_root/missing-saved-digest.yaml"
awk '
    $0 == "    SavedJobResponse:" { in_schema = 1 }
    in_schema && $0 == "        contentSha256:" {
        print "        removedContentSha256:"
        next
    }
    $0 == "    SavedJobPageResponse:" { in_schema = 0 }
    { print }
' "$source_contract" > "$missing_saved_digest"
if "$script_dir/verify-api-contract.sh" "$missing_saved_digest" >/dev/null 2>&1; then
    echo "producer contract policy test: saved job without content digest was accepted" >&2
    exit 1
fi

missing_source_state="$temporary_root/missing-source-state.yaml"
awk '
    $0 == "    SavedJobResponse:" { in_schema = 1 }
    in_schema && $0 == "        sourceState:" {
        print "        removedSourceState:"
        next
    }
    $0 == "    SavedJobPageResponse:" { in_schema = 0 }
    { print }
' "$source_contract" > "$missing_source_state"
if "$script_dir/verify-api-contract.sh" "$missing_source_state" >/dev/null 2>&1; then
    echo "producer contract policy test: saved job without source state was accepted" >&2
    exit 1
fi

echo "producer API contract policy tests: passed"
