package com.flowsight.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReceiptStatus status = ReceiptStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Structured extraction results — populated by receipt-ocr LLM service (primary) or Tesseract (fallback)
    @Column(name = "extracted_merchant", length = 255)
    private String extractedMerchant;

    @Column(name = "extracted_amount", precision = 15, scale = 2)
    private BigDecimal extractedAmount;

    @Column(name = "extracted_date")
    private LocalDate extractedDate;

    @Column(name = "line_items_json", columnDefinition = "TEXT")
    private String lineItemsJson;

    @Column(name = "ocr_confidence", precision = 4, scale = 3)
    private BigDecimal ocrConfidence;

    @Column(name = "ocr_provider", length = 30)
    private String ocrProvider;

    @Column(name = "currency", length = 3)
    private String currency;

    // OCR output as first extracted; unlike extracted_* these are not overwritten on confirm
    @Column(name = "predicted_merchant", length = 255)
    private String predictedMerchant;

    @Column(name = "predicted_merchant_raw", length = 255)
    private String predictedMerchantRaw;

    @Column(name = "predicted_amount", precision = 15, scale = 2)
    private BigDecimal predictedAmount;

    @Column(name = "predicted_date")
    private LocalDate predictedDate;

    @Column(name = "predicted_currency", length = 3)
    private String predictedCurrency;

    @Column(name = "merchant_confidence", precision = 4, scale = 3)
    private BigDecimal merchantConfidence;

    @Column(name = "amount_confidence", precision = 4, scale = 3)
    private BigDecimal amountConfidence;

    @Column(name = "date_confidence", precision = 4, scale = 3)
    private BigDecimal dateConfidence;

    @Column(name = "currency_confidence", precision = 4, scale = 3)
    private BigDecimal currencyConfidence;

    @Column(name = "ocr_decision", length = 30)
    private String ocrDecision;

    @Column(name = "ocr_model_version", length = 64)
    private String ocrModelVersion;

    // JSON of the extraction (fields, candidates, charges) without raw text and line items
    @Column(name = "extraction_details", columnDefinition = "TEXT")
    private String extractionDetails;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Receipt r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
