package com.aikiosk.backend.session;

import java.util.UUID;

public record SessionOverview(
        String sessionId,
        UUID voucherId,
        int tokenCap,
        int tokensRemaining,
        long timeRemainingSeconds,
        boolean paused) {
}
