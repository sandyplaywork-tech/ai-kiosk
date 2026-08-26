package com.aikiosk.backend.session;

public record SessionStatus(int tokenCap, int tokensRemaining, long timeRemainingSeconds) {
}
