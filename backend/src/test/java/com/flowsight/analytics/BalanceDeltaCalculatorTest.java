package com.flowsight.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the contract for balance-delta reconstruction in isolation - no Spring,
 * no CSV, just BalanceRow inputs and ReconstructedTransaction outputs.
 */
class BalanceDeltaCalculatorTest {

    private final BalanceDeltaCalculator calc = new BalanceDeltaCalculator();

    private static BalanceDeltaCalculator.BalanceRow row(int day, String desc, String balance, int rowNum) {
        return BalanceDeltaCalculator.BalanceRow.builder()
            .date(LocalDate.of(2026, 1, day))
            .description(desc)
            .balance(new BigDecimal(balance))
            .sourceRowNumber(rowNum)
            .rawText(desc)
            .build();
    }

    @Test
    void emptyInput_returnsEmptyResult() {
        BalanceDeltaCalculator.Result result = calc.reconstruct(List.of());
        assertThat(result.getTransactions()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void exampleFromSpec_reconstructsDebitAndCredit() {
        // From the task spec:
        //   Jan 1 | Opening Balance | 10000
        //   Jan 2 | Walmart         |  9500   -> -500 expense
        //   Jan 3 | Salary Credit   | 14500   -> +5000 income
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance", "10000", 2),
            row(2, "Walmart",          "9500", 3),
            row(3, "Salary Credit",   "14500", 4)
        ));

        assertThat(result.getTransactions()).hasSize(2);

        var t1 = result.getTransactions().get(0);
        assertThat(t1.getDescription()).isEqualTo("Walmart");
        assertThat(t1.getAmount()).isEqualByComparingTo("500");
        assertThat(t1.isDebit()).isTrue();

        var t2 = result.getTransactions().get(1);
        assertThat(t2.getDescription()).isEqualTo("Salary Credit");
        assertThat(t2.getAmount()).isEqualByComparingTo("5000");
        assertThat(t2.isDebit()).isFalse();
    }

