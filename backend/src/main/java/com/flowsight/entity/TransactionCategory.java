package com.flowsight.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionCategory {
    FOOD_DINING("Food & Dining"),
    GROCERIES("Groceries"),
    SHOPPING("Shopping"),
    TRANSPORTATION("Transportation"),
    UTILITIES("Utilities"),
    ENTERTAINMENT("Entertainment"),
    HEALTHCARE("Healthcare"),
    FINANCE("Finance & Banking"),
    EDUCATION("Education"),
    TRAVEL("Travel"),
    SUBSCRIPTIONS("Subscriptions"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    OTHER("Other"),
    UNCATEGORIZED("Uncategorized");

    private final String displayName;
}
