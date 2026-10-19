package com.flowsight.ml.categorization;

/**
 * Where a transaction's category came from. Stored as VARCHAR(20) without a CHECK; null on
 * rows written before V18.
 */
public enum CategorySource {
    /** keyword rules (CategorizationService), also the fallback when ml-service is off or failing */
    RULES,
    /** the global model in ml-service */
    MODEL,
    /** the model blended with the user's own reviewed transactions, or the user's merchant rule */
    PERSONAL,
    /** chosen or corrected by the user */
    USER
}
