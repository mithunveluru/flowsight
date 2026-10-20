package com.flowsight.insights;

import com.flowsight.dto.insights.Recommendation;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.UserFeedback;
import com.flowsight.ml.UserFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationRankerTest {

    private final UUID user = UUID.randomUUID();
    private final UserFeedbackRepository feedback = mock(UserFeedbackRepository.class);
    private final PersonalizationService personalization = mock(PersonalizationService.class);
    private final RecommendationStatsRepository stats = mock(RecommendationStatsRepository.class);
    private final RecommendationRanker ranker = new RecommendationRanker(feedback, personalization, stats);
    private final List<UserFeedback> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(personalization.isEnabled(user)).thenReturn(true);
        when(stats.all()).thenReturn(Map.of());
        when(feedback.findByUserIdAndSubjectTypeAndFieldOrderByCreatedAtDesc(
            eq(user), eq(SubjectType.RECOMMENDATION), eq("action"), any())).thenReturn(history);
    }

    @Test
    void withoutFeedbackLargerSavingsRankFirst() {
        List<Recommendation> ranked = ranker.rank(user, List.of(
            rec("a", "LEAK_BANK_FEES", "400"), rec("b", "LEAK_SUBSCRIPTION_CREEP", "1500"), rec("c", "BEHAVIOR_X", null)), 5);

        assertThat(ranked).extracting(Recommendation::getId).containsExactly(uid("b"), uid("a"), uid("c"));
        assertThat(ranked.get(0).getAcceptanceRate()).isEqualTo(0.5);
        assertThat(ranked.get(0).getRankingReason()).isEqualTo("Ranked by estimated saving");
    }

    @Test
    void acceptingAKindLiftsItAboveALargerSaving() {
        // the user accepted three earlier fee recommendations and dismissed two creep ones
        history.add(answer("old-1", "LEAK_BANK_FEES", "ACCEPTED", 40));
        history.add(answer("old-2", "LEAK_BANK_FEES", "ACCEPTED", 50));
        history.add(answer("old-3", "LEAK_BANK_FEES", "ACCEPTED", 60));
        history.add(answer("old-4", "LEAK_SUBSCRIPTION_CREEP", "DISMISSED", 40));
        history.add(answer("old-5", "LEAK_SUBSCRIPTION_CREEP", "DISMISSED", 50));

        List<Recommendation> ranked = ranker.rank(user, List.of(
            rec("creep", "LEAK_SUBSCRIPTION_CREEP", "1500"), rec("fees", "LEAK_BANK_FEES", "400")), 5);

        assertThat(ranked).extracting(Recommendation::getId).containsExactly(uid("fees"), uid("creep"));
        assertThat(ranked.get(0).getAcceptanceRate()).isEqualTo(0.8);       // (1 + 3) / (2 + 3)
        assertThat(ranked.get(1).getAcceptanceRate()).isEqualTo(0.25);      // 1 / (2 + 2)
        assertThat(ranked.get(0).getRankingReason()).isEqualTo("You acted on 3 of 3 similar suggestions");
    }

    @Test
    void onlyTheLatestAnswerPerRecommendationCounts() {
        history.add(answer("same", "LEAK_BANK_FEES", "ACCEPTED", 1));   // newest first
        history.add(answer("same", "LEAK_BANK_FEES", "DISMISSED", 2));

        assertThat(ranker.acceptanceRate(user, "LEAK_BANK_FEES")).isEqualTo(2.0 / 3);
    }

    @Test
    void recentlyDismissedIsHiddenAndAcceptedIsMarked() {
        history.add(answer("gone", "LEAK_BANK_FEES", "DISMISSED", 3));
        history.add(answer("kept", "LEAK_DUPLICATE_SUBSCRIPTIONS", "ACCEPTED", 3));
        history.add(answer("back", "LEAK_HIGH_FREQUENCY_SMALL_SPEND", "DISMISSED", 45));

        List<Recommendation> ranked = ranker.rank(user, List.of(
            rec("gone", "LEAK_BANK_FEES", "400"), rec("kept", "LEAK_DUPLICATE_SUBSCRIPTIONS", "300"),
            rec("back", "LEAK_HIGH_FREQUENCY_SMALL_SPEND", "200")), 5);

        assertThat(ranked).extracting(Recommendation::getId).containsExactlyInAnyOrder(uid("kept"), uid("back"));
        assertThat(ranked).filteredOn(r -> r.getId().equals(uid("kept"))).first()
            .extracting(Recommendation::getFeedback).isEqualTo("ACCEPTED");
        assertThat(ranked).filteredOn(r -> r.getId().equals(uid("back"))).first()
            .extracting(Recommendation::getFeedback).isNull();
    }

    @Test
    void globalCountsArePriorAndOptedOutUsersUseOnlyThem() {
        when(stats.all()).thenReturn(Map.of("LEAK_BANK_FEES", new RecommendationStatsRepository.Counts(90, 10)));
        history.add(answer("x", "LEAK_BANK_FEES", "DISMISSED", 40));
        history.add(answer("y", "LEAK_BANK_FEES", "DISMISSED", 41));

        double personal = ranker.acceptanceRate(user, "LEAK_BANK_FEES");
        when(personalization.isEnabled(user)).thenReturn(false);
        double global = ranker.acceptanceRate(user, "LEAK_BANK_FEES");

        // prior: rate 91/102 at strength 20 -> alpha 18.84, beta 3.16
        assertThat(global).isCloseTo(18.843 / 22.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(personal).isCloseTo(18.843 / 24.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(personal).isLessThan(global);
    }

    @Test
    void limitIsApplied() {
        List<Recommendation> candidates = new ArrayList<>();
        for (int i = 0; i < 8; i++) candidates.add(rec("r" + i, "K" + i, String.valueOf(200 + i)));

        assertThat(ranker.rank(user, candidates, 5)).hasSize(5);
    }

    private UserFeedback answer(String id, String kind, String action, int daysAgo) {
        return UserFeedback.builder()
            .userId(user)
            .subjectType(SubjectType.RECOMMENDATION)
            .subjectId(UUID.fromString(uid(id)))
            .field("action")
            .predictedValue(kind)
            .correctedValue(action)
            .source(FeedbackSource.RECOMMENDATION_FEEDBACK)
            .createdAt(Instant.now().minus(Duration.ofDays(daysAgo)))
            .build();
    }

    private static String uid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes()).toString();
    }

    private static Recommendation rec(String id, String kind, String saving) {
        return Recommendation.builder()
            .id(uid(id))
            .kind(kind)
            .potentialMonthlySaving(saving == null ? null : new BigDecimal(saving))
            .build();
    }
}
