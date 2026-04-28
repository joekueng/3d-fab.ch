package com.printcalculator.service;

import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SessionCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(SessionCleanupService.class);
    private final QuoteSessionRepository sessionRepository;

    public SessionCleanupService(QuoteSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // Run every day at 3 AM
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldSessions() {
        logger.info("Starting session cleanup job...");

        OffsetDateTime cutoff = OffsetDateTime.now();
        List<QuoteSession> oldSessions = sessionRepository.findByExpiresAtBefore(cutoff);

        int deletedCount = 0;
        for (QuoteSession session : oldSessions) {
            // CAD_ACTIVE sessions are managed manually from back-office and must be preserved.
            if ("CONVERTED".equals(session.getStatus()) || "CAD_ACTIVE".equals(session.getStatus())) {
                continue;
            }

            try {
                // Delete sessions only after their configured expiry has passed.
                deleteSessionFiles(session.getId().toString());
                sessionRepository.delete(session);
                deletedCount++;
            } catch (Exception e) {
                logger.error("Failed to cleanup session {}", session.getId(), e);
            }
        }

        logger.info("Session cleanup job finished. Deleted {} sessions.", deletedCount);
    }

    private void deleteSessionFiles(String sessionId) {
        Path sessionDir = Paths.get("storage_quotes", sessionId);
        if (Files.exists(sessionDir)) {
            try (Stream<Path> walk = Files.walk(sessionDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
            } catch (IOException e) {
                logger.error("Failed to delete directory: {}", sessionDir, e);
            }
        }
    }
}
