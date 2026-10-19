package com.flowsight.analytics;

import com.flowsight.entity.TransactionCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CategorizationServiceTest {

    private final CategorizationService service = new CategorizationService();

    @Test
    void equalScoresGoToTheLongerMatchedKeyword() {
        // "pizza" (FOOD_DINING) and "lic" inside "slice" (FINANCE) both score 0.57
        CategorizationService.CategorizationResult result = service.categorize("Pizza Slice Co", null, null);

        assertThat(result.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(result.getConfidence()).isCloseTo(0.57, within(1e-9));
    }

    @Test
    void equalScoresAndKeywordLengthsGoToTheEarlierDeclaredCategory() {
        // "cab" (TRANSPORTATION) and "emi" (FINANCE): same score, same keyword length
        assertThat(service.categorize("cab emi", null, null).getCategory())
            .isEqualTo(TransactionCategory.TRANSPORTATION);
        assertThat(service.categorize("emi cab", null, null).getCategory())
            .isEqualTo(TransactionCategory.TRANSPORTATION);
    }

    @Test
    void sameInputGivesTheSameAnswerAcrossInstances() {
        String[] inputs = {"Pizza Slice Co", "cab emi", "AIRTEL POSTPAID", "ZOMATO ORDER", "random text"};
        for (String input : inputs) {
            CategorizationService.CategorizationResult first = service.categorize(input, "m", null);
            for (int i = 0; i < 5; i++) {
                assertThat(new CategorizationService().categorize(input, "m", null)).isEqualTo(first);
            }
        }
    }

    @Test
    void noKeywordIsUncategorized() {
        CategorizationService.CategorizationResult result = service.categorize("qwxz", "plmk", null);

        assertThat(result.getCategory()).isEqualTo(TransactionCategory.UNCATEGORIZED);
        assertThat(result.getConfidence()).isEqualTo(0.25);
    }
}
