package com.flowsight.dto.receipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OcrExtractionResult {
    private BigDecimal amount;
    // null when no date could be read; the review screen must ask (never defaulted to today)
    private LocalDate date;
    // best cleaned name; merchantRaw is the line as printed
    private String merchant;
    private String merchantRaw;
    private String merchantAddress;
    // ISO code, null when nothing on the receipt indicates the currency
    private String currency;
    private boolean successful;
    private String rawText;
    private List<ReceiptLineItem> lineItems;
    // calibrated probability that amount is right (same as fields.amount.confidence)
    private Double confidence;
    // true when any field's decision is ASK_USER
    private boolean requiresConfirmation;

    private BigDecimal subtotal;
    private List<ReceiptChargeLine> taxes;
    private List<ReceiptChargeLine> discounts;
    private BigDecimal tip;
    private BigDecimal roundOff;
    // line items add up to the subtotal (or the total on tax-inclusive receipts)
    private Boolean itemsReconciled;
    // totals read from the receipt, best first; the user picks one when fields.amount is ASK_USER
    private List<ReceiptAmountOption> amountCandidates;
    // keys: merchant, amount, date, currency
    private Map<String, FieldAssessment> fields;
    // worst decision over fields
    private ReceiptDecision decision;
    // engines tried in order, e.g. "ML_SERVICE: ERROR", "TESSERACT: ok"
    private List<String> engineAttempts;
}
