package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    @Test
    void masksIdentifiersButKeepsPricesAndDates() {
        String text = String.join("\n",
            "HALDIRAM'S SNACKS PVT LTD",
            "Ph: +91 6820447270",
            "GSTIN: 29AAACV4807F1Z6",
            "PAN: AAACV4807F",
            "Tel: (328) 896-3262",
            "VAT No: 312345678901233",
            "TRN: 100123456789012",
            "VISA ****8953 1,335.56",
            "Card 4111 1111 1111 1111",
            "mail: guest@example.com",
            "Date: 18/11/2024 Total 1,23,456.00");

        String out = PiiRedactor.redact(text);

        assertThat(out).doesNotContain("6820447270", "29AAACV4807F1Z6", "AAACV4807F", "896-3262",
            "312345678901233", "100123456789012", "8953", "4111", "guest@example.com");
        assertThat(out).contains("HALDIRAM'S SNACKS", "1,335.56", "18/11/2024", "1,23,456.00");
    }
}
