package com.flowsight.ml;

public enum MlOutcome {
    OK,
    TIMEOUT,
    // network failure, 5xx or an unreadable response
    ERROR,
    // 4xx: the service is up but declined (capability disabled there, bad input)
    REJECTED,
    CIRCUIT_OPEN
}
