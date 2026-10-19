package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;

/** One of the user's reviewed transactions and how similar it is to the one being categorized. */
public record Neighbour(TransactionCategory category, double similarity) {
}
