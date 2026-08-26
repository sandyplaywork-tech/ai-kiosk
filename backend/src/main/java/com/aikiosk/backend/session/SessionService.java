package com.aikiosk.backend.session;

import com.aikiosk.backend.chat.ChatTurn;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis is the source of truth for live session state (time + token budget).
 * The key's TTL is what actually enforces the 30-min wall-clock cutoff - once
 * it expires, the session is gone with no extra cleanup step required.
 */
@Service
public class SessionService {

    private static final String KEY_PREFIX = "session:";
    private static final String MESSAGES_KEY_PREFIX = "session:messages:";
    private static final String VOUCHER_INDEX_PREFIX = "session:by-voucher:";
    private static final String STATUS_FIELD = "status";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PAUSED = "paused";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SessionService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void createSession(String sessionId, UUID voucherId, int tokenCap, int sessionLengthMinutes) {
        String key = KEY_PREFIX + sessionId;
        Map<String, String> fields = new HashMap<>();
        fields.put("voucherId", voucherId.toString());
        fields.put("tokenCap", String.valueOf(tokenCap));
        fields.put("tokensRemaining", String.valueOf(tokenCap));
        fields.put("startedAt", Instant.now().toString());
        fields.put(STATUS_FIELD, STATUS_ACTIVE);
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, Duration.ofMinutes(sessionLengthMinutes));

