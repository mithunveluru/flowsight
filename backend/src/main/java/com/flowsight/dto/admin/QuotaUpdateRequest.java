package com.flowsight.dto.admin;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QuotaUpdateRequest {
    /** New per-user receipt limit. Ignored when {@link #unlimited} is true. */
    @Min(0)
    private Integer receiptLimit;

    /** When true, the user bypasses all quota checks. */
    private Boolean unlimited;
}
