# Job Search paging and sorting

`POST /api/jobs/search` exposes one-based aggregate paging. A missing `page`
defaults to `1`; a missing `pageSize` defaults to `10`. Valid pages are `1` to
`100`, and valid page sizes are `1` to `50`. Invalid values are rejected before
any provider is called.

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
Every order then uses target role and stable canonical/provider identity as
deterministic tie-breakers.

Both `jobs` and each `resultsByTargetRole[].jobs` contain only the requested
aggregate page. `totalResults` is the number of role/job rows in the bounded
aggregate window before slicing, and `totalPages` is derived from that number
and the effective `pageSize`. A valid page beyond the final page returns an
empty job list without changing the aggregate totals.

## Fetch and memory budgets

- At most 10 distinct target roles are accepted.
- At most 100 results from any provider call enter the aggregate.
- At most 1,000 aggregate rows are sorted and exposed through paging.
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

Only complete provider snapshots are cached. Aggregate page and sort are
applied after reading the snapshot, so changing page or sort does not trigger a
new provider call while the same complete snapshot remains cached.

## Consumer rollout

This producer contract is API version `2.1.0`. Consumers may continue omitting
the additive request fields and receive the defaults. Job Finder should pin the
reviewed `2.1.0` contract before exposing page, page size and sort to the
browser.
