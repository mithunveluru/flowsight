package com.flowsight.ml.recurring;

import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.MlProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringScorerTest {

    private final MlClient ml = mock(MlClient.class);
    private final RecurringScorer scorer = new RecurringScorer(ml, mock(MlProperties.class));
    private final RecurringScorerRequest request = RecurringScorerRequest.of(LocalDate.of(2026, 6, 20), List.of(
        new RecurringScorerRequest.Group(List.of(-31, 0), List.of(499.0, 499.0), "ENTERTAINMENT")));

    private static RecurringScorerResponse.Score score() {
        return new RecurringScorerResponse.Score(0.97, true, "MONTHLY", 31, 30, 28, 33, 499, 1);
    }

    @Test
    void nothingIsSentWhileTheCapabilityIsOff() {
        assertThat(scorer.score(UUID.randomUUID(), request)).isEmpty();
        verify(ml, never()).predict(any(), any(), any(), any(), any());
    }

    @Test
    void aModelAnswerIsReturnedWithItsVersion() {
        when(ml.isActive(MlCapability.RECURRING_SCORER)).thenReturn(true);
        when(ml.predict(eq(MlCapability.RECURRING_SCORER), any(), eq(RecurringScorerResponse.class), any(), any()))
            .thenReturn(new MlPrediction<>(new RecurringScorerResponse(List.of(score())), MlPrediction.Source.MODEL,
                "logreg-1", 0.97));

        RecurringScorer.Scores scores = scorer.score(UUID.randomUUID(), request).orElseThrow();
        assertThat(scores.modelVersion()).isEqualTo("logreg-1");
        assertThat(scores.scores()).containsExactly(score());
    }

    @Test
    void rulesAreServedOnFallbackShadowOrAMisalignedAnswer() {
        when(ml.isActive(MlCapability.RECURRING_SCORER)).thenReturn(true);
        // MlClient's fallback (timeout, error, circuit open, shadow mode) hands back the supplier's null
        when(ml.predict(any(), any(), any(), any(), any()))
            .thenReturn(new MlPrediction<>(null, MlPrediction.Source.RULES, null, null));
        assertThat(scorer.score(UUID.randomUUID(), request)).isEmpty();

        when(ml.predict(any(), any(), any(), any(), any()))
            .thenReturn(new MlPrediction<>(new RecurringScorerResponse(List.of(score(), score())),
                MlPrediction.Source.MODEL, "logreg-1", 0.9));
        assertThat(scorer.score(UUID.randomUUID(), request)).isEmpty();
    }

    @Test
    void largeRequestsAreSentInChunks() {
        when(ml.isActive(MlCapability.RECURRING_SCORER)).thenReturn(true);
        List<RecurringScorerRequest.Group> groups = java.util.Collections.nCopies(RecurringScorerRequest.MAX_GROUPS + 1,
            request.groups().get(0));
        when(ml.predict(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            RecurringScorerRequest chunk = inv.getArgument(1);
            return new MlPrediction<>(new RecurringScorerResponse(java.util.Collections.nCopies(chunk.groups().size(), score())),
                MlPrediction.Source.MODEL, "logreg-1", 0.9);
        });
        assertThat(scorer.score(UUID.randomUUID(), new RecurringScorerRequest(request.asOf(), groups)).orElseThrow()
            .scores()).hasSize(RecurringScorerRequest.MAX_GROUPS + 1);
    }
}
