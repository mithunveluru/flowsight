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
@Table(name = "recurring_patterns",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "normalized_key", "period"},
        name = "idx_recurring_patterns_user_key_period"
    ))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String merchant;

    @Column(name = "normalized_key", nullable = false, length = 255)
    private String normalizedKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecurringPeriod period;

    @Column(name = "estimated_amount", precision = 15, scale = 4)
    private BigDecimal estimatedAmount;

    @Column(name = "last_seen_date")
    private LocalDate lastSeenDate;

    @Column(name = "next_expected_date")
    private LocalDate nextExpectedDate;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private int occurrenceCount = 0;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "is_cancellation_candidate", nullable = false)
    @Builder.Default
    private boolean isCancellationCandidate = false;

    @Column(name = "is_dismissed", nullable = false)
    @Builder.Default
    private boolean isDismissed = false;

    @Column(name = "is_user_confirmed", nullable = false)
    @Builder.Default
    private boolean isUserConfirmed = false;

    @CreationTimestamp
    @Column(name = "detected_at", updatable = false, nullable = false)
    private Instant detectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringPattern r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
