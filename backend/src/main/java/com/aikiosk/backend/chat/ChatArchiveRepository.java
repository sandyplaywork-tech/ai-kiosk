package com.aikiosk.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatArchiveRepository extends JpaRepository<ChatArchive, UUID> {

    Optional<ChatArchive> findBySessionId(String sessionId);

    List<ChatArchive> findByArchivedAtBefore(@Param("cutoff") Instant cutoff);
}
