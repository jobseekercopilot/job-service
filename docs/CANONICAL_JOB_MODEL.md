# Canonical Job schema 2.0

## Purpose and ownership

Job Service owns the canonical Job schema returned to Job Finder. Provider
gateways own provider credentials, external API payloads and their public
gateway contracts. The canonical model retains enough raw evidence to explain
mapping decisions without exposing full provider payloads or provider-specific
DTOs.

`api/openapi.yaml` version 1.2.0 publishes canonical Job schema version `2.0`.
The two versions are separate: the API version covers the service contract,
while `canonicalSchemaVersion` identifies the field dictionary carried by each
Job.

## Field dictionary

| Area | Canonical fields | Raw/legacy evidence | Unknown and optionality rule |
| --- | --- | --- | --- |
| Identity | `canonicalJobId`, `primarySource`, `externalJobId`, `sources` | `id`, `provider` remain deprecated aliases | A provider result must retain its provider and external ID; cross-provider stable identity remains SEARCH-06 |
| Title and company | `title`, `companyName` | `jobTitle`, `company` remain deprecated aliases | Missing text stays absent; it is never replaced with a guessed value |
| Employment | `employmentTypeCode` | `employmentType` retains the provider value | Use `UNKNOWN` until a reviewed SEARCH-05 rule maps the raw value |
| Contract | `contractTypeCode` | `contractType` retains the provider value | Use `UNKNOWN` rather than treating an absent contract as permanent |
| Workplace | `workplaceType` | `remote` remains a deprecated provider Boolean | `remote=true` can map to `REMOTE`; `remote=false` remains `UNKNOWN` because it does not distinguish hybrid/on-site |
| Location | `canonicalLocation.displayName`, city, region, country code, postcode and coordinates | `rawDisplayName`, `rawCity`, `rawRegion`, `rawCountry`, `areaParts` | `normalisationStatus` is required; country code is set only after confident mapping |
| Salary | decimal `minimum`, `maximum`, `currencyCode`, `periodCode`, predicted flag and source | raw decimal amount/currency/period plus deprecated integer/double fields | Status starts `RAW_ONLY` or `NOT_PROVIDED`; unsupported currency is not converted or fabricated |
| Timestamps | `postedAtUtc`, `expiresAtUtc`, `applicationDeadlineAtUtc` | `postedAt`, `expiresAt`, `postedDate` and source raw timestamp fields | A UTC/offset value is populated only when the provider supplied an offset; timezone-less dates remain raw |
| Skills | `skills[]` with canonical/raw name, requirement type, source, status and confidence | `rawName` | Empty means the provider supplied no mapped skills; it does not mean the job requires none |
| Experience | level, minimum/maximum years, source, status and confidence | `rawValue` | Default is `UNKNOWN` / `NOT_PROVIDED` |
| Source and publisher | integration provider, external ID, normalised/raw publisher, source type, listing/apply URLs and source timestamps | raw publisher and raw source timestamps | Source type defaults to `UNKNOWN`; every retained provider/apply option remains a source record |
| Mapping evidence | `fieldProvenance[]` with field, source identity, raw/normalised value, status, confidence and rule version | raw field value | Full provider payloads, secrets and sensitive request data must not be copied into provenance |
| User application state | application ID/status, document IDs and application timestamps | none | These fields are user-specific enrichment, never provider-cache content |

Confidence values are decimal numbers from zero to one. A missing confidence
means no reviewed confidence assertion exists. `CanonicalValueStatus` is one of
`NORMALISED`, `RAW_ONLY`, `NOT_PROVIDED`, `INVALID`, `UNSUPPORTED` or
`UNKNOWN`.

## Provider mapping matrix

| Canonical area | Reed | Adzuna | JSearch | Remaining owner |
| --- | --- | --- | --- | --- |
| Provider identity | Reed external ID | Adzuna external ID | JSearch external ID | SEARCH-06 defines cross-provider stable identity |
| Publisher/source | Reed.co.uk / job board | Adzuna / aggregator | Every apply option with raw and normalised publisher | Provider mapping issues complete fixture coverage |
| Location | Raw display label | Raw display, area parts and coordinates | Raw display/city/state/country, area parts and coordinates | SEARCH-05 normalises city/region/country/postcode |
| Salary | Raw integer range, provider currency, annual period | Raw integer GBP annual range and provider predicted flag | Raw integer range, provider currency and period | SEARCH-05 owns decimal/formula/currency policy |
| Employment/contract | Raw employment | Raw employment and contract | Raw employment | SEARCH-05 maps provider variants to explicit enums |
| Workplace | Unknown | Unknown | `remote=true` maps to `REMOTE`; false remains unknown | SEARCH-05 maps richer provider evidence |
| Posted/expiry | Raw posting value; offset instant only when supplied | Raw posting value and exact offset instant | Raw posting/expiry and exact offset instants | SEARCH-05 owns malformed/timezone-less policy |
| Skills/experience | Not present in pinned contract | Not present in pinned contract | Not present in pinned contract | Provider contract owners add evidence before mapping |

The matrix describes current pinned producer contracts. It must be updated
together with any contract revision that adds or changes provider fields.

## URL safety

Canonical `url`, `sourceUrl`, `listingUrl` and `applyUrl` expose only absolute
credential-free HTTP(S) URLs with a host. Relative, malformed, `javascript:`,
`data:`, `file:`, FTP and user-info URLs are rejected rather than returned.
Consumers must still treat URLs as untrusted external navigation and apply
their own safe-link policy; the client control is tracked separately.

## Matching ownership boundary

Job Matching does not own provider or canonical fields. Job Service correlates a
Matching response by canonical, legacy or provider identity and overlays only
`matchScore` plus user application/document/timestamp fields onto the original
canonical Job. It ignores replacement title, source, salary, location,
provenance, skills and experience values and never accepts extra jobs returned
by Matching.

This protects schema 2.0 while the broader typed immutable Job Matching response
is completed under MATCH-08.

## Compatibility and migration

Schema 2.0 is additive:

1. Existing fields remain present. Ambiguous aliases are marked deprecated but
   are not removed.
2. New enum fields always use explicit `UNKNOWN` defaults. Missing and explicit
   null list/object values fail safe to empty collections or unknown objects.
3. Existing 1.1 consumers can ignore new JSON properties. New generated
   consumers must pin the exact 1.2 producer revision and checksum.
4. Consumers migrate display/filter logic to the canonical fields, retain
   legacy fallback during one reviewed compatibility window, then remove
   fallback only through a future breaking API version.
5. Job Matching and persistence consumers must adopt this identity/provenance
   contract through their tracked issues; Job Service must not copy their
   repository-specific models back into this schema.

Serialization tests cover a full schema round trip, a legacy 1.1 payload, and
100 deterministic missing/null combinations. Provider adapter tests prove raw
evidence and exact offset timestamps survive mapping. Cache tests prove nested
canonical evidence is deep-copied and cannot be mutated across users. Matching
tests prove only Matching-owned enrichment can be overlaid.
