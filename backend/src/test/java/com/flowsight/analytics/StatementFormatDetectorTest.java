package com.flowsight.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementFormatDetectorTest {

    private final StatementFormatDetector detector = new StatementFormatDetector();

    @Test
    void emptyHeaders_returnsUnknown() {
        var r = detector.detect(List.of());
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.UNKNOWN);
    }

    @Test
    void nativeAmountFormat_classifiedAsExplicit() {
        var r = detector.detect(List.of("date", "description", "amount", "type"));
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.EXPLICIT_AMOUNT);
        assertThat(r.getAmountColumn()).isEqualTo("amount");
        assertThat(r.getBalanceColumn()).isNull();
    }

    @Test
    void hdfcStyleHeaders_classifiedAsExplicit() {
        var r = detector.detect(List.of("Date", "Narration", "Debit Amount", "Credit Amount", "Closing Balance"));
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.EXPLICIT_AMOUNT);
        assertThat(r.getDebitColumn()).isEqualTo("Debit Amount");
        assertThat(r.getCreditColumn()).isEqualTo("Credit Amount");
    }

    @Test
    void balanceOnlyHeaders_classifiedAsBalanceOnly() {
        var r = detector.detect(List.of("Date", "Description", "Balance"));
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.BALANCE_ONLY);
        assertThat(r.getBalanceColumn()).isEqualTo("Balance");
        assertThat(r.getDateColumn()).isEqualTo("Date");
        assertThat(r.getDescriptionColumn()).isEqualTo("Description");
    }

    @Test
    void runningBalanceColumn_recognized() {
        var r = detector.detect(List.of("Date", "Narration", "Running Balance"));
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.BALANCE_ONLY);
        assertThat(r.getBalanceColumn()).isEqualTo("Running Balance");
    }

    @Test
    void missingDescription_classifiedAsUnknown() {
        var r = detector.detect(List.of("Date", "Balance"));
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.UNKNOWN);
    }

    @Test
    void headersAreCaseInsensitive() {
        var r = detector.detect(List.of("DATE", "DESCRIPTION", "BALANCE"));
        assertThat(r.getFormat()).isEqualTo(StatementFormatDetector.Format.BALANCE_ONLY);
    }
}
