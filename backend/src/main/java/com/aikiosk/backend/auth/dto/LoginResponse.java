package com.aikiosk.backend.auth.dto;

public record LoginResponse(String sessionId, int tokenCap, int tokensRemaining, long timeRemainingSeconds) {
}
