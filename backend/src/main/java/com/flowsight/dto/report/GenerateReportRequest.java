package com.flowsight.dto.report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// Request to generate a report: either a preset or an explicit from/to range.
@Data
@NoArgsConstructor
public class GenerateReportRequest {

    public enum Preset {
        LAST_7_DAYS,
        LAST_30_DAYS,
        LAST_90_DAYS,
        THIS_MONTH,
        LAST_MONTH,
        THIS_YEAR,
        CUSTOM
    }

    @NotNull
    private Preset preset;

    private LocalDate from;
    private LocalDate to;
}
