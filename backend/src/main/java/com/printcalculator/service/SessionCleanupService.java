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

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(15);
        List<QuoteSession> oldSessions = sessionRepository.findByCreatedAtBefore(cutoff);

        int deletedCount = 0;
        for (QuoteSession session : oldSessions) {
            // We only delete sessions that are NOT ordered? 
            // The user request was "delete old ones". 
            // Safest is to check status if we had one. 
            // QuoteSession entity has 'status' field.
            // Let's assume we delete 'PENDING' or similar, but maybe we just delete all old inputs?
            // "rimangono in memoria... cancella quelle vecchie di 7 giorni".
            // Implementation plan said: status != 'ORDERED'.
            
            // User specified statuses: ACTIVE, EXPIRED, CONVERTED.
            // We should NOT delete sessions that have been converted to an order.
            if ("CONVERTED".equals(session.getStatus())) {
                continue;
            }

            try {
                // Delete ACTIVE or EXPIRED sessions older than 7 days
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
