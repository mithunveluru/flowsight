package com.flowsight.merchant;

// Which step of the cascade named the merchant.
public enum ResolutionStep {
    USER_RULE,
    ALIAS,
    FUZZY,
    EMBEDDING,
    LLM,
    CLEANED,
    TYPED,
    NONE
}
