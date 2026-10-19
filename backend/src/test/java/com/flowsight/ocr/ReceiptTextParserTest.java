package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTextParserTest {

    private final ReceiptTextParser parser = new ReceiptTextParser(new MerchantExtractor());

    private ReceiptTextParser.Parsed read(String... lines) {
        return parser.read(OcrDocument.fromPlainText(String.join("\n", lines)));
    }

    @Test
    void indianColumnsGiveNameQuantityRateAndAmount() {
        List<ReceiptTextParser.Item> items = read("CROMA", "Item Qty Rate Amount",
            "Samsung 55in TV 1 54,990.00 54,990.00", "Maggi 12 Pack 2 168.00 336.00", "Sub Total 55,326.00").items();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("Samsung 55in TV");
        assertThat(items.get(0).amount()).isEqualByComparingTo("54990.00");
        assertThat(items.get(1).name()).isEqualTo("Maggi 12 Pack");
        assertThat(items.get(1).quantity()).isEqualTo(2);
        assertThat(items.get(1).unitPrice()).isEqualByComparingTo("168.00");
    }

    @Test
    void usLeadingQuantityAndGulfTrailingQuantity() {
        List<ReceiptTextParser.Item> us = read("STARBUCKS", "2 Grande Latte 10.90", "SUBTOTAL 10.90").items();
        assertThat(us.get(0).name()).isEqualTo("Grande Latte");
        assertThat(us.get(0).quantity()).isEqualTo(2);

        List<ReceiptTextParser.Item> gulf = read("DANUBE", "Almarai Milk 2L x2 23.00", "Subtotal 23.00").items();
        assertThat(gulf.get(0).name()).isEqualTo("Almarai Milk 2L");
        assertThat(gulf.get(0).quantity()).isEqualTo(2);
    }

    @Test
    void summaryPaymentAndHeaderLinesAreNotItems() {
        ReceiptTextParser.Parsed p = read("SHOP", "Date: 01/02/2025", "Bread 2.99", "SUBTOTAL 2.99", "TOTAL $2.99",
            "VISA ****1234 2.99");

        assertThat(p.items()).extracting(ReceiptTextParser.Item::name).containsExactly("Bread");
        assertThat(p.ofKind(ReceiptLabels.Kind.PAYMENT)).hasSize(1);
    }

    @Test
    void integerTotalsAreReadOnlyOnTotalLines() {
        ReceiptTextParser.Parsed p = read("SHOP", "Table 12", "Biryani 180", "Total 254");

        assertThat(p.ofKind(ReceiptLabels.Kind.TOTAL)).extracting(a -> a.value().toPlainString()).containsExactly("254.00");
        assertThat(p.items()).isEmpty();
    }
}
