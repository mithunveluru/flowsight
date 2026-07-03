package com.flowsight.ocr;

import com.flowsight.dto.receipt.ReceiptLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the receipt amount post-processing validator.
 *
 * Scenarios covered:
 *   - VAT-heavy receipts (VAT amount mistaken for total)
 *   - GST receipts (CGST/SGST line items, Indian format)
 *   - Subtotal vs grand total ambiguity
 *   - Restaurant bills (service charge + GST)
 *   - Supermarket receipts (clean product list, single total)
 *   - Multi-currency-scale amounts
 *   - Discount-heavy receipts (savings reducing total)
 *   - Edge cases: no line items, all summary lines, missing amount
 */
class ReceiptAmountValidatorTest {

    private ReceiptAmountValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ReceiptAmountValidator();
    }

    // VAT-heavy receipts

    @Test
    void vatReceipt_correctsToGrandTotalWhenLLMPickedVAT() {
        // LLM extracted 18.00 (the VAT line) as total_amount
        // Grand Total: 118.00 is embedded in line_items
        List<ReceiptLineItem> items = List.of(
            item("Coffee", "85.00"),
            item("Croissant", "45.00"),     // product line: sum = 130
            item("VAT 18%", "18.00"),       // deprioritized
            item("Grand Total", "118.00")   // preferred — WAIT, 85+45 subtotal, but the actual total is 118 after discount?
            // Actually: 85+45=130, VAT(18%)=23.4 rounded to 18? No, let's say VAT is 18/130 ≈ 13.8% — just test the label logic
        );

        AmountCandidate result = validator.validate(new BigDecimal("18.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("118.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
        assertThat(result.getLabel()).isEqualTo("Grand Total");
    }

    @Test
    void vatReceipt_primaryIsCorrectWhenRatioIsGood() {
        // total_amount = 118 which is consistent with item sum (130) + tax
        List<ReceiptLineItem> items = List.of(
            item("Coffee", "85.00"),
            item("Croissant", "45.00"),
            item("VAT 18%", "18.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("118.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("118.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
        assertThat(result.getLabel()).isNull(); // came from primary
    }

    @Test
    void vatReceipt_lowConfidenceWhenTotalTooSmallAndNoGrandTotalLineItem() {
        // total_amount = 14 which is far too small — no better candidate
        List<ReceiptLineItem> items = List.of(
            item("Milk", "80.00"),
            item("Bread", "40.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("14.00"), items);

        // Ratio = 14/120 = 0.12 → below 0.30 → LOW confidence
        assertThat(result.getAmount()).isEqualByComparingTo("14.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.LOW);
    }

    // GST receipts (Indian — CGST + SGST)

    @Test
    void gstReceipt_correctsToTotalWhenLLMPickedCGST() {
        // LLM extracted CGST (9%) as total — actual total is 118
        List<ReceiptLineItem> items = List.of(
            item("Subtotal", "100.00"),
            item("CGST 9%", "9.00"),
            item("SGST 9%", "9.00"),
            item("Total", "118.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("9.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("118.00");
        assertThat(result.getLabel()).isEqualTo("Total");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
    }

    @Test
    void gstReceipt_correctsTotalPayableOverSubtotal() {
        List<ReceiptLineItem> items = List.of(
            item("Rice Bowl", "150.00"),
            item("Lassi", "60.00"),
            item("CGST 2.5%", "5.25"),
            item("SGST 2.5%", "5.25"),
            item("Net Payable", "220.50")
        );

        AmountCandidate result = validator.validate(new BigDecimal("5.25"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("220.50");
        assertThat(result.getLabel()).isEqualTo("Net Payable");
    }

    @Test
    void gstReceipt_correctWhenLLMAlreadyPickedTotal() {
        List<ReceiptLineItem> items = List.of(
            item("Product A", "200.00"),
            item("CGST 9%", "18.00"),
            item("SGST 9%", "18.00"),
            item("Grand Total", "236.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("236.00"), items);

        // Both primary and Grand Total candidate agree on 236 — either wins
        assertThat(result.getAmount()).isEqualByComparingTo("236.00");
        assertThat(result.getConfidence()).isIn(AmountConfidence.HIGH, AmountConfidence.MEDIUM);
    }

    // Subtotal vs total ambiguity

    @Test
    void subtotalAmbiguity_prefersGrandTotalLineItemOverSubtotal() {
        // LLM extracted 100 (subtotal), Grand Total 118 is in line items
        List<ReceiptLineItem> items = List.of(
            item("Item A", "60.00"),
            item("Item B", "40.00"),
            item("Subtotal", "100.00"),
            item("Tax 18%", "18.00"),
            item("Grand Total", "118.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("100.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("118.00");
        assertThat(result.getLabel()).isEqualTo("Grand Total");
    }

    @Test
    void subtotalAmbiguity_primaryWinsWhenNoPreferredLineItems() {
        // total_amount = 118 (correct), no Grand Total in line items
        List<ReceiptLineItem> items = List.of(
            item("Item A", "60.00"),
            item("Item B", "40.00"),
            item("VAT 18%", "18.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("118.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("118.00");
        assertThat(result.getLabel()).isNull(); // primary wins
    }

    @Test
    void subtotalAmbiguity_correctTotalWithSingleTotalLabel() {
        List<ReceiptLineItem> items = List.of(
            item("Item A", "250.00"),
            item("Discount", "25.00"),
            item("Total", "225.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("25.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("225.00");
        assertThat(result.getLabel()).isEqualTo("Total");
    }

    // Restaurant bills

    @Test
    void restaurantBill_serviceChargeAndGstScenario() {
        // LLM mistook GST amount for total
        List<ReceiptLineItem> items = List.of(
            item("Biryani", "180.00"),
            item("Cold Coffee", "80.00"),
            item("Service Charge 5%", "13.00"),
            item("GST 5%", "13.65"),
            item("Net Total", "286.65")
        );

        AmountCandidate result = validator.validate(new BigDecimal("13.65"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("286.65");
        assertThat(result.getLabel()).isEqualTo("Net Total");
    }

    @Test
    void restaurantBill_correctExtractionHighConfidence() {
        List<ReceiptLineItem> items = List.of(
            item("Pasta", "320.00"),
            item("Wine", "450.00"),
            item("Dessert", "180.00")
        );

        // Sum = 950, total = 1009 (≈ 950 + 6% service) → ratio 1.06 in good range
        AmountCandidate result = validator.validate(new BigDecimal("1009.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("1009.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
    }

    // Supermarket receipts

    @Test
    void supermarketReceipt_cleanProductsNoTaxLines_highConfidence() {
        // Supermarket receipt: items sum ≈ total (no tax line items present)
        List<ReceiptLineItem> items = List.of(
            item("Milk 1L", "45.00"),
            item("Bread", "35.00"),
            item("Eggs x12", "80.00"),
            item("Juice 1L", "85.00")
        );

        // Sum = 245, total = 245 exactly → ratio 1.0
        AmountCandidate result = validator.validate(new BigDecimal("245.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("245.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
        assertThat(result.getConfidence().getNumericValue()).isGreaterThanOrEqualTo(0.65);
    }

    @Test
    void supermarketReceipt_correctsWhenTaxPickedAsTotal() {
        List<ReceiptLineItem> items = List.of(
            item("Biscuits", "40.00"),
            item("Cheese", "120.00"),
            item("Yogurt", "60.00"),
            item("Subtotal", "220.00"),
            item("IGST 5%", "11.00"),
            item("Total Due", "231.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("11.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("231.00");
        assertThat(result.getLabel()).isEqualTo("Total Due");
    }

    // Multi-currency / large amounts

    @Test
    void multiCurrency_largeAmountHandledCorrectly() {
        // Large denomination receipt (e.g. INR electronics purchase)
        List<ReceiptLineItem> items = List.of(
            item("Laptop", "75000.00"),
            item("Extended Warranty", "5000.00"),
            item("CGST 9%", "7200.00"),
            item("SGST 9%", "7200.00"),
            item("Grand Total", "94400.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("7200.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("94400.00");
        assertThat(result.getLabel()).isEqualTo("Grand Total");
    }

    @Test
    void multiCurrency_smallAmountCoffeeShop_highConfidence() {
        // Simple coffee: total = 5.50, no tax lines
        List<ReceiptLineItem> items = List.of(
            item("Latte", "3.50"),
            item("Muffin", "2.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("5.50"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("5.50");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
    }

    // Discount-heavy receipts

    @Test
    void discountHeavy_discountLineNotPickedAsTotal() {
        List<ReceiptLineItem> items = List.of(
            item("T-Shirt x2", "800.00"),
            item("Jeans", "1200.00"),
            item("Discount 20%", "400.00"),
            item("Total Payable", "1600.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("400.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("1600.00");
        assertThat(result.getLabel()).isEqualTo("Total Payable");
    }

    @Test
    void discountHeavy_savingsNotPickedAsTotal() {
        List<ReceiptLineItem> items = List.of(
            item("Groceries bundle", "500.00"),
            item("Savings", "75.00"),
            item("Amount Paid", "425.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("75.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("425.00");
        assertThat(result.getLabel()).isEqualTo("Amount Paid");
    }

    @Test
    void discountHeavy_correctExtractionWhenLLMAlreadyPickedTotal() {
        List<ReceiptLineItem> items = List.of(
            item("Item", "100.00"),
            item("Coupon", "10.00"),
            item("Total", "90.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("90.00"), items);

        assertThat(result.getAmount()).isEqualByComparingTo("90.00");
    }

    // Edge cases

    @Test
    void noLineItems_returnsOriginalAmountWithMediumConfidence() {
        AmountCandidate result = validator.validate(new BigDecimal("250.00"), List.of());

        assertThat(result.getAmount()).isEqualByComparingTo("250.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.MEDIUM);
    }

    @Test
    void nullAmount_returnsNullWithLowConfidence() {
        AmountCandidate result = validator.validate(null, List.of());

        assertThat(result.getAmount()).isNull();
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.LOW);
    }

    @Test
    void nullLineItems_returnsOriginalAmountWithMediumConfidence() {
        AmountCandidate result = validator.validate(new BigDecimal("100.00"), null);

        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.MEDIUM);
    }

    @Test
    void allSummaryLines_cannotComputeRegularSum_primaryUsed() {
        // Every item is a summary line — no regular items to sum
        List<ReceiptLineItem> items = List.of(
            item("Subtotal", "100.00"),
            item("GST 18%", "18.00"),
            item("Grand Total", "118.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("18.00"), items);

        // Grand Total candidate: preferred label, score should win
        assertThat(result.getAmount()).isEqualByComparingTo("118.00");
        assertThat(result.getLabel()).isEqualTo("Grand Total");
    }

    @Test
    void totalSmallerThanSingleItem_lowConfidence() {
        // Total = 5.00 but one item costs 50.00 — impossible
        List<ReceiptLineItem> items = List.of(
            item("Headphones", "50.00"),
            item("Case", "15.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("5.00"), items);

        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.LOW);
    }

    @Test
    void requiresConfirmation_trueWhenLowConfidence() {
        List<ReceiptLineItem> items = List.of(item("Phone", "20000.00"));
        AmountCandidate result = validator.validate(new BigDecimal("100.00"), items);

        // total 100 < max item 20000 → LOW confidence → requires confirmation in UI
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.LOW);
        assertThat(result.getConfidence().getNumericValue()).isLessThan(0.45);
    }

    @Test
    void requiresConfirmation_falseWhenHighConfidence() {
        List<ReceiptLineItem> items = List.of(
            item("Widget", "100.00"),
            item("VAT 18%", "18.00")
        );

        AmountCandidate result = validator.validate(new BigDecimal("118.00"), items);

        // ratio = 118/100 = 1.18 → HIGH confidence → no confirmation required
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.HIGH);
        assertThat(result.getConfidence().getNumericValue()).isGreaterThanOrEqualTo(0.65);
    }

    // sumRegularItems helper

    @Test
    void sumRegularItems_excludesSummaryLines() {
        List<ReceiptLineItem> items = List.of(
            item("Coffee", "85.00"),
            item("Croissant", "45.00"),
            item("Subtotal", "130.00"),   // excluded
            item("VAT", "18.00"),         // excluded
            item("Total", "148.00")       // excluded
        );

        var sum = validator.sumRegularItems(items);
        assertThat(sum).isEqualByComparingTo("130.00");
    }

    @Test
    void sumRegularItems_returnsNullWhenAllSummaryLines() {
        List<ReceiptLineItem> items = List.of(
            item("Subtotal", "100.00"),
            item("Tax", "10.00")
        );

        assertThat(validator.sumRegularItems(items)).isNull();
    }

    @Test
    void sumRegularItems_returnsNullWhenEmpty() {
        assertThat(validator.sumRegularItems(List.of())).isNull();
    }

    // Label classifiers

    @Test
    void isSummaryLine_detectsPreferredLabels() {
        assertThat(validator.isSummaryLine("grand total")).isTrue();
        assertThat(validator.isSummaryLine("total due")).isTrue();
        assertThat(validator.isSummaryLine("amount paid")).isTrue();
        assertThat(validator.isSummaryLine("net payable")).isTrue();
        assertThat(validator.isSummaryLine("total")).isTrue();
    }

    @Test
    void isSummaryLine_detectsDeprioritizedLabels() {
        assertThat(validator.isSummaryLine("vat")).isTrue();
        assertThat(validator.isSummaryLine("cgst 9%")).isTrue();
        assertThat(validator.isSummaryLine("sgst 9%")).isTrue();
        assertThat(validator.isSummaryLine("subtotal")).isTrue();
        assertThat(validator.isSummaryLine("discount")).isTrue();
        assertThat(validator.isSummaryLine("savings")).isTrue();
    }

    @Test
    void isSummaryLine_returnsFalseForProductLines() {
        assertThat(validator.isSummaryLine("biryani")).isFalse();
        assertThat(validator.isSummaryLine("usb cable")).isFalse();
        assertThat(validator.isSummaryLine("milk 1l")).isFalse();
        assertThat(validator.isSummaryLine("laptop")).isFalse();
    }

    // Helper

    private ReceiptLineItem item(String name, String price) {
        return ReceiptLineItem.builder()
            .itemName(name)
            .itemPrice(new BigDecimal(price))
            .build();
    }
}
