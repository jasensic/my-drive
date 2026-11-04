-- Background work (see domain::jobs): queued -> running -> deleted when done, or failed.
CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY,
    kind TEXT NOT NULL,
    subject UUID,
    state TEXT NOT NULL DEFAULT 'queued',
    attempts INTEGER NOT NULL DEFAULT 0,
    run_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

-- One active job per (kind, subject); jobs without a subject share one slot per kind.
CREATE UNIQUE INDEX IF NOT EXISTS jobs_one_active
    ON jobs (kind, (COALESCE(subject, '00000000-0000-0000-0000-000000000000'::uuid)))
    WHERE state IN ('queued', 'running');

CREATE INDEX IF NOT EXISTS jobs_due ON jobs (run_at) WHERE state IN ('queued', 'running');
