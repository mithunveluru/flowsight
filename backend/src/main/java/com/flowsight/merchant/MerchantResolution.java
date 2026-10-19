package com.flowsight.merchant;

import com.flowsight.entity.TransactionCategory;

/**
 * name is null only for step NONE. key is the grouping key recurring detection uses for
 * this name. category is set when one of the user's rules carries a category.
 */
public record MerchantResolution(
    String name,
    String key,
    ResolutionStep step,
    double confidence,
    TransactionCategory category
) {
    public static final MerchantResolution NONE = new MerchantResolution(null, "", ResolutionStep.NONE, 0.0, null);
}
