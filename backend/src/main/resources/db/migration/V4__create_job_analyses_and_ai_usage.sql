-- Structured data extracted from a job posting, one per application.
CREATE TABLE job_analyses (
    id                   UUID          PRIMARY KEY,
    application_id       UUID          NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    user_id              UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- The text the user pasted; later AI features (matching, writing) read it again.
    posting_text         TEXT          NOT NULL,
    -- SHA-256 of the normalized text: the same posting is never sent to the model twice.
    posting_hash         CHAR(64)      NOT NULL,
    company              VARCHAR(160),
    role_title           VARCHAR(200),
    seniority            VARCHAR(20)   NOT NULL,
    required_skills      TEXT[]        NOT NULL,
    nice_to_have_skills  TEXT[]        NOT NULL,
    work_mode            VARCHAR(20),
    location             VARCHAR(160),
    languages            TEXT[]        NOT NULL,
    salary               VARCHAR(160),
    summary              TEXT          NOT NULL,
    -- LLM, or FALLBACK when the keyword extractor produced the result.
    source               VARCHAR(20)   NOT NULL,
    fallback_reason      VARCHAR(30),
    model                VARCHAR(100),
    analyzed_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_job_analyses_application UNIQUE (application_id),
    CONSTRAINT ck_job_analyses_source CHECK (source IN ('LLM', 'FALLBACK')),
    CONSTRAINT ck_job_analyses_seniority
        CHECK (seniority IN ('INTERN', 'JUNIOR', 'MID', 'SENIOR', 'LEAD', 'UNSPECIFIED')),
    CONSTRAINT ck_job_analyses_work_mode
        CHECK (work_mode IS NULL OR work_mode IN ('REMOTE', 'HYBRID', 'ONSITE'))
);

-- Cache lookup: "has this user already analyzed this exact text with the model?"
CREATE INDEX idx_job_analyses_user_hash ON job_analyses (user_id, posting_hash);

-- How many AI calls each user made on each day (enforces the per-user daily limit).
CREATE TABLE ai_usage (
    id         UUID    PRIMARY KEY,
    user_id    UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    usage_date DATE    NOT NULL,
    calls      INTEGER NOT NULL,
    CONSTRAINT uk_ai_usage_user_date UNIQUE (user_id, usage_date),
    CONSTRAINT ck_ai_usage_calls CHECK (calls >= 0)
);
