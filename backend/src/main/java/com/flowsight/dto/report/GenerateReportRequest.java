package com.flowsight.dto.report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request to generate a new Intelligence Report.
 *
 * <p>Either supply a {@link #preset} (server picks the dates) or
 * explicit {@link #from} and {@link #to} for a custom range.
 */
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
