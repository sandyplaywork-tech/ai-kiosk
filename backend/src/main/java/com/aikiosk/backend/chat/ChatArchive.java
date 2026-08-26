package com.aikiosk.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable Postgres mirror of a session's Redis-backed conversation, upserted
 * on every exchange so it survives regardless of what happens to the Redis
 * key. Retained for kiosk.session.retention-hours, then hard-deleted by
 * {@link ChatArchiveService}'s scheduled sweep - never soft-deleted.
 */
@Entity
@Table(name = "chat_archives")
public class ChatArchive {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "voucher_id", nullable = false)
    private UUID voucherId;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "session_started_at", nullable = false)
    private Instant sessionStartedAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    protected ChatArchive() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(UUID voucherId) {
        this.voucherId = voucherId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public Instant getSessionStartedAt() {
        return sessionStartedAt;
    }

    public void setSessionStartedAt(Instant sessionStartedAt) {
        this.sessionStartedAt = sessionStartedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
