package com.flowsight.merchant;

import com.flowsight.entity.TransactionCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

// A user's own mapping from a merchant grouping key to the name and/or category they chose.
@Entity
@Table(name = "user_merchant_rules")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMerchantRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "match_key", nullable = false, length = 255)
    private String matchKey;

    @Column(name = "canonical_name", length = 255)
    private String canonicalName;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private TransactionCategory category;

    @Column(name = "applied_count", nullable = false)
    @Builder.Default
    private int appliedCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
