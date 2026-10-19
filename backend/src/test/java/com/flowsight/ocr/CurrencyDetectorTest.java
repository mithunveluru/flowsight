package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyDetectorTest {

    @Test
    void indianGstReceipt() {
        CurrencyDetector.Guess g = CurrencyDetector.detect("CAFE COFFEE DAY\nGSTIN: 29AAACV4807F1Z6\nCGST @2.5% 6.06\nNet Payable Rs.254.00");
        assertThat(g.code()).isEqualTo("INR");
        assertThat(g.confidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void saudiReceiptWithVatNumber() {
        assertThat(CurrencyDetector.detect("PANDA\nKing Fahd Rd, Riyadh\nVAT No: 312345678901233\nTotal SAR 45.00").code())
            .isEqualTo("SAR");
    }

    @Test
    void emiratiReceiptWithTrn() {
        assertThat(CurrencyDetector.detect("LULU HYPERMARKET\nAl Wasl Rd, Dubai\nTRN: 100123456789012\nTotal AED 36.19").code())
            .isEqualTo("AED");
    }

    @Test
    void usReceiptWithSalesTaxAndZip() {
        assertThat(CurrencyDetector.detect("TARGET\nChicago IL 60606\nSALES TAX 10.25% 1.02\nTOTAL $10.97").code())
            .isEqualTo("USD");
    }

    @Test
    void noCueMeansUnknownNotInr() {
        CurrencyDetector.Guess g = CurrencyDetector.detect("CORNER SHOP\nMilk 2.00\nTotal 2.00");
        assertThat(g.code()).isNull();
        assertThat(g.confidence()).isZero();
    }

    @Test
    void lowercaseWordsAndNumbersAreNotAUsZip() {
        assertThat(CurrencyDetector.detect("Bill No 12345\nTotal 50.00").code()).isNull();
    }
}
