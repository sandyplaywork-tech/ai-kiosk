package com.aikiosk.backend.session;

public record AdminSessionSummary(
        String sessionId,
        String label,
        int tokenCap,
        int tokensRemaining,
        long timeRemainingSeconds,
        boolean paused) {
}
