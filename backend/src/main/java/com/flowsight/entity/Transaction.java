package com.flowsight.entity;

import jakarta.persistence.*;
import lombok.*;
import com.flowsight.ml.categorization.CategoryAlternative;
import com.flowsight.ml.categorization.CategorySource;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String merchant;

    // the merchant text as it arrived (statement descriptor, typed name); merchant holds the resolved name
    @Column(name = "merchant_raw", length = 255)
    private String merchantRaw;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private TransactionCategory category;

    @Column(length = 100)
    private String subcategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionSource source;

    // DECIMAL(5,4) in DB — BigDecimal matches the fixed-precision column; Double would fail @Column scale validation
    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", length = 20)
    private CategorySource categorySource;

    @Column(name = "category_model_version", length = 64)
    private String categoryModelVersion;

    // top three model categories with their probabilities, best first
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_alternatives", columnDefinition = "jsonb")
    private List<CategoryAlternative> categoryAlternatives;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_reviewed", nullable = false)
    @Builder.Default
    private boolean reviewed = false;

    // Set when this transaction was extracted from a scanned receipt (nullable for manual/CSV)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction t)) return false;
        return id != null && id.equals(t.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
