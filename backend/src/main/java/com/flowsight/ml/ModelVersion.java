package com.flowsight.ml;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String capability;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "first_seen_at", nullable = false, insertable = false, updatable = false)
    private Instant firstSeenAt;
}
