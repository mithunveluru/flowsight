package com.flowsight.insights;

import com.flowsight.analytics.InsightsService;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.entity.Role;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs V20 against Postgres: accept/dismiss is user-scoped, recorded as feedback, and changes ranking.
@SpringBootTest
@ActiveProfiles("test")
class RecommendationFeedbackIntegrationTest {

    @Autowired private UserRepository                userRepository;
    @Autowired private TransactionRepository         transactions;
    @Autowired private InsightsService               insights;
    @Autowired private RecommendationFeedbackService feedback;
    @Autowired private PersonalizationService        personalization;
    @Autowired private JdbcTemplate                  jdbc;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user = userRepository.save(newUser());
        other = userRepository.save(newUser());
        for (int i = 1; i <= 3; i++) transactions.save(fee(user, LocalDate.now().minusDays(20L * i)));
        jdbc.update("DELETE FROM recommendation_kind_stats WHERE kind = 'LEAK_BANK_FEES'");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllById(List.of(user.getId(), other.getId()));
        jdbc.update("DELETE FROM recommendation_kind_stats WHERE kind = 'LEAK_BANK_FEES'");
    }

    @Test
    void acceptIsRecordedOnceAndRaisesTheAcceptanceRate() {
        Recommendation fees = feesRecommendation(user);
        assertThat(fees.getAcceptanceRate()).isEqualTo(0.5);

        feedback.record(user, UUID.fromString(fees.getId()), true);
        feedback.record(user, UUID.fromString(fees.getId()), true);

        assertThat(feedbackRows(user)).isEqualTo(1);
        assertThat(stat("accepted")).isEqualTo(1);
        Recommendation after = feesRecommendation(user);
        assertThat(after.getFeedback()).isEqualTo("ACCEPTED");
        assertThat(after.getAcceptanceRate()).isGreaterThan(0.5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = ?",
            Long.class, user.getId(), RecommendationFeedbackService.AUDIT_ACTION)).isEqualTo(1);
    }

    @Test
    void dismissHidesItAndMovesTheGlobalCount() {
        Recommendation fees = feesRecommendation(user);
        feedback.record(user, UUID.fromString(fees.getId()), true);

        feedback.record(user, UUID.fromString(fees.getId()), false);

        assertThat(insights.getInsights(user.getId()).getRecommendations())
            .noneMatch(r -> r.getId().equals(fees.getId()));
        assertThat(stat("accepted")).isZero();
        assertThat(stat("dismissed")).isEqualTo(1);
    }

    @Test
    void anotherUsersRecommendationIdIsNotFound() {
        Recommendation fees = feesRecommendation(user);
        for (int i = 1; i <= 3; i++) transactions.save(fee(other, LocalDate.now().minusDays(20L * i)));

        assertThatThrownBy(() -> feedback.record(other, UUID.fromString(fees.getId()), true))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(feedbackRows(other)).isZero();
    }

    @Test
    void optedOutFeedbackIsStoredButNotPooled() {
        personalization.setEnabled(user.getId(), false);
        Recommendation fees = feesRecommendation(user);

        feedback.record(user, UUID.fromString(fees.getId()), true);

        assertThat(feedbackRows(user)).isEqualTo(1);
        assertThat(stat("accepted")).isZero();
        assertThat(feesRecommendation(user).getAcceptanceRate()).isEqualTo(0.5);
    }

    private Recommendation feesRecommendation(User u) {
        return insights.getInsights(u.getId()).getRecommendations().stream()
            .filter(r -> "LEAK_BANK_FEES".equals(r.getKind())).findFirst().orElseThrow();
    }

    private long feedbackRows(User u) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_feedback WHERE user_id = ? AND subject_type = 'RECOMMENDATION'",
            Long.class, u.getId());
    }

    private long stat(String column) {
        List<Long> rows = jdbc.queryForList(
            "SELECT " + column + " FROM recommendation_kind_stats WHERE kind = 'LEAK_BANK_FEES'", Long.class);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private static Transaction fee(User u, LocalDate date) {
        return Transaction.builder()
            .user(u)
            .amount(new BigDecimal("600.00"))
            .transactionDate(date)
            .description("Late payment fee")
            .merchant("HDFC Bank")
            .category(TransactionCategory.FINANCE)
            .type(TransactionType.DEBIT)
            .source(TransactionSource.MANUAL)
            .build();
    }

    private static User newUser() {
        return User.builder()
            .email("insights-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
