package com.flowsight.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyParserTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "1,234.50|1234.50",
        "1.234,50|1234.50",
        "1,23,456.00|123456.00",
        "45.00|45.00",
        "45,00|45.00",
        "Rs.254.00|254.00",
        "₹1,23,456.00|123456.00",
        "SAR 45.00|45.00",
        "$29.65|29.65",
        "١٢٣٫٥٠|123.50",
        "١٬٢٣٤٫٥٠|1234.50",
        "۱۲۳٫۵۰|123.50",
        "1, 335.56|1335.56",
        "336 .66|336.66",
        "(12.75)|-12.75",
        "-0.37|-0.37",
        "12.75-|-12.75"
    })
    void parsesReceiptAmountFormats(String raw, String expected) {
        assertThat(MoneyParser.parse(raw)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(expected));
    }

    @Test
    void skipsPercentagesAndKeepsTheAmount() {
        List<MoneyParser.Amount> found = MoneyParser.findAmounts("CGST @2.5% 6.06");
        assertThat(found).extracting(MoneyParser.Amount::value).containsExactly(new BigDecimal("6.06"));
    }

    @Test
    void ignoresNumbersGluedToWords() {
        assertThat(MoneyParser.findAmounts("Total Incl. VAT AED 4,VO")).isEmpty();
        assertThat(MoneyParser.findAmounts("Total Excl. VAT qr4")).isEmpty();
        assertThat(MoneyParser.findAmounts("Almarai Milk 2L")).isEmpty();
    }

    @Test
    void phoneNumbersAreNotAmounts() {
        assertThat(MoneyParser.findAmounts("Ph: +91 6820447270")).noneMatch(MoneyParser.Amount::twoDecimals)
            .noneMatch(a -> a.value().compareTo(new BigDecimal("6820447270")) == 0);
    }

    @Test
    void flagsTwoDecimalAmounts() {
        List<MoneyParser.Amount> found = MoneyParser.findAmounts("Cappuccino 1 180.00 180.00");
        assertThat(found).extracting(MoneyParser.Amount::twoDecimals).containsExactly(false, true, true);
    }

    @Test
    void rejectsAmbiguousOrEmptyInput() {
        assertThat(MoneyParser.parse("")).isEmpty();
        assertThat(MoneyParser.parse("12.00 13.00")).isEmpty();
        assertThat(MoneyParser.parse("n/a")).isEmpty();
    }
}
