-- Analyses stored before V6 were produced by the model at least once, but V6 gave them
-- model_calls = 0, which reads as "free". Count them as one call; their tokens stay unknown.
UPDATE job_analyses SET model_calls = 1 WHERE source = 'LLM' AND model_calls = 0 AND prompt_tokens IS NULL;
