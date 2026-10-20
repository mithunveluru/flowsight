package com.flowsight.analytics;

import com.flowsight.dto.insights.ConsequenceProjection;
import com.flowsight.repository.RecurringPatternRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConsequenceCalculatorReturnTest {

    private final ConsequenceCalculator calc = new ConsequenceCalculator(mock(RecurringPatternRepository.class));

    @Test
    void theReturnIsAParameterAndTravelsWithTheProjection() {
        ConsequenceProjection byDefault = calc.project("Gym", new BigDecimal("1000"), null);
        ConsequenceProjection zero = calc.project("Gym", new BigDecimal("1000"), null, 0);
        assertThat(byDefault.getAssumedAnnualReturnPercent()).isEqualTo(8.0);
        assertThat(byDefault.getTenYearOpportunityCost()).isEqualByComparingTo("182946.04");
        assertThat(zero.getAssumedAnnualReturnPercent()).isEqualTo(0.0);
        assertThat(zero.getTenYearOpportunityCost()).isEqualByComparingTo("120000");
    }
}
