package com.aikiosk.backend.session;

import java.time.Instant;
import java.util.UUID;

public record SessionMeta(UUID voucherId, Instant startedAt) {
}
