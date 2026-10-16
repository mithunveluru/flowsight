package com.flowsight.onboarding;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Test
    void coversAboutNinetyDaysAndNothingInTheFuture() {
        List<Transaction> rows = SampleDataGenerator.generate(null, TODAY);

        assertThat(rows).allSatisfy(t -> {
            assertThat(t.getTransactionDate()).isBetween(TODAY.minusMonths(3), TODAY);
            assertThat(t.getSource()).isEqualTo(TransactionSource.SAMPLE);
            assertThat(t.getAmount()).isPositive();
        });
        assertThat(rows).extracting(Transaction::getTransactionDate).contains(TODAY.minusMonths(3));
    }

    @Test
    void isDeterministicForTheSameDay() {
        List<String> a = SampleDataGenerator.generate(null, TODAY).stream().map(SampleDataGeneratorTest::key).toList();
        List<String> b = SampleDataGenerator.generate(null, TODAY).stream().map(SampleDataGeneratorTest::key).toList();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void containsTheStoriesTheProductExplains() {
        List<Transaction> rows = SampleDataGenerator.generate(null, TODAY);

        assertThat(rows).filteredOn(t -> t.getType() == TransactionType.CREDIT
            && t.getCategory() == TransactionCategory.INCOME).hasSizeGreaterThanOrEqualTo(3);
        assertThat(amounts(rows, "Netflix")).containsExactly(
            new BigDecimal("499.00"), new BigDecimal("499.00"), new BigDecimal("649.00"), new BigDecimal("649.00"));
        assertThat(rows).filteredOn(t -> t.getCategory() == TransactionCategory.SUBSCRIPTIONS)
            .extracting(Transaction::getMerchant).contains("Spotify", "Apple Music");
        assertThat(rows).filteredOn(t -> "Third Wave Coffee".equals(t.getMerchant()))
            .hasSizeGreaterThanOrEqualTo(20)
            .allSatisfy(t -> assertThat(t.getAmount()).isLessThanOrEqualTo(new BigDecimal("500")));
        assertThat(rows).filteredOn(t -> t.getCategory() == TransactionCategory.UNCATEGORIZED).isNotEmpty();
    }

    private static List<BigDecimal> amounts(List<Transaction> rows, String merchant) {
        return rows.stream()
            .filter(t -> merchant.equals(t.getMerchant()))
            .sorted((x, y) -> x.getTransactionDate().compareTo(y.getTransactionDate()))
            .map(Transaction::getAmount)
            .toList();
    }

    private static String key(Transaction t) {
        return t.getTransactionDate() + "|" + Objects.toString(t.getMerchant()) + "|" + t.getAmount();
    }
}
