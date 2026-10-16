package com.flowsight.onboarding;

public record SampleDataStatus(boolean present, long transactionCount) {

    static SampleDataStatus of(long count) {
        return new SampleDataStatus(count > 0, count);
    }
}
