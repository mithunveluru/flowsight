package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Summary of the date range and months where the user has transaction data.
 *
 * <p>Used by the frontend to:
 * <ul>
 *   <li>Surface a "you have data here" hint when the current month is empty
 *       but other months contain transactions (typical after CSV import).</li>
 *   <li>Auto-suggest a non-empty default range on the analytics page.</li>
 * </ul>
 */
@Data
@Builder
public class ActivityBoundsResponse {
    private LocalDate    earliestTransactionDate;
    private LocalDate    latestTransactionDate;
    private boolean      currentMonthHasData;
    private long         totalTransactionCount;
    /** Months containing at least one transaction, newest first as "YYYY-MM". */
    private List<String> monthsWithActivity;
}
