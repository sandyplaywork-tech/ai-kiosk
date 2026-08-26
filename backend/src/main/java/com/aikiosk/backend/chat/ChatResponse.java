package com.aikiosk.backend.chat;

public record ChatResponse(String reply, int tokensRemaining, long timeRemainingSeconds) {
}
