package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewedHistoryTest {

    private final UUID user = UUID.randomUUID();
    private final ReviewedHistory history = ReviewedHistory.inMemory(Map.of(user, List.of(
        new ReviewedHistory.Reviewed("Al Noor Pharmacy", "POS PURCHASE AL NOOR PHARMACY DUBAI AE", TransactionCategory.HEALTHCARE),
        new ReviewedHistory.Reviewed(null, "ATM CASH WITHDRAWAL DUBAI", TransactionCategory.OTHER))));

    @Test
    void sharedStatementBoilerplateDoesNotMakeANeighbour() {
        List<Neighbour> found = history.nearest(user, List.of(
            CategorizerRequest.Item.of("POS PURCHASE GOLDEN GRILL DUBAI AE", "Golden Grill", null))).get(0);

        assertThat(found).allMatch(n -> n.similarity() < PersonalBlend.MIN_SIMILARITY);
    }

    @Test
    void theSameMerchantIsAStrongNeighbour() {
        List<Neighbour> found = history.nearest(user, List.of(
            CategorizerRequest.Item.of("APPLE PAY AL NOOR PHARMACY SHARJAH", "Al Noor Pharmacy", null))).get(0);

        assertThat(found.get(0)).isEqualTo(new Neighbour(TransactionCategory.HEALTHCARE, 1.0));
    }

    @Test
    void descriptionsCountWhenAMerchantIsMissing() {
        List<Neighbour> found = history.nearest(user, List.of(
            CategorizerRequest.Item.of("ATM CASH WITHDRAWAL DUBAI", null, null))).get(0);

        assertThat(found.get(0)).isEqualTo(new Neighbour(TransactionCategory.OTHER, 1.0));
    }

    @Test
    void otherUsersSeeNothing() {
        assertThat(history.nearest(UUID.randomUUID(), List.of(
            CategorizerRequest.Item.of("ATM CASH WITHDRAWAL DUBAI", "Al Noor Pharmacy", null))).get(0)).isEmpty();
    }
}
