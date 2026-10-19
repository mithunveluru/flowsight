package com.flowsight.dto.receipt;

/**
 * What the review screen should do with an extracted field: prefill it, prefill it and draw
 * attention to it, or ask the user to supply or pick the value.
 */
public enum ReceiptDecision {
    ACCEPT,
    ACCEPT_WITH_HIGHLIGHT,
    ASK_USER;

    public static final double ACCEPT_AT = 0.90;
    public static final double HIGHLIGHT_AT = 0.60;

    public static ReceiptDecision of(double confidence) {
        if (confidence >= ACCEPT_AT) return ACCEPT;
        if (confidence >= HIGHLIGHT_AT) return ACCEPT_WITH_HIGHLIGHT;
        return ASK_USER;
    }

    public ReceiptDecision worst(ReceiptDecision other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
