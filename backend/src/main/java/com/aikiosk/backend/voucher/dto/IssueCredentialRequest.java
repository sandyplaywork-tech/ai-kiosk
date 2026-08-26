package com.aikiosk.backend.voucher.dto;

import com.aikiosk.backend.voucher.CredentialType;

public record IssueCredentialRequest(
        CredentialType type,
        Integer tokenCap,
        Integer sessionLengthMinutes) {
}
