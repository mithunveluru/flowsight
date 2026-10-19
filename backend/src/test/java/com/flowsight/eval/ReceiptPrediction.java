package com.flowsight.eval;

import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptLineItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// decision and field_confidence are not scored by score.py; they feed per-field reliability tables.
record ReceiptPrediction(
    String provider,
    String merchant,
    BigDecimal total,
    String date,
    String currency,
    List<Item> lineItems,
    Double confidence,
    boolean requiresConfirmation,
    String decision,
    Map<String, Double> fieldConfidence,
    String error
) {

    record Item(String name, String quantity, BigDecimal price) {}

    static ReceiptPrediction of(String provider, OcrExtractionResult result) {
        List<ReceiptLineItem> items = result.getLineItems() != null ? result.getLineItems() : List.of();
        Map<String, Double> fields = new TreeMap<>();
        if (result.getFields() != null) {
            for (Map.Entry<String, FieldAssessment> e : result.getFields().entrySet()) {
                fields.put(e.getKey(), e.getValue().confidence());
            }
        }
        return new ReceiptPrediction(
            provider,
            result.getMerchant(),
            result.getAmount(),
            result.getDate() != null ? result.getDate().toString() : null,
            result.getCurrency(),
            items.stream().map(i -> new Item(i.getItemName(), i.getItemQuantity(), i.getItemPrice())).toList(),
            result.getConfidence(),
            result.isRequiresConfirmation(),
            result.getDecision() != null ? result.getDecision().name() : null,
            fields,
            null);
    }

    static ReceiptPrediction failed(String error) {
        return new ReceiptPrediction("FAILED", null, null, null, null, List.of(), null, false, null, Map.of(), error);
    }
}
