package com.flowsight.ml;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "prediction_log")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PredictionLogEntry {

    public enum Mode { SERVE, SHADOW }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // null for predictions not made on behalf of a user (batch jobs)
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 50, updatable = false)
    private String capability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MlOutcome outcome;

    @Column(name = "model_version", length = 64, updatable = false)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", length = 30, updatable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    @Column(precision = 5, scale = 4, updatable = false)
    private BigDecimal confidence;

    @Column(name = "latency_ms", nullable = false, updatable = false)
    private int latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_summary", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> outputSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
