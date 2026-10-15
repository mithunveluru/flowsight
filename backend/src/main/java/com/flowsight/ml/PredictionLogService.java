package com.flowsight.ml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionLogService {

    // Long enough to compare a shadow model across a few billing cycles.
    public static final Duration RETENTION = Duration.ofDays(90);

    private final PredictionLogRepository repository;
    private final ModelVersionRepository  modelVersions;

    private final Set<String> seenVersions = ConcurrentHashMap.newKeySet();

    // Separate transaction so a logging failure never rolls back the caller's work.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PredictionLogEntry entry) {
        repository.save(entry);
        String version = entry.getModelVersion();
        if (version != null) {
            String key = entry.getCapability() + "@" + version;
            if (!seenVersions.contains(key)) {
                modelVersions.recordSeen(entry.getCapability(), version);
                seenVersions.add(key);
            }
        }
    }

    @Scheduled(fixedDelayString = "PT12H", initialDelayString = "PT10M")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteOlderThan(Instant.now().minus(RETENTION));
        if (deleted > 0) log.info("Purged {} prediction log rows older than {} days", deleted, RETENTION.toDays());
    }
}
