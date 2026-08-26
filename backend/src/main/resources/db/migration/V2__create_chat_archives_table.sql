CREATE TABLE chat_archives (
    id UUID PRIMARY KEY,
    voucher_id UUID NOT NULL REFERENCES vouchers(id),
    session_id VARCHAR(64) NOT NULL,
    transcript TEXT NOT NULL,
    session_started_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_chat_archives_session_id ON chat_archives (session_id);
CREATE INDEX idx_chat_archives_archived_at ON chat_archives (archived_at);
