package com.flowsight.assistant;

import com.flowsight.entity.TransactionCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntentMatcherTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "How much did I spend this month?|SPENDING_SUMMARY|THIS_MONTH|",
        "What was my income last month?|SPENDING_SUMMARY|LAST_MONTH|",
        "How much did I spend on food in the last 30 days?|SPENDING_BY_CATEGORY|LAST_30_DAYS|FOOD_DINING",
        "Show my spending breakdown by category this year|SPENDING_BY_CATEGORY|THIS_YEAR|",
        "What did groceries cost me last month?|SPENDING_BY_CATEGORY|LAST_MONTH|GROCERIES",
        "Where did I spend the most?|TOP_MERCHANTS|THIS_MONTH|",
        "Which subscriptions am I paying for?|RECURRING|THIS_MONTH|",
        "Am I wasting money anywhere?|LEAKS|THIS_MONTH|",
        "Am I over budget?|BUDGET_STATUS|THIS_MONTH|",
        "How are my goals going?|GOAL_STATUS|THIS_MONTH|",
        "Cancel my Netflix subscription|WRITE_REQUEST|THIS_MONTH|",
        "Can you set a budget for dining?|WRITE_REQUEST|THIS_MONTH|",
        "What does everyone else spend on rent?|OTHER_USERS|THIS_MONTH|",
        "What's the weather in Mumbai?|UNSUPPORTED|THIS_MONTH|"
    })
    void matchesIntentPeriodAndCategory(String question, IntentMatcher.Type type, Period period, String category) {
        IntentMatcher.Intent intent = IntentMatcher.match(question);

        assertThat(intent.type()).isEqualTo(type);
        assertThat(intent.period()).isEqualTo(period);
        assertThat(intent.category()).isEqualTo(category == null ? null : TransactionCategory.valueOf(category));
    }
}
