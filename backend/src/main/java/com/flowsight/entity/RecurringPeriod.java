package com.flowsight.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Detected payment period with day-range bounds used for interval classification.
 */
@Getter
@RequiredArgsConstructor
public enum RecurringPeriod {
    WEEKLY   ("Weekly",        7,   5,  10, 52),
    BIWEEKLY ("Every 2 weeks", 14, 11,  18, 26),
    MONTHLY  ("Monthly",       30, 25,  40, 12),
    QUARTERLY("Quarterly",     91, 80, 105,  4),
    ANNUAL   ("Annual",       365,340, 395,  1);

    private final String displayName;
    private final int    nominalDays;
    private final int    minDays;
    private final int    maxDays;
    /** Occurrences per year — used to compute annual cost. */
    private final int    annualFrequency;

    public static RecurringPeriod fromDays(int days) {
        for (RecurringPeriod p : values()) {
            if (days >= p.minDays && days <= p.maxDays) return p;
        }
        return null;
    }
}
