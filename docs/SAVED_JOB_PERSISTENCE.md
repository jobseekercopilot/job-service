# Saved-job persistence and recovery

## Ownership and API contract

Job Service is the authority for saved canonical jobs. It derives ownership
only from the verified access-token `sub`; client-supplied owner headers and
snapshot metadata are ignored. Saved IDs are stable per owner and canonical job
ID, so two users saving the same listing never share an owner-visible record.

`POST /api/jobs/saved` accepts canonical Job schema 2.0. It returns:

- `201` and `X-Saved-Job-Outcome: CREATED` for the first save;
- `200` with `REPLAYED` when the canonical content is unchanged;
- `200` with `UPDATED` when changed source content appends a snapshot; or
- `200` with `REACTIVATED` when a previously unsaved job becomes active.

`GET` returns only the owner's active record. Missing, unsaved and other-user
identifiers all return the same safe `404`. `DELETE` is idempotent and always
returns `204` for an authenticated caller, including for missing and
other-owner identifiers.

The authoritative response includes `savedJobId`, canonical ID/schema,
snapshot version, `sha256:` content version, raw SHA-256 digest, capture/source
timestamps, source state and the immutable canonical snapshot. Downstream
generation must retrieve this response server-side; browser job content is not
authoritative.

## Data model and invariants

Flyway migration `V1__create_saved_job_snapshots.sql` creates:

- `saved_jobs`, the mutable owner-scoped selection with `ACTIVE` or `UNSAVED`
  state and a pointer to its current snapshot; and
- `saved_job_snapshots`, append-only canonical JSON plus schema version,
  digest, snapshot version and provenance timestamps.

Unique constraints enforce one selection per owner/canonical job, one content
digest per saved job, and one row per snapshot version. Deterministic UUIDs,
row locks, optimistic versioning, unique constraints and bounded retries make
equivalent concurrent saves converge on one identity and snapshot.

Before hashing and storage, canonical JSON keys are recursively ordered and
user-specific search/application fields are removed. Snapshot input is bounded
by field, collection and encoded-size limits. A passed expiry or application
deadline returns `sourceState=EXPIRED_SNAPSHOT`; provider deletion or expiry
does not erase the user-owned historical snapshot.

Unsave is deliberately soft. It removes the record from active list/get
results but retains immutable snapshot history for recovery and existing
references. A later save reactivates the same identity and content version.
Any retention/deletion policy change requires privacy and product approval plus
a new forward migration.

## Production startup controls

The default profile requires PostgreSQL and validates all of the following
before Flyway migration and application readiness:

- a `jdbc:postgresql:` URL and `sslmode=verify-full`;
- a dedicated non-root database role and a strong injected password;
- explicit encryption-at-rest and encrypted-backup declarations with
  non-secret key references;
- Flyway enabled, validation enabled and clean disabled;
- Hibernate `ddl-auto=validate`; and
- no H2 console, SQL output or bind-value logging.

Secrets belong in the deployment secret manager, never source, environment
examples, logs or diagnostics. The `production-safety-check=false` escape hatch
is for isolated tests and local disposable PostgreSQL only; it is forbidden in
deployed environments.

## Backup, restore and rollback

The platform database plan should provide encrypted automatic snapshots and
point-in-time recovery. Before a migration or risky release:

1. Record the application revision, Flyway version, database snapshot ID and
   expected `saved_jobs`/`saved_job_snapshots` counts.
2. Create and verify an encrypted database snapshot or `pg_dump` artefact in an
   access-controlled location.
3. Restore into a new database; never overwrite the only healthy database.
4. Run Flyway validation and start Job Service with Hibernate validation
   against the restored database.
5. Compare schema history, row counts and a sample of content digests without
   logging owner IDs or canonical JSON.
6. Switch application traffic only after health and owner-isolation smoke
   checks pass.

For application rollback, deploy the previous compatible image. If a schema
rollback is required, restore the last verified backup into a new PostgreSQL
database and switch the connection. Do not use Flyway clean, destructive
down-migrations or Hibernate schema mutation.

## Required release evidence

Run the API contract policy, full Maven verification and source-built
container verification. In addition, use disposable real PostgreSQL to prove:

- migration from an empty database;
- equivalent and changed concurrent saves;
- owner isolation and idempotent unsave;
- data survival across application restart;
- dump/restore into a new database; and
- startup rejection for unsafe production settings.

Record tool versions and redacted commands/results on SEARCH-11. Cloud
encryption, key management, automated backups and restore timing remain
deployment evidence owned by the Infrastructure epic; local proof must not be
represented as AWS production approval.
