package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

// Date range and months where the user has data; UI uses it to hint and default the range.
@Data
@Builder
public class ActivityBoundsResponse {
    private LocalDate    earliestTransactionDate;
    private LocalDate    latestTransactionDate;
    private boolean      currentMonthHasData;
    private long         totalTransactionCount;
    // Months containing at least one transaction, newest first as "YYYY-MM".
    private List<String> monthsWithActivity;
}
