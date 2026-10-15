package com.flowsight.ml;

import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs V15 against Postgres and checks the entities, defaults, retention and cascades.
@SpringBootTest
@ActiveProfiles("test")
class MlStorageIntegrationTest {

    @Autowired private UserRepository         userRepository;
    @Autowired private PersonalizationService personalization;
    @Autowired private FeedbackService        feedbackService;
    @Autowired private PredictionLogService   predictionLog;
    @Autowired private ModelVersionRepository modelVersions;
    @Autowired private JdbcTemplate           jdbc;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user  = userRepository.save(newUser());
        other = userRepository.save(newUser());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllById(java.util.List.of(user.getId(), other.getId()));
    }

    @Test
    void personalizationDefaultsOnAndIsPerUser() {
        assertThat(personalization.isEnabled(user.getId())).isTrue();

        personalization.setEnabled(user.getId(), false);

        assertThat(personalization.isEnabled(user.getId())).isFalse();
        assertThat(personalization.isEnabled(other.getId())).isTrue();
        assertThat(personalization.isEnabled(UUID.randomUUID())).isFalse();
        assertThatThrownBy(() -> personalization.setEnabled(UUID.randomUUID(), true))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void feedbackIsStoredAndOnlyServedForPersonalizationWhenAllowed() {
        UUID txId = UUID.randomUUID();
        feedbackService.record(user.getId(), new FeedbackCorrection(
            SubjectType.TRANSACTION, txId, "category", "SHOPPING", "GROCERIES",
            FeedbackSource.TRANSACTION_EDIT, "cat-2026-10-01", 0.61237));
        feedbackService.record(other.getId(), new FeedbackCorrection(
            SubjectType.TRANSACTION, UUID.randomUUID(), "category", null, "TRAVEL",
            FeedbackSource.TRANSACTION_EDIT, null, null));

        var mine = feedbackService.forPersonalization(user.getId(), SubjectType.TRANSACTION, "category", 10);
        assertThat(mine).singleElement().satisfies(f -> {
            assertThat(f.getSubjectId()).isEqualTo(txId);
            assertThat(f.getCorrectedValue()).isEqualTo("GROCERIES");
            assertThat(f.getConfidence()).isEqualByComparingTo("0.6124");
            assertThat(f.getCreatedAt()).isNotNull();
        });

        personalization.setEnabled(user.getId(), false);
        assertThat(feedbackService.forPersonalization(user.getId(), SubjectType.TRANSACTION, "category", 10))
            .isEmpty();
        assertThat(count("user_feedback", user.getId())).isEqualTo(1);
    }

    @Test
    void predictionLogRecordsEntryAndCatalogsVersionOnce() {
        for (int i = 0; i < 2; i++) {
            predictionLog.record(PredictionLogEntry.builder()
                .userId(user.getId())
                .capability("categorizer")
                .mode(PredictionLogEntry.Mode.SERVE)
                .outcome(MlOutcome.OK)
                .modelVersion("it-" + user.getId())
                .subjectType(SubjectType.TRANSACTION)
                .subjectId(UUID.randomUUID())
                .confidence(Confidence.of(0.9))
                .latencyMs(42)
                .outputSummary(Map.of("category", "GROCERIES"))
                .build());
        }

        assertThat(count("prediction_log", user.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT output_summary->>'category' FROM prediction_log WHERE user_id = ? LIMIT 1",
            String.class, user.getId())).isEqualTo("GROCERIES");
        assertThat(modelVersions.findByCapabilityAndVersion("categorizer", "it-" + user.getId())).isPresent();
        jdbc.update("DELETE FROM model_versions WHERE version = ?", "it-" + user.getId());
    }

    @Test
    void retentionPurgesOnlyExpiredRows() {
        insertLogRow(user.getId(), Instant.now().minus(PredictionLogService.RETENTION).minusSeconds(3600));
        insertLogRow(user.getId(), Instant.now().minusSeconds(60));

        predictionLog.purgeExpired();

        assertThat(count("prediction_log", user.getId())).isEqualTo(1);
    }

    @Test
    void deletingTheUserRemovesTheirMlData() {
        feedbackService.record(user.getId(), new FeedbackCorrection(
            SubjectType.RECEIPT, UUID.randomUUID(), "merchant", "Wal Mart", "Walmart",
            FeedbackSource.RECEIPT_REVIEW, null, null));
        insertLogRow(user.getId(), Instant.now());
        insertLogRow(other.getId(), Instant.now());

        userRepository.deleteById(user.getId());

        assertThat(count("user_feedback", user.getId())).isZero();
        assertThat(count("prediction_log", user.getId())).isZero();
        assertThat(count("prediction_log", other.getId())).isEqualTo(1);
    }

    private void insertLogRow(UUID userId, Instant createdAt) {
        jdbc.update("""
            INSERT INTO prediction_log (user_id, capability, mode, outcome, latency_ms, created_at)
            VALUES (?, 'categorizer', 'SERVE', 'TIMEOUT', 400, ?)
            """, userId, Timestamp.from(createdAt));
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private static User newUser() {
        return User.builder()
            .email("ml-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
