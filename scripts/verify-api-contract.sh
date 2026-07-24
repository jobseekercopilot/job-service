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
search_status_schema="$temporary_root/search-status.yaml"
matching_status_schema="$temporary_root/matching-status.yaml"
provider_status_schema="$temporary_root/provider-result-status.yaml"

test -f "$contract" && test ! -L "$contract" || {
    echo "producer contract policy: api/openapi.yaml is missing or symbolic" >&2
    exit 1
}

grep -Eq '^openapi: 3\.0\.[0-9]+$' "$contract"
grep -F '  version: 1.2.0' "$contract" >/dev/null
grep -F '  /api/jobs/search:' "$contract" >/dev/null
grep -F '      operationId: searchJobs' "$contract" >/dev/null

sed -n '/^    JobSearchRequest:$/,/^    HomeLocation:$/p' "$contract" \
    | sed -n '/^      properties:$/,$p' > "$request_schema"
grep -F '        homeLocation:' "$request_schema" >/dev/null
grep -F '          $ref: "#/components/schemas/HomeLocation"' "$request_schema" >/dev/null
grep -F '        selectedProviders:' "$request_schema" >/dev/null

sed -n '/^    HomeLocation:$/,/^    SalaryExpectation:$/p' "$contract" > "$home_schema"
for property in displayName postcode latitude longitude; do
    grep -F "        $property:" "$home_schema" >/dev/null
done

sed -n '/^    Job:$/,/^    CanonicalLocation:$/p' "$contract" \
    | sed -n '/^      properties:$/,$p' > "$job_schema"
for property in canonicalSchemaVersion canonicalJobId primarySource externalJobId \
        canonicalLocation employmentTypeCode contractTypeCode workplaceType \
        postedAtUtc expiresAtUtc applicationDeadlineAtUtc sourceUrl sources \
        skills experience fieldProvenance applicationStatus applicationId \
        cvDocumentId coverLetterDocumentId appliedAt applicationUpdatedAt; do
    grep -F "        $property:" "$job_schema" >/dev/null
done

sed -n '/^    CanonicalLocation:$/,/^    JobSourceReference:$/p' \
    "$contract" > "$location_schema"
for property in rawDisplayName rawCity rawRegion rawCountry displayName postcode \
        latitude longitude areaParts city region countryCode sourceProvider \
        normalisationStatus normalisationConfidence; do
    grep -F "        $property:" "$location_schema" >/dev/null
done

sed -n '/^    JobSourceReference:$/,/^    JobSalary:$/p' \
    "$contract" > "$source_schema"
for property in integrationProvider provider publisher rawPublisher sourceType \
        externalJobId listingUrl applyUrl directApply providerPostedAtRaw \
        providerPostedAtUtc providerExpiresAtRaw providerExpiresAtUtc \
        retrievedAtUtc; do
    grep -F "        $property:" "$source_schema" >/dev/null
done

sed -n '/^    JobSalary:$/,/^    JobExperience:$/p' \
    "$contract" > "$salary_schema"
for property in rawMinimum rawMaximum rawCurrency rawPeriod minimum maximum \
        currencyCode periodCode predicted sourceProvider normalisationStatus \
        normalisationConfidence normalisationMethod; do
    grep -F "        $property:" "$salary_schema" >/dev/null
done

sed -n '/^    JobExperience:$/,/^    JobSkill:$/p' \
    "$contract" > "$experience_schema"
for property in rawValue level minimumYears maximumYears \
        normalisationConfidence normalisationStatus sourceProvider; do
    grep -F "        $property:" "$experience_schema" >/dev/null
done

sed -n '/^    JobSkill:$/,/^    JobFieldProvenance:$/p' \
    "$contract" > "$skill_schema"
for property in name rawName type normalisationConfidence \
        normalisationStatus sourceProvider; do
    grep -F "        $property:" "$skill_schema" >/dev/null
done

sed -n '/^    JobFieldProvenance:$/,/^    CanonicalValueStatus:$/p' \
    "$contract" > "$provenance_schema"
for property in fieldName sourceProvider sourceExternalJobId rawValue \
        normalisedValue status confidence ruleVersion; do
    grep -F "        $property:" "$provenance_schema" >/dev/null
done

for enum_schema in CanonicalValueStatus EmploymentTypeCode ContractTypeCode \
        WorkplaceTypeCode ExperienceLevelCode JobSkillType JobSourceType \
        SalaryPeriodCode; do
    enum_file="$temporary_root/$enum_schema.yaml"
    sed -n "/^    $enum_schema:\$/,/^      type: string\$/p" \
        "$contract" > "$enum_file"
    grep -F '      - UNKNOWN' "$enum_file" >/dev/null
done

sed -n '/^    ReedJobSearchResponse:$/,/^    TargetRoleJobResults:$/p' "$contract" \
    | sed -n '/^      properties:$/,$p' > "$response_schema"
grep -F '        resultsByTargetRole:' "$response_schema" >/dev/null
grep -F '        providerResults:' "$response_schema" >/dev/null
grep -F '        searchStatus:' "$response_schema" >/dev/null
grep -F '        matchingStatus:' "$response_schema" >/dev/null
sed -n '/^        searchStatus:$/,/^          type: string$/p' \
    "$response_schema" > "$search_status_schema"
sed -n '/^        matchingStatus:$/,/^          type: string$/p' \
    "$response_schema" > "$matching_status_schema"
for status in COMPLETE PARTIAL; do
    grep -F "          - $status" "$search_status_schema" >/dev/null
done
for status in COMPLETE NOT_RUN UNAVAILABLE TIMED_OUT SATURATED; do
    grep -F "          - $status" "$matching_status_schema" >/dev/null
done

sed -n '/^    ProviderResultStatus:$/,$p' "$contract" > "$provider_status_schema"
for field in provider status rawResultCount; do
    grep -F "        $field:" "$provider_status_schema" >/dev/null
done
for status in SUCCESS DISABLED UNAVAILABLE TIMED_OUT SATURATED RATE_LIMITED \
        CONFIGURATION_ERROR REJECTED; do
    grep -F "          - $status" "$provider_status_schema" >/dev/null
done

echo "producer API contract policy: passed"
