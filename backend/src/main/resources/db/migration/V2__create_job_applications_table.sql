CREATE TABLE job_applications (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company     VARCHAR(120)  NOT NULL,
    role_title  VARCHAR(160)  NOT NULL,
    job_url     VARCHAR(2048),
    location    VARCHAR(120),
    work_mode   VARCHAR(20),
    salary_note VARCHAR(120),
    notes       TEXT,
    status      VARCHAR(20)   NOT NULL,
    position    INTEGER       NOT NULL,
    applied_at  DATE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_job_applications_status
        CHECK (status IN ('SAVED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED')),
    CONSTRAINT ck_job_applications_work_mode
        CHECK (work_mode IS NULL OR work_mode IN ('REMOTE', 'HYBRID', 'ONSITE')),
    CONSTRAINT ck_job_applications_position CHECK (position >= 0)
);

-- Every read is "the cards of one user, by column, in order".
CREATE INDEX idx_job_applications_user_status_position
    ON job_applications (user_id, status, position);
