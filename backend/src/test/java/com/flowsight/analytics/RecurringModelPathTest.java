package com.flowsight.analytics;

import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.entity.*;
import com.flowsight.ml.FeedbackCorrection;
import com.flowsight.ml.FeedbackService;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.recurring.RecurringScorer;
import com.flowsight.ml.recurring.RecurringScorerRequest;
import com.flowsight.ml.recurring.RecurringScorerResponse;
import com.flowsight.repository.RecurringPatternRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringModelPathTest {

    private final TransactionRepository      transactions = mock(TransactionRepository.class);
    private final RecurringPatternRepository patterns     = mock(RecurringPatternRepository.class);
    private final UserRepository             users        = mock(UserRepository.class);
    private final RecurringScorer            scorer       = mock(RecurringScorer.class);
    private final FeedbackService            feedback     = mock(FeedbackService.class);
    private final List<RecurringPattern>     saved        = new ArrayList<>();
    private final LocalDate                  today        = LocalDate.now();

    private final User user = User.builder().id(UUID.randomUUID()).email("m@example.com")
        .passwordHash("x").role(Role.USER).build();

    private RecurringDetectionService service;

    @BeforeEach
    void setUp() {
        service = new RecurringDetectionService(transactions, patterns, users, new MerchantNormalizationService(),
            scorer, feedback);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(patterns.save(any())).thenAnswer(inv -> {
            RecurringPattern p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            saved.add(p);
            return p;
        });
        List<Transaction> txns = new ArrayList<>();
        for (int m = 4; m >= 0; m--) txns.add(debit("Netflix", "649", today.minusMonths(m), TransactionCategory.ENTERTAINMENT));
        int[] daysAgo = {40, 33, 26, 19, 12, 5};
        for (int d : daysAgo) txns.add(debit("Zomato", String.valueOf(400 + d * 7), today.minusDays(d), TransactionCategory.FOOD_DINING));
        when(transactions.findForRecurringDetection(eq(user.getId()), any())).thenReturn(txns);
    }

    private static Transaction debit(String merchant, String amount, LocalDate date, TransactionCategory category) {
        return Transaction.builder().merchant(merchant).amount(new BigDecimal(amount)).type(TransactionType.DEBIT)
            .category(category).transactionDate(date).build();
    }

    @Test
    void theScorersAnswerIsStoredWithItsProbabilityAndInterval() {
        when(scorer.active()).thenReturn(true);
        ArgumentCaptor<RecurringScorerRequest> request = ArgumentCaptor.forClass(RecurringScorerRequest.class);
        when(scorer.score(eq(user.getId()), request.capture())).thenReturn(Optional.of(new RecurringScorer.Scores(List.of(
            new RecurringScorerResponse.Score(0.97, true, "MONTHLY", 30.5, 30, 28, 33, 649, 1),
            new RecurringScorerResponse.Score(0.03, false, "WEEKLY", 7, 2, 1, 3, 600, 1)), "logreg-1")));

        List<RecurringPatternResponse> result = service.detectAndRefresh(user.getId());

        RecurringScorerRequest sent = request.getValue();
        assertThat(sent.asOf()).isEqualTo(today.toString());
        assertThat(sent.groups()).hasSize(2);
        assertThat(sent.groups().get(0).days()).hasSize(5).allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(0));
        assertThat(sent.groups().get(0).category()).isEqualTo("ENTERTAINMENT");
        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.getMerchant()).isEqualTo("Netflix");
            assertThat(r.getScoredBy()).isEqualTo("MODEL");
            assertThat(r.getProbability()).isEqualByComparingTo("0.97");
            assertThat(r.getModelVersion()).isEqualTo("logreg-1");
            assertThat(r.getNextExpectedDate()).isEqualTo(today.plusDays(30));
            assertThat(r.getNextExpectedLow()).isEqualTo(today.plusDays(28));
            assertThat(r.getNextExpectedHigh()).isEqualTo(today.plusDays(33));
            assertThat(r.getConfidenceTier()).isEqualTo("HIGH");
        });
    }

    @Test
    void parallelChargesAreCountedInTheAmount() {
        when(scorer.active()).thenReturn(true);
        when(scorer.score(any(), any())).thenReturn(Optional.of(new RecurringScorer.Scores(List.of(
            new RecurringScorerResponse.Score(0.95, true, "MONTHLY", 30, 10, 8, 12, 649, 2),
            new RecurringScorerResponse.Score(0.02, false, null, 7, null, null, null, 600, 1)), "logreg-1")));

        RecurringPatternResponse r = service.detectAndRefresh(user.getId()).get(0);
        assertThat(r.getChargesPerPeriod()).isEqualTo(2);
        assertThat(r.getEstimatedAmount()).isEqualByComparingTo("1298");
        assertThat(r.getMonthlyEquivalent()).isEqualByComparingTo("1298");
    }

    @Test
    void theRulesRunWhenTheScorerDoesNotAnswer() {
        when(scorer.active()).thenReturn(true);
        when(scorer.score(any(), any())).thenReturn(Optional.empty());

        List<RecurringPatternResponse> result = service.detectAndRefresh(user.getId());

        assertThat(result).isNotEmpty().allSatisfy(r -> {
            assertThat(r.getScoredBy()).isEqualTo("RULES");
            assertThat(r.getProbability()).isNull();
            assertThat(r.getNextExpectedHigh()).isNull();
        });
    }

    @Test
    void confirmAndDismissAreRecordedAsFeedback() {
        RecurringPattern p = RecurringPattern.builder().id(UUID.randomUUID()).user(user).merchant("Netflix")
            .normalizedKey("netflix").period(RecurringPeriod.MONTHLY).probability(new BigDecimal("0.8100"))
            .modelVersion("logreg-1").build();
        when(patterns.findByIdAndUserId(p.getId(), user.getId())).thenReturn(Optional.of(p));

        service.confirm(p.getId(), user.getId());
        service.dismiss(p.getId(), user.getId());

        ArgumentCaptor<FeedbackCorrection> c = ArgumentCaptor.forClass(FeedbackCorrection.class);
        verify(feedback, org.mockito.Mockito.times(2)).record(eq(user.getId()), c.capture());
        assertThat(c.getAllValues()).extracting(FeedbackCorrection::correctedValue).containsExactly("RECURRING", "NOT_RECURRING");
        assertThat(c.getAllValues()).allSatisfy(f -> {
            assertThat(f.subjectType()).isEqualTo(SubjectType.RECURRING_PATTERN);
            assertThat(f.subjectId()).isEqualTo(p.getId());
            assertThat(f.source()).isEqualTo(FeedbackSource.RECURRING_REVIEW);
            assertThat(f.modelVersion()).isEqualTo("logreg-1");
            assertThat(f.confidence()).isEqualTo(0.81);
        });
    }

    @Test
    void aScoredPatternIsNotOverdueUntilItsIntervalHasPassed() {
        RecurringPattern p = RecurringPattern.builder().id(UUID.randomUUID()).user(user).merchant("Netflix")
            .normalizedKey("netflix").period(RecurringPeriod.MONTHLY).estimatedAmount(new BigDecimal("649"))
            .nextExpectedDate(today.minusDays(2)).nextExpectedLow(today.minusDays(4)).nextExpectedHigh(today.plusDays(1))
            .modelVersion("logreg-1").build();
        RecurringPattern late = RecurringPattern.builder().id(UUID.randomUUID()).user(user).merchant("Spotify")
            .normalizedKey("spotify").period(RecurringPeriod.MONTHLY).estimatedAmount(new BigDecimal("119"))
            .nextExpectedDate(today.minusDays(5)).nextExpectedHigh(today.minusDays(3)).modelVersion("logreg-1").build();
        when(patterns.findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(user.getId())).thenReturn(List.of(p, late));

        assertThat(service.getStored(user.getId())).extracting(RecurringPatternResponse::getStatus)
            .containsExactly("DUE_SOON", "OVERDUE");
    }
}
