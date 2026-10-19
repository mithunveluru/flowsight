package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;

public record CategoryAlternative(TransactionCategory category, double probability) {
}
