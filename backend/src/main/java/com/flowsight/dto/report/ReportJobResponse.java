package com.flowsight.dto.report;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ReportJobResponse {
    private UUID    id;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String  periodLabel;
    private String  status;            // PENDING | GENERATING | READY | FAILED
    private Long    pdfSizeBytes;
    private String  errorMessage;
    private int     downloadCount;
    private Instant createdAt;
    private Instant completedAt;
}
