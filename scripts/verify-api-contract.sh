#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
contract=${1:-"$script_dir/../api/openapi.yaml"}
temporary_root=$(mktemp -d)
trap 'rm -rf "$temporary_root"' EXIT INT TERM
request_schema="$temporary_root/job-search-request.yaml"
home_schema="$temporary_root/home-location.yaml"

test -f "$contract" && test ! -L "$contract" || {
    echo "producer contract policy: api/openapi.yaml is missing or symbolic" >&2
    exit 1
}

grep -Eq '^openapi: 3\.0\.[0-9]+$' "$contract"
grep -F '  /api/jobs/search:' "$contract" >/dev/null
grep -F '      operationId: searchJobs' "$contract" >/dev/null

sed -n '/^    JobSearchRequest:$/,/^    HomeLocation:$/p' "$contract" > "$request_schema"
grep -F '        homeLocation:' "$request_schema" >/dev/null
grep -F '          $ref: "#/components/schemas/HomeLocation"' "$request_schema" >/dev/null
grep -F '        selectedProviders:' "$request_schema" >/dev/null

sed -n '/^    HomeLocation:$/,/^    SalaryExpectation:$/p' "$contract" > "$home_schema"
for property in displayName postcode latitude longitude; do
    grep -F "        $property:" "$home_schema" >/dev/null
done

echo "producer API contract policy: passed"
