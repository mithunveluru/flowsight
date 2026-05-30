package com.flowsight.dto.receipt;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class OcrExtractionResult {
    private BigDecimal amount;
    private LocalDate date;
    private String merchant;
    private String merchantAddress;
    private String currency;
    private boolean successful;
    private String rawText;
    private List<ReceiptLineItem> lineItems;
    private Double confidence;
    // true when amount confidence is LOW — the frontend should prompt the user to verify
    private boolean requiresConfirmation;
}
