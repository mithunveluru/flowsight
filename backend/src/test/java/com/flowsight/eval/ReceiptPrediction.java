package com.flowsight.eval;

import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptLineItem;

import java.math.BigDecimal;
import java.util.List;

record ReceiptPrediction(
    String provider,
    String merchant,
    BigDecimal total,
    String date,
    String currency,
    List<Item> lineItems,
    Double confidence,
    boolean requiresConfirmation,
    String error
) {

    record Item(String name, String quantity, BigDecimal price) {}

    static ReceiptPrediction of(String provider, OcrExtractionResult result) {
        List<ReceiptLineItem> items = result.getLineItems() != null ? result.getLineItems() : List.of();
        return new ReceiptPrediction(
            provider,
            result.getMerchant(),
            result.getAmount(),
            result.getDate() != null ? result.getDate().toString() : null,
            result.getCurrency(),
            items.stream().map(i -> new Item(i.getItemName(), i.getItemQuantity(), i.getItemPrice())).toList(),
            result.getConfidence(),
            result.isRequiresConfirmation(),
            null);
    }

    static ReceiptPrediction failed(String error) {
        return new ReceiptPrediction("FAILED", null, null, null, null, List.of(), null, false, error);
    }
}
