CREATE TABLE vouchers (
    id UUID PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    password_hash VARCHAR(100),
    voucher_code VARCHAR(32) UNIQUE,
    token_cap INTEGER NOT NULL,
    session_length_minutes INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    CONSTRAINT vouchers_identity_check CHECK (
        (username IS NOT NULL AND password_hash IS NOT NULL) OR voucher_code IS NOT NULL
    )
);

CREATE INDEX idx_vouchers_username ON vouchers (username) WHERE username IS NOT NULL;
CREATE INDEX idx_vouchers_voucher_code ON vouchers (voucher_code) WHERE voucher_code IS NOT NULL;
