package com.flowsight.ocr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptLabelsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "TOTAL $29.65|TOTAL",
        "Total SAR 45.00|TOTAL",
        "Net Total 286.65|TOTAL",
        "GRAND TOTAL $33.75|GRAND_TOTAL",
        "Net Payable Rs.254.00|GRAND_TOTAL",
        "Total Incl. VAT SAR 45.00|GRAND_TOTAL",
        "Total Excl. VAT 39.13|SUBTOTAL",
        "SUBTOTAL 21.30|SUBTOTAL",
        "Sub-Total 21.30|SUBTOTAL",
        "Total Items: 4|ITEM_COUNT",
        "Total Tendered 40.00|TENDERED",
        "CASH 40.00|TENDERED",
        "CHANGE 6.25|CHANGE",
        "VISA ****1234 21.19|PAYMENT",
        "Paid by UPI 254.00|PAYMENT",
        "mada ****1234 45.00|PAYMENT",
        "CGST @2.5% 6.06|TAX",
        "SALES TAX 8.625% 2.35|TAX",
        "VAT 15% 5.87|TAX",
        "Total GST 12.12|TAX_TOTAL",
        "Discount -12.75|DISCOUNT",
        "Round Off -0.37|ROUND_OFF",
        "TIP 4.10|TIP",
        "Service Charge 10% 25.00|SERVICE_CHARGE",
        "You Saved 15.00|SAVINGS",
        "Seedless Grapes 4.50|OTHER",
        "Cappuccino 1 180.00 180.00|OTHER"
    })
    void classifiesByWholeTokens(String line, ReceiptLabels.Kind kind) {
        assertThat(ReceiptLabels.classify(line)).isEqualTo(kind);
    }
}
