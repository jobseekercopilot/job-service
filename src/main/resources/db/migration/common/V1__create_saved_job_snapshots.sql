CREATE TABLE saved_jobs (
    id UUID PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    canonical_job_id VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL,
    current_snapshot_id UUID,
    current_snapshot_version BIGINT NOT NULL DEFAULT 0,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unsaved_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_saved_jobs_owner_canonical
        UNIQUE (user_id, canonical_job_id),
    CONSTRAINT ck_saved_jobs_state
        CHECK (state IN ('ACTIVE', 'UNSAVED')),
    CONSTRAINT ck_saved_jobs_snapshot_version
        CHECK (current_snapshot_version >= 0)
);

CREATE TABLE saved_job_snapshots (
    id UUID PRIMARY KEY,
    saved_job_id UUID NOT NULL,
    snapshot_version BIGINT NOT NULL,
    canonical_schema_version VARCHAR(32) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    content_version VARCHAR(72) NOT NULL,
    snapshot_json TEXT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_retrieved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_saved_job_snapshots_saved_job
        FOREIGN KEY (saved_job_id) REFERENCES saved_jobs(id),
    CONSTRAINT ux_saved_job_snapshots_digest
        UNIQUE (saved_job_id, content_sha256),
    CONSTRAINT ux_saved_job_snapshots_version
        UNIQUE (saved_job_id, snapshot_version),
    CONSTRAINT ck_saved_job_snapshots_version
        CHECK (snapshot_version > 0)
);

ALTER TABLE saved_jobs
    ADD CONSTRAINT fk_saved_jobs_current_snapshot
    FOREIGN KEY (current_snapshot_id) REFERENCES saved_job_snapshots(id);

CREATE INDEX ix_saved_jobs_owner_state_updated
    ON saved_jobs(user_id, state, updated_at DESC);

CREATE INDEX ix_saved_job_snapshots_saved_job_created
    ON saved_job_snapshots(saved_job_id, created_at DESC);
