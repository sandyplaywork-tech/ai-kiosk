package com.aikiosk.backend.chat;

import com.aikiosk.backend.config.KioskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Keeps a durable Postgres mirror of each session's conversation (belt-and-
 * suspenders alongside Redis, which only holds it for the session's own
 * lifetime + grace period) and hard-deletes it once the configured retention
 * window has passed - never soft-deleted, and every sweep is logged so
 * deletion is verifiable rather than silently trusted.
 */
@Service
public class ChatArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ChatArchiveService.class);
    private static final long SWEEP_INTERVAL_MS = 15 * 60 * 1000L;

    private final ChatArchiveRepository repository;
    private final KioskProperties kioskProperties;

    public ChatArchiveService(ChatArchiveRepository repository, KioskProperties kioskProperties) {
        this.repository = repository;
        this.kioskProperties = kioskProperties;
    }

    @Transactional
    public void archive(String sessionId, UUID voucherId, Instant sessionStartedAt, List<ChatTurn> turns) {
        ChatArchive archive = repository.findBySessionId(sessionId).orElseGet(ChatArchive::new);
        archive.setSessionId(sessionId);
        archive.setVoucherId(voucherId);
        archive.setSessionStartedAt(sessionStartedAt);
        archive.setTranscript(ChatTranscriptFormatter.format(turns));
        archive.setArchivedAt(Instant.now());
        repository.save(archive);
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    @Transactional
    public void shredExpiredArchives() {
        Instant cutoff = Instant.now().minus(kioskProperties.getSession().getRetentionHours(), ChronoUnit.HOURS);
        List<ChatArchive> expired = repository.findByArchivedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return;
        }

        List<String> sessionIds = expired.stream().map(ChatArchive::getSessionId).toList();
        repository.deleteAllInBatch(expired);

        log.info("Hard-deleted {} chat archive(s) past the {}h retention window at {}: sessionIds={}",
                expired.size(), kioskProperties.getSession().getRetentionHours(), Instant.now(), sessionIds);
    }
}
