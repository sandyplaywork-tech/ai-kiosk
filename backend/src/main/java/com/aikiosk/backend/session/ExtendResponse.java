package com.aikiosk.backend.session;

public record ExtendResponse(int tokenCap, int tokensRemaining, long timeRemainingSeconds) {
}
