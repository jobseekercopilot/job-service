#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
contract=${1:-"$script_dir/../api/openapi.yaml"}
temporary_root=$(mktemp -d)
trap 'rm -rf "$temporary_root"' EXIT INT TERM
request_schema="$temporary_root/job-search-request.yaml"
home_schema="$temporary_root/home-location.yaml"
job_schema="$temporary_root/job.yaml"
location_schema="$temporary_root/location.yaml"
source_schema="$temporary_root/source.yaml"
salary_schema="$temporary_root/salary.yaml"
experience_schema="$temporary_root/experience.yaml"
skill_schema="$temporary_root/skill.yaml"
provenance_schema="$temporary_root/provenance.yaml"
response_schema="$temporary_root/job-search-response.yaml"
request_sort_schema="$temporary_root/job-search-request-sort.yaml"
response_sort_schema="$temporary_root/job-search-response-sort.yaml"
search_status_schema="$temporary_root/search-status.yaml"
matching_status_schema="$temporary_root/matching-status.yaml"
provider_status_schema="$temporary_root/provider-result-status.yaml"
target_role_schema="$temporary_root/target-role-results.yaml"
target_role_search_status_schema="$temporary_root/target-role-search-status.yaml"
saved_job_schema="$temporary_root/saved-job-response.yaml"
saved_job_page_schema="$temporary_root/saved-job-page-response.yaml"

extract_schema() {
    awk -v target="    $1:" '
        $0 == target { found = 1 }
        found && $0 != target && $0 ~ /^    [A-Za-z][A-Za-z0-9]*:$/ { exit }
        found { print }
    ' "$contract"
}

extract_property() {
    extract_schema "$1" | awk -v target="        $2:" '
        $0 == target { found = 1 }
        found && $0 != target && $0 ~ /^        [A-Za-z][A-Za-z0-9]*:$/ { exit }
        found { print }
    '
}

test -f "$contract" && test ! -L "$contract" || {
    echo "producer contract policy: api/openapi.yaml is missing or symbolic" >&2
    exit 1
}

grep -Eq '^openapi: 3\.0\.[0-9]+$' "$contract"
grep -F '  version: 2.2.0' "$contract" >/dev/null
grep -F '  /api/jobs/search:' "$contract" >/dev/null
grep -F '      operationId: searchJobs' "$contract" >/dev/null
grep -F '  /api/jobs/saved:' "$contract" >/dev/null
grep -F '  /api/jobs/saved/{savedJobId}:' "$contract" >/dev/null
for operation in list save get unsave; do
    grep -F "      operationId: $operation" "$contract" >/dev/null
done

extract_schema JobSearchRequest | sed -n '/^      properties:$/,$p' \
    > "$request_schema"
grep -F '        homeLocation:' "$request_schema" >/dev/null
grep -E "          \\\$ref: ['\"]#/components/schemas/HomeLocation['\"]" \
    "$request_schema" >/dev/null
grep -F '        selectedProviders:' "$request_schema" >/dev/null
for property in page pageSize sort; do
    grep -F "        $property:" "$request_schema" >/dev/null
done
extract_property JobSearchRequest sort > "$request_sort_schema"
for sort in MOST_RELEVANT CLOSEST HIGHEST_SALARY NEWEST_POSTED \
        OLDEST_POSTED COMPANY_AZ JOB_TITLE_AZ; do
    grep -F "          - $sort" "$request_sort_schema" >/dev/null
done

extract_schema HomeLocation > "$home_schema"
for property in displayName postcode latitude longitude; do
    grep -F "        $property:" "$home_schema" >/dev/null
done

extract_schema Job | sed -n '/^      properties:$/,$p' > "$job_schema"
for property in canonicalSchemaVersion canonicalJobId primarySource externalJobId \
        canonicalLocation employmentTypeCode contractTypeCode workplaceType \
        postedAtUtc expiresAtUtc applicationDeadlineAtUtc sourceUrl sources \
        skills experience fieldProvenance applicationStatus applicationId \
        cvDocumentId coverLetterDocumentId appliedAt applicationUpdatedAt; do
    grep -F "        $property:" "$job_schema" >/dev/null
done

extract_schema CanonicalLocation > "$location_schema"
for property in rawDisplayName rawCity rawRegion rawCountry displayName postcode \
        latitude longitude areaParts city region countryCode sourceProvider \
        normalisationStatus normalisationConfidence; do
    grep -F "        $property:" "$location_schema" >/dev/null
done

