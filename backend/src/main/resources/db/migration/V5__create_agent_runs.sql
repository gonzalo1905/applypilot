-- One run of the agent team (Extractor -> Matcher -> Writer <-> Reviewer) for an application.
CREATE TABLE agent_runs (
    id                 UUID          PRIMARY KEY,
    application_id     UUID          NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- PENDING (queued), RUNNING, SUCCEEDED, PARTIAL (match without a letter) or FAILED.
    status             VARCHAR(20)   NOT NULL,
    -- The agent working right now, so a polling client can show progress.
    current_agent      VARCHAR(20),
    -- Final results, stored as JSON text written by the application.
    match_result       TEXT,
    cover_letter       TEXT,
    letter_language    VARCHAR(10),
    review             TEXT,
    revisions          INTEGER       NOT NULL DEFAULT 0,
    error_code         VARCHAR(40),
    created_at         TIMESTAMPTZ   NOT NULL,
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    CONSTRAINT ck_agent_runs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_agent_runs_current_agent
        CHECK (current_agent IS NULL OR current_agent IN ('EXTRACTOR', 'MATCHER', 'WRITER', 'REVIEWER')),
    CONSTRAINT ck_agent_runs_revisions CHECK (revisions BETWEEN 0 AND 2)
);

-- "Latest run of this application" and "runs still active" lookups.
CREATE INDEX idx_agent_runs_application_created ON agent_runs (application_id, created_at DESC);
CREATE INDEX idx_agent_runs_status ON agent_runs (status) WHERE status IN ('PENDING', 'RUNNING');

-- One step of a run: which agent ran, on what input, what it produced and what it cost.
-- Together the steps make a run traceable without logging any user data.
CREATE TABLE agent_run_steps (
    id                 UUID          PRIMARY KEY,
    run_id             UUID          NOT NULL REFERENCES agent_runs (id) ON DELETE CASCADE,
    step_order         INTEGER       NOT NULL,
    agent              VARCHAR(20)   NOT NULL,
    -- 1 for the first draft or review, 2 and 3 for revisions.
    attempt            INTEGER       NOT NULL,
    -- LLM, FALLBACK (deterministic code), REUSED (earlier result) or SKIPPED.
    source             VARCHAR(20)   NOT NULL,
    reason             VARCHAR(40),
    -- SHA-256 of the step's input: equal hashes mean the agent saw exactly the same input.
    input_hash         CHAR(64)      NOT NULL,
    output             TEXT,
    model              VARCHAR(100),
    model_calls        INTEGER       NOT NULL DEFAULT 0,
    tool_calls         INTEGER       NOT NULL DEFAULT 0,
    prompt_tokens      INTEGER,
    completion_tokens  INTEGER,
    latency_ms         BIGINT        NOT NULL,
    started_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_agent_run_steps_order UNIQUE (run_id, step_order),
    CONSTRAINT ck_agent_run_steps_agent CHECK (agent IN ('EXTRACTOR', 'MATCHER', 'WRITER', 'REVIEWER')),
    CONSTRAINT ck_agent_run_steps_source CHECK (source IN ('LLM', 'FALLBACK', 'REUSED', 'SKIPPED'))
);