        // Lets a later login with the same voucher find this session again
        // (resume) without needing the sessionId itself. Same TTL as the
        // session so it disappears on its own once the session is gone.
        redisTemplate.opsForValue().set(
                voucherIndexKey(voucherId.toString()), sessionId, Duration.ofMinutes(sessionLengthMinutes));
    }

    public Optional<SessionStatus> getStatus(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        int tokenCap = Integer.parseInt((String) entries.get("tokenCap"));
        int tokensRemaining = Integer.parseInt((String) entries.get("tokensRemaining"));
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long timeRemainingSeconds = ttlSeconds == null ? 0 : Math.max(ttlSeconds, 0);

        return Optional.of(new SessionStatus(tokenCap, tokensRemaining, timeRemainingSeconds));
    }

    /**
     * Read-only identity fields a chat-archive write needs but that don't
     * belong in the frontend-facing {@link SessionStatus}.
     */
    public Optional<SessionMeta> getMeta(String sessionId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(KEY_PREFIX + sessionId);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        UUID voucherId = UUID.fromString((String) entries.get("voucherId"));
        Instant startedAt = Instant.parse((String) entries.get("startedAt"));
        return Optional.of(new SessionMeta(voucherId, startedAt));
    }

    /**
     * Atomic decrement so a burst of near-simultaneous requests against the
     * same session can't each read a stale tokensRemaining and both proceed.
     * Floors at 0 - going over budget mid-response is expected (the cap is
     * checked before the call, not enforced mid-stream).
     */
    public void deductTokens(String sessionId, long tokensUsed) {
        if (tokensUsed <= 0) {
            return;
        }
        String key = KEY_PREFIX + sessionId;
        Long remaining = redisTemplate.opsForHash().increment(key, "tokensRemaining", -tokensUsed);
        if (remaining != null && remaining < 0) {
            redisTemplate.opsForHash().put(key, "tokensRemaining", "0");
        }
    }

    /**
     * Hard-ends a session immediately, no grace period. Idempotent - deleting
     * a key that's already gone is a no-op, not an error. Reserved for a
     * forced end (e.g. the admin kill switch in build-sequence step 7);
     * manual logout goes through {@link #pauseSession} instead.
     */
    public void endSession(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
        redisTemplate.delete(MESSAGES_KEY_PREFIX + sessionId);
    }

    /**
     * Manual-logout path: instead of deleting the session, mark it paused and
     * shrink its TTL down to whichever is shorter - the configured grace
     * period, or however much wall-clock time it already had left. No time is
     * refunded on resume; the clock was never stopped, just capped.
     */
    public void pauseSession(String sessionId, int graceMinutes) {
        String key = KEY_PREFIX + sessionId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return;
        }

        Long currentTtlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (currentTtlSeconds == null || currentTtlSeconds <= 0) {
            return;
        }

        long graceSeconds = graceMinutes * 60L;
        long newTtlSeconds = Math.min(currentTtlSeconds, graceSeconds);

        redisTemplate.opsForHash().put(key, STATUS_FIELD, STATUS_PAUSED);
        redisTemplate.expire(key, Duration.ofSeconds(newTtlSeconds));
        redisTemplate.expire(MESSAGES_KEY_PREFIX + sessionId, Duration.ofSeconds(newTtlSeconds));

        Object voucherId = entries.get("voucherId");
        if (voucherId != null) {
            redisTemplate.expire(voucherIndexKey((String) voucherId), Duration.ofSeconds(newTtlSeconds));
        }
    }

    /**
     * Looks up a still-paused, still-alive session for this voucher so login
     * can resume it. Returns empty if the voucher never had a paused session,
     * or its grace period has already lapsed (hard-deleted by its TTL) - the
     * caller falls back to rejecting the login as already-used.
     */
    public Optional<String> findResumableSessionId(String voucherId) {
        String sessionId = redisTemplate.opsForValue().get(voucherIndexKey(voucherId));
        if (sessionId == null) {
            return Optional.empty();
        }
        Object status = redisTemplate.opsForHash().get(KEY_PREFIX + sessionId, STATUS_FIELD);
        return STATUS_PAUSED.equals(status) ? Optional.of(sessionId) : Optional.empty();
    }

    public void resumeSession(String sessionId) {
        redisTemplate.opsForHash().put(KEY_PREFIX + sessionId, STATUS_FIELD, STATUS_ACTIVE);
    }

    public boolean isPaused(String sessionId) {
        Object status = redisTemplate.opsForHash().get(KEY_PREFIX + sessionId, STATUS_FIELD);
        return STATUS_PAUSED.equals(status);
    }

    /**
     * Voucher linking: folds a second, freshly-consumed voucher's budget into
     * an already-running session - grows both the cap and the remaining count
     * by the same amount (nothing's been spent from the new allocation yet),
     * and pushes the TTL out by the added minutes. No new session, no lost
     * history - same key, same messages list, just a bigger budget.
     */
    public void extendSession(String sessionId, int additionalTokenCap, int additionalMinutes) {
        String key = KEY_PREFIX + sessionId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return;
        }

        redisTemplate.opsForHash().increment(key, "tokenCap", additionalTokenCap);
        redisTemplate.opsForHash().increment(key, "tokensRemaining", additionalTokenCap);

        Long currentTtlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (currentTtlSeconds == null || currentTtlSeconds <= 0) {
            return;
        }

        long newTtlSeconds = currentTtlSeconds + additionalMinutes * 60L;
        redisTemplate.expire(key, Duration.ofSeconds(newTtlSeconds));
        redisTemplate.expire(MESSAGES_KEY_PREFIX + sessionId, Duration.ofSeconds(newTtlSeconds));

        Object voucherId = entries.get("voucherId");
        if (voucherId != null) {
            redisTemplate.expire(voucherIndexKey((String) voucherId), Duration.ofSeconds(newTtlSeconds));
        }
    }

    private String voucherIndexKey(String voucherId) {
        return VOUCHER_INDEX_PREFIX + voucherId;
    }

    /**
     * Admin dashboard read. Uses KEYS rather than SCAN - fine at this
     * project's target scale (10 concurrent sessions); revisit with SCAN if
     * that ever grows enough for KEYS's O(N) blocking to matter.
     */
    public List<SessionOverview> listActiveSessions() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<SessionOverview> overviews = new ArrayList<>();
        for (String key : keys) {
            if (key.startsWith(MESSAGES_KEY_PREFIX) || key.startsWith(VOUCHER_INDEX_PREFIX)) {
                continue;
            }
            String sessionId = key.substring(KEY_PREFIX.length());
            getStatus(sessionId).ifPresent(status -> getMeta(sessionId).ifPresent(meta ->
                    overviews.add(new SessionOverview(
                            sessionId,
                            meta.voucherId(),
                            status.tokenCap(),
                            status.tokensRemaining(),
                            status.timeRemainingSeconds(),
                            isPaused(sessionId)))));
        }
        return overviews;
    }

    /**
     * Conversation history lives in a separate list key so the token/time
     * counters stay small, fixed-shape hash reads. Kept in sync with the
     * session's own TTL on every append - it has no independent expiry.
     */
    public void appendMessage(String sessionId, String role, String content) {
        String messagesKey = MESSAGES_KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(messagesKey, serialize(role, content));

        Long ttlSeconds = redisTemplate.getExpire(KEY_PREFIX + sessionId, TimeUnit.SECONDS);
        if (ttlSeconds != null && ttlSeconds > 0) {
            redisTemplate.expire(messagesKey, Duration.ofSeconds(ttlSeconds));
        }
    }

    public List<ChatTurn> getMessages(String sessionId) {
        String messagesKey = MESSAGES_KEY_PREFIX + sessionId;
        List<String> raw = redisTemplate.opsForList().range(messagesKey, 0, -1);
        List<ChatTurn> turns = new ArrayList<>();
        if (raw == null) {
            return turns;
        }
        for (String entry : raw) {
            turns.add(deserialize(entry));
        }
        return turns;
    }

    private String serialize(String role, String content) {
        try {
            return objectMapper.writeValueAsString(new ChatTurn(role, content));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat turn", e);
        }
    }

    private ChatTurn deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChatTurn.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize chat turn", e);
        }
    }
}
