CREATE TABLE cvs (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    -- Where the file lives in the storage backend (local disk today, cloud storage later).
    storage_key    VARCHAR(255) NOT NULL,
    -- Plain text extracted from the PDF; this is what the AI features read.
    extracted_text TEXT         NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL,
    -- One CV per user.
    CONSTRAINT uk_cvs_user UNIQUE (user_id)
);