extract_schema JobSourceReference > "$source_schema"
for property in integrationProvider provider publisher rawPublisher sourceType \
        externalJobId listingUrl applyUrl directApply providerPostedAtRaw \
        providerPostedAtUtc providerExpiresAtRaw providerExpiresAtUtc \
        retrievedAtUtc; do
    grep -F "        $property:" "$source_schema" >/dev/null
done

extract_schema JobSalary > "$salary_schema"
for property in rawMinimum rawMaximum rawCurrency rawPeriod minimum maximum \
        currencyCode periodCode predicted sourceProvider normalisationStatus \
        normalisationConfidence normalisationMethod; do
    grep -F "        $property:" "$salary_schema" >/dev/null
done

extract_schema JobExperience > "$experience_schema"
for property in rawValue level minimumYears maximumYears \
        normalisationConfidence normalisationStatus sourceProvider; do
    grep -F "        $property:" "$experience_schema" >/dev/null
done

extract_schema JobSkill > "$skill_schema"
for property in name rawName type normalisationConfidence \
        normalisationStatus sourceProvider; do
    grep -F "        $property:" "$skill_schema" >/dev/null
done

extract_schema JobFieldProvenance > "$provenance_schema"
for property in fieldName sourceProvider sourceExternalJobId rawValue \
        normalisedValue status confidence ruleVersion; do
    grep -F "        $property:" "$provenance_schema" >/dev/null
done

for enum_schema in CanonicalValueStatus EmploymentTypeCode ContractTypeCode \
        WorkplaceTypeCode ExperienceLevelCode JobSkillType JobSourceType \
        SalaryPeriodCode; do
    extract_schema "$enum_schema" \
        | grep -F '      - UNKNOWN' >/dev/null
done

extract_schema ReedJobSearchResponse | sed -n '/^      properties:$/,$p' \
    > "$response_schema"
grep -F '        resultsByTargetRole:' "$response_schema" >/dev/null
grep -F '        providerResults:' "$response_schema" >/dev/null
grep -F '        searchStatus:' "$response_schema" >/dev/null
grep -F '        matchingStatus:' "$response_schema" >/dev/null
for property in totalResults page pageSize totalPages sort; do
    grep -F "        $property:" "$response_schema" >/dev/null
done
extract_property ReedJobSearchResponse searchStatus > "$search_status_schema"
extract_property ReedJobSearchResponse matchingStatus > "$matching_status_schema"
extract_property ReedJobSearchResponse sort > "$response_sort_schema"
for status in COMPLETE PARTIAL; do
    grep -F "          - $status" "$search_status_schema" >/dev/null
done
for status in COMPLETE NOT_RUN UNAVAILABLE TIMED_OUT SATURATED; do
    grep -F "          - $status" "$matching_status_schema" >/dev/null
done
for sort in MOST_RELEVANT CLOSEST HIGHEST_SALARY NEWEST_POSTED \
        OLDEST_POSTED COMPANY_AZ JOB_TITLE_AZ; do
    grep -F "          - $sort" "$response_sort_schema" >/dev/null
done

extract_schema TargetRoleJobResults > "$target_role_schema"
for property in targetRole jobs totalResults page pageSize totalPages \
        providerResults searchStatus matchingStatus; do
    grep -F "        $property:" "$target_role_schema" >/dev/null
done
extract_property TargetRoleJobResults searchStatus \
    > "$target_role_search_status_schema"
for status in COMPLETE PARTIAL UNAVAILABLE; do
    grep -F "          - $status" "$target_role_search_status_schema" >/dev/null
done

extract_schema ProviderResultStatus > "$provider_status_schema"
for field in provider status rawResultCount; do
    grep -F "        $field:" "$provider_status_schema" >/dev/null
done
for status in SUCCESS DISABLED UNAVAILABLE TIMED_OUT SATURATED RATE_LIMITED \
        CONFIGURATION_ERROR REJECTED; do
    grep -F "          - $status" "$provider_status_schema" >/dev/null
done

extract_schema SavedJobResponse > "$saved_job_schema"
for property in savedJobId canonicalJobId canonicalSchemaVersion snapshotVersion \
        contentVersion contentSha256 capturedAt sourceRetrievedAt sourceState \
        savedAt updatedAt job; do
    grep -F "        $property:" "$saved_job_schema" >/dev/null
done

extract_schema SavedJobPageResponse > "$saved_job_page_schema"
for property in items page size totalElements totalPages; do
    grep -F "        $property:" "$saved_job_page_schema" >/dev/null
done

echo "producer API contract policy: passed"
