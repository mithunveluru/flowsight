package com.flowsight.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingValidatorTest {

    private final FactSet facts = new FactSet()
        .amount("total_spend", new BigDecimal("123456.40"))
        .amount("dining", new BigDecimal("1234.40"))
        .amount("fees", new BigDecimal("400"))
        .percent("dining_share", 22.4)
        .count("transactions", 37)
        .date("from", LocalDate.of(2026, 9, 1))
        .date("to", LocalDate.of(2026, 9, 30))
        .text("merchant", "7-Eleven Store 42");

    @ParameterizedTest
    @ValueSource(strings = {
        "You spent ₹1,234 on dining.",
        "You spent ₹1234 on dining.",
        "You spent ₹1234.40 on dining.",
        "You spent Rs.1,234 on dining.",
        "You spent ₹1.2k on dining.",
        "You spent 1,234 rupees on dining.",
        "Total spend was ₹1,23,456.",
        "Total spend was ₹123,456.",
        "Total spend was ₹1.23 lakh.",
        "Total spend was ₹1.2L.",
        "Total spend was about ₹123,000.",
        "Dining was 22% of spending.",
        "Dining was 22.4% of spending.",
        "Dining was 22 percent of spending.",
        "You made 37 transactions.",
        "From 2026-09-01 to 2026-09-30 you spent ₹400 on fees.",
        "From 1 Sep 2026 to 30 Sep 2026 you spent ₹400 on fees.",
        "Between September 1 and September 30, 2026 fees were ₹400.",
        "In September 2026 fees were ₹400.",
        "Your top merchant was 7-Eleven Store 42.",
        "Spending looks steady with nothing unusual."
    })
    void groundedTextPasses(String text) {
        GroundingValidator.Result r = GroundingValidator.check(text, facts);
        assertThat(r.grounded()).as(r.ungrounded().toString()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "You spent ₹1,250 on dining.",
        "You spent ₹1.3k on dining.",
        "You could save ₹14,813 a year on dining.",
        "Dining was 25% of spending.",
        "Fees were 400% higher.",
        "You spent ₹37 on dining.",
        "You made 38 transactions.",
        "On 15 Sep 2026 you paid fees.",
        "In October 2026 fees were ₹400.",
        "Fees were five thousand rupees.",
        "You spent twelve times more on dining.",
        "Total spend was ₹2 lakh.",
        "Total spend was about ₹100,000."
    })
    void inventedNumbersAreRejected(String text) {
        assertThat(GroundingValidator.check(text, facts).grounded()).isFalse();
    }

    @Test
    void reportsTheOffendingValues() {
        GroundingValidator.Result r = GroundingValidator.check("₹1,234 on dining and ₹999 on fees, 3 times.", facts);

        assertThat(r.grounded()).isFalse();
        assertThat(r.ungrounded()).containsExactly("999", "3");
    }

    @Test
    void negativeFactsMatchTheirMagnitude() {
        FactSet net = new FactSet().amount("net_cashflow", new BigDecimal("-12033"));

        assertThat(GroundingValidator.check("Outflows exceeded income by ₹12,033.", net).grounded()).isTrue();
    }

    @Test
    void percentNeedsAPercentFactAndCurrencyNeedsAnAmount() {
        FactSet onlyCount = new FactSet().count("subscriptions", 22);

        assertThat(GroundingValidator.check("You have 22 subscriptions.", onlyCount).grounded()).isTrue();
        assertThat(GroundingValidator.check("They cost 22% of spend.", onlyCount).grounded()).isFalse();
        assertThat(GroundingValidator.check("They cost ₹22.", onlyCount).grounded()).isFalse();
    }

    @Test
    void yearOfAFactDateIsAllowed() {
        assertThat(GroundingValidator.check("Your 2026 spending so far.", facts).grounded()).isTrue();
        assertThat(GroundingValidator.check("Your 2025 spending so far.", facts).grounded()).isFalse();
    }

    @Test
    void arabicIndicDigitsAreChecked() {
        assertThat(GroundingValidator.check("Fees were ₹٤٠٠.", facts).grounded()).isTrue();
        assertThat(GroundingValidator.check("Fees were ₹٥٠٠.", facts).grounded()).isFalse();
    }
}
