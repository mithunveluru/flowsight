package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

// A sustained shift in one category's weekly spend, found by a CUSUM test against the user's own earlier weeks.
@Data
@Builder
public class CategoryDrift {
    private String     category;
    private String     displayName;
    private String     direction;           // UP | DOWN
    private LocalDate  since;               // first day of the week the shift started
    private int        weeksSince;
    private BigDecimal baselineWeekly;      // mean weekly spend in the reference weeks
    private BigDecimal recentWeekly;        // mean weekly spend since the shift
    private BigDecimal monthlyChange;       // (recent - baseline) * 52 / 12, signed
}
