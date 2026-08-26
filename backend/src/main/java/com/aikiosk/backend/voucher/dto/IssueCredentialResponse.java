package com.aikiosk.backend.voucher.dto;

import java.util.UUID;

/**
 * Plaintext password is only ever present in this one response - it is not
 * retrievable afterward, since only the bcrypt hash is persisted.
 */
public record IssueCredentialResponse(
        UUID id,
        String username,
        String password,
        String voucherCode,
        int tokenCap,
        int sessionLengthMinutes) {
}
