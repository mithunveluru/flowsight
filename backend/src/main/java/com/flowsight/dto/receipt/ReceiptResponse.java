package com.flowsight.dto.receipt;

import com.flowsight.dto.transaction.TransactionResponse;
import com.flowsight.entity.ReceiptStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReceiptResponse {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private ReceiptStatus status;
    private String ocrText;
    private String errorMessage;
    private OcrExtractionResult extraction;
    private TransactionResponse transaction;
    private List<ReceiptLineItem> lineItems;
    private String ocrProvider;
    private Double confidence;
    private boolean requiresConfirmation;
    // receipt currency: the confirmed one once confirmed, else the detected one (null if unknown)
    private String currency;
    // worst per-field decision of the extraction; see extraction.fields
    private ReceiptDecision decision;
    private Instant createdAt;
}
