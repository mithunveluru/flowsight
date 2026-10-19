package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptDatesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private static ReceiptDates.Guess find(String currency, String... lines) {
        return ReceiptDates.find(List.of(lines), currency, CLOCK);
    }

    @Test
    void usReceiptReadsMonthFirst() {
        ReceiptDates.Guess g = find("USD", "03/04/2025 14:22 Reg 3");
        assertThat(g.date()).isEqualTo(LocalDate.of(2025, 3, 4));
        assertThat(g.ambiguous()).isTrue();
    }

    @Test
    void otherCurrenciesReadDayFirst() {
        assertThat(find("INR", "Date: 03/04/2025").date()).isEqualTo(LocalDate.of(2025, 4, 3));
        assertThat(find(null, "03/04/2025").date()).isEqualTo(LocalDate.of(2025, 4, 3));
    }

    @Test
    void aDayAbove12DecidesTheOrder() {
        ReceiptDates.Guess us = find("INR", "04/29/2024");
        assertThat(us.date()).isEqualTo(LocalDate.of(2024, 4, 29));
        assertThat(us.ambiguous()).isFalse();
        assertThat(find("USD", "28/06/2025").date()).isEqualTo(LocalDate.of(2025, 6, 28));
    }

    @Test
    void textualIsoTwoDigitYearAndArabicIndic() {
        assertThat(find("INR", "Bill No: 46294 Date: 18 Nov 2024").date()).isEqualTo(LocalDate.of(2024, 11, 18));
        assertThat(find("USD", "Nov 18, 2024").date()).isEqualTo(LocalDate.of(2024, 11, 18));
        assertThat(find("SAR", "Date: 2025-08-16 Inv: 5512").date()).isEqualTo(LocalDate.of(2025, 8, 16));
        assertThat(find("USD", "11/10/24 08:54").date()).isEqualTo(LocalDate.of(2024, 11, 10));
        assertThat(find("SAR", "التاريخ ٢٠٢٥-٠٨-١٦").date()).isEqualTo(LocalDate.of(2025, 8, 16));
    }

    @Test
    void wordsThatStartLikeMonthsAreNotDates() {
        assertThat(find("USD", "1 Market St, San Francisco CA 94105").date()).isNull();
    }

    @Test
    void futureOrMissingDatesAreNotGuessed() {
        assertThat(find("INR", "Date: 01/01/2027").date()).isNull();
        assertThat(find("INR", "TOTAL 45.00").date()).isNull();
    }

    @Test
    void labelledDateWinsOverOtherDates() {
        ReceiptDates.Guess g = find("INR", "Valid till 31/12/2025", "Bill Date: 05/01/2025");
        assertThat(g.date()).isEqualTo(LocalDate.of(2025, 1, 5));
        assertThat(g.labelled()).isTrue();
    }
}
