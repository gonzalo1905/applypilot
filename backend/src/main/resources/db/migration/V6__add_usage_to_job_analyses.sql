-- What a job posting analysis cost, so a match run can trace its Extractor step too.
-- Zero calls and no tokens for keyword fallbacks and reused (cached) results.
ALTER TABLE job_analyses
    ADD COLUMN model_calls       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN prompt_tokens     INTEGER,
    ADD COLUMN completion_tokens INTEGER;
