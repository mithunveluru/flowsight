package com.flowsight.ml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private UserFeedbackRepository repository;
    @Mock private PersonalizationService personalization;
    @InjectMocks private FeedbackService service;

    @Test
    void boundsValuesToColumnSizesAndClampsConfidence() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID userId = UUID.randomUUID();

        UserFeedback saved = service.record(userId, new FeedbackCorrection(
            SubjectType.RECEIPT, UUID.randomUUID(), "  merchant ", "a".repeat(800), "b".repeat(501),
            FeedbackSource.RECEIPT_REVIEW, "v".repeat(70), 1.7));

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getField()).isEqualTo("merchant");
        assertThat(saved.getPredictedValue()).hasSize(500);
        assertThat(saved.getCorrectedValue()).hasSize(500);
        assertThat(saved.getModelVersion()).hasSize(64);
        assertThat(saved.getConfidence()).isEqualByComparingTo("1.0000");
    }

    @Test
    void rejectsIncompleteCorrections() {
        assertThatThrownBy(() -> service.record(UUID.randomUUID(), new FeedbackCorrection(
            SubjectType.RECEIPT, UUID.randomUUID(), " ", "a", "b", FeedbackSource.RECEIPT_REVIEW, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(null, new FeedbackCorrection(
            SubjectType.RECEIPT, UUID.randomUUID(), "amount", "a", "b", FeedbackSource.RECEIPT_REVIEW, null, null)))
            .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void optedOutUserGetsNoPersonalizationHistory() {
        UUID userId = UUID.randomUUID();
        when(personalization.isEnabled(userId)).thenReturn(false);

        assertThat(service.forPersonalization(userId, SubjectType.TRANSACTION, "category", 50)).isEmpty();
        verifyNoInteractions(repository);
    }
}
