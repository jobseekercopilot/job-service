# Job Service identity boundary

## Trust contract

Job Finder authenticates the user at the public edge and forwards the original
Bearer access token to Job Service. Job Service does not trust Job Finder's
parsing result: it independently verifies the token against the configured
JWKS, issuer and audience.

An accepted token must:

- use RS256;
- have a signature from a public key in `AUTH_JWKS_URI`;
- have the exact configured `JOB_SERVICE_JWT_ISSUER`;
- include `JOB_SERVICE_JWT_AUDIENCE`;
- be within its valid time window;
- contain a nonblank `sub`;
- contain `token_type: access`.

The verified `sub` is the only search identity. `X-User-Id` is absent from the
published contract and cannot override the subject. Health is public. Search
and API documentation require authentication; all unrelated routes fail
closed. Application logs retain counts, provider status, timings and a bounded
correlation identifier, but omit tokens, subjects, requested roles and
locations. Spring Web and Security logging remain pinned at INFO so a broad
runtime debug flag cannot render request or authentication details.

## Rotation

The authentication service publishes both active and retiring public keys
during a rotation window. Tokens signed by either published RS256 key remain
valid while their claims are valid. Keep the retiring public key available
until the longest accepted token lifetime and deployment overlap have passed,
then remove it. Job Service never receives a private signing key.

## Local and test identities

Automated tests start an in-process JWKS endpoint and create disposable RSA
keys and short-lived tokens. They cover active and previous published keys,
forged signatures, unknown keys, wrong algorithms, issuer/audience failures,
expired tokens, non-access tokens and missing subjects. No production key or
token is required for a local test.

For a manual local stack, run the authentication service and set:

```text
AUTH_JWKS_URI=http://authentication-service:8084/.well-known/jwks.json
JOB_SERVICE_JWT_ISSUER=job-seeker-copilot-authentication
JOB_SERVICE_JWT_AUDIENCE=job-seeker-copilot-services
```

Use a short-lived access token issued by that local authentication service.
Do not commit tokens or private keys, put them in URLs, or copy them into logs.