    @Test
    void debitTransactions_allMarkedAsDebit() {
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance", "50000", 2),
            row(2, "Grocery",         "48500", 3),
            row(3, "Fuel",            "46500", 4),
            row(4, "Subscription",    "46000", 5)
        ));
        assertThat(result.getTransactions()).hasSize(3);
        assertThat(result.getTransactions()).allMatch(BalanceDeltaCalculator.ReconstructedTransaction::isDebit);
        assertThat(result.getTransactions().get(0).getAmount()).isEqualByComparingTo("1500");
        assertThat(result.getTransactions().get(1).getAmount()).isEqualByComparingTo("2000");
        assertThat(result.getTransactions().get(2).getAmount()).isEqualByComparingTo("500");
    }

    @Test
    void salaryCredits_allMarkedAsCredit() {
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance",  "10000", 2),
            row(2, "Salary",           "85000", 3),
            row(3, "Bonus",            "95000", 4)
        ));
        assertThat(result.getTransactions()).hasSize(2);
        assertThat(result.getTransactions()).allMatch(t -> !t.isDebit());
        assertThat(result.getTransactions().get(0).getAmount()).isEqualByComparingTo("75000");
        assertThat(result.getTransactions().get(1).getAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void mixedCreditsAndDebits_classifiedIndependently() {
        var result = calc.reconstruct(List.of(
            row(1,  "Opening Balance", "20000", 2),
            row(2,  "Salary",          "75000", 3),
            row(5,  "Rent",            "55000", 4),
            row(10, "Grocery",         "53500", 5),
            row(15, "Refund",          "54200", 6)
        ));
        assertThat(result.getTransactions()).hasSize(4);
        assertThat(result.getTransactions().get(0).isDebit()).isFalse(); // Salary
        assertThat(result.getTransactions().get(1).isDebit()).isTrue();  // Rent
        assertThat(result.getTransactions().get(2).isDebit()).isTrue();  // Grocery
        assertThat(result.getTransactions().get(3).isDebit()).isFalse(); // Refund
        assertThat(result.getTransactions().get(3).getAmount()).isEqualByComparingTo("700");
    }

    @Test
    void firstRowWithoutExplicitOpeningMarker_isUsedAsAnchorSilently() {
        // No "Opening Balance" row - the first observed balance just anchors.
        var result = calc.reconstruct(List.of(
            row(2, "Walmart", "9500",  2),
            row(3, "Salary",  "14500", 3)
        ));
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getTransactions().get(0).getDescription()).isEqualTo("Salary");
        assertThat(result.getTransactions().get(0).getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void closingBalanceRow_isSkipped_notEmittedAsTransaction() {
        var result = calc.reconstruct(List.of(
            row(1,  "Opening Balance",  "10000", 2),
            row(5,  "Coffee",            "9700", 3),
            row(31, "Closing Balance",   "9700", 4)
        ));
        // Only one real transaction; closing balance is suppressed.
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getTransactions().get(0).getDescription()).isEqualTo("Coffee");
    }

    @Test
    void rowsMissingBalance_areSkippedAndReported() {
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance", "10000", 2),
            BalanceDeltaCalculator.BalanceRow.builder()
                .date(LocalDate.of(2026, 1, 5))
                .description("Broken row")
                .balance(null)
                .sourceRowNumber(3)
                .build(),
            row(7, "Coffee", "9700", 4)
        ));
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getTransactions().get(0).getDescription()).isEqualTo("Coffee");
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Row 3"));
    }

    @Test
    void duplicateRows_areDeduplicated() {
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance", "10000", 2),
            row(2, "Walmart",          "9500", 3),
            row(2, "Walmart",          "9500", 4)  // exact duplicate
        ));
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("duplicate"));
    }

    @Test
    void zeroBalanceDelta_doesNotEmitTransaction() {
        // An info-only row with the same balance carries no money movement.
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance",   "10000", 2),
            row(2, "Account verified",  "10000", 3),
            row(3, "Coffee",             "9700", 4)
        ));
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getTransactions().get(0).getDescription()).isEqualTo("Coffee");
    }

    @Test
    void outOfOrderRows_areSortedByDate() {
        var result = calc.reconstruct(List.of(
            row(3, "Salary",          "14500", 4),
            row(1, "Opening Balance", "10000", 2),
            row(2, "Walmart",          "9500", 3)
        ));
        assertThat(result.getTransactions()).hasSize(2);
        assertThat(result.getTransactions().get(0).getDescription()).isEqualTo("Walmart");
        assertThat(result.getTransactions().get(1).getDescription()).isEqualTo("Salary");
    }

    @Test
    void suspiciousJump_getsLowerConfidenceAndWarning() {
        // Tiny transactions establish a low median; one row jumps ~10000x.
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance", "100000", 2),
            row(2, "Coffee",           "99950", 3),  // delta 50
            row(3, "Coffee",           "99900", 4),  // delta 50
            row(4, "Coffee",           "99850", 5),  // delta 50
            row(5, "Coffee",           "99800", 6),  // delta 50
            row(6, "Fraudulent wire",       "0", 7)  // delta 99800 > 100 * median 50
        ));
        var suspicious = result.getTransactions().get(result.getTransactions().size() - 1);
        assertThat(suspicious.isSuspicious()).isTrue();
        assertThat(suspicious.getConfidence()).isLessThan(new BigDecimal("1.0"));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("suspicious"));
    }

    @Test
    void normalTransactions_haveFullConfidence() {
        var result = calc.reconstruct(List.of(
            row(1, "Opening Balance", "10000", 2),
            row(2, "Walmart",          "9500", 3),
            row(3, "Salary",          "14500", 4)
        ));
        assertThat(result.getTransactions())
            .allMatch(t -> t.getConfidence().compareTo(new BigDecimal("1.0")) == 0);
        assertThat(result.getTransactions()).noneMatch(BalanceDeltaCalculator.ReconstructedTransaction::isSuspicious);
    }
}
