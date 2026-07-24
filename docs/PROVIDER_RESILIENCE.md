# Job Search provider resilience

## Availability contract

Job Service searches enabled provider gateways concurrently. A shared request
deadline bounds the full multi-role search, while every provider and optional
Job Matching call has a smaller per-call budget. Work runs on a fixed-size
executor with a bounded queue; excess work is rejected immediately and reported
as saturation.

When at least one provider succeeds, its canonical jobs are returned even when
another provider times out or fails. The response is:

- `searchStatus=COMPLETE` when every attempted provider and Job Matching
  enrichment completes.
- `searchStatus=PARTIAL` when usable provider jobs are returned with provider
  or matching degradation.
- HTTP 503 when no requested provider is enabled or every attempted provider
  fails.

Job Matching is optional enrichment. Its timeout, saturation or unavailability
does not remove provider results. `matchingStatus` reports `COMPLETE`,
`NOT_RUN`, `UNAVAILABLE`, `TIMED_OUT` or `SATURATED`.

## Default budgets

| Environment variable | Default | Purpose |
| --- | ---: | --- |
| `JOB_SEARCH_REQUEST_TIMEOUT_MS` | 5000 ms | Shared deadline for the entire request |
| `JOB_SEARCH_PROVIDER_TIMEOUT_MS` | 2500 ms | Per-provider execution and HTTP read budget |
| `JOB_SEARCH_MATCHING_TIMEOUT_MS` | 1000 ms | Optional Job Matching execution and HTTP read budget |
| `JOB_SEARCH_CONNECT_TIMEOUT_MS` | 500 ms | Provider and matching connection budget |
| `JOB_SEARCH_EXECUTOR_THREADS` | 8 | Maximum concurrent downstream tasks |
| `JOB_SEARCH_EXECUTOR_QUEUE_CAPACITY` | 16 | Maximum queued downstream tasks |

Every timeout must be positive, provider and matching timeouts must not exceed
the request timeout, executor threads must be positive, and queue capacity
cannot be negative. Invalid settings stop service startup. A zero-capacity queue
provides direct handoff and rejects work when every downstream thread is busy.

The shared request deadline includes provider work, canonical processing,
distance enrichment, optional matching and additional requested roles. A task
that cannot finish within its remaining budget is cancelled. The underlying
HTTP timeout independently bounds a call that does not respond to thread
interruption.

## Provider failure taxonomy

| Status | Meaning | Response action |
| --- | --- | --- |
| `SUCCESS` | Provider completed, including a healthy empty result | Include its jobs and raw count |
| `DISABLED` | Provider is disabled by runtime configuration | Do not treat as an attempted failure |
| `TIMED_OUT` | Provider exceeded its execution, connection or read budget | Preserve other providers and mark partial |
| `SATURATED` | Bounded executor had no capacity | Fail fast, preserve other providers and mark partial |
| `RATE_LIMITED` | Gateway returned HTTP 429 | Preserve other providers and mark partial |
| `CONFIGURATION_ERROR` | Gateway returned HTTP 401 or 403 | Preserve other providers; correct gateway configuration |
| `REJECTED` | Gateway rejected another 4xx request | Preserve other providers; inspect request compatibility |
| `UNAVAILABLE` | Other gateway, network or runtime failure | Preserve other providers and mark partial |

Client responses contain stable categories and messages, not raw downstream
response bodies, credentials or exception detail. The inbound
`X-Correlation-Id` is preserved in executor work and forwarded on provider and
Job Matching HTTP requests.

## Cache isolation

Only complete provider fan-out results are cached. Cache keys include role,
location, distance, remote preference, employment types, salary bounds,
currency and provider selection. Cached values are deep provider-only
snapshots. User-specific matching and application fields are excluded so one
user's application identifiers, status or documents cannot appear in another
user's search.

The current cache remains process-local and bounded only by service lifetime.
Provider-terms-aware TTL, maximum size and distributed invalidation remain
tracked outside SEARCH-07.

## Operator actions

- Rising `TIMED_OUT`: check provider latency and connectivity, then compare the
  provider/read budget with the shared request budget. Do not raise timeouts
  beyond the user-facing latency objective without review.
- Rising `SATURATED`: inspect downstream latency and concurrent demand before
  changing thread or queue capacity. A larger queue increases waiting time and
  memory use; it does not create downstream capacity.
- `RATE_LIMITED`: use the provider gateway's rate-limit controls and provider
  agreement. Job Service does not own provider credentials or retry policy.
- `CONFIGURATION_ERROR`: validate the provider gateway's secret/configuration
  and fixture/live mode. Never add provider credentials to Job Service.
- Matching degradation with healthy providers: keep search available and
  investigate Job Matching separately.
- All-provider HTTP 503: correlate the request across Job Service and gateways,
  check enabled-provider configuration, then use provider-specific runbooks.

Rollback restores the prior reviewed Job Service revision and its OpenAPI
contract together. Do not retain the new `1.1.0` response contract while
running an implementation that cannot populate the required status fields.
