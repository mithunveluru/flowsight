package com.flowsight.ocr;

import com.flowsight.dto.receipt.ReceiptDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptReconcilerTest {

    private final ReceiptTextParser parser = new ReceiptTextParser(new MerchantExtractor());
    private final ReceiptReconciler reconciler = new ReceiptReconciler();

    private ReceiptTextParser.Parsed pass(String... lines) {
        return parser.read(OcrDocument.fromPlainText(String.join("\n", lines)));
    }

    private ReceiptReconciler.TotalAssessment reconcile(ReceiptTextParser.Parsed... passes) {
        return reconciler.reconcile(List.of(passes));
    }

    @Test
    void indianGstBillWithDiscountAndRoundOffReconciles() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass(
            "CHAI POINT", "Cappuccino 1 180.00 180.00", "Veg Puff 1 75.00 75.00", "Sub Total 255.00",
            "Discount -12.75", "CGST @2.5% 6.06", "SGST @2.5% 6.06", "Round Off -0.37",
            "Net Payable Rs.254.00", "Paid by Card 254.00"));

        assertThat(t.value()).isEqualByComparingTo("254.00");
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ACCEPT);
        assertThat(t.assessment().basis()).contains("arithmetic", "payment");
        assertThat(t.itemsReconciled()).isTrue();
    }

    @Test
    void cashTenderedAndChangeAreNotTheTotal() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass(
            "PANDA", "Dates 1kg 28.00", "Chicken 1kg 11.13", "Subtotal 39.13", "VAT 15% 5.87",
            "Total SAR 45.00", "Cash SAR 50.00", "Change SAR 5.00"));

        assertThat(t.value()).isEqualByComparingTo("45.00");
        assertThat(t.assessment().basis()).contains("payment");
        assertThat(t.conflict()).isFalse();
    }

    @Test
    void totalItemsAndTenderedLinesNeverWin() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass(
            "TOTAL WINE & MORE", "Cabernet 750ML 19.99", "Total Items: 1", "SUBTOTAL 19.99", "SALES TAX 6% 1.20",
            "TOTAL $21.19", "Total Tendered 40.00", "CASH 40.00", "CHANGE 18.81"));

        assertThat(t.value()).isEqualByComparingTo("21.19");
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ACCEPT);
    }

    @Test
    void tipAfterTotalPicksTheBillThatWasPaid() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass(
            "BILL'S DINER", "Burger Deluxe 12.00", "Coffee 2.50", "SUBTOTAL 14.50", "SALES TAX 8% 1.16",
            "TOTAL $15.66", "TIP 2.90", "GRAND TOTAL $18.56", "VISA ****1234 18.56"));

        assertThat(t.value()).isEqualByComparingTo("18.56");
        assertThat(t.conflict()).isFalse();
    }

    @Test
    void taxInclusiveGulfReceipt() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass(
            "LULU", "Almarai Milk 2L 11.50", "Arabic Bread 3.00", "Total Excl. VAT 13.81", "VAT 5% 0.69",
            "Total Incl. VAT AED 14.50", "Card ****1234 14.50"));

        assertThat(t.value()).isEqualByComparingTo("14.50");
        assertThat(t.assessment().confidence()).isGreaterThanOrEqualTo(0.95);
        assertThat(t.itemsReconciled()).isTrue();
    }

    @Test
    void subtotalIsNotAcceptedWhenTaxIsPrinted() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass(
            "CAFE", "Latte 60.00", "Sandwich 40.00", "Subtotal 100.00", "VAT 5% 5.00", "Total 105.00"));

        assertThat(t.value()).isEqualByComparingTo("105.00");
    }

    @Test
    void conflictingWellSupportedTotalsAskTheUser() {
        ReceiptTextParser.Parsed a = pass("SHOP", "Item 1985.60", "Sub Total 1985.60", "CGST 49.63", "SGST 49.63",
            "Round Off -0.86", "Total 2084.00");
        ReceiptTextParser.Parsed b = pass("SHOP", "Item 1985.00", "Sub Total 1985.00", "CGST 49.63", "SGST 49.63",
            "Round Off -0.26", "Total 2084.60", "Paid by UPI 2084.60");

        ReceiptReconciler.TotalAssessment t = reconcile(a, b);

        assertThat(t.conflict()).isTrue();
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ASK_USER);
        assertThat(t.options()).extracting(o -> o.amount().toPlainString()).contains("2084.00", "2084.60");
    }

    @Test
    void paymentInOnePassIsVerifiedByAnotherPassArithmetic() {
        ReceiptTextParser.Parsed a = pass("CVS", "VISA ****7996 56.53");
        ReceiptTextParser.Parsed b = pass("CVS", "Toothpaste 52.04", "SUBTOTAL 52.04", "SALES TAX 8.625% 4.49", "TOTAL $56.55");

        ReceiptReconciler.TotalAssessment t = reconcile(a, b);

        assertThat(t.value()).isEqualByComparingTo("56.53");
        assertThat(t.assessment().basis()).contains("arithmetic", "payment");
        // another pass printed a different total, so it is highlighted rather than accepted
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ACCEPT_WITH_HIGHLIGHT);
    }

    @Test
    void labelOnlyTotalIsNotAccepted() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass("SHOP", "Total 45.00"));

        assertThat(t.value()).isEqualByComparingTo("45.00");
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ASK_USER);
    }

    @Test
    void cashMinusChangeIsOnlyASuggestion() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass("SHOP", "Cash 1,000.00", "Change 447.00"));

        assertThat(t.value()).isEqualByComparingTo("553.00");
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ASK_USER);
        assertThat(t.assessment().basis()).contains("derived");
    }

    @Test
    void garbledTotalThatCashMinusChangeContradictsAsksTheUser() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass("SHOP", "Grand Total Rs 953 200:", "Cash 1,000.00", "Change 447.00"));

        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ASK_USER);
        assertThat(t.options()).extracting(o -> o.amount().toPlainString()).contains("553.00");
    }

    @Test
    void nothingReadMeansNoTotal() {
        ReceiptReconciler.TotalAssessment t = reconcile(pass("SHOP", "Thank you"));

        assertThat(t.value()).isNull();
        assertThat(t.assessment().decision()).isEqualTo(ReceiptDecision.ASK_USER);
    }

    @Test
    void toleranceIsOneCentPerRoundedComponent() {
        assertThat(ReceiptReconciler.tolerance(0)).isEqualByComparingTo("0.00");
        assertThat(ReceiptReconciler.tolerance(2)).isEqualByComparingTo("0.02");
    }
}
