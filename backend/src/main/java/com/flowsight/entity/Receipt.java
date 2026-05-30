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
