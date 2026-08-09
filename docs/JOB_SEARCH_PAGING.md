# Job Search paging and sorting

`POST /api/jobs/search` exposes one-based target-role paging. A missing `page`
defaults to `1`; a missing `pageSize` defaults to `10`. Valid pages are `1` to
`100`, and valid page sizes are `1` to `50`. Invalid values are rejected before
any provider is called.

The effective page and page size are applied independently to each requested
target role. A request for `developer` and `tester` with `page=2&pageSize=10`
therefore returns up to ten jobs from the second bounded page for each role.
One role can no longer consume the shared page before another role is grouped.

## Stable aggregate order

The supported sort values are:

- `MOST_RELEVANT` (default)
- `CLOSEST`
- `HIGHEST_SALARY`
- `NEWEST_POSTED`
- `OLDEST_POSTED`
- `COMPANY_AZ`
- `JOB_TITLE_AZ`

Sort names are case-insensitive on input and are returned in uppercase.
Unavailable distance, salary, date, company or title values are placed last.
Every role order then uses stable canonical/provider identity as a
deterministic tie-breaker.

Each `resultsByTargetRole[]` item is authoritative for one role and includes:

- only that role's requested page in `jobs`;
- the bounded `totalResults`, `page`, `pageSize` and `totalPages`;
- that role's `providerResults`;
- that role's `searchStatus`; and
- that role's `matchingStatus`.

`searchStatus=UNAVAILABLE` distinguishes a role whose providers failed from a
healthy role with zero matches. A partial or unavailable role does not discard
healthy roles returned by the same multi-role request. HTTP 503 remains the
outcome when no requested role has any successful provider.

The top-level `jobs`, totals and status fields retain the API 2.1 aggregate
compatibility semantics: `jobs` contains at most `pageSize` rows from the
stably ordered aggregate window, `totalResults` is that bounded window's size,
and `totalPages` is derived from the aggregate total. The same requested page
is also applied independently inside each role result. A valid page beyond one
role's final page returns an empty list for that role without changing its
total.

## Fetch and memory budgets

- At most 10 distinct target roles are accepted.
- At most 100 results from any provider call enter the aggregate.
- At most 1,000 role/job rows enter the legacy aggregate compatibility window.
  Each role result is built from its own provider-bounded canonical snapshot,
  so an earlier role cannot consume a later role's page or totals.
- JSearch may follow one continuation cursor, for at most two gateway calls.
  `JSEARCH_MAX_CURSOR_PAGES` may reduce that budget to one, but cannot raise it
  above two. Blank or repeated cursors stop traversal.
- Reed and Adzuna request one bounded gateway page. Their pinned contracts do
  not provide a common cursor model; their page-size settings define the
  current provider snapshot.

These are snapshot-window semantics, not a promise to exhaust every external
provider result. This prevents one browser request from causing unbounded,
potentially chargeable provider calls. `providerResults[].rawResultCount`
records the provider adapter result count before the coordinator's defensive
cap.

Complete provider snapshots are cached by target role and search criteria and
may be reused across owners because they contain no user-specific enrichment.
The cache identity deliberately excludes page and sort. A partial snapshot is
kept separately for the requesting owner so a later page uses the same
canonical job window and provider outcomes; a new page-one request still
retries the degraded provider instead of treating the partial snapshot as
fresh.

Role-specific page and sort are applied after reading the canonical,
deduplicated snapshot, so later pages do not trigger a new provider call and
cannot repeat a canonical job from an earlier page. Optional application and
document enrichment is re-run for every response page and is never stored in
either provider snapshot cache.

## Consumer rollout

This producer contract is API version `2.3.0`. Consumers may continue omitting
the additive request fields and receive the defaults. Existing top-level
response fields remain available. Consumers with role tabs should use
`resultsByTargetRole` metadata, or issue one single-role request per tab, and
Job Finder should pin the reviewed `2.3.0` contract before exposing the new
role-scoped state to the browser.
