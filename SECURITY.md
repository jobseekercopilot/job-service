# Security policy

Do not report vulnerabilities, leaked credentials, or personal data in a
public issue. Use the repository's private GitHub Security Advisory channel or
contact an authorised Job Seeker Copilot maintainer directly.

Revoke and rotate a suspected credential before relying on source cleanup.
Include the affected commit or endpoint, impact, reproduction steps, and any
known containment action. Never include live tokens, provider payloads, user
profiles, CVs, application data, or session recordings in a report.

Job Service accepts only RS256 access tokens from the configured JWKS/issuer
boundary. Search ownership comes only from the verified JWT `sub`; callers
cannot override it with `X-User-Id`. Authentication failures use a stable,
redacted response and application logs must not contain access tokens, raw
subjects, roles or locations.

Publish the new and previous public signing keys together during rotation.
Remove the previous key only after the maximum accepted token lifetime and
deployment overlap have elapsed. Never distribute private signing keys to this
service.
